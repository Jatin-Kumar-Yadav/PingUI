package com.pingpaisa.app

import com.pingpaisa.app.data.SavingsGoal
import com.pingpaisa.app.healthscore.Money
import kotlin.math.roundToInt

/** One savings goal card. */
internal data class GoalRow(
    val id: Int,
    val name: String,
    val percent: Int,           // 0..100
    val saved: Double,
    val target: Double,
    val savedText: String,      // "₹1,36,000 saved of ₹2,00,000"
    val toGoText: String        // "₹64,000 to go" / "Goal reached"
) {
    val fraction: Float get() = percent / 100f
}

internal object GoalsUi {
    fun from(goals: List<SavingsGoal>): List<GoalRow> = goals.map { g ->
        val percent = if (g.targetAmount > 0) (g.savedAmount / g.targetAmount * 100).roundToInt().coerceIn(0, 100) else 0
        GoalRow(
            id = g.id, name = g.name, percent = percent, saved = g.savedAmount, target = g.targetAmount,
            savedText = "${Money.inr(g.savedAmount)} saved of ${Money.inr(g.targetAmount)}",
            toGoText = if (g.savedAmount >= g.targetAmount) "Goal reached" else "${Money.inr(g.targetAmount - g.savedAmount)} to go"
        )
    }

    /** The design's sample goal. */
    val Sample = GoalRow(0, "Dream trip to Japan", 68, 136000.0, 200000.0, "₹1,36,000 saved of ₹2,00,000", "₹64,000 to go")
}

internal data class GoalErrors(val name: String? = null, val target: String? = null, val saved: String? = null) {
    val isValid: Boolean get() = name == null && target == null && saved == null
}

internal object GoalValidation {
    const val MAX_NAME_LENGTH = 40

    /** [saved] may be empty, meaning nothing saved yet. */
    fun validate(name: String, target: String, saved: String): GoalErrors = GoalErrors(
        name = when {
            name.isBlank() -> "Give your goal a name, e.g. Trip to Japan"
            name.trim().length > MAX_NAME_LENGTH -> "Keep it under $MAX_NAME_LENGTH characters"
            else -> null
        },
        target = if ((target.toDoubleOrNull() ?: 0.0) > 0.0) null else "Enter a target above ₹0",
        saved = if (saved.isEmpty() || saved.toDoubleOrNull() != null) null else "Enter an amount, or leave it empty"
    )
}
