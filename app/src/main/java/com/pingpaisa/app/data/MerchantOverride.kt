package com.pingpaisa.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A category the user picked for a merchant; reused for all future payments to it. Ported unchanged. */
@Entity(tableName = "merchant_overrides")
data class MerchantOverride(
    @PrimaryKey
    val recipient: String,   // stored lowercase, exact match
    val category: String
)
