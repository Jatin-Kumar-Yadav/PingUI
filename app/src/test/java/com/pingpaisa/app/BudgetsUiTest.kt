package com.pingpaisa.app

import com.pingpaisa.app.data.Budget
import com.pingpaisa.app.data.Transaction
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class BudgetsUiTest {
    private val today = LocalDate.of(2025, 11, 20)
    private fun tx(amount: Double, category: String, date: String = "05/11/25", type: String = "expense") =
        Transaction(amount = amount, recipient = "X", date = date, refNumber = "r$amount$date$category", rawText = "",
            category = category, type = type)
    private fun ui(budgets: List<Budget>, all: List<Transaction>) = BudgetsUi.from(budgets, all, today, Locale.US)

    @Test fun noBudgetsIsEmpty() {
        val u = ui(emptyList(), listOf(tx(100.0, "Food & Dining")))
        assertTrue(u.isEmpty)
        assertEquals("NOVEMBER 2025", u.month)
    }

    @Test fun percentAndUsageText() {
        val u = ui(listOf(Budget("Food & Dining", 20000.0)), listOf(tx(12400.0, "Food & Dining")))
        val row = u.rows.single()
        assertEquals(62, row.percent)
        assertEquals("₹12.4K / ₹20K", row.usage)
        assertEquals("₹20,000", u.totalLimit)
        assertEquals("Remaining", u.remainingLabel)
        assertEquals("₹7,600", u.remaining)
    }

    @Test fun onlyThisMonthsExpensesCount() {
        val u = ui(listOf(Budget("Food & Dining", 1000.0)), listOf(
            tx(300.0, "Food & Dining"),
            tx(500.0, "Food & Dining", date = "28/10/25"),
            tx(900.0, "Food & Dining", type = "income"),
            tx(50.0, "Food & Dining", date = "31/02/25")))
        assertEquals(30, u.rows.single().percent)
    }

    @Test fun spendingInOtherCategoriesIsIgnored() {
        val u = ui(listOf(Budget("Travel", 1000.0)), listOf(tx(400.0, "Food & Dining")))
        assertEquals(0, u.rows.single().percent)
        assertEquals("₹1,000", u.remaining)
    }

    @Test fun overBudgetShowsHowMuch() {
        val u = ui(listOf(Budget("Travel", 1000.0)), listOf(tx(1500.0, "Travel")))
        val row = u.rows.single()
        assertEquals(150, row.percent)
        assertTrue(row.isOver)
        assertEquals(1f, row.fraction, 0f)
        assertEquals("Over budget by", u.remainingLabel)
        assertEquals("₹500", u.remaining)
    }

    @Test fun ringColourByUsage() {
        assertEquals(BudgetsUi.ringFor(0), BudgetsUi.ringFor(79))
        assertNotEquals(BudgetsUi.ringFor(79), BudgetsUi.ringFor(80))
        assertEquals(BudgetsUi.ringFor(80), BudgetsUi.ringFor(99))
        assertNotEquals(BudgetsUi.ringFor(99), BudgetsUi.ringFor(100))
        assertEquals(BudgetsUi.ringFor(100), BudgetsUi.ringFor(400))
    }

    @Test fun mostUsedBudgetComesFirst() {
        val u = ui(listOf(Budget("Travel", 1000.0), Budget("Shopping", 1000.0), Budget("Grocery", 1000.0)),
            listOf(tx(900.0, "Shopping"), tx(100.0, "Travel")))
        assertEquals(listOf("Shopping", "Travel", "Grocery"), u.rows.map { it.category })
    }

    @Test fun sampleIsNotEditable() {
        assertFalse(BudgetsUi.Sample.editable)
        assertEquals(4, BudgetsUi.Sample.rows.size)
    }

    @Test fun validation() {
        assertTrue(BudgetValidation.validate("Food", "500").isValid)
        assertNotNull(BudgetValidation.validate("", "500").category)
        assertNotNull(BudgetValidation.validate("Food", "").limit)
        assertNotNull(BudgetValidation.validate("Food", "0").limit)
        assertTrue(BudgetValidation.isAmountText("12.50"))
        assertFalse(BudgetValidation.isAmountText("12.505"))
    }

    @Test fun availableHidesBudgetedCategoriesAnyCase() {
        assertEquals(listOf("Travel"), BudgetValidation.available(listOf("Food & Dining", "Travel"), listOf(Budget("food & dining", 1.0))))
    }
}
