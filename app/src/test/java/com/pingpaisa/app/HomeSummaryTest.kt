package com.pingpaisa.app

import com.pingpaisa.app.data.Budget
import com.pingpaisa.app.data.Transaction
import com.pingpaisa.app.healthscore.ScoreCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale

class HomeSummaryTest {

    private val today = LocalDate.of(2026, 11, 6)
    private val en = Locale.ENGLISH

    private var nextId = 1
    private fun expense(amount: Double, date: String, category: String = "Food & Dining", who: String = "Cafe") =
        Transaction(id = nextId, amount = amount, recipient = who, date = date, refNumber = "R${nextId++}",
            rawText = "", category = category)

    private fun income(amount: Double, date: String) =
        Transaction(id = nextId, amount = amount, recipient = "Bonus", date = date, refNumber = "R${nextId++}",
            rawText = "", category = "Income", type = Transaction.TYPE_INCOME)

    private fun build(all: List<Transaction>, budgets: List<Budget> = emptyList(), income: Double = 50000.0,
                      outflow: Double = 0.0, day: LocalDate = today) =
        HomeSummary.build(all, budgets, income, outflow, day, en)

    @Test fun spentCountsOnlyThisMonthsExpenses() {
        val s = build(listOf(
            expense(100.0, "03/11/26"), expense(250.0, "05/11/26"),
            expense(999.0, "30/10/26"),              // last month
            income(5000.0, "04/11/26")               // income is not spending
        ))
        assertEquals(350.0, s.spent, 0.001)
        assertEquals(999.0, s.previousSpent, 0.001)
        assertEquals("November", s.monthName)
        assertEquals("October", s.previousMonthName)
    }

    @Test fun changePercentComparesWithLastMonth() {
        assertEquals(-50, build(listOf(expense(100.0, "02/11/26"), expense(200.0, "10/10/26"))).changePercent)
        assertEquals(100, build(listOf(expense(400.0, "02/11/26"), expense(200.0, "10/10/26"))).changePercent)
        assertNull(build(listOf(expense(400.0, "02/11/26"))).changePercent)   // nothing last month
    }

    @Test fun januaryLooksBackToDecember() {
        val s = build(listOf(expense(70.0, "20/12/26"), expense(30.0, "02/01/27")), day = LocalDate.of(2027, 1, 10))
        assertEquals(30.0, s.spent, 0.001)
        assertEquals(70.0, s.previousSpent, 0.001)
        assertEquals("December", s.previousMonthName)
    }

    @Test fun categoriesAreGroupedAndBiggestFirst() {
        val s = build(listOf(
            expense(100.0, "01/11/26", "Travel"), expense(300.0, "02/11/26", "Food & Dining"),
            expense(250.0, "03/11/26", "Food & Dining"), expense(50.0, "04/11/26", "Shopping")
        ))
        assertEquals(listOf("Food & Dining", "Travel", "Shopping"), s.categories.map { it.category })
        assertEquals(550.0, s.categories[0].amount, 0.001)
    }

    @Test fun budgetLeftIsNullUntilABudgetExists() {
        assertNull(build(listOf(expense(100.0, "01/11/26"))).budgetLeft)
    }

    @Test fun budgetLeftSubtractsOnlyBudgetedCategories() {
        val s = build(
            listOf(expense(3000.0, "01/11/26", "Food & Dining"), expense(900.0, "02/11/26", "Travel")),
            budgets = listOf(Budget("Food & Dining", 8000.0), Budget("Shopping", 2000.0))
        )
        assertEquals(10000.0 - 3000.0, s.budgetLeft!!, 0.001)   // Travel is not budgeted
    }

    @Test fun savingsIsIncomeMinusOutflowAndCanBeNegative() {
        assertEquals(30000.0, build(emptyList(), income = 50000.0, outflow = 20000.0).savings, 0.001)
        assertEquals(-5000.0, build(emptyList(), income = 50000.0, outflow = 55000.0).savings, 0.001)
    }

    @Test fun recentTakesTheFirstThreeIncludingIncome() {
        val all = listOf(expense(1.0, "06/11/26"), income(2.0, "06/11/26"), expense(3.0, "05/11/26"), expense(4.0, "04/11/26"))
        assertEquals(3, build(all).recent.size)
        assertEquals(all.take(3), build(all).recent)
    }

