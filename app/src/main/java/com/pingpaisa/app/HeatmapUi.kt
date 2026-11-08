package com.pingpaisa.app

import com.pingpaisa.app.data.Transaction
import com.pingpaisa.app.data.isIncome
import com.pingpaisa.app.healthscore.Money
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/**
 * One square of the calendar. [day] is null for the empty squares before the 1st and after the
 * last day. [level] (0..4) picks the colour. [label], [title] and [amount] fill the card shown
 * when the square is selected.
 */
internal data class HeatCell(val day: Int?, val level: Int, val label: String = "", val title: String = "", val amount: String = "")

/** One month of the money map. [cells] is always a whole number of Monday-first weeks. */
internal data class HeatmapMonth(
    val tab: String,             // "November"
    val total: String,           // "₹42,680"
    val activeDays: String,      // "23 active days"
    val cells: List<HeatCell>,
    val defaultSelected: Int,    // index into [cells]: the highest spend day, or today
    val insight: String
)

internal data class HeatmapUi(val months: List<HeatmapMonth>) {
    companion object {
        const val MONTHS = 3

        /** The last [MONTHS] months, oldest first (the current one is last), from expenses only. */
        fun from(all: List<Transaction>, today: LocalDate, locale: Locale = Locale.getDefault()): HeatmapUi {
            val thisMonth = YearMonth.from(today)
            val expenses = all.filter { !it.isIncome }.mapNotNull { t -> HomeSummary.parseDate(t.date)?.let { it to t } }
            return HeatmapUi((MONTHS - 1 downTo 0).map { month(expenses, thisMonth.minusMonths(it.toLong()), today, locale) })
        }

        private fun month(expenses: List<Pair<LocalDate, Transaction>>, month: YearMonth, today: LocalDate, locale: Locale): HeatmapMonth {
            val inMonth = expenses.filter { YearMonth.from(it.first) == month }
            val byDay = inMonth.groupBy({ it.first.dayOfMonth }, { it.second.amount }).mapValues { it.value.sum() }
            val name = month.month.getDisplayName(TextStyle.FULL, locale)
            val top = byDay.values.maxOrNull() ?: 0.0
            val topDay = if (top > 0) byDay.maxBy { it.value }.key else null
            val dateFormat = DateTimeFormatter.ofPattern("EEE, d MMMM", locale)

            fun levelOf(amount: Double) = when {
                amount <= 0 || top <= 0 -> 0
                amount / top <= .25 -> 1
                amount / top <= .5 -> 2
                amount / top <= .75 -> 3
                else -> 4
            }
            val lead = month.atDay(1).dayOfWeek.value - 1           // Monday = 0
            val days = (1..month.lengthOfMonth()).map { d ->
                val amount = byDay[d] ?: 0.0
                val date = month.atDay(d)
                HeatCell(d, levelOf(amount), date.format(dateFormat).uppercase(locale),
                    when {
                        d == topDay -> "Highest spend day"
                        amount > 0 -> "Daily spending"
                        date.isAfter(today) -> "Not here yet"
                        else -> "No spending"
                    }, Money.inr(amount))
            }
            val padded = List(lead) { HeatCell(null, 0) } + days
            val cells = padded + List((7 - padded.size % 7) % 7) { HeatCell(null, 0) }
            val default = when {
                topDay != null -> lead + topDay - 1
                month == YearMonth.from(today) -> lead + today.dayOfMonth - 1
                else -> lead
            }
            return HeatmapMonth(name, Money.inr(byDay.values.sum()),
                byDay.count { it.value > 0 }.let { "$it active ${if (it == 1) "day" else "days"}" },
                cells, default, insight(inMonth, month, today, name))
        }

        /** Weekend against weekday daily average, over the days that have happened. */
        fun insight(inMonth: List<Pair<LocalDate, Transaction>>, month: YearMonth, today: LocalDate, name: String): String {
            if (inMonth.isEmpty()) return "No spending recorded in $name yet."
            val last = minOf(month.lengthOfMonth(), if (month == YearMonth.from(today)) today.dayOfMonth else month.lengthOfMonth())
            val dates = (1..last).map { month.atDay(it) }
            fun weekend(d: LocalDate) = d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY
            val weekendDays = dates.count(::weekend)
            val weekdayDays = dates.size - weekendDays
            val onWeekends = inMonth.filter { weekend(it.first) }
            val weekendAverage = if (weekendDays > 0) onWeekends.sumOf { it.second.amount } / weekendDays else 0.0
            val weekdayAverage = if (weekdayDays > 0) inMonth.filterNot { weekend(it.first) }.sumOf { it.second.amount } / weekdayDays else 0.0
            if (weekendDays == 0 || weekdayDays == 0 || weekdayAverage <= 0 || weekendAverage <= 0) return "Not enough days yet to compare weekends with weekdays."
            val ratio = weekendAverage / weekdayAverage
            val topWeekend = onWeekends.groupBy { it.second.category }.mapValues { (_, l) -> l.sumOf { it.second.amount } }.maxBy { it.value }.key
            return when {
                ratio >= 1.1 -> "You spend ${((ratio - 1) * 100).roundToInt()}% more per day on weekends, mostly on $topWeekend."
                ratio <= .9 -> "You spend ${((1 - ratio) * 100).roundToInt()}% less per day on weekends than on weekdays."
                else -> "Your spending is about the same on weekends and weekdays."
            }
        }

        /** The design sample content: three tabs showing the same June grid. */
        val Sample: HeatmapUi = run {
            val levels = listOf(0,1,0,2,0,3,1,0,2,4,3,0,1,2,0,1,3,4,2,0,0,1,2,3,4,1,0,2,3,1,2,0,4,3,1)
            HeatmapUi(listOf("April", "May", "June").map { tab ->
                HeatmapMonth(tab, "₹42,680", "23 active days",
                    levels.mapIndexed { i, level ->
                        HeatCell(if (i < 2) null else i - 1, level,
                            if (i == 17) "WED, 18 JUNE" else "${i + 1} ${tab.uppercase()}",
                            if (i == 17) "Highest spend day" else "Daily spending", "₹6,840")
                    }, 17, "You spend 22% more on weekends, mostly on dining and travel.")
            })
        }
    }
}
