package com.pingpaisa.app

import com.pingpaisa.app.data.Transaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

class TransactionsUiTest {

    private val today = LocalDate.of(2026, 11, 6)
    private val nov = YearMonth.of(2026, 11)
    private val en = Locale.ENGLISH

    private var nextId = 1
    private fun expense(amount: Double, date: String, category: String = "Food & Dining", who: String = "Cafe", source: String = "SMS") =
        Transaction(id = nextId, amount = amount, recipient = who, date = date, refNumber = "R${nextId++}",
            rawText = "", category = category, source = source)

    private fun income(amount: Double, date: String) =
        Transaction(id = nextId, amount = amount, recipient = "Salary", date = date, refNumber = "R${nextId++}",
            rawText = "", category = "Income", type = Transaction.TYPE_INCOME, source = "manual")

    private fun ui(all: List<Transaction>, month: YearMonth = nov) = TransactionsUi.from(all, month, today, en)

    @Test fun eyebrowNamesTheMonth() {
        assertEquals("NOVEMBER 2026", ui(emptyList()).eyebrow)
        assertEquals("OCTOBER 2026", ui(emptyList(), YearMonth.of(2026, 10)).eyebrow)
    }

    @Test fun emptyMonthHasNoSlicesOrDays() {
        val u = ui(emptyList())
        assertTrue(u.isEmpty); assertTrue(u.slices.isEmpty()); assertEquals("₹0", u.spent)
    }

    @Test fun donutSharesAddUpAndAreBiggestFirst() {
        val u = ui(listOf(
            expense(500.0, "01/11/26", "Food & Dining"), expense(300.0, "02/11/26", "Travel"), expense(200.0, "03/11/26", "Shopping")
        ))
        assertEquals(listOf("Food & Dining", "Travel", "Shopping"), u.slices.map { it.label })
        assertEquals(1.0f, u.slices.sumOf { it.fraction.toDouble() }.toFloat(), 0.001f)
        assertEquals(.5f, u.slices[0].fraction, 0.001f)
        assertEquals("₹1K", u.spent)
    }

    @Test fun fourCategoriesAreAllShown() {
        val u = ui(listOf(
            expense(400.0, "01/11/26", "A"), expense(300.0, "01/11/26", "B"),
            expense(200.0, "01/11/26", "C"), expense(100.0, "01/11/26", "D")
        ))
        assertEquals(listOf("A", "B", "C", "D"), u.slices.map { it.label })
    }

    @Test fun moreThanFourCategoriesFoldTheRestIntoOther() {
        val u = ui(listOf(
            expense(400.0, "01/11/26", "A"), expense(300.0, "01/11/26", "B"), expense(200.0, "01/11/26", "C"),
            expense(100.0, "01/11/26", "D"), expense(50.0, "01/11/26", "E")
        ))
        assertEquals(listOf("A", "B", "C", "Other"), u.slices.map { it.label })
        assertEquals(150.0 / 1050.0, u.slices[3].fraction.toDouble(), 0.001)
    }

    @Test fun incomeNeverCountsAsSpending() {
        val u = ui(listOf(expense(100.0, "02/11/26"), income(85000.0, "01/11/26")))
        assertEquals("₹100", u.spent)
        assertEquals(1, u.slices.size)
    }

    @Test fun daysAreGroupedNewestFirstWithNetAmounts() {
        val u = ui(listOf(
            expense(320.0, "06/11/26", who = "Coffee"), expense(486.0, "06/11/26", who = "Uber"),
            income(85000.0, "05/11/26"), expense(100.0, "01/11/26")
        ))
        assertEquals(listOf("TODAY", "YESTERDAY", "01 NOV"), u.days.map { it.label })
        assertEquals("−₹806", u.days[0].net)
        assertEquals("+₹85,000", u.days[1].net)
        assertEquals("−₹100", u.days[2].net)
        assertEquals(listOf("Coffee", "Uber"), u.days[0].rows.map { it.name })
    }

    @Test fun rowsShowSignsAndSource() {
        val u = ui(listOf(expense(320.0, "06/11/26", "Food & Dining", "Coffee"), expense(50.0, "06/11/26", "Travel", "Auto", source = "manual"),
            income(5000.0, "05/11/26")))
        assertEquals("−₹320", u.days[0].rows[0].amount)
        assertEquals("Food & Dining • UPI", u.days[0].rows[0].note)
        assertEquals("Travel • Manual", u.days[0].rows[1].note)
        assertEquals("+₹5,000", u.days[1].rows[0].amount)
        assertEquals("Income • Manual", u.days[1].rows[0].note)
    }

    @Test fun otherMonthsAreExcludedAndBrowsingFlagsAreRight() {
        val all = listOf(expense(100.0, "02/11/26"), expense(900.0, "10/10/26"))
        val current = ui(all)
        assertEquals("₹100", current.spent)
        assertTrue(current.canGoBack); assertFalse(current.canGoForward)
        val october = ui(all, YearMonth.of(2026, 10))
        assertEquals("₹900", october.spent)
        assertFalse(october.canGoBack); assertTrue(october.canGoForward)
    }

    @Test fun unreadableDatesAreSkippedNotCrashing() {
        val u = ui(listOf(expense(100.0, "31/02/26"), expense(40.0, "garbage"), expense(60.0, "02/11/26")))
        assertEquals("₹60", u.spent)
        assertEquals(1, u.days.size)
    }
}
