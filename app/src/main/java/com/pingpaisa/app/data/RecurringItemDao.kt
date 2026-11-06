package com.pingpaisa.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Ported from Smart_Expense_Tracker (original queries unchanged); observeAll is new. */
@Dao
interface RecurringItemDao {

    @Query("SELECT * FROM recurring_items ORDER BY dayOfMonth ASC")
    fun observeAll(): Flow<List<RecurringItem>>

    @Insert
    suspend fun insert(item: RecurringItem)

    @Delete
    suspend fun delete(item: RecurringItem)

    @Query("SELECT * FROM recurring_items ORDER BY dayOfMonth ASC")
    suspend fun getAll(): List<RecurringItem>
}
