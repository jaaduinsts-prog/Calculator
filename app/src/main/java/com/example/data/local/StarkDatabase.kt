package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.CalcHistoryEntity
import com.example.data.model.ChatRoomEntity
import com.example.data.model.MessageEntity

@Database(
    entities = [ChatRoomEntity::class, MessageEntity::class, CalcHistoryEntity::class],
    version = 1,
    exportSchema = false
)
abstract class StarkDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun calcDao(): CalcDao

    companion object {
        @Volatile
        private var INSTANCE: StarkDatabase? = null

        fun getInstance(context: Context): StarkDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    StarkDatabase::class.java,
                    "stark_encrypted_vault.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
