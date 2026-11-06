package com.pingpaisa.app

import com.pingpaisa.app.data.DetectedRecurring
import com.pingpaisa.app.data.RecurringItem
import com.pingpaisa.app.data.Transaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

class RecurringUiTest {

    private val today = LocalDate.of(2026, 11, 6)
    private val en = Locale.ENGLISH

    private fun item(id: Int, name: String, amount: Double, day: Int, type: String) =
        RecurringItem(id = id, name = name, amount = amount, category = "Financial Services", dayOfMonth = day, type = type)

    private var nextId = 1
    private fun paid(name: String, amount: Double, date: String) =
        Transaction(id = nextId, amount = amount, recipient = name, date = date, refNumber = "R${nextId++}", rawText = "")

    @Test fun dueDayIsClampedToShortMonths() {
        assertEquals(LocalDate.of(2026, 11, 30), RecurringUi.dueDateIn(YearMonth.of(2026, 11), 31))
        assertEquals(LocalDate.of(2026, 2, 28), RecurringUi.dueDateIn(YearMonth.of(2026, 2), 31))
        assertEquals(LocalDate.of(2026, 11, 5), RecurringUi.dueDateIn(YearMonth.of(2026, 11), 5))
    }

    @Test fun manualPaymentIsDueThisMonthIfItsDayIsStillAhead() {
        assertEquals(LocalDate.of(2026, 11, 15), RecurringUi.nextDueForManual(item(1, "Home loan EMI", 24500.0, 15, "EMI"), emptyList(), today))
        assertEquals(today, RecurringUi.nextDueForManual(item(1, "Rent", 15000.0, 6, "Other"), emptyList(), today))   // due today
    }

    @Test fun manualPaymentMovesToNextMonthOnceItsDayHasPassed() {
        assertEquals(LocalDate.of(2026, 12, 5), RecurringUi.nextDueForManual(item(1, "SIP", 5000.0, 5, "SIP"), emptyList(), today))
    }

    @Test fun manualPaymentMovesToNextMonthWhenAlreadyPaidThisMonth() {
        val payments = listOf(paid("home loan emi", 24500.0, "02/11/26"))   // name match ignores case
        assertEquals(LocalDate.of(2026, 12, 15),
            RecurringUi.nextDueForManual(item(1, "Home loan EMI", 24500.0, 15, "EMI"), payments, today))
    }

    @Test fun detectedPaymentIsAMonthAfterTheLastOneAndNeverInThePast() {
        val d = DetectedRecurring("Netflix", 649.0, "Entertainment", 3)
        assertEquals(LocalDate.of(2026, 11, 20),
            RecurringUi.nextDueForDetected(d, listOf(paid("Netflix", 649.0, "20/09/26"), paid("Netflix", 649.0, "20/10/26")), today))
        // Last seen in August: rolls forward to the next date after today.
        assertEquals(LocalDate.of(2026, 11, 10), RecurringUi.nextDueForDetected(d, listOf(paid("Netflix", 649.0, "10/08/26")), today))
        assertNull(RecurringUi.nextDueForDetected(d, emptyList(), today))
    }

    @Test fun dueTextAndRing() {
        assertEquals("Due today", RecurringUi.dueText(today, today))
        assertEquals("Due tomorrow", RecurringUi.dueText(today.plusDays(1), today))
        assertEquals("Due in 9 days", RecurringUi.dueText(LocalDate.of(2026, 11, 15), today))
        assertEquals(0.7f, RecurringUi.cycleProgress(LocalDate.of(2026, 11, 15), today), 0.01f)   // 21 of 31 days gone
        assertEquals(1f, RecurringUi.cycleProgress(today, today), 0.001f)
    }

    @Test fun rowsAreSoonestFirstAndTotalCountsOnlyThisMonth() {
        val ui = RecurringUi.from(
            manual = listOf(item(1, "Home loan EMI", 24500.0, 15, "EMI"), item(2, "Index fund SIP", 10000.0, 3, "SIP"),
                item(3, "Rent", 15000.0, 8, "Other")),
            detected = emptyList(), transactions = emptyList(), today = today, locale = en
        )
        assertEquals(listOf("Rent", "Home loan EMI", "Index fund SIP"), ui.rows.map { it.name })   // 8 Nov, 15 Nov, 3 Dec
        assertEquals("₹39,500", ui.dueThisMonth)       // SIP's next date is in December
        assertEquals("NOV", ui.calendarMonth)
        assertEquals("8", ui.calendarDay)
        assertEquals("₹24,500", ui.rows[1].amount)
    }

