package com.pingpaisa.app

import com.pingpaisa.app.data.Budget
import com.pingpaisa.app.data.Transaction
import com.pingpaisa.app.data.isIncome
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import java.time.format.TextStyle
import java.util.Locale

data class CategoryTotal(val category: String, val amount: Double)

/**
 * The numbers behind the Home screen, worked out from the saved transactions.
 * Plain Kotlin (no Android) so it can be unit-tested. Only expenses count as
 * spending; income rows are used only for "recent activity".
 */
data class HomeSummary(
    val monthName: String,
    val previousMonthName: String,
    val spent: Double,                    // expenses this calendar month
    val previousSpent: Double,            // expenses last calendar month
    val income: Double,                   // salary + this month's extra income
    val savings: Double,                  // income minus this month's outflow (can be negative)
    val budgetLeft: Double?,              // null when no budget has been set
    val categories: List<CategoryTotal>,  // this month, biggest first
    val recent: List<Transaction>,        // newest first, all types
    val hasTransactions: Boolean,
    val today: LocalDate
) {
    /** Change in spending versus last month in percent, or null if nothing was spent last month. */
    val changePercent: Int?
        get() = if (previousSpent > 0) Math.round((spent - previousSpent) / previousSpent * 100).toInt() else null

    companion object {
        const val RECENT_COUNT = 3

        /**
         * Transactions store dates as "dd/MM/yy", the same format as the bank SMS. Strict, so an
         * impossible date such as 31/02 is rejected instead of quietly becoming 28 February.
         * ("uu" rather than "yy" because strict mode needs a year without an era; 26 still means 2026.)
         */
        private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/uu").withResolverStyle(ResolverStyle.STRICT)

        fun parseDate(text: String): LocalDate? =
            try { LocalDate.parse(text.trim(), dateFormat) } catch (e: DateTimeParseException) { null }

        /**
         * @param all every transaction, newest first (as the database returns them)
         * @param monthlyIncome salary plus this month's extra income
         * @param monthlyOutflow this month's spending plus EMIs not yet seen as payments (same as the score uses)
         */
        fun build(
            all: List<Transaction>,
            budgets: List<Budget>,
            monthlyIncome: Double,
            monthlyOutflow: Double,
            today: LocalDate,
            locale: Locale = Locale.getDefault()
        ): HomeSummary {
            val thisMonth = YearMonth.from(today)
            val lastMonth = thisMonth.minusMonths(1)
            fun inMonth(t: Transaction, month: YearMonth) =
                parseDate(t.date)?.let { YearMonth.from(it) == month } == true

            val expenses = all.filter { !it.isIncome }
            val spentNow = expenses.filter { inMonth(it, thisMonth) }
            val categories = spentNow.groupBy { it.category }
                .map { (category, list) -> CategoryTotal(category, list.sumOf { it.amount }) }
                .sortedByDescending { it.amount }

            val budgetLeft = if (budgets.isEmpty()) null else {
                val budgeted = budgets.map { it.category }.toSet()
                budgets.sumOf { it.monthlyLimit } - spentNow.filter { it.category in budgeted }.sumOf { it.amount }
            }

            return HomeSummary(
                monthName = thisMonth.month.getDisplayName(TextStyle.FULL, locale),
                previousMonthName = lastMonth.month.getDisplayName(TextStyle.FULL, locale),
                spent = spentNow.sumOf { it.amount },
                previousSpent = expenses.filter { inMonth(it, lastMonth) }.sumOf { it.amount },
                income = monthlyIncome,
                savings = monthlyIncome - monthlyOutflow,
                budgetLeft = budgetLeft,
                categories = categories,
                recent = all.take(RECENT_COUNT),
                hasTransactions = all.isNotEmpty(),
                today = today
            )
        }

        /** "Today", "Yesterday", otherwise "03 Nov". Unreadable dates are shown as they are. */
        fun friendlyDate(text: String, today: LocalDate, locale: Locale = Locale.getDefault()): String {
            val date = parseDate(text) ?: return text
            return when (date) {
                today -> "Today"
                today.minusDays(1) -> "Yesterday"
                else -> date.format(DateTimeFormatter.ofPattern("dd MMM", locale))
            }
        }
    }
}
