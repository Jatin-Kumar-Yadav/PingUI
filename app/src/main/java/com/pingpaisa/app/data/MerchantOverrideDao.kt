package com.pingpaisa.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Ported from Smart_Expense_Tracker (original queries unchanged); observeAll is new. */
@Dao
interface MerchantOverrideDao {

    /** Remembered merchants, for the Settings → Merchants list and count. */
    @Query("SELECT * FROM merchant_overrides ORDER BY recipient ASC")
    fun observeAll(): Flow<List<MerchantOverride>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setOverride(override: MerchantOverride)

    /** Settings → Merchants → Forget: future payments go back to automatic categorizing. */
    @Query("DELETE FROM merchant_overrides WHERE recipient = :recipient")
    suspend fun forget(recipient: String)

    @Query("UPDATE merchant_overrides SET category = :new WHERE category = :old")
    suspend fun renameCategory(old: String, new: String)

    @Query("DELETE FROM merchant_overrides WHERE category = :category")
    suspend fun forgetCategory(category: String)

    @Query("SELECT category FROM merchant_overrides WHERE recipient = :recipient")
    suspend fun getOverride(recipient: String): String?
}
