package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.audio.StarkAudioEngine
import com.example.data.local.StarkDatabase
import com.example.data.model.CalcHistoryEntity
import com.example.data.model.ChatRoomEntity
import com.example.data.model.DecryptedMessage
import com.example.data.model.MessageEntity
import com.example.data.repository.StarkRepository
import com.example.data.sync.StarkSyncRelay
import com.example.data.sync.SyncState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val database = StarkDatabase.getInstance(application)
    private val repository = StarkRepository(database.chatDao(), database.calcDao())
    private val syncRelay = StarkSyncRelay(application, viewModelScope)

    val syncState: StateFlow<SyncState> = syncRelay.syncState

    private val prefs = application.getSharedPreferences("stark_device_prefs", Context.MODE_PRIVATE)

    // --- Calculator State ---
    private val _displayExpression = MutableStateFlow("")
    val displayExpression: StateFlow<String> = _displayExpression.asStateFlow()

    private val _liveResult = MutableStateFlow("")
    val liveResult: StateFlow<String> = _liveResult.asStateFlow()

    private val _isUnlocking = MutableStateFlow(false)
    val isUnlocking: StateFlow<Boolean> = _isUnlocking.asStateFlow()

    private val _isSecretRoomOpen = MutableStateFlow(false)
    val isSecretRoomOpen: StateFlow<Boolean> = _isSecretRoomOpen.asStateFlow()

    val calcHistory: StateFlow<List<CalcHistoryEntity>> = repository.getHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Secret Room State ---
    private val _currentRoom = MutableStateFlow(
        ChatRoomEntity(
            roomId = "room_default_stark_subnet",
            roomCode = "MARK-85",
            roomName = "Direct Messages",
            roomTheme = "MARK-85",
            description = "E2EE Encrypted Channel (AES-256-GCM)"
        )
    )
    val currentRoom: StateFlow<ChatRoomEntity> = _currentRoom.asStateFlow()

    val allRooms: StateFlow<List<ChatRoomEntity>> = repository.getAllRooms()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val myDeviceId: String = prefs.getString("device_id", null) ?: run {
        val newId = "dev_" + UUID.randomUUID().toString().take(8)
        prefs.edit().putString("device_id", newId).apply()
        newId
    }

    private val _activeSenderId = MutableStateFlow(
        prefs.getString("active_identity", "tony_stark") ?: "tony_stark"
    )
    val activeSenderId: StateFlow<String> = _activeSenderId.asStateFlow()

    private val _activeSenderName = MutableStateFlow(
        prefs.getString("custom_user_name", "Tony Stark") ?: "Tony Stark"
    )
    val activeSenderName: StateFlow<String> = _activeSenderName.asStateFlow()

    private val _isSoundMuted = MutableStateFlow(false)
    val isSoundMuted: StateFlow<Boolean> = _isSoundMuted.asStateFlow()

    val peerLastSeenTime: StateFlow<Long> = syncRelay.peerLastSeenTime
    val isPeerTyping: StateFlow<Boolean> = syncRelay.isPeerTyping

    // Reactive messages based on active room
    val messages: StateFlow<List<DecryptedMessage>> = _currentRoom
        .flatMapLatest { room ->
            repository.getDecryptedMessages(room.roomId, room.roomCode)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val scheduledMessages: StateFlow<List<MessageEntity>> = _currentRoom
        .flatMapLatest { room ->
            repository.getScheduledMessages(room.roomId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var dispatchTimerJob: Job? = null

    init {
        // Wire up remote sync callbacks
        syncRelay.setCallbacks(
            onMessage = { remoteMsg ->
                val inserted = repository.insertRemoteMessageIfNotExists(remoteMsg)
                if (inserted) {
                    StarkAudioEngine.playRepulsorBlast()
                    if (_isSecretRoomOpen.value) {
                        markRoomAsRead()
                    }
                }
            },
            onReaction = { cipher, emoji, user ->
                repository.applyRemoteReaction(cipher, emoji, user)
            },
            onDelete = { cipher ->
                repository.applyRemoteDelete(cipher)
            },
            onReadReceipt = { readerId, timestamp ->
                val room = _currentRoom.value
                repository.markSentMessagesAsRead(room.roomId, timestamp, _activeSenderId.value)
            }
        )

        viewModelScope.launch(Dispatchers.IO) {
            repository.preseedInitialRoomsIfEmpty()
            val initialRoom = _currentRoom.value
            syncRelay.startListening(initialRoom.roomCode, initialRoom.roomId, _activeSenderId.value)
        }

        // Start scheduled messages & burn timer worker
        dispatchTimerJob = viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                try {
                    repository.checkAndDispatchScheduledMessages()
                } catch (_: Exception) {}
                delay(1000)
            }
        }
    }

    // --- Calculator Logic ---
    fun onCalcKey(key: String) {
        when (key) {
            "AC" -> {
                _displayExpression.value = ""
                _liveResult.value = ""
            }
            "BACK" -> {
                val cur = _displayExpression.value
                if (cur.isNotEmpty()) {
                    _displayExpression.value = cur.dropLast(1)
                    updateLiveResult()
                }
            }
            "=" -> {
                val expr = _displayExpression.value.trim()
                // Strict Dual-Passcode System:
                // 3000 -> Pepper Potts
                // 3002 -> Tony Stark
                when {
                    expr == "3000" || expr.endsWith("3000") -> {
                        triggerSecretProtocolUnlock(isTony = false)
                    }
                    expr == "3002" || expr.endsWith("3002") -> {
                        triggerSecretProtocolUnlock(isTony = true)
                    }
                    else -> {
                        evaluateAndCommit()
                    }
                }
            }
            "()" -> {
                val cur = _displayExpression.value
                val openCount = cur.count { it == '(' }
                val closeCount = cur.count { it == ')' }
                if (openCount > closeCount && cur.isNotEmpty() && cur.last().isDigit()) {
                    _displayExpression.value = "$cur)"
                } else {
                    _displayExpression.value = "$cur("
                }
                updateLiveResult()
            }
            "%" -> {
                _displayExpression.value += "%"
                updateLiveResult()
            }
            "÷" -> { _displayExpression.value += "/"; updateLiveResult() }
            "×" -> { _displayExpression.value += "*"; updateLiveResult() }
            "-" -> { _displayExpression.value += "-"; updateLiveResult() }
            "+" -> { _displayExpression.value += "+"; updateLiveResult() }
            else -> {
                _displayExpression.value += key
                updateLiveResult()
            }
        }
    }

    private fun triggerSecretProtocolUnlock(isTony: Boolean) {
        if (_isUnlocking.value) return
        _isUnlocking.value = true
        StarkAudioEngine.playArcReactorCharge()

        if (isTony) {
            _activeSenderId.value = "tony_stark"
            _activeSenderName.value = "Tony Stark"
            prefs.edit().putString("active_identity", "tony_stark").apply()
        } else {
            _activeSenderId.value = "pepper_potts"
            _activeSenderName.value = "Pepper Potts"
            prefs.edit().putString("active_identity", "pepper_potts").apply()
        }

        viewModelScope.launch {
            val room = _currentRoom.value
            syncRelay.startListening(room.roomCode, room.roomId, _activeSenderId.value)
            delay(1200)
            _isUnlocking.value = false
            _isSecretRoomOpen.value = true
            _displayExpression.value = ""
            _liveResult.value = ""
            markRoomAsRead()
        }
    }

    fun markRoomAsRead() {
        val room = _currentRoom.value
        val now = System.currentTimeMillis()
        viewModelScope.launch(Dispatchers.IO) {
            repository.markIncomingMessagesAsRead(room.roomId, now, _activeSenderId.value)
            syncRelay.publishReadReceipt(room.roomCode, _activeSenderId.value, now)
        }
    }

    private fun updateLiveResult() {
        val expr = _displayExpression.value
        if (expr.isBlank() || expr.all { it.isDigit() || it == '.' }) {
            _liveResult.value = ""
            return
        }
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val eval = evaluateExpression(expr)
                if (eval != null) {
                    _liveResult.value = formatResult(eval)
                } else {
                    _liveResult.value = ""
                }
            } catch (_: Exception) {
                _liveResult.value = ""
            }
        }
    }

    private fun evaluateAndCommit() {
        val expr = _displayExpression.value
        if (expr.isBlank()) return
        try {
            val eval = evaluateExpression(expr)
            if (eval != null) {
                val formatted = formatResult(eval)
                viewModelScope.launch(Dispatchers.IO) {
                    repository.saveCalculation(expr, formatted)
                }
                _displayExpression.value = formatted
                _liveResult.value = ""
            }
        } catch (_: Exception) {
            _liveResult.value = "Error"
        }
    }

    private fun formatResult(num: Double): String {
        return if (num == num.toLong().toDouble()) {
            num.toLong().toString()
        } else {
            val df = DecimalFormat("#.########")
            df.format(num)
        }
    }

    private fun evaluateExpression(expr: String): Double? {
        val clean = expr.replace("×", "*").replace("÷", "/")
        return try {
            SimpleMathEvaluator.evaluate(clean)
        } catch (_: Exception) {
            null
        }
    }

    fun clearCalcHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearCalcHistory()
        }
    }

    // --- Stealth Auto-Reset on Backgrounding / Eject ---
    fun onAppBackgrounded() {
        if (_isSecretRoomOpen.value || _isUnlocking.value) {
            _isSecretRoomOpen.value = false
            _isUnlocking.value = false
            _displayExpression.value = ""
            _liveResult.value = ""
            StarkAudioEngine.playStealthLock()
        }
    }

    fun lockStealth() {
        onAppBackgrounded()
    }

    // --- Chat Room Methods ---
    fun switchIdentity() {
        if (_activeSenderId.value == "tony_stark") {
            _activeSenderId.value = "pepper_potts"
            _activeSenderName.value = "Pepper Potts"
            prefs.edit().putString("active_identity", "pepper_potts").apply()
        } else {
            _activeSenderId.value = "tony_stark"
            _activeSenderName.value = "Tony Stark"
            prefs.edit().putString("active_identity", "tony_stark").apply()
        }
        val room = _currentRoom.value
        syncRelay.startListening(room.roomCode, room.roomId, _activeSenderId.value)
        StarkAudioEngine.playRepulsorBlast()
    }

    fun setCustomUserName(name: String) {
        val trimmed = name.trim().ifEmpty { if (_activeSenderId.value == "tony_stark") "Tony Stark" else "Pepper Potts" }
        _activeSenderName.value = trimmed
        prefs.edit().putString("custom_user_name", trimmed).apply()
    }

    fun toggleSound() {
        val muted = StarkAudioEngine.toggleMute()
        _isSoundMuted.value = muted
    }

    fun switchRoom(roomCode: String, roomName: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val room = repository.getOrCreateRoom(roomCode, roomName)
            _currentRoom.value = room
            syncRelay.startListening(room.roomCode, room.roomId, _activeSenderId.value)
            StarkAudioEngine.playArcReactorCharge()
            markRoomAsRead()
        }
    }

    fun notifyTyping() {
        val room = _currentRoom.value
        viewModelScope.launch(Dispatchers.IO) {
            syncRelay.publishTyping(room.roomCode, _activeSenderId.value)
        }
    }

    fun sendMessage(
        text: String,
        type: String = "TEXT",
        mediaUrl: String? = null,
        voiceDurationSec: Int = 0,
        voiceWaveform: List<Float> = emptyList(),
        replyToId: Long? = null,
        replyToText: String? = null,
        isSelfDestruct: Boolean = false,
        selfDestructSec: Int = 0,
        scheduleDelayMs: Long? = null
    ) {
        val room = _currentRoom.value
        viewModelScope.launch(Dispatchers.IO) {
            val (_, entity) = repository.sendMessage(
                roomId = room.roomId,
                roomCode = room.roomCode,
                senderId = _activeSenderId.value,
                senderName = _activeSenderName.value,
                plainText = text,
                messageType = type,
                mediaUrl = mediaUrl,
                voiceDurationSec = voiceDurationSec,
                voiceWaveform = voiceWaveform,
                replyToId = replyToId,
                replyToText = replyToText,
                isSelfDestruct = isSelfDestruct,
                selfDestructSec = selfDestructSec,
                scheduledDelayMs = scheduleDelayMs
            )

            // Publish message to remote peer devices if not a future-scheduled message
            if (scheduleDelayMs == null || scheduleDelayMs <= 0) {
                syncRelay.publishMessage(
                    roomCode = room.roomCode,
                    senderId = _activeSenderId.value,
                    senderName = _activeSenderName.value,
                    cipherTextBase64 = entity.cipherTextBase64,
                    ivBase64 = entity.ivBase64,
                    saltBase64 = entity.saltBase64,
                    keyFingerprint = entity.keyFingerprint,
                    messageType = type,
                    mediaFilePath = mediaUrl,
                    voiceDurationSec = voiceDurationSec,
                    voiceWaveform = entity.voiceWaveform,
                    timestamp = entity.timestamp,
                    replyToText = replyToText,
                    isSelfDestruct = isSelfDestruct,
                    selfDestructSec = selfDestructSec
                )
            }
        }
    }

    fun toggleReaction(message: MessageEntity, emoji: String) {
        val room = _currentRoom.value
        viewModelScope.launch(Dispatchers.IO) {
            repository.toggleReaction(message, emoji, _activeSenderId.value)
            syncRelay.publishReaction(room.roomCode, message.cipherTextBase64, emoji, _activeSenderId.value)
        }
    }

    fun deleteMessage(id: Long) {
        val room = _currentRoom.value
        viewModelScope.launch(Dispatchers.IO) {
            val msgList = messages.value
            val target = msgList.firstOrNull { it.entity.id == id }
            repository.deleteMessage(id)
            if (target != null) {
                syncRelay.publishDelete(room.roomCode, target.entity.cipherTextBase64, _activeSenderId.value)
            }
        }
    }

    fun cancelScheduledMessage(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteMessage(id)
        }
    }

    override fun onCleared() {
        super.onCleared()
        dispatchTimerJob?.cancel()
    }
}

