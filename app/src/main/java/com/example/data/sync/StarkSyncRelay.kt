package com.example.data.sync

import android.content.Context
import android.util.Base64
import android.util.Log
import com.example.data.model.MessageEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Custom DNS resolver that prioritizes IPv4 addresses over IPv6.
 * Resolves connection failures in Android emulator / container environments where
 * IPv6 addresses are advertised via DNS but outbound IPv6 routing is unsupported.
 */
object PreferIpv4Dns : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        return try {
            val addresses = Dns.SYSTEM.lookup(hostname)
            val ipv4 = addresses.filterIsInstance<Inet4Address>()
            val ipv6 = addresses.filterIsInstance<Inet6Address>()
            if (ipv4.isNotEmpty()) {
                ipv4 + ipv6
            } else {
                addresses
            }
        } catch (e: Exception) {
            Dns.SYSTEM.lookup(hostname)
        }
    }
}

enum class SyncState {
    CONNECTING,
    CONNECTED,
    OFFLINE
}

class StarkSyncRelay(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val tag = "StarkSyncRelay"

    private val sseClient = OkHttpClient.Builder()
        .dns(PreferIpv4Dns)
        .retryOnConnectionFailure(true)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val httpClient = OkHttpClient.Builder()
        .dns(PreferIpv4Dns)
        .retryOnConnectionFailure(true)
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val _syncState = MutableStateFlow(SyncState.CONNECTING)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val _peerLastSeenTime = MutableStateFlow(0L)
    val peerLastSeenTime: StateFlow<Long> = _peerLastSeenTime.asStateFlow()

    private val _isPeerTyping = MutableStateFlow(false)
    val isPeerTyping: StateFlow<Boolean> = _isPeerTyping.asStateFlow()

    private var activeRoomCode: String? = null
    private var activeRoomId: String? = null
    private var mySenderId: String = "tony_stark"

    private var listenerJob: Job? = null
    private var heartbeatJob: Job? = null
    private var typingResetJob: Job? = null

    private var onNewRemoteMessage: (suspend (MessageEntity) -> Unit)? = null
    private var onRemoteReaction: (suspend (cipherBase64: String, emoji: String, user: String) -> Unit)? = null
    private var onRemoteDelete: (suspend (cipherBase64: String) -> Unit)? = null
    private var onRemoteReadReceipt: (suspend (readerId: String, readTimestamp: Long) -> Unit)? = null

    fun setCallbacks(
        onMessage: suspend (MessageEntity) -> Unit,
        onReaction: suspend (cipherBase64: String, emoji: String, user: String) -> Unit,
        onDelete: suspend (cipherBase64: String) -> Unit,
        onReadReceipt: suspend (readerId: String, readTimestamp: Long) -> Unit
    ) {
        this.onNewRemoteMessage = onMessage
        this.onRemoteReaction = onReaction
        this.onRemoteDelete = onDelete
        this.onRemoteReadReceipt = onReadReceipt
    }

    fun startListening(roomCode: String, roomId: String, senderId: String) {
        if (activeRoomCode == roomCode && listenerJob?.isActive == true) {
            mySenderId = senderId
            return
        }

        activeRoomCode = roomCode
        activeRoomId = roomId
        mySenderId = senderId

        listenerJob?.cancel()
        heartbeatJob?.cancel()

        listenerJob = scope.launch(Dispatchers.IO) {
            val topic = getTopicForRoom(roomCode)
            Log.d(tag, "Subscribing to sync channel for #$roomCode ($topic)")

            // 1. Catchup poll to sync chat over internet (fetch previous messages)
            try {
                _syncState.value = SyncState.CONNECTING
                fetchCatchupMessages(topic)
            } catch (e: Exception) {
                Log.w(tag, "Catchup poll error: ${e.message}")
            }

            // 2. Start Periodic Presence Heartbeat (every 10s for responsive active/inactive status)
            heartbeatJob = scope.launch(Dispatchers.IO) {
                while (isActive) {
                    try {
                        publishHeartbeat(roomCode, mySenderId)
                    } catch (_: Exception) {}
                    delay(10_000L) // every 10 seconds
                }
            }

            // 3. Persistent Realtime SSE Stream
            while (isActive) {
                try {
                    val streamUrl = "https://ntfy.sh/$topic/json?since=10m"
                    val request = Request.Builder()
                        .url(streamUrl)
                        .header("Accept", "application/x-ndjson")
                        .build()

                    sseClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            _syncState.value = SyncState.OFFLINE
                            delay(3000)
                            return@use
                        }

                        _syncState.value = SyncState.CONNECTED
                        val source = response.body?.byteStream() ?: return@use
                        val reader = BufferedReader(InputStreamReader(source))

                        while (isActive) {
                            val line = reader.readLine() ?: break
                            val jsonLine = line.trim()
                            if (jsonLine.isEmpty()) continue

                            try {
                                val ntfyEvent = JSONObject(jsonLine)
                                val eventType = ntfyEvent.optString("event")
                                if (eventType == "message") {
                                    val rawMessageStr = ntfyEvent.optString("message")
                                    val attachmentObj = ntfyEvent.optJSONObject("attachment")
                                    val attachmentUrl = attachmentObj?.optString("url")

                                    handleIncomingRawRelay(rawMessageStr, attachmentUrl)
                                }
                            } catch (e: Exception) {
                                Log.w(tag, "Error parsing stream event: ${e.message}")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(tag, "SSE connection dropped: ${e.message}. Reconnecting in 3s...")
                    _syncState.value = SyncState.OFFLINE
                    delay(3000)
                }
            }
        }
    }

    fun stopListening() {
        listenerJob?.cancel()
        listenerJob = null
        heartbeatJob?.cancel()
        heartbeatJob = null
        _syncState.value = SyncState.OFFLINE
    }

    private suspend fun fetchCatchupMessages(topic: String) {
        val pollUrl = "https://ntfy.sh/$topic/json?poll=1&since=72h"
        val request = Request.Builder().url(pollUrl).build()

        httpClient.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                val bodyStr = response.body?.string() ?: return
                bodyStr.lines().forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty()) {
                        try {
                            val eventObj = JSONObject(trimmed)
                            if (eventObj.optString("event") == "message") {
                                val msgText = eventObj.optString("message")
                                val attachmentObj = eventObj.optJSONObject("attachment")
                                val attachUrl = attachmentObj?.optString("url")
                                handleIncomingRawRelay(msgText, attachUrl)
                            }
                        } catch (_: Exception) {}
                    }
                }
            }
        }
    }

    private suspend fun handleIncomingRawRelay(rawMessage: String, attachmentUrl: String?) {
        if (rawMessage.isBlank() && attachmentUrl.isNullOrBlank()) return

        // Check if rawMessage is base64 encoded packet or direct json
        var parsedJson = rawMessage
        if (rawMessage.startsWith("BASE64:")) {
            try {
                val b64 = rawMessage.removePrefix("BASE64:")
                parsedJson = String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8)
            } catch (_: Exception) {}
        }

        handleIncomingRelayPayload(parsedJson, attachmentUrl)
    }

    private suspend fun handleIncomingRelayPayload(rawJson: String, attachmentUrl: String? = null) {
        try {
            if (!rawJson.startsWith("{")) {
                // If it's a raw attachment notification without json text, ignore or skip
                return
            }
            val packet = JSONObject(rawJson)
            val action = packet.optString("action", "MESSAGE")
            val payload = packet.optJSONObject("payload") ?: return

            val remoteSenderId = payload.optString("senderId")
            // Ignore echoes of our own actions
            if (remoteSenderId == mySenderId) {
                return
            }

            // Update peer last seen presence timestamp
            val now = System.currentTimeMillis()
            _peerLastSeenTime.value = now

            when (action) {
                "HEARTBEAT" -> {
                    // Handled: peer last seen updated
                }

                "TYPING" -> {
                    _isPeerTyping.value = true
                    typingResetJob?.cancel()
                    typingResetJob = scope.launch {
                        delay(4000)
                        _isPeerTyping.value = false
                    }
                }

                "MESSAGE" -> {
                    val currentRoom = activeRoomId ?: return
                    val cipher = payload.optString("cipherTextBase64")
                    val iv = payload.optString("ivBase64")
                    val salt = payload.optString("saltBase64")
                    val fingerprint = payload.optString("keyFingerprint")
                    val senderName = payload.optString("senderName", "Partner")
                    val messageType = payload.optString("messageType", "TEXT")
                    val mediaBase64 = payload.optString("mediaBase64", "")
                    val remoteMediaUrl = payload.optString("remoteMediaUrl", "")
                    val voiceDuration = payload.optInt("voiceDurationSec", 0)
                    val waveform = payload.optString("voiceWaveform", "")
                    val timestamp = payload.optLong("timestamp", System.currentTimeMillis())
                    val replyToText = payload.optString("replyToText", null)
                    val isSelfDestruct = payload.optBoolean("isSelfDestruct", false)
                    val selfDestructSec = payload.optInt("selfDestructSec", 0)

                    var localMediaPath: String? = null

                    // 1. Decode base64 media if attached directly in payload (Instant zero-lag playback)
                    if (mediaBase64.isNotBlank()) {
                        try {
                            val decodedBytes = Base64.decode(mediaBase64, Base64.DEFAULT)
                            val isVideo = messageType == "VIDEO"
                            val isVoice = messageType == "VOICE"
                            val isGif = messageType == "GIF"
                            val ext = if (isVideo) "mp4" else if (isVoice) "m4a" else if (isGif) "gif" else "jpg"
                            val mediaDir = File(context.filesDir, "vault_media").apply { mkdirs() }
                            val targetFile = File(mediaDir, "sync_${timestamp}_${remoteSenderId.hashCode()}.$ext")
                            FileOutputStream(targetFile).use { fos ->
                                fos.write(decodedBytes)
                            }
                            localMediaPath = targetFile.absolutePath
                        } catch (e: Exception) {
                            Log.e(tag, "Failed to write decoded base64 media: ${e.message}")
                        }
                    }

                    // 2. Download from remote attachment url if not embedded as base64
                    val downloadTargetUrl = if (!attachmentUrl.isNullOrBlank()) attachmentUrl else if (remoteMediaUrl.isNotBlank() && remoteMediaUrl.startsWith("http")) remoteMediaUrl else null

                    if (localMediaPath == null && downloadTargetUrl != null) {
                        try {
                            val isVideo = messageType == "VIDEO"
                            val isVoice = messageType == "VOICE"
                            val isGif = messageType == "GIF"
                            val ext = if (isVideo) "mp4" else if (isVoice) "m4a" else if (isGif) "gif" else "jpg"
                            val mediaDir = File(context.filesDir, "vault_media").apply { mkdirs() }
                            val targetFile = File(mediaDir, "dl_${timestamp}_${remoteSenderId.hashCode()}.$ext")

                            val downloadReq = Request.Builder().url(downloadTargetUrl).build()
                            httpClient.newCall(downloadReq).execute().use { dlResp ->
                                if (dlResp.isSuccessful) {
                                    dlResp.body?.byteStream()?.use { input ->
                                        FileOutputStream(targetFile).use { output ->
                                            input.copyTo(output)
                                        }
                                    }
                                    localMediaPath = targetFile.absolutePath
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(tag, "Failed to download remote attachment: ${e.message}")
                        }
                    }

                    // Fallback to remote URL if file download failed so Coil can still stream it
                    if (localMediaPath == null && downloadTargetUrl != null) {
                        localMediaPath = downloadTargetUrl
                    }

                    val burnTime = if (isSelfDestruct) timestamp + (selfDestructSec * 1000L) else null

                    val newEntity = MessageEntity(
                        roomId = currentRoom,
                        senderId = remoteSenderId,
                        senderName = senderName,
                        cipherTextBase64 = cipher,
                        ivBase64 = iv,
                        saltBase64 = salt,
                        keyFingerprint = fingerprint,
                        messageType = messageType,
                        mediaUrl = localMediaPath,
                        voiceDurationSeconds = voiceDuration,
                        voiceWaveform = waveform,
                        timestamp = timestamp,
                        isRead = false,
                        reactionsJson = "{}",
                        replyToId = null,
                        replyToText = replyToText,
                        isSelfDestruct = isSelfDestruct,
                        selfDestructDurationSec = selfDestructSec,
                        burnTimestamp = burnTime,
                        isScheduled = false,
                        scheduledTime = null,
                        isScheduledDispatched = true
                    )

                    onNewRemoteMessage?.invoke(newEntity)
                }

                "REACTION" -> {
                    val cipher = payload.optString("cipherTextBase64")
                    val emoji = payload.optString("emoji")
                    val user = payload.optString("user", remoteSenderId)
                    if (cipher.isNotEmpty() && emoji.isNotEmpty()) {
                        onRemoteReaction?.invoke(cipher, emoji, user)
                    }
                }

                "DELETE" -> {
                    val cipher = payload.optString("cipherTextBase64")
                    if (cipher.isNotEmpty()) {
                        onRemoteDelete?.invoke(cipher)
                    }
                }

                "READ" -> {
                    val readerId = payload.optString("senderId")
                    val readTimestamp = payload.optLong("timestamp", System.currentTimeMillis())
                    onRemoteReadReceipt?.invoke(readerId, readTimestamp)
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error handling incoming payload: ${e.message}")
        }
    }

    suspend fun publishMessage(
        roomCode: String,
        senderId: String,
        senderName: String,
        cipherTextBase64: String,
        ivBase64: String,
        saltBase64: String,
        keyFingerprint: String,
        messageType: String = "TEXT",
        mediaFilePath: String? = null,
        voiceDurationSec: Int = 0,
        voiceWaveform: String = "",
        timestamp: Long = System.currentTimeMillis(),
        replyToText: String? = null,
        isSelfDestruct: Boolean = false,
        selfDestructSec: Int = 0
    ) {
        scope.launch(Dispatchers.IO) {
            try {
                val topic = getTopicForRoom(roomCode)
                var mediaBase64 = ""

                if (!mediaFilePath.isNullOrBlank()) {
                    try {
                        val file = File(mediaFilePath)
                        if (file.exists()) {
                            val fileBytes = file.readBytes()
                            // Embed base64 directly up to 800KB for zero-latency instant peer transmission
                            if (file.length() < 800 * 1024) {
                                mediaBase64 = Base64.encodeToString(fileBytes, Base64.NO_WRAP)
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(tag, "Could not read media file for inline encoding: ${e.message}")
                    }
                }

                val payloadObj = JSONObject().apply {
                    put("senderId", senderId)
                    put("senderName", senderName)
                    put("cipherTextBase64", cipherTextBase64)
                    put("ivBase64", ivBase64)
                    put("saltBase64", saltBase64)
                    put("keyFingerprint", keyFingerprint)
                    put("messageType", messageType)
                    put("mediaBase64", mediaBase64)
                    put("remoteMediaUrl", "")
                    put("voiceDurationSec", voiceDurationSec)
                    put("voiceWaveform", voiceWaveform)
                    put("timestamp", timestamp)
                    put("replyToText", replyToText)
                    put("isSelfDestruct", isSelfDestruct)
                    put("selfDestructSec", selfDestructSec)
                }

                val packet = JSONObject().apply {
                    put("action", "MESSAGE")
                    put("payload", payloadObj)
                }

                // Immediately post message packet to relay without blocking on external file hosting
                postToRelay(topic, packet.toString())
            } catch (e: Exception) {
                Log.e(tag, "Failed to publish message: ${e.message}")
            }
        }
    }

    private fun uploadFileToRelay(topic: String, file: File): String? {
        return try {
            val url = "https://ntfy.sh/$topic"
            val bytes = file.readBytes()
            val mediaTypeString = when {
                file.name.endsWith(".mp4", true) -> "video/mp4"
                file.name.endsWith(".m4a", true) || file.name.endsWith(".aac", true) -> "audio/mp4"
                file.name.endsWith(".gif", true) -> "image/gif"
                file.name.endsWith(".png", true) -> "image/png"
                file.name.endsWith(".webp", true) -> "image/webp"
                else -> "image/jpeg"
            }

            val requestBody = bytes.toRequestBody(mediaTypeString.toMediaType())
            val req = Request.Builder()
                .url(url)
                .put(requestBody)
                .header("Filename", file.name)
                .header("Title", "STARK_ATTACHMENT")
                .header("Message", "MEDIA_ATTACHMENT")
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val respBody = resp.body?.string() ?: ""
                    try {
                        val json = JSONObject(respBody)
                        val attach = json.optJSONObject("attachment")
                        attach?.optString("url")
                    } catch (_: Exception) {
                        null
                    }
                } else null
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to upload file to relay: ${e.message}")
            null
        }
    }

    suspend fun publishHeartbeat(roomCode: String, senderId: String) {
        val topic = getTopicForRoom(roomCode)
        val packet = JSONObject().apply {
            put("action", "HEARTBEAT")
            put("payload", JSONObject().apply {
                put("senderId", senderId)
                put("timestamp", System.currentTimeMillis())
            })
        }
        postToRelay(topic, packet.toString())
    }

    suspend fun publishTyping(roomCode: String, senderId: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val topic = getTopicForRoom(roomCode)
                val packet = JSONObject().apply {
                    put("action", "TYPING")
                    put("payload", JSONObject().apply {
                        put("senderId", senderId)
                        put("timestamp", System.currentTimeMillis())
                    })
                }
                postToRelay(topic, packet.toString())
            } catch (_: Exception) {}
        }
    }

    suspend fun publishReaction(roomCode: String, cipherTextBase64: String, emoji: String, senderId: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val topic = getTopicForRoom(roomCode)
                val packet = JSONObject().apply {
                    put("action", "REACTION")
                    put("payload", JSONObject().apply {
                        put("senderId", senderId)
                        put("cipherTextBase64", cipherTextBase64)
                        put("emoji", emoji)
                        put("user", senderId)
                    })
                }
                postToRelay(topic, packet.toString())
            } catch (e: Exception) {
                Log.e(tag, "Failed to publish reaction: ${e.message}")
            }
        }
    }

    suspend fun publishDelete(roomCode: String, cipherTextBase64: String, senderId: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val topic = getTopicForRoom(roomCode)
                val packet = JSONObject().apply {
                    put("action", "DELETE")
                    put("payload", JSONObject().apply {
                        put("senderId", senderId)
                        put("cipherTextBase64", cipherTextBase64)
                    })
                }
                postToRelay(topic, packet.toString())
            } catch (e: Exception) {
                Log.e(tag, "Failed to publish delete: ${e.message}")
            }
        }
    }

    suspend fun publishReadReceipt(roomCode: String, senderId: String, readTimestamp: Long) {
        scope.launch(Dispatchers.IO) {
            try {
                val topic = getTopicForRoom(roomCode)
                val packet = JSONObject().apply {
                    put("action", "READ")
                    put("payload", JSONObject().apply {
                        put("senderId", senderId)
                        put("timestamp", readTimestamp)
                    })
                }
                postToRelay(topic, packet.toString())
            } catch (e: Exception) {
                Log.e(tag, "Failed to publish read receipt: ${e.message}")
            }
        }
    }

    private fun postToRelay(topic: String, bodyJson: String) {
        val base64Body = "BASE64:" + Base64.encodeToString(bodyJson.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val requestBody = base64Body.toRequestBody("text/plain; charset=utf-8".toMediaType())
        val url = "https://ntfy.sh/$topic"

        var attempts = 0
        val maxAttempts = 3
        while (attempts < maxAttempts) {
            attempts++
            try {
                val req = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .header("Title", "STARK_COMM")
                    .build()

                httpClient.newCall(req).execute().use { response ->
                    if (response.isSuccessful) {
                        return
                    } else {
                        Log.w(tag, "postToRelay returned code ${response.code} (attempt $attempts/$maxAttempts)")
                    }
                }
            } catch (e: Exception) {
                Log.w(tag, "postToRelay attempt $attempts failed: ${e.message}")
                if (attempts < maxAttempts) {
                    try { Thread.sleep(500L * attempts) } catch (_: Exception) {}
                }
            }
        }
    }

    companion object {
        fun getTopicForRoom(roomCode: String): String {
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest("STARK_ROOM_SECRET_KEY_${roomCode.trim().uppercase()}".toByteArray(Charsets.UTF_8))
            val hex = digest.take(12).joinToString("") { "%02x".format(it) }
            return "stark_vlt_$hex"
        }
    }
}
