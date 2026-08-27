package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.CalcHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CalcDao {
    @Query("SELECT * FROM calc_history ORDER BY timestamp DESC LIMIT 50")
    fun getHistory(): Flow<List<CalcHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(item: CalcHistoryEntity)

    @Query("DELETE FROM calc_history")
    suspend fun clearHistory()
}
