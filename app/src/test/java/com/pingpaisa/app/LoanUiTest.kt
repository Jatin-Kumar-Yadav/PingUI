package com.pingpaisa.app

import com.pingpaisa.app.healthscore.LoanMath
import com.pingpaisa.app.healthscore.LoanQuote
import com.pingpaisa.app.healthscore.ScoreCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LoanUiTest {

    private fun base(income: Double = 100000.0, expense: Double = 40000.0, emi: Double = 0.0) =
        ScoreCalculator.RawFactors(income, expense, emi, emptyList(), null)

    @Test fun emiUsesTheReducingBalanceFormula() {
        // ₹3,00,000 at 11% for 3 years is about ₹9,822 a month.
        assertEquals(9822.0, LoanMath.emi(300000.0, 36, 11.0), 1.0)
        val u = LoanUi.from(base(), 300000f, 36)
        assertEquals("₹9,822", u.emiText)
        assertEquals(300000f, u.amount)
        assertEquals("11%", u.rateText)
        assertTrue(u.rateAssumed)
    }

    @Test fun limitComesFromIncomeSpendingAndEmis() {
        // Room for a new EMI: min(50% of income − EMIs, income − spending) = min(50,000, 60,000) = 50,000.
        val u = LoanUi.from(base(), 300000f, 36)
        val expected = (LoanMath.principalFromEmi(50000.0, 36, 11.0) / 10000).toInt() * 10000f
        assertEquals(expected, u.maxAmount)
        assertTrue(u.canBorrow)
        assertTrue(u.limitNote.startsWith("Up to "))
        assertTrue(u.limitNote.contains("an assumed 11%"))
    }

    @Test fun chosenAmountIsKeptInsideTheLimitAndOnTheStep() {
        val tight = LoanUi.from(base(expense = 95000.0), 900000f, 36)   // only ₹5,000 a month to spare
        assertEquals(tight.maxAmount, tight.amount)
        assertEquals(10000f, LoanUi.from(base(), 3000f, 36).amount)     // never below ₹10K
        assertEquals(250000f, LoanUi.from(base(), 254999f, 36).amount)  // rounded to ₹10K steps
    }

    @Test fun noRoomMeansNoLoan() {
        val u = LoanUi.from(base(expense = 100000.0), 300000f, 36)
        assertFalse(u.canBorrow)
        assertEquals(0f, u.amount)
        assertFalse(LoanUi.from(base(income = 0.0, expense = 0.0), 300000f, 36).hasIncome)
    }

    @Test fun healthImpactComparesBeforeAndAfter() {
        val u = LoanUi.from(base(), 1500000f, 36)
        assertTrue(u.scoreAfter <= u.scoreBefore)
        assertEquals(100, u.scoreBefore)
        assertTrue(u.riskAfter.endsWith("risk"))
    }

    @Test fun incomeShareMentionsExistingEmis() {
        assertTrue(LoanUi.from(base(), 300000f, 36).incomeShare.endsWith("of your monthly income."))
        assertTrue(LoanUi.from(base(emi = 10000.0, expense = 50000.0), 300000f, 36).incomeShare.contains("with your other EMIs"))
    }

    @Test fun planShowsPaymentsTotalAndInterest() {
        val u = LoanUi.from(base(), 300000f, 36)
        assertTrue(u.planText.startsWith("36 monthly payments of ₹9,822"))
        assertTrue(u.planText.contains("is interest"))
    }

    private val quotes = listOf(LoanQuote("HDFC", 10.5), LoanQuote("SBI", 9.0), LoanQuote("Fintech X", 24.0))

    @Test fun withoutQuotesTheRateIsAssumed() {
        val u = LoanUi.from(base(), 300000f, 36)
        assertTrue(u.rateAssumed)
        assertTrue(u.quotes.isEmpty())
        assertEquals(null, u.selectedBank)
        assertTrue(u.verdict in setOf("SAFE", "TIGHT", "RISKY"))
        assertTrue(u.verdictReasons.isNotEmpty())
    }

    @Test fun limitUsesTheHighestQuotedRate() {
        val withQuotes = LoanUi.from(base(), 300000f, 36, quotes)
        val expected = (LoanMath.principalFromEmi(50000.0, 36, 24.0) / 10000).toInt() * 10000f
        assertEquals(expected, withQuotes.maxAmount)
        assertTrue(withQuotes.limitNote.contains("your highest quote, 24%"))
    }

    @Test fun comparisonIsSafestThenCheapestAndPicksTheBestByDefault() {
        val u = LoanUi.from(base(), 300000f, 36, quotes)
        assertEquals(listOf("SBI", "HDFC", "Fintech X"), u.quotes.map { it.bank })   // all safe here: by interest
        assertEquals("SBI", u.selectedBank)
        assertTrue(u.quotes[0].selected)
        assertFalse(u.rateAssumed)
        assertEquals("9%", u.rateText)
        assertEquals("Looks affordable with SBI", u.verdictTitle)
    }

    @Test fun aTappedQuoteDrivesTheEmiAndVerdict() {
        val u = LoanUi.from(base(), 300000f, 36, quotes, selectedBank = "Fintech X")
        assertEquals("Fintech X", u.selectedBank)
        assertEquals("24%", u.rateText)
        assertEquals(u.quotes.first { it.bank == "Fintech X" }.emiText, u.emiText)
    }

    @Test fun riskyQuotesSortAfterSafeOnes() {
        // Little room to spare: an expensive loan becomes risky and drops to the end.
        val tight = base(expense = 85000.0)
        val u = LoanUi.from(tight, 400000f, 36, listOf(LoanQuote("Cheap", 8.0), LoanQuote("Costly", 36.0)))
        assertEquals("Cheap", u.quotes.first().bank)
        assertTrue(u.quotes.map { it.verdict }.zipWithNext().all { (a, b) -> listOf("SAFE", "TIGHT", "RISKY").let { it.indexOf(a) <= it.indexOf(b) } })
    }

    @Test fun quoteFormRules() {
        assertEquals(LoanQuote("HDFC Bank", 10.5), LoanUi.quoteFrom("  HDFC Bank ", "10.5"))
        assertEquals(LoanQuote("A B", 9.0), LoanUi.quoteFrom("A|B", "9"))        // "|" is the saved separator
        assertNull(LoanUi.quoteFrom("", "10"))
        assertNull(LoanUi.quoteFrom("HDFC", "0"))
        assertNull(LoanUi.quoteFrom("HDFC", "61"))
        assertNull(LoanUi.quoteFrom("HDFC", "abc"))
        assertNull(LoanUi.quoteFrom("x".repeat(25), "10"))
        val (nameError, rateError) = LoanUi.quoteErrors("", "70")
        assertTrue(nameError != null && rateError != null)
    }

    @Test fun addingTheSameBankAgainReplacesIt() {
        val updated = LoanUi.withQuote(quotes, LoanQuote("hdfc", 9.9))
        assertEquals(3, updated.size)
        assertEquals(9.9, updated.first { it.bankName.equals("hdfc", true) }.annualRate, 0.0)
    }

    @Test fun customYearsAreOneToThirty() {
        assertEquals(7, LoanUi.customYears("7"))
        assertNull(LoanUi.customYears("0"))
        assertNull(LoanUi.customYears("31"))
        assertNull(LoanUi.customYears(""))
    }
}