    @Test fun unreadableDatesAreIgnoredNotCrashing() {
        val s = build(listOf(expense(100.0, "garbage"), expense(50.0, "02/11/26")))
        assertEquals(50.0, s.spent, 0.001)
        assertTrue(s.hasTransactions)
    }

    @Test fun impossibleDatesAreRejectedButRealOnesParse() {
        assertNull(HomeSummary.parseDate("31/02/26"))
        assertNull(HomeSummary.parseDate("29/02/26"))                    // 2026 is not a leap year
        assertEquals(LocalDate.of(2028, 2, 29), HomeSummary.parseDate("29/02/28"))
        assertEquals(LocalDate.of(2026, 11, 6), HomeSummary.parseDate("06/11/26"))
        assertEquals(LocalDate.of(2026, 12, 31), HomeSummary.parseDate(" 31/12/26 "))
    }

    @Test fun friendlyDates() {
        assertEquals("Today", HomeSummary.friendlyDate("06/11/26", today, en))
        assertEquals("Yesterday", HomeSummary.friendlyDate("05/11/26", today, en))
        assertEquals("03 Nov", HomeSummary.friendlyDate("03/11/26", today, en))
        assertEquals("not a date", HomeSummary.friendlyDate("not a date", today, en))
    }

    // ---- the words shown on screen

    private fun ui(all: List<Transaction>, name: String = "Janvi Yadav", risk: ScoreCalculator.RiskLevel? = null,
                   hour: Int = 9, income: Double = 100000.0, outflow: Double = 0.0) =
        HomeUi.from(build(all, income = income, outflow = outflow), name, risk, LocalDateTime.of(2026, 11, 6, hour, 0), en)

    @Test fun greetingFollowsTheTimeOfDayAndUsesTheFirstName() {
        assertEquals("GOOD MORNING", ui(emptyList(), hour = 9).greeting)
        assertEquals("GOOD AFTERNOON", ui(emptyList(), hour = 14).greeting)
        assertEquals("GOOD EVENING", ui(emptyList(), hour = 20).greeting)
        assertEquals("Namaste, Janvi", ui(emptyList()).title)
        assertEquals("Namaste", ui(emptyList(), name = " ").title)
    }

    @Test fun emptyStateAndSubtitleReflectTheData() {
        assertTrue(ui(emptyList()).isEmpty)
        assertEquals("Add your first expense to get started.", ui(emptyList()).subtitle)
        assertEquals("No spending recorded yet this month", ui(emptyList()).changeLead)
        val withData = listOf(expense(100.0, "02/11/26"))
        assertFalse(ui(withData).isEmpty)
        assertEquals("Your money is looking healthy this month.", ui(withData, risk = ScoreCalculator.RiskLevel.LOW).subtitle)
        assertEquals("Spending is running high this month.", ui(withData, risk = ScoreCalculator.RiskLevel.HIGH).subtitle)
    }

    @Test fun monthCardTextAndStats() {
        val u = ui(listOf(expense(12430.0, "02/11/26"), expense(20000.0, "10/10/26")), income = 100000.0, outflow = 12430.0)
        assertEquals("₹12,430", u.spent)
        assertEquals("38% less", u.changeStrong)
        assertEquals(" than October", u.changeTrail)
        assertFalse(u.changeUp)
        assertEquals("₹1L", u.incomeStat)
        assertEquals("₹87.6K", u.savingsStat)
        assertEquals("Not set", u.budgetStat)
        val more = ui(listOf(expense(300.0, "02/11/26"), expense(100.0, "10/10/26")))
        assertEquals("200% more", more.changeStrong)
        assertTrue(more.changeUp)
    }

    @Test fun recentRowsShowSignsAndFriendlyNotes() {
        val u = ui(listOf(expense(320.0, "06/11/26", "Food & Dining", "Third Wave Coffee"), income(5000.0, "05/11/26")))
        assertEquals("−₹320", u.recent[0].amount)
        assertEquals("Food & Dining • Today", u.recent[0].note)
        assertEquals("+₹5,000", u.recent[1].amount)
        assertEquals("Income • Yesterday", u.recent[1].note)
    }

    @Test fun negativeSavingsKeepTheirSign() {
        assertEquals("−₹5K", ui(emptyList(), income = 50000.0, outflow = 55000.0).savingsStat)
    }
}
