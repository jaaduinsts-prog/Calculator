package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.ChatRoomEntity
import com.example.data.model.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_rooms ORDER BY lastActiveTimestamp DESC")
    fun getAllRooms(): Flow<List<ChatRoomEntity>>

    @Query("SELECT * FROM chat_rooms WHERE roomId = :roomId LIMIT 1")
    suspend fun getRoomById(roomId: String): ChatRoomEntity?

    @Query("SELECT * FROM chat_rooms WHERE roomCode = :roomCode LIMIT 1")
    suspend fun getRoomByCode(roomCode: String): ChatRoomEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateRoom(room: ChatRoomEntity)

    @Query("SELECT * FROM chat_messages WHERE roomId = :roomId AND isScheduledDispatched = 1 ORDER BY timestamp ASC")
    fun getMessagesForRoom(roomId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE roomId = :roomId AND isScheduled = 1 AND isScheduledDispatched = 0 ORDER BY scheduledTime ASC")
    fun getScheduledMessagesForRoom(roomId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE isScheduled = 1 AND isScheduledDispatched = 0 AND scheduledTime <= :currentTime")
    suspend fun getDueScheduledMessages(currentTime: Long): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity): Long

    @Update
    suspend fun updateMessage(message: MessageEntity)

    @Query("SELECT * FROM chat_messages WHERE cipherTextBase64 = :cipher LIMIT 1")
    suspend fun getMessageByCipher(cipher: String): MessageEntity?

    @Query("DELETE FROM chat_messages WHERE cipherTextBase64 = :cipher")
    suspend fun deleteMessageByCipher(cipher: String)

    @Query("DELETE FROM chat_messages WHERE id = :messageId")
    suspend fun deleteMessageById(messageId: Long)

    @Query("DELETE FROM chat_messages WHERE roomId = :roomId")
    suspend fun clearMessagesForRoom(roomId: String)

    @Query("UPDATE chat_messages SET isRead = 1 WHERE roomId = :roomId AND timestamp <= :readUpToTimestamp AND senderId != :readerId")
    suspend fun markIncomingMessagesAsRead(roomId: String, readUpToTimestamp: Long, readerId: String)

    @Query("UPDATE chat_messages SET isRead = 1 WHERE roomId = :roomId AND timestamp <= :readUpToTimestamp AND senderId = :mySenderId")
    suspend fun markSentMessagesAsRead(roomId: String, readUpToTimestamp: Long, mySenderId: String)

    @Query("UPDATE chat_messages SET isRead = 1 WHERE roomId = :roomId")
    suspend fun markAllMessagesAsRead(roomId: String)

    @Query("DELETE FROM chat_messages WHERE isSelfDestruct = 1 AND burnTimestamp IS NOT NULL AND burnTimestamp <= :currentTime")
    suspend fun purgeExpiredSelfDestructMessages(currentTime: Long)
}
