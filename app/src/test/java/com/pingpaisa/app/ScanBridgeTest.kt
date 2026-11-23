package com.pingpaisa.app

import com.pingpaisa.app.data.RecurringItem
import com.pingpaisa.app.data.Transaction
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ScanBridgeTest {
    private val today = LocalDate.of(2025, 11, 15)
    private var n = 0
    private fun tx(amount: Double, date: String, recipient: String = "Cafe", type: String = "expense") =
        Transaction(amount = amount, recipient = recipient, date = date, refNumber = "r${n++}", rawText = "", type = type)
    private fun item(name: String, amount: Double, type: String) =
        RecurringItem(name = name, amount = amount, category = "X", dayOfMonth = 5, type = type)

    // ------------------------------------------------------------ monthlyTotals

    @Test fun noTransactionsNoHistory() {
        assertEquals(emptyList<Double>(), ScanBridge.monthlyTotals(emptyList(), listOf(item("Rent", 1000.0, "Other")), today))
    }

    @Test fun completeMonthsOnlyFromTheFirstTransaction() {
        val totals = ScanBridge.monthlyTotals(listOf(tx(100.0, "10/09/25"), tx(200.0, "02/10/25"), tx(999.0, "03/11/25")), emptyList(), today)
        assertEquals(listOf(100.0, 200.0), totals)   // September, October; November is not complete
    }

    @Test fun emptyMonthInTheMiddleCountsAsZero() {
        assertEquals(listOf(100.0, 0.0, 300.0), ScanBridge.monthlyTotals(listOf(tx(100.0, "10/08/25"), tx(300.0, "10/10/25")), emptyList(), today))
    }

    @Test fun unpaidRecurringAddedOncePaidNotDoubled() {
        val recurring = listOf(item("Bajaj EMI", 5000.0, "EMI"), item("Landlord", 15000.0, "Other"), item("SIP fund", 2000.0, "SIP"))
        val totals = ScanBridge.monthlyTotals(listOf(tx(15000.0, "01/10/25", recipient = "Landlord"), tx(500.0, "05/10/25")), recurring, today)
        assertEquals(listOf(20500.0), totals)   // 15,000 rent seen + 500 + 5,000 EMI not seen; the SIP is not spending
    }

    @Test fun incomeAndBadDatesIgnored() {
        val totals = ScanBridge.monthlyTotals(listOf(tx(100.0, "10/10/25"), tx(9000.0, "11/10/25", type = "income"), tx(50.0, "31/02/25")), emptyList(), today)
        assertEquals(listOf(100.0), totals)
    }

    @Test fun atMostSixMonths() {
        val all = (1..10).map { tx(it * 10.0, "05/%02d/25".format(it)) }   // January to October
        assertEquals(listOf(50.0, 60.0, 70.0, 80.0, 90.0, 100.0), ScanBridge.monthlyTotals(all, emptyList(), today))
    }

    // ----------------------------------------------------------------- prefill

    @Test fun prefillFromAppData() {
        val recurring = listOf(item("Bajaj EMI", 5000.0, "EMI"), item("Netflix", 649.0, "Other"), item("SIP fund", 2000.0, "SIP"))
        val all = listOf(tx(3000.0, "10/09/25"), tx(5000.0, "10/10/25"), tx(649.0, "12/10/25", recipient = "Netflix"), tx(700.0, "02/11/25"))
        val p = ScanBridge.prefill(60000.0, recurring, all, today)
        assertEquals(60000.0, p.netIncome, 0.0)
        assertEquals(5649.0, p.fixedExpenses, 0.0)
        assertEquals(4000.0, p.variableExpenses, 0.0)       // (3,000 + 5,000) / 2; Netflix is a recurring payment
        assertEquals(2000.0, p.monthlySavingsTarget, 0.0)
        assertEquals(0.0, p.savings, 0.0)                   // left for the user
        assertEquals(0.0, p.emergencyReserve, 0.0)
    }

    @Test fun prefillUsesThisMonthsPaceWithoutCompleteMonths() {
        val p = ScanBridge.prefill(50000.0, emptyList(), listOf(tx(1500.0, "03/11/25")), today)
        assertEquals(3000.0, p.variableExpenses, 0.0)       // 1,500 in 15 days → 3,000 for 30 days
    }

    @Test fun prefillWithNothingRecorded() {
        val p = ScanBridge.prefill(0.0, emptyList(), emptyList(), today)
        assertEquals(0.0, p.netIncome, 0.0)
        assertEquals(0.0, p.variableExpenses, 0.0)
        assertFalse(p.isUsable())                            // the form still asks for an income
    }

    @Test fun prefillIsUsableWithASalary() {
        assertTrue(ScanBridge.prefill(50000.0, emptyList(), emptyList(), today).isUsable())
    }
}
