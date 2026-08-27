package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_rooms")
data class ChatRoomEntity(
    @PrimaryKey val roomId: String,
    val roomCode: String,
    val roomName: String,
    val roomTheme: String = "MARK-85",
    val description: String = "",
    val createdTimestamp: Long = System.currentTimeMillis(),
    val lastActiveTimestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "chat_messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val roomId: String,
    val senderId: String,
    val senderName: String,
    val cipherTextBase64: String,
    val ivBase64: String,
    val saltBase64: String,
    val keyFingerprint: String,
    val messageType: String = "TEXT", // TEXT, GIF, VOICE, IMAGE, SYSTEM
    val mediaUrl: String? = null,
    val voiceDurationSeconds: Int = 0,
    val voiceWaveform: String = "", // Comma-separated floats e.g. "0.2,0.5,0.8"
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,
    val reactionsJson: String = "{}", // JSON map e.g. {"❤️": ["tony"], "🔥": ["pepper"]}
    val replyToId: Long? = null,
    val replyToText: String? = null,
    val isSelfDestruct: Boolean = false,
    val selfDestructDurationSec: Int = 0,
    val burnTimestamp: Long? = null,
    val isScheduled: Boolean = false,
    val scheduledTime: Long? = null,
    val isScheduledDispatched: Boolean = true
)

@Entity(tableName = "calc_history")
data class CalcHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val expression: String,
    val result: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class DecryptedMessage(
    val entity: MessageEntity,
    val plainText: String,
    val isDecryptedSuccess: Boolean,
    val reactions: Map<String, Int> = emptyMap(),
    val myReactions: Set<String> = emptySet(),
    val waveformList: List<Float> = emptyList()
)
