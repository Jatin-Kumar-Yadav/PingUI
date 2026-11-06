package com.pingpaisa.app

import com.pingpaisa.app.healthscore.HealthScoreHistory
import com.pingpaisa.app.healthscore.InsightEngine
import com.pingpaisa.app.healthscore.Money
import com.pingpaisa.app.healthscore.ScoreCalculator
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/** One bar under "What shapes your score". [value] is 0..100. */
internal data class HealthFactorUi(val label: String, val value: Int, val detail: String)

/** Everything the Financial health screen shows, already turned into display text. */
internal data class HealthUi(
    val eyebrow: String,         // "NOVEMBER 2026"
    val score: Int,              // 0..100, also drives the gauge
    val riskLabel: String,       // "LOW RISK"
    val change: String,          // "Up 4 points since October"
    val factors: List<HealthFactorUi>,
    val insightLabel: String,    // "INSIGHT" (rule-based) or "AI INSIGHT"
    val insight: String,
    val basis: String,           // what the score is based on
    val scoreText: String = score.toString(),
    val isSample: Boolean = false   // the sample keeps the artwork exactly as designed
) {
    companion object {
        private fun pct(p: Int): String = if (p >= 100) "100%+" else "$p%"

        fun from(
            result: ScoreCalculator.ScoreResult,
            insight: InsightEngine.Insight,
            monthlyIncome: Double,
            monthlyOutflow: Double,
            history: List<HealthScoreHistory>,
            today: LocalDate,
            zone: ZoneId = ZoneId.systemDefault(),
            locale: Locale = Locale.getDefault()
        ): HealthUi {
            val f = result.factors
            val month = YearMonth.from(today)
            return HealthUi(
                eyebrow = month.month.getDisplayName(TextStyle.FULL, locale).uppercase(locale) + " " + month.year,
                score = result.score,
                riskLabel = when (result.risk) {
                    ScoreCalculator.RiskLevel.LOW -> "LOW RISK"
                    ScoreCalculator.RiskLevel.MEDIUM -> "MEDIUM RISK"
                    ScoreCalculator.RiskLevel.HIGH -> "HIGH RISK"
                },
                change = changeText(result.score, history, today, zone, locale),
                // Same four factors and wording as the tracker's breakdown card.
                factors = listOf(
                    HealthFactorUi("Savings rate", (f.savings * 100).roundToInt(),
                        if (f.savingsRatePercent < 0) "You're spending more than you earn"
                        else "You save ${f.savingsRatePercent}% of income (30%+ = full marks)"),
                    HealthFactorUi("EMI burden", (f.emi * 100).roundToInt(),
                        "EMIs use ${pct(f.emiRatioPercent)} of income (50%+ = zero marks)"),
                    HealthFactorUi("Spending consistency", (f.consistency * 100).roundToInt(),
                        if (f.consistencyKnown) "How steady your month-to-month spending is"
                        else "Needs 2+ months of data, counted as neutral for now"),
                    HealthFactorUi("Recurring reliability", (f.reliability * 100).roundToInt(),
                        if (f.reliabilityKnown) "Share of recurring payments made on schedule"
                        else "No recurring payments detected yet, counted as neutral")
                ),
                insightLabel = if (insight.isAi) "AI INSIGHT" else "INSIGHT",
                insight = insight.text,
                basis = "Based on ${Money.inr(monthlyIncome)} income and ${Money.inr(monthlyOutflow)} going out this month, " +
                    "including EMIs and other recurring payments not yet paid."
            )
        }

        /** Compares with the last score saved before this month started. */
        fun changeText(current: Int, history: List<HealthScoreHistory>, today: LocalDate,
                       zone: ZoneId, locale: Locale = Locale.getDefault()): String {
            val monthStart = today.withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val before = history.filter { it.timestamp < monthStart }.maxByOrNull { it.timestamp }
                ?: return "Your first score this month. Check back next month to see the change."
            val monthName = Instant.ofEpochMilli(before.timestamp).atZone(zone).month.getDisplayName(TextStyle.FULL, locale)
            val diff = current - before.score
            fun points(n: Int) = if (n == 1) "1 point" else "$n points"
            return when {
                diff > 0 -> "Up ${points(diff)} since $monthName"
                diff < 0 -> "Down ${points(-diff)} since $monthName"
                else -> "Same as in $monthName"
            }
        }

        /** Save a snapshot when there is none for today yet, or the score changed since the last one. */
        fun shouldRecord(score: Int, latest: HealthScoreHistory?, today: LocalDate, zone: ZoneId): Boolean {
            if (latest == null) return true
            val latestDay = Instant.ofEpochMilli(latest.timestamp).atZone(zone).toLocalDate()
            return latestDay != today || latest.score != score
        }

        /** The design's original sample content, used by Android Studio previews and the screenshot tests. */
        val Sample = HealthUi(
            eyebrow = "JUNE 2025", score = 78, riskLabel = "EXCELLENT", change = "Up 24 points since May",
            factors = listOf(
                HealthFactorUi("Savings rate", 88, ""), HealthFactorUi("Budget control", 74, ""),
                HealthFactorUi("Bill consistency", 94, ""), HealthFactorUi("Debt health", 68, "")
            ),
            insightLabel = "AI RECOMMENDATION", insight = "Reduce dining by ₹1,800 to cross an 800 score next month.",
            basis = "", scoreText = "782", isSample = true
        )
    }
}
