package com.pingpaisa.app

import com.pingpaisa.app.data.Budget
import com.pingpaisa.app.data.RecurringItem
import com.pingpaisa.app.data.Transaction
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class ForecastUiTest {
    private val today = LocalDate.of(2025, 11, 10)   // 30-day month, day 10
    private var n = 0
    private fun tx(amount: Double, date: String, category: String = "Food & Dining", recipient: String = "Cafe", type: String = "expense") =
        Transaction(amount = amount, recipient = recipient, date = date, refNumber = "r${n++}", rawText = "", category = category, type = type)
    private fun ui(all: List<Transaction>, outflow: Double = all.filter { it.type == "expense" }.sumOf { it.amount },
                   recurring: List<RecurringItem> = emptyList(), income: Double = 50000.0, budgets: List<Budget> = emptyList()) =
        ForecastUi.from(all, recurring, outflow, income, budgets, today, Locale.US)

    @Test fun projectsTheDailyAverage() {
        val u = ui(listOf(tx(1000.0, "02/11/25"), tx(1000.0, "09/11/25")))
        // 2,000 over 10 days = 200 a day, 20 days left
        assertEquals("₹6,000", u.projected)
        assertEquals("Projected total spend by 30 November", u.projectedCaption)
        assertEquals("Based on 10 of 30 days", u.confidenceText)
        assertEquals(listOf("1 Nov", "Today", "30 Nov"), u.axis)
    }

    @Test fun recurringPaymentsCountOnceNotDaily() {
        val rent = RecurringItem(name = "Landlord", amount = 15000.0, category = "Rent", dayOfMonth = 1)
        val emi = RecurringItem(name = "Bajaj EMI", amount = 5000.0, category = "Loan", dayOfMonth = 20, type = "EMI")
        val all = listOf(tx(15000.0, "01/11/25", recipient = "Landlord"), tx(1000.0, "05/11/25"))
        // outflow as the health score gives it: 16,000 tracked + the 5,000 EMI not seen yet
        val u = ui(all, outflow = 21000.0, recurring = listOf(rent, emi))
        assertEquals("₹23,000", u.projected)   // 21,000 + 100 a day x 20
    }

    @Test fun incomeIsIgnoredAndOnlyThisMonthCounts() {
        val u = ui(listOf(tx(1000.0, "05/11/25"), tx(9000.0, "05/11/25", type = "income"), tx(5000.0, "20/10/25")), outflow = 1000.0)
        assertEquals("₹3,000", u.projected)
    }

    @Test fun verdictAgainstIncome() {
        assertEquals("You’re on track", ui(listOf(tx(1000.0, "05/11/25"))).verdict)
        assertEquals("Close to your income", ui(listOf(tx(14000.0, "05/11/25"))).verdict)       // 42,000
        assertEquals("Heading past your income", ui(listOf(tx(20000.0, "05/11/25"))).verdict)   // 60,000
        assertEquals("Add your salary to compare", ui(emptyList(), income = 0.0).verdict)
    }

    @Test fun budgetAlertAtThisPace() {
        val u = ui(listOf(tx(3000.0, "05/11/25")), budgets = listOf(Budget("Food & Dining", 8000.0)))
        val a = u.alert!!
        assertEquals("Food & Dining may cross its budget", a.title)
        assertTrue(a.text.contains("₹1,000"))   // 9,000 at this pace vs 8,000
        assertTrue(a.opensBudgets)
    }

    @Test fun biggestCategoryWhenNoBudgetIsCrossed() {
        val u = ui(listOf(tx(600.0, "05/11/25", category = "Travel"), tx(400.0, "05/11/25")))
        assertEquals("Travel is your biggest spend", u.alert!!.title)
        assertTrue(u.alert!!.text.contains("60%"))
    }

    @Test fun noSpendingNoAlert() {
        val u = ui(emptyList())
        assertNull(u.alert)
        assertEquals("₹0", u.projected)
    }

    @Test fun chartPathsStayInsideTheArtwork() {
        val u = ui(listOf(tx(1000.0, "02/11/25"), tx(1000.0, "09/11/25")))
        assertTrue(u.actualPath!!.startsWith("M10.0 120.0"))
        assertTrue(u.projectionPath!!.endsWith("L290.0 ${String.format(Locale.US, "%.1f", 120 - 95 * 6000 / 6000.0)}"))
        assertTrue(u.fillPath!!.endsWith("Z"))
        assertTrue(u.todayX in 10f..290f && u.todayY in 25f..120f)
    }

    @Test fun niceMax() {
        assertEquals(55000.0, ForecastUi.niceMax(54200.0), 0.0)
        assertEquals(850.0, ForecastUi.niceMax(830.0), 0.0)
        assertEquals(6000.0, ForecastUi.niceMax(6000.0), 0.0)
    }
}
