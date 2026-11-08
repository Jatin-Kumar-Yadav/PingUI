package com.pingpaisa.app

import com.pingpaisa.app.data.Transaction
import com.pingpaisa.app.data.isIncome
import com.pingpaisa.app.healthscore.Money
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** One pair of bars. The fractions (0..1) are relative to the biggest value on the chart. */
internal data class FlowMonth(val label: String, val income: Double, val expenses: Double, val incomeFraction: Float, val expenseFraction: Float)

/** Everything the Cash flow screen shows. A null change means "nothing to compare with", so no badge. */
internal data class CashFlowUi(
    val netText: String,             // "+₹32,320" / "−₹5,000"
    val netCaption: String,
    val netBadge: String?,           // change in net savings against last month: "↑ 18%"
    val months: List<FlowMonth>,     // oldest first, the last one is this month
    val moneyIn: String,
    val moneyInChange: String?,
    val moneyOut: String,
    val moneyOutChange: String?,
    val hasData: Boolean
) {
    companion object {
        const val MONTHS = 6

        /**
         * @param salary the monthly salary; it counts as income in every month since the first
         *   transaction was recorded (earlier months are shown empty rather than guessed)
         * @param currentOutflow this month's spending plus unpaid EMIs, the same figure Home and the
         *   health score use, so "Net savings" matches them; older months use their transactions only
         */
        fun from(all: List<Transaction>, salary: Double, currentOutflow: Double, today: LocalDate, locale: Locale = Locale.getDefault()): CashFlowUi {
            val thisMonth = YearMonth.from(today)
            val dated = all.mapNotNull { t -> HomeSummary.parseDate(t.date)?.let { YearMonth.from(it) to t } }
            val first = dated.minOfOrNull { it.first }
            val incomeBy = dated.filter { it.second.isIncome }.groupBy({ it.first }, { it.second.amount }).mapValues { it.value.sum() }
            val spentBy = dated.filter { !it.second.isIncome }.groupBy({ it.first }, { it.second.amount }).mapValues { it.value.sum() }

            val list = (MONTHS - 1 downTo 0).map { thisMonth.minusMonths(it.toLong()) }
            val values = list.map { m ->
                val active = m == thisMonth || (first != null && m >= first)
                val income = if (active) salary + (incomeBy[m] ?: 0.0) else 0.0
                val out = if (m == thisMonth) currentOutflow else spentBy[m] ?: 0.0
                m to (income to out)
            }
            val top = values.maxOf { maxOf(it.second.first, it.second.second) }
            fun frac(v: Double) = if (top <= 0 || v <= 0) 0f else (v / top).toFloat().coerceAtLeast(0.02f)
            val months = values.map { (m, io) ->
                FlowMonth(m.month.getDisplayName(TextStyle.SHORT, locale), io.first, io.second, frac(io.first), frac(io.second))
            }

            val now = months.last()
            val prev = months[months.size - 2]
            val net = now.income - now.expenses
            val prevNet = prev.income - prev.expenses
            val activeNets = months.filter { it.income > 0 || it.expenses > 0 }.map { it.income - it.expenses }

            val badge = if (prevNet > 0 && prev.income > 0) {
                val change = ((net - prevNet) / prevNet * 100).roundToInt()
                if (change == 0) null else (if (change > 0) "↑ " else "↓ ") + abs(change) + "%"
            } else null
            val caption = when {
                net < 0 -> "You spent more than you earned"
                net > 0 && activeNets.size > 1 && net >= activeNets.max() -> "Best of the last $MONTHS months"
                now.income > 0 -> "${(net / now.income * 100).roundToInt()}% of your income kept"
                else -> "Nothing recorded yet"
            }
            return CashFlowUi(
                netText = (if (net < 0) "−" else "+") + Money.inr(abs(net)),
                netCaption = caption, netBadge = badge, months = months,
                moneyIn = Money.inr(now.income), moneyInChange = changeText(now.income, prev.income),
                moneyOut = Money.inr(now.expenses), moneyOutChange = changeText(now.expenses, prev.expenses),
                hasData = all.isNotEmpty()
            )
        }

        /** "+6.2%" / "−8.0%", or null when last month was zero. */
        fun changeText(now: Double, before: Double): String? {
            if (before <= 0) return null
            val pct = (now - before) / before * 100
            return (if (pct < 0) "−" else "+") + String.format(Locale.US, "%.1f", abs(pct)) + "%"
        }

        /** The design sample content. */
        val Sample = CashFlowUi(
            netText = "+₹32,320", netCaption = "Best month this quarter", netBadge = "↑ 18%",
            months = listOf("Jan" to (72 to 48), "Feb" to (90 to 55), "Mar" to (64 to 68), "Apr" to (84 to 44), "May" to (76 to 60), "Jun" to (94 to 51))
                .map { (label, b) -> FlowMonth(label, 0.0, 0.0, b.first / 100f, b.second / 100f) },
            moneyIn = "₹85,000", moneyInChange = "+6.2%", moneyOut = "₹42,680", moneyOutChange = "−8.0%", hasData = true
        )
    }
}
