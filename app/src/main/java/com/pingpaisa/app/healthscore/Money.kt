package com.pingpaisa.app.healthscore

import java.util.Locale
import kotlin.math.abs

/**
 * Indian-style rupee formatting (lakh / crore grouping).
 * The single formatter for the whole app: it replaces PingUI's former
 * formatRupees(Int), which used the same grouping.
 */
object Money {

    /** Same as [inr] for whole-rupee values used by the UI (slider amounts, EMIs). */
    fun inr(value: Int): String = inr(value.toDouble())

    /**
     * 430000 -> "₹4,30,000", 1234567 -> "₹12,34,567".
     * Negatives use the typographic minus "−" to match the PingUI design (e.g. "−₹320").
     */
    fun inr(value: Double): String {
        val rounded = Math.round(value)
        val digits = abs(rounded).toString()
        val grouped = if (digits.length <= 3) {
            digits
        } else {
            val lastThree = digits.takeLast(3)
            val rest = digits.dropLast(3)
            rest.reversed().chunked(2).joinToString(",").reversed() + "," + lastThree
        }
        return (if (rounded < 0) "−₹" else "₹") + grouped
    }

    /** Short form for labels: 10000 -> "₹10K", 430000 -> "₹4.3L", 25000000 -> "₹2.5 Cr". */
    fun compact(value: Double): String = when {
        value >= 1e7 -> "₹${trimmed(value / 1e7)} Cr"
        value >= 1e5 -> "₹${trimmed(value / 1e5)}L"
        value >= 1e3 -> "₹${trimmed(value / 1e3)}K"
        else -> "₹${Math.round(value)}"
    }

    private fun trimmed(x: Double): String = String.format(Locale.US, "%.1f", x).removeSuffix(".0")
}