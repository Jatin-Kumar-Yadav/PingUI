package com.pingpaisa.app

import com.pingpaisa.app.data.Transaction
import com.pingpaisa.app.data.isIncome
import com.pingpaisa.app.healthscore.Money
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** One donut segment and legend row. [fraction] is its share of the month's spending (0..1). */
internal data class DonutSlice(val label: String, val amount: String, val fraction: Float)

/** [id] is null only for the sample rows used in previews. */
internal data class TxRow(
    val id: Int?, val name: String, val note: String, val amount: String, val icon: Int, val tone: Tone
)

/** One day in the list: its heading, the day's net amount ("−₹806" / "+₹84,514") and its rows. */
internal data class TxDay(val label: String, val net: String, val rows: List<TxRow>)

/** Everything the Transactions screen shows for one month, already turned into display text. */
internal data class TransactionsUi(
    val eyebrow: String,              // "NOVEMBER 2026"
    val spent: String,                // centre of the donut, "₹42.6K"
    val slices: List<DonutSlice>,     // empty when nothing was spent
    val days: List<TxDay>,            // newest day first; empty when the month has no transactions
    val showMonthSwitcher: Boolean,   // false for the design sample
    val canGoBack: Boolean,           // an earlier month has transactions
    val canGoForward: Boolean,        // this is not the current month
    val monthName: String             // "November", for the empty message
) {
    val isEmpty: Boolean get() = days.isEmpty()

    companion object {
        /** Top categories shown separately; the rest are combined into "Other". */
        private const val SEPARATE_SLICES = 3

        /**
         * @param all every transaction (any order)
         * @param month the month to show
         * @param today decides "Today" / "Yesterday" and the newest month that can be shown
         */
        fun from(all: List<Transaction>, month: YearMonth, today: LocalDate, locale: Locale = Locale.getDefault()): TransactionsUi {
            val dated = all.mapNotNull { t -> HomeSummary.parseDate(t.date)?.let { it to t } }
            val inMonth = dated.filter { YearMonth.from(it.first) == month }

            // Spending by category (expenses only), biggest first.
            val totals = inMonth.filter { !it.second.isIncome }
                .groupBy { it.second.category }
                .map { (category, list) -> category to list.sumOf { it.second.amount } }
                .sortedByDescending { it.second }
            val spent = totals.sumOf { it.second }
            val shown = if (totals.size <= SEPARATE_SLICES + 1) totals
            else totals.take(SEPARATE_SLICES) + ("Other" to totals.drop(SEPARATE_SLICES).sumOf { it.second })
            val slices = shown.map { (label, amount) -> DonutSlice(label, Money.compact(amount), (amount / spent).toFloat()) }

            val days = inMonth.groupBy { it.first }.toSortedMap(reverseOrder()).map { (date, pairs) ->
                val net = pairs.sumOf { if (it.second.isIncome) it.second.amount else -it.second.amount }
                TxDay(
                    label = dayLabel(date, today, locale),
                    net = if (Math.round(net) == 0L) "₹0" else if (net > 0) "+" + Money.inr(net) else Money.inr(net),
                    rows = pairs.map { (_, t) -> row(t) }
                )
            }

            return TransactionsUi(
                eyebrow = month.month.getDisplayName(TextStyle.FULL, locale).uppercase(locale) + " " + month.year,
                spent = Money.compact(spent),
                slices = slices,
                days = days,
                showMonthSwitcher = true,
                canGoBack = dated.any { YearMonth.from(it.first) < month },
                canGoForward = month < YearMonth.from(today),
                monthName = month.month.getDisplayName(TextStyle.FULL, locale)
            )
        }

        private fun row(t: Transaction): TxRow {
            val style = categoryStyle(t.category, t.isIncome)
            return TxRow(
                id = t.id, name = t.recipient,
                note = (if (t.isIncome) "Income" else t.category) + " • " + sourceLabel(t),
                amount = amountText(t), icon = style.icon, tone = style.tone
            )
        }

        private fun dayLabel(date: LocalDate, today: LocalDate, locale: Locale): String = when (date) {
            today -> "TODAY"
            today.minusDays(1) -> "YESTERDAY"
            else -> date.format(DateTimeFormatter.ofPattern("dd MMM", locale)).uppercase(locale)
        }

        /** The design's original sample content, used by Android Studio previews and the screenshot tests. */
        val Sample = TransactionsUi(
            eyebrow = "JUNE 2025", spent = "₹42.6K",
            slices = listOf(
                DonutSlice("Food", "₹12.4K", .29f), DonutSlice("Bills", "₹10.8K", .25f),
                DonutSlice("Travel", "₹8.6K", .21f), DonutSlice("Other", "₹10.8K", .25f)
            ),
            days = listOf(
                TxDay("TODAY", "−₹806", listOf(
                    TxRow(null, "Third Wave Coffee", "Food • Today, 9:42 AM", "−₹320", R.drawable.ic_pingpaisa_coffee, Tone.Peach),
                    TxRow(null, "Uber India", "Travel • Yesterday", "−₹486", R.drawable.ic_pingpaisa_car, Tone.Sage)
                )),
                TxDay("YESTERDAY", "+₹84,514", listOf(
                    TxRow(null, "Salary credit", "Income • 01 Jun", "+₹85,000", R.drawable.ic_pingpaisa_wallet, Tone.Teal)
                ))
            ),
            showMonthSwitcher = false, canGoBack = false, canGoForward = false, monthName = "June"
        )
    }
}
