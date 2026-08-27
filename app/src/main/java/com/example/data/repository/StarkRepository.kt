package com.example.data.repository

import com.example.data.crypto.CryptoEngine
import com.example.data.crypto.EncryptedPayload
import com.example.data.local.CalcDao
import com.example.data.local.ChatDao
import com.example.data.model.CalcHistoryEntity
import com.example.data.model.ChatRoomEntity
import com.example.data.model.DecryptedMessage
import com.example.data.model.MessageEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.json.JSONObject

class StarkRepository(
    private val chatDao: ChatDao,
    private val calcDao: CalcDao
) {
    fun getHistory(): Flow<List<CalcHistoryEntity>> = calcDao.getHistory()

    suspend fun saveCalculation(expression: String, result: String) {
        calcDao.insertHistory(
            CalcHistoryEntity(
                expression = expression,
                result = result,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun clearCalcHistory() {
        calcDao.clearHistory()
    }

    fun getAllRooms(): Flow<List<ChatRoomEntity>> = chatDao.getAllRooms()

    suspend fun getOrCreateRoom(roomCode: String, roomName: String? = null): ChatRoomEntity {
        val normalizedCode = roomCode.trim().uppercase()
        val existing = chatDao.getRoomByCode(normalizedCode)
        if (existing != null) {
            return existing
        }
        val defaultName = roomName ?: when (normalizedCode) {
            "MARK-85", "3000", "1970" -> "Stark & Pepper Secure Subnet"
            else -> "Room $normalizedCode"
        }
        val newRoom = ChatRoomEntity(
            roomId = "room_${System.currentTimeMillis()}_${normalizedCode.hashCode()}",
            roomCode = normalizedCode,
            roomName = defaultName,
            roomTheme = "MARK-85",
            description = "E2EE Encrypted Channel (AES-256-GCM)",
            createdTimestamp = System.currentTimeMillis(),
            lastActiveTimestamp = System.currentTimeMillis()
        )
        chatDao.insertOrUpdateRoom(newRoom)
        return newRoom
    }

    fun getDecryptedMessages(roomId: String, roomCode: String): Flow<List<DecryptedMessage>> {
        return chatDao.getMessagesForRoom(roomId).map { entities ->
            val currentTime = System.currentTimeMillis()
            entities.map { entity ->
                val payload = EncryptedPayload(
                    cipherTextBase64 = entity.cipherTextBase64,
                    ivBase64 = entity.ivBase64,
                    saltBase64 = entity.saltBase64,
                    keyFingerprint = entity.keyFingerprint
                )
                val decryptedText = CryptoEngine.decrypt(payload, roomCode)
                val isSuccess = !decryptedText.startsWith("[ENCRYPTED_PAYLOAD")

                // Parse reactions json e.g. {"❤️": 2, "🔥": 1}
                val reactionsMap = mutableMapOf<String, Int>()
                val myReactionsSet = mutableSetOf<String>()
                try {
                    val jsonObj = JSONObject(entity.reactionsJson)
                    val keys = jsonObj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val arr = jsonObj.optJSONArray(key)
                        if (arr != null) {
                            reactionsMap[key] = arr.length()
                            for (i in 0 until arr.length()) {
                                if (arr.optString(i) == "current_user") {
                                    myReactionsSet.add(key)
                                }
                            }
                        } else {
                            val count = jsonObj.optInt(key, 0)
                            if (count > 0) reactionsMap[key] = count
                        }
                    }
                } catch (_: Exception) {}

                val waveform = if (entity.voiceWaveform.isNotEmpty()) {
                    entity.voiceWaveform.split(",").mapNotNull { it.trim().toFloatOrNull() }
                } else emptyList()

                DecryptedMessage(
                    entity = entity,
                    plainText = decryptedText,
                    isDecryptedSuccess = isSuccess,
                    reactions = reactionsMap,
                    myReactions = myReactionsSet,
                    waveformList = waveform
                )
            }
        }
    }

    fun getScheduledMessages(roomId: String): Flow<List<MessageEntity>> =
        chatDao.getScheduledMessagesForRoom(roomId)

    suspend fun sendMessage(
        roomId: String,
        roomCode: String,
        senderId: String,
        senderName: String,
        plainText: String,
        messageType: String = "TEXT",
        mediaUrl: String? = null,
        voiceDurationSec: Int = 0,
        voiceWaveform: List<Float> = emptyList(),
        replyToId: Long? = null,
        replyToText: String? = null,
        isSelfDestruct: Boolean = false,
        selfDestructSec: Int = 0,
        scheduledDelayMs: Long? = null
    ): Pair<Long, MessageEntity> {
        val encrypted = CryptoEngine.encrypt(plainText, roomCode.trim().uppercase())
        val waveformStr = voiceWaveform.joinToString(",") { "%.2f".format(it) }
        val now = System.currentTimeMillis()

        val isScheduled = scheduledDelayMs != null && scheduledDelayMs > 0
        val scheduledTime = if (isScheduled) now + scheduledDelayMs!! else null

        val messageEntity = MessageEntity(
            roomId = roomId,
            senderId = senderId,
            senderName = senderName,
            cipherTextBase64 = encrypted.cipherTextBase64,
            ivBase64 = encrypted.ivBase64,
            saltBase64 = encrypted.saltBase64,
            keyFingerprint = encrypted.keyFingerprint,
            messageType = messageType,
            mediaUrl = mediaUrl,
            voiceDurationSeconds = voiceDurationSec,
            voiceWaveform = waveformStr,
            timestamp = if (isScheduled) scheduledTime!! else now,
            isRead = false,
            reactionsJson = "{}",
            replyToId = replyToId,
            replyToText = replyToText,
            isSelfDestruct = isSelfDestruct,
            selfDestructDurationSec = selfDestructSec,
            burnTimestamp = if (isSelfDestruct && !isScheduled) now + (selfDestructSec * 1000L) else null,
            isScheduled = isScheduled,
            scheduledTime = scheduledTime,
            isScheduledDispatched = !isScheduled
        )

        val id = chatDao.insertMessage(messageEntity)

        // Update room's last active timestamp
        val room = chatDao.getRoomById(roomId)
        if (room != null) {
            chatDao.insertOrUpdateRoom(room.copy(lastActiveTimestamp = now))
        }

        return Pair(id, messageEntity.copy(id = id))
    }

    suspend fun insertRemoteMessageIfNotExists(message: MessageEntity): Boolean {
        val existing = chatDao.getMessageByCipher(message.cipherTextBase64)
        if (existing == null) {
            chatDao.insertMessage(message)
            val room = chatDao.getRoomById(message.roomId)
            if (room != null) {
                chatDao.insertOrUpdateRoom(room.copy(lastActiveTimestamp = message.timestamp))
            }
            return true
        }
        return false
    }

    suspend fun applyRemoteReaction(cipherTextBase64: String, emoji: String, user: String) {
        val message = chatDao.getMessageByCipher(cipherTextBase64) ?: return
        toggleReaction(message, emoji, user)
    }

    suspend fun applyRemoteDelete(cipherTextBase64: String) {
        chatDao.deleteMessageByCipher(cipherTextBase64)
    }

    suspend fun toggleReaction(message: MessageEntity, emoji: String, currentUserId: String = "current_user") {
        try {
            val jsonObj = JSONObject(message.reactionsJson)
            val currentArray = jsonObj.optJSONArray(emoji) ?: org.json.JSONArray()
            val userList = mutableListOf<String>()
            for (i in 0 until currentArray.length()) {
                userList.add(currentArray.getString(i))
            }
            if (userList.contains(currentUserId)) {
                userList.remove(currentUserId)
            } else {
                userList.add(currentUserId)
            }

            val newJson = JSONObject()
            val keys = jsonObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                if (k != emoji) {
                    newJson.put(k, jsonObj.get(k))
                }
            }
            if (userList.isNotEmpty()) {
                val newArr = org.json.JSONArray()
                userList.forEach { newArr.put(it) }
                newJson.put(emoji, newArr)
            }

            chatDao.updateMessage(message.copy(reactionsJson = newJson.toString()))
        } catch (_: Exception) {}
    }

    suspend fun deleteMessage(id: Long) {
        chatDao.deleteMessageById(id)
    }

    suspend fun clearRoomHistory(roomId: String) {
        chatDao.clearMessagesForRoom(roomId)
    }

    suspend fun checkAndDispatchScheduledMessages() {
        val now = System.currentTimeMillis()
        val dueList = chatDao.getDueScheduledMessages(now)
        dueList.forEach { msg ->
            val burn = if (msg.isSelfDestruct) now + (msg.selfDestructDurationSec * 1000L) else null
            chatDao.updateMessage(
                msg.copy(
                    isScheduledDispatched = true,
                    timestamp = now,
                    burnTimestamp = burn
                )
            )
        }
        chatDao.purgeExpiredSelfDestructMessages(now)
    }

    suspend fun markIncomingMessagesAsRead(roomId: String, readUpToTimestamp: Long, readerId: String) {
        chatDao.markIncomingMessagesAsRead(roomId, readUpToTimestamp, readerId)
    }

    suspend fun markSentMessagesAsRead(roomId: String, readUpToTimestamp: Long, mySenderId: String) {
        chatDao.markSentMessagesAsRead(roomId, readUpToTimestamp, mySenderId)
    }

    suspend fun markAllMessagesAsRead(roomId: String) {
        chatDao.markAllMessagesAsRead(roomId)
    }

    suspend fun preseedInitialRoomsIfEmpty() {
        getOrCreateRoom("3000", "Stark & Pepper Comm [MARK-85]")
    }
}
