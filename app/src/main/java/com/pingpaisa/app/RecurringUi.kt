package com.pingpaisa.app

import com.pingpaisa.app.data.DetectedRecurring
import com.pingpaisa.app.data.RecurringItem
import com.pingpaisa.app.data.Transaction
import com.pingpaisa.app.data.isIncome
import com.pingpaisa.app.healthscore.Money
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * One row on the Recurring screen. [type] is "EMI", "SIP" or "Other" for payments the user
 * added, null for auto-detected ones (their type isn't known until the user confirms it).
 * [itemId] is the saved RecurringItem's id (null for detected rows and the design sample).
 */
internal data class RecurringRow(
    val key: String,
    val name: String,
    val amount: String,
    val monthlyAmount: Double,
    val dueText: String,          // "Due in 3 days", "Due tomorrow", "Due today"
    val progress: Float,          // how far through the current cycle (0..1), drawn as the ring
    val icon: Int,
    val tone: Tone,
    val type: String?,
    val detected: Boolean,
    val itemId: Int?,
    val nextDue: LocalDate?,
    val dayOfMonth: Int? = null   // the due day (saved for manual rows, estimated for detected ones)
)

/** What the add / confirm sheet starts with. Amount and day are text, as typed. */
internal data class RecurringDraft(
    val name: String = "",
    val amount: String = "",
    val day: String = "",
    val type: String = "EMI",
    val confirming: Boolean = false   // opened from an auto-detected row
) {
    companion object {
        /** Prefilled from a detected payment: its name, its average amount and the day it usually goes out. */
        fun fromDetected(row: RecurringRow): RecurringDraft = RecurringDraft(
            name = row.name,
            amount = if (row.monthlyAmount % 1.0 == 0.0) "%.0f".format(Locale.US, row.monthlyAmount)
                     else "%.2f".format(Locale.US, row.monthlyAmount),
            day = row.dayOfMonth?.toString() ?: "",
            type = "Other",
            confirming = true
        )
    }
}

internal data class RecurringErrors(val name: String? = null, val amount: String? = null, val day: String? = null) {
    val isValid: Boolean get() = name == null && amount == null && day == null
}

/** Rules for the add / confirm sheet (the old AddRecurringDialog's checks, with messages). */
internal object RecurringValidation {
    const val MAX_NAME_LENGTH = 40

    /** What the amount box accepts while typing: up to 9 digits and 2 decimals. */
    fun isAmountText(text: String): Boolean = Regex("""\d{0,9}(\.\d{0,2})?""").matches(text)

    fun validate(name: String, amount: String, day: String): RecurringErrors = RecurringErrors(
        name = when {
            name.isBlank() -> "Give it a name, e.g. Bajaj Finserv EMI"
            name.trim().length > MAX_NAME_LENGTH -> "Keep it under $MAX_NAME_LENGTH characters"
            else -> null
        },
        amount = if ((amount.toDoubleOrNull() ?: 0.0) > 0.0) null else "Enter an amount above ₹0",
        day = if (day.toIntOrNull() in 1..31) null else "Pick a day from 1 to 31"
    )
}

/** Everything the Recurring screen shows, already turned into display text. */
internal data class RecurringUi(
    val dueThisMonth: String,     // "₹35,368": what is still due before the month ends
    val calendarMonth: String,    // "NOV": month of the next payment
    val calendarDay: String,      // "15": day of the next payment ("–" when there is none)
    val rows: List<RecurringRow>, // soonest first
    val isSample: Boolean = false
) {
    val isEmpty: Boolean get() = rows.isEmpty()

    /** The rows under a filter tab: All, EMI, SIP or Other. Detected rows only appear under All. */
    fun filtered(tab: String): List<RecurringRow> = if (tab == "All") rows else rows.filter { it.type == tab }

    companion object {
        /** The due date of [day] in [month], moved to the month's last day when it is shorter (31 → 30 Nov). */
        fun dueDateIn(month: YearMonth, day: Int): LocalDate = month.atDay(day.coerceIn(1, month.lengthOfMonth()))

        /**
         * Next due date of a payment the user added. It is this month's due day, unless that day has
         * passed or a payment to the same name already went out this month; then it is next month's.
         */
        fun nextDueForManual(item: RecurringItem, expenses: List<Transaction>, today: LocalDate): LocalDate {
            val thisMonth = YearMonth.from(today)
            val due = dueDateIn(thisMonth, item.dayOfMonth)
            val paid = expenses.any {
                it.recipient.trim().equals(item.name.trim(), ignoreCase = true) &&
                    HomeSummary.parseDate(it.date)?.let { d -> YearMonth.from(d) == thisMonth } == true
            }
            return if (paid || due.isBefore(today)) dueDateIn(thisMonth.plusMonths(1), item.dayOfMonth) else due
        }

        /** Next due date of a detected payment: a month after its last payment, moved forward past today. */
        fun nextDueForDetected(item: DetectedRecurring, expenses: List<Transaction>, today: LocalDate): LocalDate? {
            val last = expenses.filter { it.recipient.trim().equals(item.name.trim(), ignoreCase = true) }
                .mapNotNull { HomeSummary.parseDate(it.date) }.maxOrNull() ?: return null
            var next = last.plusMonths(1)
            while (next.isBefore(today)) next = next.plusMonths(1)
            return next
        }

        fun dueText(next: LocalDate, today: LocalDate): String = when (val days = ChronoUnit.DAYS.between(today, next)) {
            0L -> "Due today"
            1L -> "Due tomorrow"
            else -> "Due in $days days"
        }

        /** Share of the cycle (previous due date → next due date) that has already gone by. */
        fun cycleProgress(next: LocalDate, today: LocalDate): Float {
            val previous = next.minusMonths(1)
            val length = ChronoUnit.DAYS.between(previous, next).coerceAtLeast(1)
            return (ChronoUnit.DAYS.between(previous, today).toFloat() / length).coerceIn(0f, 1f)
        }

        private fun styleFor(type: String?, name: String): CategoryStyle = when (type) {
            "EMI" -> CategoryStyle(R.drawable.ic_pingpaisa_bill, Tone.Peach)
            "SIP" -> CategoryStyle(R.drawable.ic_pingpaisa_chart, Tone.Sage)
            "Other" -> CategoryStyle(R.drawable.ic_pingpaisa_repeat,
                if (Math.floorMod(name.lowercase().hashCode(), 2) == 0) Tone.Pink else Tone.Teal)
            else -> CategoryStyle(R.drawable.ic_pingpaisa_repeat, Tone.Lavender)   // detected
        }

        /**
         * @param manual payments the user added (or confirmed)
         * @param detected payments RecurringDetector found; ones with the same name as a manual
         *        payment are left out, so a confirmed payment isn't listed twice
         * @param transactions all transactions; only expenses are used
         */
        fun from(
            manual: List<RecurringItem>,
            detected: List<DetectedRecurring>,
            transactions: List<Transaction>,
            today: LocalDate,
            locale: Locale = Locale.getDefault()
        ): RecurringUi {
            val expenses = transactions.filter { !it.isIncome }
            val manualNames = manual.map { it.name.trim().lowercase() }.toSet()

            val manualRows = manual.map { item ->
                val next = nextDueForManual(item, expenses, today)
                val style = styleFor(item.type, item.name)
                RecurringRow("m${item.id}", item.name, Money.inr(item.amount), item.amount, dueText(next, today),
                    cycleProgress(next, today), style.icon, style.tone, item.type, detected = false, itemId = item.id, nextDue = next,
                    dayOfMonth = item.dayOfMonth)
            }
            val detectedRows = detected.filter { it.name.trim().lowercase() !in manualNames }.map { item ->
                val next = nextDueForDetected(item, expenses, today)
                val style = styleFor(null, item.name)
                RecurringRow("d${item.name.lowercase()}", item.name, Money.inr(item.averageAmount), item.averageAmount,
                    next?.let { dueText(it, today) } ?: "Seen ${item.occurrences} times",
                    next?.let { cycleProgress(it, today) } ?: 0f, style.icon, style.tone, null, detected = true, itemId = null, nextDue = next,
                    dayOfMonth = next?.dayOfMonth)
            }

            val rows = (manualRows + detectedRows).sortedWith(compareBy(nullsLast()) { it.nextDue })
            val thisMonth = YearMonth.from(today)
            val soonest = rows.firstOrNull { it.nextDue != null }?.nextDue
            return RecurringUi(
                dueThisMonth = Money.inr(rows.filter { it.nextDue?.let { d -> YearMonth.from(d) == thisMonth } == true }.sumOf { it.monthlyAmount }),
                calendarMonth = (soonest ?: today).month.getDisplayName(TextStyle.SHORT, locale).uppercase(locale).take(3),
                calendarDay = soonest?.dayOfMonth?.toString() ?: "–",
                rows = rows
            )
        }

        /** The design's original sample content, used by Android Studio previews and the screenshot tests. */
        val Sample = RecurringUi(
            dueThisMonth = "₹35,368", calendarMonth = "JUN", calendarDay = "30", isSample = true,
            rows = listOf(
                RecurringRow("s1", "Home loan EMI", "₹24,500", 24500.0, "Due in 3 days", .72f, R.drawable.ic_pingpaisa_bill, Tone.Peach, "EMI", false, null, null),
                RecurringRow("s2", "Index fund SIP", "₹10,000", 10000.0, "Due in 8 days", .48f, R.drawable.ic_pingpaisa_chart, Tone.Sage, "SIP", false, null, null),
                RecurringRow("s3", "Netflix", "₹649", 649.0, "Due in Tomorrow", .9f, R.drawable.ic_pingpaisa_repeat, Tone.Pink, "Other", true, null, null),
                RecurringRow("s4", "iCloud+", "₹219", 219.0, "Due in 12 days", .64f, R.drawable.ic_pingpaisa_repeat, Tone.Lavender, "Other", true, null, null)
            )
        )
    }
}
