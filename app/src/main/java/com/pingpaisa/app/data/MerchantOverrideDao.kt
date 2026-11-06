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

    @Query("SELECT category FROM merchant_overrides WHERE recipient = :recipient")
    suspend fun getOverride(recipient: String): String?
}