// Simple Parser/Evaluator for basic and scientific math
object SimpleMathEvaluator {
    fun evaluate(str: String): Double {
        return object : Any() {
            var pos = -1
            var ch = 0

            fun nextChar() {
                ch = if (++pos < str.length) str[pos].code else -1
            }

            fun eat(charToEat: Int): Boolean {
                while (ch == ' '.code) nextChar()
                if (ch == charToEat) {
                    nextChar()
                    return true
                }
                return false
            }

            fun parse(): Double {
                nextChar()
                val x = parseExpression()
                if (pos < str.length) throw RuntimeException("Unexpected: " + ch.toChar())
                return x
            }

            fun parseExpression(): Double {
                var x = parseTerm()
                while (true) {
                    if (eat('+'.code)) x += parseTerm()
                    else if (eat('-'.code)) x -= parseTerm()
                    else return x
                }
            }

            fun parseTerm(): Double {
                var x = parseFactor()
                while (true) {
                    if (eat('*'.code)) x *= parseFactor()
                    else if (eat('/'.code)) {
                        val divisor = parseFactor()
                        if (divisor == 0.0) throw ArithmeticException("Div by zero")
                        x /= divisor
                    } else if (eat('%'.code)) x %= parseFactor()
                    else return x
                }
            }

            fun parseFactor(): Double {
                if (eat('+'.code)) return parseFactor()
                if (eat('-'.code)) return -parseFactor()

                var x: Double
                val startPos = pos
                if (eat('('.code)) {
                    x = parseExpression()
                    eat(')'.code)
                } else if ((ch >= '0'.code && ch <= '9'.code) || ch == '.'.code) {
                    while ((ch >= '0'.code && ch <= '9'.code) || ch == '.'.code) nextChar()
                    x = str.substring(startPos, pos).toDouble()
                } else if (ch >= 'a'.code && ch <= 'z'.code || ch == '√'.code) {
                    while ((ch >= 'a'.code && ch <= 'z'.code) || ch == '√'.code) nextChar()
                    val func = str.substring(startPos, pos)
                    x = parseFactor()
                    x = when (func) {
                        "sqrt", "√" -> sqrt(x)
                        "sin" -> sin(Math.toRadians(x))
                        "cos" -> cos(Math.toRadians(x))
                        "tan" -> tan(Math.toRadians(x))
                        else -> throw RuntimeException("Unknown func: $func")
                    }
                } else {
                    throw RuntimeException("Unexpected: " + ch.toChar())
                }

                if (eat('^'.code)) x = Math.pow(x, parseFactor())
                return x
            }
        }.parse()
    }
}
