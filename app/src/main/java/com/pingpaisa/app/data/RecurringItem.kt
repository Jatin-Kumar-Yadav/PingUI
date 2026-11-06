package com.pingpaisa.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A recurring payment the user added or confirmed (EMI, SIP or other). Ported unchanged. */
@Entity(tableName = "recurring_items")
data class RecurringItem(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val amount: Double,
    val category: String,
    val dayOfMonth: Int,       // 1-31, roughly when it's due each month
    val type: String = "Other" // "EMI", "SIP", or "Other"
)
