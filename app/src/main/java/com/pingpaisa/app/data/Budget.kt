package com.pingpaisa.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A monthly spending limit for one category (e.g. "Food & Dining" -> ₹8,000).
 * Spent-so-far is not stored; it is calculated from this month's transactions.
 */
@Entity(tableName = "budgets")
data class Budget(
    @PrimaryKey
    val category: String,
    val monthlyLimit: Double
)