    @Test fun filterTabsUseTheTypeAndDetectedOnlyShowUnderAll() {
        val ui = RecurringUi.from(
            manual = listOf(item(1, "Home loan EMI", 24500.0, 15, "EMI"), item(2, "Index fund SIP", 10000.0, 20, "SIP")),
            detected = listOf(DetectedRecurring("Netflix", 649.0, "Entertainment", 3)),
            transactions = listOf(paid("Netflix", 649.0, "20/10/26")), today = today, locale = en
        )
        assertEquals(3, ui.filtered("All").size)
        assertEquals(listOf("Home loan EMI"), ui.filtered("EMI").map { it.name })
        assertEquals(listOf("Index fund SIP"), ui.filtered("SIP").map { it.name })
        assertTrue(ui.filtered("Other").isEmpty())
        val netflix = ui.rows.first { it.name == "Netflix" }
        assertTrue(netflix.detected); assertNull(netflix.type); assertNull(netflix.itemId)
    }

    @Test fun confirmedPaymentIsNotListedTwice() {
        val ui = RecurringUi.from(
            manual = listOf(item(1, "Netflix", 649.0, 20, "Other")),
            detected = listOf(DetectedRecurring("NETFLIX", 649.0, "Entertainment", 3)),
            transactions = emptyList(), today = today, locale = en
        )
        assertEquals(1, ui.rows.size)
        assertFalse(ui.rows[0].detected)
    }

    @Test fun confirmingADetectedPaymentPrefillsTheForm() {
        val ui = RecurringUi.from(emptyList(), listOf(DetectedRecurring("Netflix", 649.0, "Entertainment", 3)),
            listOf(paid("Netflix", 649.0, "20/10/26")), today, en)
        val draft = RecurringDraft.fromDetected(ui.rows[0])
        assertEquals(RecurringDraft("Netflix", "649", "20", "Other", confirming = true), draft)
        val avg = ui.rows[0].copy(monthlyAmount = 649.333)
        assertEquals("649.33", RecurringDraft.fromDetected(avg).amount)
    }

    @Test fun manualRowsKeepTheirDueDay() {
        val ui = RecurringUi.from(listOf(item(4, "Rent", 15000.0, 31, "Other")), emptyList(), emptyList(), today, en)
        assertEquals(31, ui.rows[0].dayOfMonth)
        assertEquals(4, ui.rows[0].itemId)
    }

    @Test fun amountBoxAcceptsOnlyMoneyShapedText() {
        assertTrue(RecurringValidation.isAmountText(""))
        assertTrue(RecurringValidation.isAmountText("24500"))
        assertTrue(RecurringValidation.isAmountText("649.5"))
        assertTrue(RecurringValidation.isAmountText("649."))
        assertFalse(RecurringValidation.isAmountText("649.555"))
        assertFalse(RecurringValidation.isAmountText("1,000"))
        assertFalse(RecurringValidation.isAmountText("-5"))
        assertFalse(RecurringValidation.isAmountText("1234567890"))
    }

    @Test fun formChecks() {
        assertTrue(RecurringValidation.validate("Home loan EMI", "24500", "15").isValid)
        val bad = RecurringValidation.validate(" ", "0", "32")
        assertTrue(bad.name != null && bad.amount != null && bad.day != null)
        assertEquals(null, RecurringValidation.validate("x", "1", "1").day)
        assertEquals(null, RecurringValidation.validate("x", "1", "31").day)
        assertTrue(RecurringValidation.validate("x", "1", "0").day != null)
        assertTrue(RecurringValidation.validate("x", "1", "").day != null)
        assertTrue(RecurringValidation.validate("x".repeat(41), "1", "5").name != null)
    }

    @Test fun emptyWhenNothingRecurs() {
        val ui = RecurringUi.from(emptyList(), emptyList(), emptyList(), today, en)
        assertTrue(ui.isEmpty)
        assertEquals("₹0", ui.dueThisMonth)
        assertEquals("–", ui.calendarDay)
        assertEquals("NOV", ui.calendarMonth)
    }
}
