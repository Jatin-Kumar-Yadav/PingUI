package com.pingpaisa.app

import com.pingpaisa.app.data.Transaction
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class CashFlowUiTest {
    private val today = LocalDate.of(2025, 11, 20)
    private var n = 0
    private fun tx(amount: Double, date: String, type: String = "expense") =
        Transaction(amount = amount, recipient = "X", date = date, refNumber = "r${n++}", rawText = "", type = type)
    private fun ui(all: List<Transaction>, salary: Double = 50000.0, outflow: Double = 0.0) =
        CashFlowUi.from(all, salary, outflow, today, Locale.US)

    @Test fun sixMonthsEndingThisMonth() {
        assertEquals(listOf("Jun", "Jul", "Aug", "Sep", "Oct", "Nov"), ui(emptyList()).months.map { it.label })
    }

    @Test fun nothingRecordedYet() {
        val u = ui(emptyList(), salary = 50000.0, outflow = 0.0)
        assertFalse(u.hasData)
        assertEquals("₹50,000", u.moneyIn)
        assertNull(u.moneyInChange)
        assertTrue(u.months.dropLast(1).all { it.income == 0.0 })
    }

    @Test fun salaryCountsFromTheFirstTransactionMonth() {
        val u = ui(listOf(tx(100.0, "10/09/25")))
        assertEquals(listOf(0.0, 0.0, 0.0, 50000.0, 50000.0, 50000.0), u.months.map { it.income })
    }

    @Test fun extraIncomeAndExpensesPerMonth() {
        val u = ui(listOf(tx(2000.0, "05/10/25", "income"), tx(8000.0, "06/10/25"), tx(300.0, "07/10/25")), outflow = 12000.0)
        val oct = u.months[4]
        assertEquals(52000.0, oct.income, 0.0)
        assertEquals(8300.0, oct.expenses, 0.0)
        assertEquals(12000.0, u.months[5].expenses, 0.0)   // this month uses the health outflow
    }

    @Test fun netSavingsMatchesIncomeMinusOutflow() {
        val u = ui(listOf(tx(100.0, "01/10/25")), outflow = 18000.0)
        assertEquals("+₹32,000", u.netText)
    }

    @Test fun negativeNet() {
        val u = ui(listOf(tx(100.0, "01/10/25")), outflow = 60000.0)
        assertEquals("−₹10,000", u.netText)
        assertEquals("You spent more than you earned", u.netCaption)
    }

    @Test fun changeAgainstLastMonth() {
        val u = ui(listOf(tx(20000.0, "05/10/25")), outflow = 10000.0)
        assertEquals("−50.0%", u.moneyOutChange)
        assertEquals("+0.0%", u.moneyInChange)
        assertEquals("↑ 33%", u.netBadge)   // net 40,000 vs 30,000
    }

    @Test fun changeTextHandlesZeroBefore() {
        assertNull(CashFlowUi.changeText(5.0, 0.0))
        assertEquals("+6.2%", CashFlowUi.changeText(106.2, 100.0))
    }

    @Test fun barsAreRelativeToTheBiggestValue() {
        val u = ui(listOf(tx(100.0, "01/10/25")), salary = 100000.0, outflow = 50000.0)
        assertEquals(1f, u.months.last().incomeFraction, 0f)
        assertEquals(.5f, u.months.last().expenseFraction, 0.001f)
        assertEquals(0f, u.months.first().incomeFraction, 0f)
    }

    @Test fun invalidDatesAreIgnored() {
        assertEquals(0.0, ui(listOf(tx(900.0, "31/02/25"))).months[4].expenses, 0.0)
    }
}
