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

    /**
     * Same as the tracker, plus the sub-category is reset: the old one (e.g. "Cafe & Coffee")
     * would be wrong under a category the user picked themselves.
     */
    @Query("UPDATE transactions SET category = :category, subcategory = 'Miscellaneous' WHERE recipient = :recipient")
    suspend fun updateCategoryForRecipient(recipient: String, category: String)

    /** Settings → Merchants: every expense to this merchant, whatever its spelling case ([key] is lowercase, trimmed). */
    @Query("UPDATE transactions SET category = :category, subcategory = 'Miscellaneous' WHERE LOWER(TRIM(recipient)) = :key AND type = 'expense'")
    suspend fun updateCategoryForMerchantKey(key: String, category: String)

    /** Settings → Categories: rename a category, or move its payments elsewhere. */
    @Query("UPDATE transactions SET category = :new WHERE category = :old")
    suspend fun renameCategory(old: String, new: String)

    @Query("UPDATE transactions SET note = :note WHERE id = :id")
    suspend fun updateNote(id: Int, note: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(transaction: Transaction)

    @Query("SELECT EXISTS(SELECT 1 FROM transactions WHERE refNumber = :ref)")
    suspend fun existsByRef(ref: String): Boolean

    @Query("SELECT * FROM transactions ORDER BY id DESC")
    suspend fun getAll(): List<Transaction>
}
