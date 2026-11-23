package com.pingpaisa.app.data

import com.pingpaisa.app.HomeSummary
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

data class DetectedRecurring(
    val name: String,
    val averageAmount: Double,
    val category: String,
    val occurrences: Int
)

object RecurringDetector {

    // The app's one strict date reader (dd/MM/yy; impossible dates such as 31/02 are rejected).
    // Replaces a shared SimpleDateFormat, which is not thread-safe and accepted 31/02 as 3 March.
    private fun parseDate(dateStr: String): LocalDate? = HomeSummary.parseDate(dateStr)

    fun detect(transactions: List<Transaction>): List<DetectedRecurring> {
        val results = mutableListOf<DetectedRecurring>()

        // Group by recipient (case-insensitive)
        val grouped = transactions.groupBy { it.recipient.lowercase().trim() }

        for ((_, txns) in grouped) {
            if (txns.size < 2) continue // need at least 2 payments to detect a pattern

            val sorted = txns.sortedBy { parseDate(it.date) ?: LocalDate.MIN }
            val amounts = sorted.map { it.amount }
            val avgAmount = amounts.average()

            // Check amounts are all within ~15% of the average (EMIs/SIPs are usually fixed or near-fixed)
            val amountsConsistent = amounts.all { abs(it - avgAmount) / avgAmount <= 0.15 }
            if (!amountsConsistent) continue

            // Check gaps between consecutive payments are roughly monthly (25-35 days)
            var monthlyGapsFound = 0
            for (i in 1 until sorted.size) {
                val d1 = parseDate(sorted[i - 1].date)
                val d2 = parseDate(sorted[i].date)
                if (d1 != null && d2 != null) {
                    val gapDays = abs(ChronoUnit.DAYS.between(d1, d2))
                    if (gapDays in 25L..35L) monthlyGapsFound++
                }
            }

            // Require at least one clean monthly gap to call it recurring
            if (monthlyGapsFound >= 2) {
                results.add(
                    DetectedRecurring(
                        name = sorted.first().recipient,
                        averageAmount = avgAmount,
                        category = sorted.first().category,
                        occurrences = sorted.size
                    )
                )
            }
        }

        return results
    }
}