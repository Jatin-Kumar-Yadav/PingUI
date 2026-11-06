package com.pingpaisa.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The single on-device account created at sign-up.
 *
 * The password is never stored: only a salted PBKDF2 hash ([passwordHash]) and
 * its random [passwordSalt], both Base64. [monthlySalary] is the fixed monthly
 * income used by the health score, cash flow and loan screens; extra income is
 * stored as Transaction rows with type = "income".
 */
@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey
    val id: Int = SINGLE_USER_ID,
    val name: String,
    val monthlySalary: Double,
    val passwordHash: String,
    val passwordSalt: String,
    val appLockEnabled: Boolean = true,      // ask for the password when the app opens
    val biometricEnabled: Boolean = false,   // allow fingerprint instead of the password
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        /** One account per phone, so the profile always uses this row. */
        const val SINGLE_USER_ID = 1
    }
}
