package com.pingpaisa.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A savings target such as "Trip to Japan": ₹2,00,000, of which [savedAmount] is put aside so far. */
@Entity(tableName = "savings_goals")
data class SavingsGoal(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val targetAmount: Double,
    val savedAmount: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis()
)
