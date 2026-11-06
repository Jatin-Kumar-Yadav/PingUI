package com.pingpaisa.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One money movement. Ported from Smart_Expense_Tracker.
 *
 * New in PingPaisa: [type] separates money going out ("expense", the only kind
 * the old app had) from money coming in ("income"). Existing behaviour is kept
 * by defaulting to "expense".
 */
@Entity(
    tableName = "transactions",
    indices = [Index(value = ["refNumber"], unique = true)]
)
data class Transaction(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val amount: Double,
    val recipient: String,
    val date: String,            // "dd/MM/yy", same format as the SMS
    val refNumber: String,       // UPI reference, or "MANUAL-<millis>" for manual entries
    val rawText: String,
    val source: String = "SMS",  // "SMS" or "manual"
    val category: String = "Uncategorized",
    val subcategory: String = "Miscellaneous",
    val type: String = TYPE_EXPENSE
) {
    companion object {
        const val TYPE_EXPENSE = "expense"
        const val TYPE_INCOME = "income"
    }
}

/** Kept outside the class so Room never treats it as a column. */
val Transaction.isIncome: Boolean get() = type == Transaction.TYPE_INCOME
