package com.pingpaisa.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SavingsGoalDao {

    @Query("SELECT * FROM savings_goals ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<SavingsGoal>>

    @Insert
    suspend fun insert(goal: SavingsGoal)

    /** Used for editing the goal or adding money to it. */
    @Update
    suspend fun update(goal: SavingsGoal)

    @Delete
    suspend fun delete(goal: SavingsGoal)

    @Query("SELECT * FROM savings_goals ORDER BY createdAt ASC")
    suspend fun getAll(): List<SavingsGoal>
}
