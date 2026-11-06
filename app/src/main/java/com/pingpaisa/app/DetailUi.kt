package com.pingpaisa.app

import com.pingpaisa.app.data.Transaction
import com.pingpaisa.app.data.isIncome
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What the Detail screen is showing: still reading, nothing to show, or a transaction. */
internal sealed interface DetailLoad {
    data object Loading : DetailLoad
    data object Missing : DetailLoad
    data class Found(val transaction: Transaction) : DetailLoad
}

/** A tile in the "Choose category" grid. [category] is null for the "More" tile that opens the full list. */
internal data class CategoryTile(val label: String, val category: String?, val icon: Int, val tone: Tone)

/**
 * The grid: the design's four quick categories, the [selected] one if it isn't among them
 * (so a category picked from the full list stays visible and ticked), then "More".
 */
internal fun detailCategoryTiles(selected: String): List<CategoryTile> {
    val quick = nativeCategories.map { CategoryTile(it.label, datasetCategoryFor(it.label), it.icon, it.tone) }
    val extra = if (quick.any { it.category == selected } || selected.isBlank()) emptyList()
    else categoryStyle(selected).let { listOf(CategoryTile(selected, selected, it.icon, it.tone)) }
    return quick + extra + CategoryTile("More", null, R.drawable.ic_pingpaisa_plus, Tone.Cream)
}

/** Every category the picker lists: the dataset's plus custom ones in use, once each (any case), sorted. "Income" is never offered. */
internal fun pickableCategories(dataset: List<String>, inUse: List<String>): List<String> =
    (dataset + inUse).map { it.trim() }
        .filter { it.isNotEmpty() && !it.equals("Income", ignoreCase = true) }
        .distinctBy { it.lowercase() }
        .sortedBy { it.lowercase() }

/** A custom category typed in the picker: null when it's fine, otherwise the message to show. */
internal fun customCategoryError(text: String): String? = when {
    text.isBlank() -> "Type a category name"
    text.trim().length > 30 -> "Keep it under 30 characters"
    text.trim().equals("Income", ignoreCase = true) -> "Income is set by the Expense/Income switch"
    else -> null
}

/** Everything the Detail screen shows for one transaction, already turned into display text. */
internal data class DetailUi(
    val id: Int,
    val recipient: String,
    val category: String,
    val note: String,
    val canRecategorize: Boolean,    // income has no category to choose
    val amount: String,              // "−₹320" / "+₹5,000"
    val isIncome: Boolean,
    val name: String,
    val status: String,              // badge in the corner of the card
    val subline: String,             // "Today • UPI"
    val icon: Int,
    val tone: Tone,
    val rows: List<Pair<String, String>>   // label to value, under the dashed line
) {
    companion object {
        fun from(t: Transaction, today: LocalDate, locale: Locale = Locale.getDefault()): DetailUi {
            val style = categoryStyle(t.category, t.isIncome)
            val manual = t.source == "manual"
            return DetailUi(
                id = t.id,
                recipient = t.recipient,
                category = t.category,
                note = t.note,
                canRecategorize = !t.isIncome,
                amount = amountText(t),
                isIncome = t.isIncome,
                name = t.recipient,
                status = when {
                    t.isIncome -> "Received"
                    manual -> "Recorded"
                    else -> "Successful"
                },
                subline = HomeSummary.friendlyDate(t.date, today, locale) + " • " + sourceLabel(t),
                icon = style.icon,
                tone = style.tone,
                rows = buildList {
                    add("Category" to if (t.isIncome) "Income" else t.category)
                    if (!t.isIncome && t.subcategory.isNotBlank() && t.subcategory != "Miscellaneous") add("Type" to t.subcategory)
                    add("Date" to fullDate(t.date, locale))
                    if (manual) add("Added" to "Manually") else add("UPI reference" to t.refNumber)
                }
            )
        }

        /** "05 Nov 2026"; unreadable dates are shown as they are. */
        private fun fullDate(text: String, locale: Locale): String =
            HomeSummary.parseDate(text)?.format(DateTimeFormatter.ofPattern("dd MMM yyyy", locale)) ?: text
    }
}
