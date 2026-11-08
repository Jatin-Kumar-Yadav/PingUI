package com.pingpaisa.app

import androidx.compose.ui.graphics.Color
import com.pingpaisa.app.data.Budget
import com.pingpaisa.app.data.Transaction
import com.pingpaisa.app.data.isIncome
import com.pingpaisa.app.healthscore.Money
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/** One budget card: a category's monthly limit and how much of it this month's spending has used. */
internal data class BudgetRow(
    val category: String,
    val limit: Double,
    val spent: Double,
    val percent: Int,            // may go above 100 when over budget
    val usage: String,           // "₹12.4K / ₹20K"
    val ring: Color,
    val icon: Int,
    val tone: Tone
) {
    val fraction: Float get() = (percent / 100f).coerceIn(0f, 1f)
    val isOver: Boolean get() = percent >= 100
}

/**
 * Everything the Budgets screen shows, already turned into text. [editable] is false for the
 * design's sample (previews and screenshot tests), where the + button and the cards do nothing.
 */
internal data class BudgetsUi(
    val month: String,                 // "NOVEMBER 2025"
    val totalLimit: String,            // "₹60,000"
    val remainingLabel: String,        // "Remaining" or "Over budget by"
    val remaining: String,             // "₹17,320"
    val rows: List<BudgetRow>,
    val editable: Boolean = true
) {
    val isEmpty: Boolean get() = rows.isEmpty()

    companion object {
        private val OnTrack = Color(0xFF8BAE86)
        private val Close = Color(0xFFE5AF81)
        private val Over = Color(0xFFE99A98)

        /** Green under 80%, orange from 80%, red once the limit is used up. */
        fun ringFor(percent: Int): Color = when {
            percent >= 100 -> Over
            percent >= 80 -> Close
            else -> OnTrack
        }

        /**
         * @param all every transaction (income is ignored)
         * Only spending in a budgeted category counts towards the total, like Home's "Budget left".
         */
        fun from(budgets: List<Budget>, all: List<Transaction>, today: LocalDate, locale: Locale = Locale.getDefault()): BudgetsUi {
            val thisMonth = YearMonth.from(today)
            val spentBy = all.filter { !it.isIncome && HomeSummary.parseDate(it.date)?.let { d -> YearMonth.from(d) == thisMonth } == true }
                .groupBy { it.category }.mapValues { (_, list) -> list.sumOf { it.amount } }
            val rows = budgets.map { b ->
                val spent = spentBy[b.category] ?: 0.0
                val percent = if (b.monthlyLimit > 0) (spent / b.monthlyLimit * 100).roundToInt() else 0
                val style = categoryStyle(b.category)
                BudgetRow(b.category, b.monthlyLimit, spent, percent,
                    "${Money.compact(spent)} / ${Money.compact(b.monthlyLimit)}", ringFor(percent), style.icon, style.tone)
            }.sortedWith(compareByDescending<BudgetRow> { it.percent }.thenBy { it.category.lowercase() })
            val left = rows.sumOf { it.limit } - rows.sumOf { it.spent }
            return BudgetsUi(
                month = "${thisMonth.month.getDisplayName(TextStyle.FULL, locale)} ${thisMonth.year}".uppercase(locale),
                totalLimit = Money.inr(rows.sumOf { it.limit }),
                remainingLabel = if (left < 0) "Over budget by" else "Remaining",
                remaining = Money.inr(kotlin.math.abs(left)),
                rows = rows
            )
        }

        /** The design's sample content. */
        val Sample = BudgetsUi(
            month = "JUNE 2025", totalLimit = "₹60,000", remainingLabel = "Remaining", remaining = "₹17,320",
            rows = listOf(
                Triple("Food", 62, "₹12.4K / ₹20K"), Triple("Travel", 43, "₹8.6K / ₹20K"),
                Triple("Shopping", 86, "₹8.6K / ₹10K"), Triple("Bills", 72, "₹10.8K / ₹15K")
            ).mapIndexed { i, (label, percent, usage) ->
                val item = nativeCategories.first { it.label == label }
                BudgetRow(label, 0.0, 0.0, percent, usage,
                    listOf(Color(0xFFE5AF81), Color(0xFF8BAE86), Color(0xFFE99A98), Color(0xFF9E92CA))[i], item.icon, item.tone)
            },
            editable = false
        )
    }
}

internal data class BudgetErrors(val category: String? = null, val limit: String? = null) {
    val isValid: Boolean get() = category == null && limit == null
}

/** Rules for the add / edit budget sheet. */
internal object BudgetValidation {
    fun isAmountText(text: String): Boolean = RecurringValidation.isAmountText(text)

    fun validate(category: String, limit: String): BudgetErrors = BudgetErrors(
        category = if (category.isBlank()) "Choose a category" else null,
        limit = if ((limit.toDoubleOrNull() ?: 0.0) > 0.0) null else "Enter a limit above ₹0"
    )

    /** Categories still without a budget, in the picker's order. */
    fun available(choices: List<String>, budgets: List<Budget>): List<String> {
        val taken = budgets.map { it.category.lowercase() }.toSet()
        return choices.filter { it.lowercase() !in taken }
    }
}
