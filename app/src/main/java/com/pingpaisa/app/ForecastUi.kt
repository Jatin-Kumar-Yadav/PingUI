package com.pingpaisa.app

import com.pingpaisa.app.data.Budget
import com.pingpaisa.app.data.RecurringItem
import com.pingpaisa.app.data.Transaction
import com.pingpaisa.app.data.isIncome
import com.pingpaisa.app.healthscore.Money
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

/** The alert card under the chart: a budget that may be crossed, or the biggest category. */
internal data class ForecastAlert(val title: String, val text: String, val opensBudgets: Boolean)

/**
 * Everything the forecast screen shows. The chart is the design's spend_trajectory artwork with its
 * paths replaced: [actualPath] (spent so far), [projectionPath] (dashed, today to month end),
 * [fillPath] (the area under the actual line) and the "today" dot at [todayX], [todayY]. All in the
 * artwork's 300 x 140 viewBox. Null paths mean "keep the artwork as drawn" (the design sample).
 */
internal data class ForecastUi(
    val title: String,               // "November forecast"
    val subtitle: String,
    val verdict: String,             // "You’re on track"
    val projected: String,           // "₹54,200"
    val projectedCaption: String,    // "Projected total spend by 30 November"
    val confidence: Float,           // share of the month already seen
    val confidenceText: String,      // "Based on 18 of 30 days"
    val scaleMax: String,            // "₹60K"
    val axis: List<String>,          // "1 Nov", "Today", "30 Nov"
    val alert: ForecastAlert?,
    val actualPath: String? = null,
    val projectionPath: String? = null,
    val fillPath: String? = null,
    val todayX: Float = 155f,
    val todayY: Float = 67f,
    val projectedAmount: Double = 54200.0   // the number behind [projected], used by Paisa AI
) {
    companion object {
        private const val LEFT = 10f
        private const val RIGHT = 290f
        private const val BOTTOM = 120f
        private const val TOP = 25f

        /**
         * Projected month-end spend = this month's outflow so far (tracked spending plus recurring
         * EMI / "Other" payments not yet seen, the figure Home and the health score use) plus the
         * daily average of everyday spending times the days left. Payments to a recurring item's
         * name are left out of the daily average, since they happen once a month, not every day.
         */
        fun from(
            all: List<Transaction>,
            recurring: List<RecurringItem>,
            outflow: Double,
            monthlyIncome: Double,
            budgets: List<Budget>,
            today: LocalDate,
            locale: Locale = Locale.getDefault()
        ): ForecastUi {
            val month = YearMonth.from(today)
            val days = month.lengthOfMonth()
            val day = today.dayOfMonth
            val spent = all.filter { !it.isIncome }.mapNotNull { t ->
                HomeSummary.parseDate(t.date)?.takeIf { YearMonth.from(it) == month && !it.isAfter(today) }?.let { it to t }
            }
            val recurringNames = recurring.map { it.name.trim().lowercase() }.toSet()
            val everyday = spent.filter { it.second.recipient.trim().lowercase() !in recurringNames }.sumOf { it.second.amount }
            val dailyAverage = everyday / day
            val projected = outflow + dailyAverage * (days - day)

            val monthName = month.month.getDisplayName(TextStyle.FULL, locale)
            val short = month.month.getDisplayName(TextStyle.SHORT, locale)
            val verdict = when {
                monthlyIncome <= 0 -> "Add your salary to compare"
                projected > monthlyIncome -> "Heading past your income"
                projected > monthlyIncome * 0.8 -> "Close to your income"
                else -> "You’re on track"
            }

            // Chart: cumulative tracked spending per day, then a dashed line to the projection.
            val scale = niceMax(maxOf(projected, outflow, 1.0))
            fun x(d: Int) = if (days <= 1) LEFT else LEFT + (RIGHT - LEFT) * (d - 1) / (days - 1)
            fun y(v: Double) = (BOTTOM - (BOTTOM - TOP) * (v / scale)).toFloat()
            val byDay = spent.groupBy { it.first.dayOfMonth }.mapValues { (_, l) -> l.sumOf { it.second.amount } }
            var running = 0.0
            val points = (1..day).map { d -> running += byDay[d] ?: 0.0; x(d) to y(running) }
            val actual = "M" + points.joinToString(" L") { "${f(it.first)} ${f(it.second)}" }
            val (tx, ty) = points.last()
            val projection = "M${f(tx)} ${f(ty)} L${f(x(days))} ${f(y(projected))}"
            val fill = "$actual L${f(tx)} 140 L${f(LEFT)} 140Z"

            return ForecastUi(
                title = "$monthName forecast",
                subtitle = "Based on your spending so far this month",
                verdict = verdict,
                projected = Money.inr(projected),
                projectedCaption = "Projected total spend by $days $monthName",
                confidence = day.toFloat() / days,
                confidenceText = "Based on $day of $days days",
                scaleMax = Money.compact(scale),
                axis = listOf("1 $short", "Today", "$days $short"),
                alert = alert(spent.map { it.second }, budgets, day, days),
                actualPath = actual, projectionPath = projection, fillPath = fill, todayX = tx, todayY = ty,
                projectedAmount = projected
            )
        }

        /** The budget most likely to be crossed at this pace; otherwise the biggest category; null with no spending. */
        private fun alert(spent: List<Transaction>, budgets: List<Budget>, day: Int, days: Int): ForecastAlert? {
            if (spent.isEmpty()) return null
            val byCategory = spent.groupBy { it.category }.mapValues { (_, l) -> l.sumOf { it.amount } }
            val over = budgets.mapNotNull { b ->
                val pace = (byCategory[b.category] ?: 0.0) / day * days
                (pace - b.monthlyLimit).takeIf { it > 0 }?.let { b.category to it }
            }.maxByOrNull { it.second }
            if (over != null) return ForecastAlert("${over.first} may cross its budget",
                "At this pace it ends about ${Money.inr(over.second)} over the limit.", opensBudgets = true)
            val total = byCategory.values.sum()
            val (category, amount) = byCategory.maxBy { it.value }
            return ForecastAlert("$category is your biggest spend",
                "${Money.inr(amount)} so far, ${(amount / total * 100).roundToInt()}% of this month’s spending.", opensBudgets = budgets.isEmpty())
        }

        /** Rounds the top of the chart up to a tidy number: 54,200 → 55,000; 830 → 850. */
        fun niceMax(value: Double): Double {
            val magnitude = Math.pow(10.0, Math.floor(Math.log10(value)))
            val step = magnitude / 2
            return ceil(value / step) * step
        }

        private fun f(v: Float) = String.format(Locale.US, "%.1f", v)

        /** The design sample content. */
        val Sample = ForecastUi(
            title = "June forecast", subtitle = "Powered by your last 6 months of spending", verdict = "You’re on track",
            projected = "₹54,200", projectedCaption = "Projected total spend by 30 June",
            confidence = .92f, confidenceText = "92% confidence", scaleMax = "₹60K", axis = listOf("1 Jun", "Today", "30 Jun"),
            alert = ForecastAlert("Dining spike detected", "Food spend may cross budget by ₹2,100.", opensBudgets = false)
        )
    }
}
