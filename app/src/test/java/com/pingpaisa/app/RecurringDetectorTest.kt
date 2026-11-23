package com.pingpaisa.app

import com.pingpaisa.app.data.RecurringDetector
import com.pingpaisa.app.data.Transaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecurringDetectorTest {
    private var n = 0
    private fun tx(recipient: String, amount: Double, date: String) =
        Transaction(amount = amount, recipient = recipient, date = date, refNumber = "r${n++}", rawText = "", category = "Bills")

    @Test fun threeMonthlyPaymentsAreDetected() {
        val found = RecurringDetector.detect(listOf(
            tx("Netflix", 649.0, "05/09/25"), tx("netflix ", 649.0, "05/10/25"), tx("Netflix", 649.0, "04/11/25")))
        assertEquals(1, found.size)
        assertEquals(3, found.single().occurrences)
        assertEquals(649.0, found.single().averageAmount, 0.001)
    }

    @Test fun orderInTheListDoesNotMatter() {
        val found = RecurringDetector.detect(listOf(
            tx("Gym", 1500.0, "04/11/25"), tx("Gym", 1500.0, "05/09/25"), tx("Gym", 1500.0, "05/10/25")))
        assertEquals(1, found.size)
    }

    @Test fun twoPaymentsAreNotEnough() {
        assertTrue(RecurringDetector.detect(listOf(tx("Gym", 1500.0, "05/09/25"), tx("Gym", 1500.0, "05/10/25"))).isEmpty())
    }

    @Test fun differentAmountsAreNotRecurring() {
        assertTrue(RecurringDetector.detect(listOf(
            tx("Shop", 100.0, "05/09/25"), tx("Shop", 900.0, "05/10/25"), tx("Shop", 400.0, "04/11/25"))).isEmpty())
    }

    @Test fun gapsThatAreNotMonthlyAreNotRecurring() {
        assertTrue(RecurringDetector.detect(listOf(
            tx("Cafe", 200.0, "01/11/25"), tx("Cafe", 200.0, "08/11/25"), tx("Cafe", 200.0, "15/11/25"))).isEmpty())
    }

    @Test fun impossibleDatesAreNotReadAsAnotherDay() {
        // 31/02 used to be read as 3 March (a monthly gap from 31 Jan); now it is rejected and counts as no gap.
        assertTrue(RecurringDetector.detect(listOf(
            tx("Rent", 15000.0, "31/01/25"), tx("Rent", 15000.0, "31/02/25"), tx("Rent", 15000.0, "31/03/25"))).isEmpty())
    }
}
