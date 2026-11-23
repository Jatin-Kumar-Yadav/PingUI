package com.pingpaisa.app

import com.pingpaisa.app.healthscore.Money

/** Subtitle of the Settings → Payment capture row (notification access on or off). */
internal fun paymentCaptureSubtitle(enabled: Boolean): String =
    if (enabled) "On · reading bank payment alerts" else "Off · tap to turn on, so payments are added for you"

/** What Settings → Reset all data deletes, shown before asking for the password. */
internal val resetDeletes = listOf(
    "Your account and password",
    "All transactions, budgets, savings goals and recurring payments",
    "Remembered merchants, custom categories and the score history",
    "Saved loan quotes",
    "Scanned products, their photos and the scanner's Financial profile"
)

/** Subtitle of the Settings → App lock row. */
internal fun appLockSubtitle(enabled: Boolean): String =
    if (enabled) "On · password when PingPaisa opens" else "Off · anyone with your phone can open PingPaisa"

/** The signed-in account as the Settings screen shows it. Null on the screen means the design sample. */
internal data class SettingsAccount(val name: String, val salary: Double) {
    /** "Janvi Yadav" -> "JY", "Janvi" -> "J"; never empty. */
    val initials: String get() = initialsOf(name)
    val salaryText: String get() = "Monthly salary ${Money.inr(salary)}"

    /** The salary as the edit field shows it: "40000", or "40000.50". */
    val salaryInput: String get() = plainAmount(salary)

    companion object {
        fun initialsOf(name: String): String =
            name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2)
                .joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
    }
}
