package com.pingpaisa.app.healthscore

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Ported from Smart_Expense_Tracker (original queries unchanged); observeAll is new. */
@Dao
interface HealthScoreHistoryDao {
    @Query("SELECT * FROM health_score_history ORDER BY timestamp ASC")
    fun observeAll(): Flow<List<HealthScoreHistory>>

    @Insert
    suspend fun insert(entry: HealthScoreHistory)

    @Query("SELECT * FROM health_score_history ORDER BY timestamp DESC LIMIT 1")
    suspend fun latest(): HealthScoreHistory?

    @Query("SELECT * FROM health_score_history ORDER BY timestamp ASC")
    suspend fun getAll(): List<HealthScoreHistory>
}