package com.pingpaisa.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Ported from Smart_Expense_Tracker (original queries unchanged).
 * The observe* queries are new: they re-emit whenever the table changes, so screens
 * update by themselves when the notification listener saves a payment.
 */
@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY id DESC")
    fun observeAll(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    fun observeById(id: Int): Flow<Transaction?>

    @Query("UPDATE transactions SET category = :category WHERE recipient = :recipient")
    suspend fun updateCategoryForRecipient(recipient: String, category: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(transaction: Transaction)

    @Query("SELECT EXISTS(SELECT 1 FROM transactions WHERE refNumber = :ref)")
    suspend fun existsByRef(ref: String): Boolean

    @Query("SELECT * FROM transactions ORDER BY id DESC")
    suspend fun getAll(): List<Transaction>
}
