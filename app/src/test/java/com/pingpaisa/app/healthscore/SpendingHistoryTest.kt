package com.pingpaisa.app.healthscore

import com.pingpaisa.app.data.RecurringItem
import com.pingpaisa.app.data.Transaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** SpendingHistory works on the real current month, so dates here are built from today. */
class SpendingHistoryTest {

    private val today: String = SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(Date())

    private var nextId = 1
    private fun spent(amount: Double, who: String) =
        Transaction(id = nextId, amount = amount, recipient = who, date = today, refNumber = "R${nextId++}", rawText = "")

    private fun recurring(name: String, amount: Double, type: String) =
        RecurringItem(id = nextId++, name = name, amount = amount, category = "x", dayOfMonth = 15, type = type)

    @Test fun emiAndOtherAddToOutflowButSipDoesNot() {
        val outflow = SpendingHistory.monthlyOutflow(
            listOf(spent(1000.0, "Cafe")),
            listOf(recurring("Home loan", 24500.0, "EMI"), recurring("Rent", 15000.0, "Other"), recurring("Index fund", 10000.0, "SIP"))
        )
        assertEquals(1000.0 + 24500.0 + 15000.0, outflow, 0.001)
    }

    @Test fun aRecurringPaymentAlreadySeenThisMonthIsNotCountedTwice() {
        val outflow = SpendingHistory.monthlyOutflow(
            listOf(spent(649.0, "netflix")),                        // same name, any case
            listOf(recurring("Netflix", 649.0, "Other"))
        )
        assertEquals(649.0, outflow, 0.001)
    }

    @Test fun bigOtherPaymentNowLowersTheScore() {
        // The case from the phone: ₹1,08,000 income, ~₹21,000 spent, ₹80,000 "Other" recurring.
        val expenses = listOf(spent(20966.0, "Shop"))
        val items = listOf(recurring("Lgrh", 80000.0, "Other"), recurring("Jfuti", 10000.0, "SIP"))
        val raw = ScoreCalculator.RawFactors(
            income = 108000.0,
            expense = SpendingHistory.monthlyOutflow(expenses, items),
            totalEmiAmount = 0.0, monthlyTotals = emptyList(), recurringReliability = null
        )
        assertEquals(100966.0, raw.expense, 0.001)
        assertTrue(ScoreCalculator.calculate(raw).score < 100)
    }
}
