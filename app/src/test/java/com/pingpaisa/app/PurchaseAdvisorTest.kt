package com.pingpaisa.app

import com.pingpaisa.app.finance.Advice
import com.pingpaisa.app.finance.Decision
import com.pingpaisa.app.finance.EmiOption
import com.pingpaisa.app.finance.EmiSource
import com.pingpaisa.app.finance.FinancialProfile
import com.pingpaisa.app.finance.IssueLevel
import com.pingpaisa.app.finance.Priority
import com.pingpaisa.app.finance.PurchaseAdvisor
import com.pingpaisa.app.finance.Recommendation
import com.pingpaisa.app.finance.Suitability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class PurchaseAdvisorTest {
    private fun d(s: String) = BigDecimal(s)

    /** Comfortable: D = 100k - 40k - 10k = 50k, allowance 25k, savings 500k over a 100k reserve. */
    private fun rich(ret: Double? = null) = FinancialProfile(
        netIncome = 100_000.0, fixedExpenses = 30_000.0, variableExpenses = 10_000.0, monthlySavingsTarget = 10_000.0,
        savings = 500_000.0, emergencyReserve = 100_000.0, expectedAnnualReturnPct = ret
    )

    /** Tight: D = 60k - 30k - 5k = 25k, allowance 12.5k, savings 15k over a 10k reserve (5k spendable). */
    private fun tight(maxWait: Int = 12) = FinancialProfile(
        netIncome = 60_000.0, fixedExpenses = 20_000.0, variableExpenses = 10_000.0, monthlySavingsTarget = 5_000.0,
        savings = 15_000.0, emergencyReserve = 10_000.0, maxWaitMonths = maxWait
    )

    private fun emi(m: String?, n: Int?, lender: String = "Bank", a: String = "0", f: String = "0", noCost: Boolean = false,
                    confirmed: Boolean = true, cash: String = "0") = EmiOption(
        lender = lender, monthlyEmi = m?.let { d(it) }, tenureMonths = n, downPayment = d(a), processingFee = d(f),
        noCostAdvertised = noCost, confirmed = confirmed, cashback = d(cash)
    )

    private fun advise(p: FinancialProfile, price: String, vararg o: EmiOption): Advice =
        PurchaseAdvisor.advise(p, "x", d(price), Priority.NORMAL, o.toList())

    // ---- cash plan ----
    @Test fun cashPlanReportsSavingsAndMonthlyImpact() {
        val a = advise(rich(), "18999")
        assertEquals(Decision.BUY_NOW, a.cash.decision)
        assertEquals(d("0.00"), a.cash.fromSavings)
        assertEquals(d("500000.00"), a.cash.savingsAfter)
        assertEquals(d("31001.00"), a.cash.leftThisMonthAfter)    // 50,000 - 18,999
        assertEquals(Recommendation.PAY_IN_FULL, a.recommendation)
        assertNull(a.chosenEmiId)
    }

    @Test fun cashPlanThatNeedsSavingsAboveReserveIsACaution() {
        val a = advise(rich(), "60000")
        assertEquals(Decision.BUY_WITH_CAUTION, a.cash.decision)
        assertEquals(d("35000.00"), a.cash.fromSavings)           // 60,000 - 25,000 allowance
        assertEquals(d("465000.00"), a.cash.savingsAfter)
        assertTrue(!a.cash.reserveBreached)
    }

    @Test fun cashBeyondTheSafeBudgetWouldBreachTheReserveAndSaysSo() {
        val a = advise(tight(), "40000")
        assertTrue(a.cash.reserveBreached)                        // 15,000 - (40,000 - 12,500) < 10,000
        assertTrue(!a.cash.fits)
    }

    // ---- the core comparison ----
    @Test fun noCostEmiWithoutAReturnAssumptionDoesNotBeatCash() {
        val a = advise(rich(), "18999", emi("3166.50", 6, noCost = true))
        assertEquals(Recommendation.PAY_IN_FULL, a.recommendation)
        assertTrue(a.assumptions.any { it.contains("No expected return") })
    }

    @Test fun withAReturnAssumptionAFreeEmiCanWin() {
        val a = advise(rich(ret = 12.0), "18999", emi("3166.50", 6, noCost = true))
        assertEquals(Recommendation.CHOOSE_EMI, a.recommendation)
        assertNotNull(a.chosen)
        assertTrue(a.chosen!!.cost!!.netAdvantage.signum() > 0)
        assertTrue(a.reasons.any { it.contains("today's money") })
    }

    @Test fun emiThatIsAffordableButMoreExpensiveLosesToAffordableCash() {
        val a = advise(rich(ret = 6.0), "18999", emi("3400", 6, f = "199"))
        assertEquals(Recommendation.PAY_IN_FULL, a.recommendation)
    }

    @Test fun processingFeeOnANoCostOfferIsFlaggedAndCostsMoney() {
        val a = advise(rich(), "18999", emi("3166.50", 6, f = "499", noCost = true))
        val plan = a.emis.single()
        assertTrue(plan.issues.any { it.level == IssueLevel.WARNING && it.text.contains("No Cost EMI") })
        assertEquals(d("499.00"), plan.cost!!.extraVsCash)
        assertEquals(Recommendation.PAY_IN_FULL, a.recommendation)
    }

    @Test fun cheapestMonthlyIsNotAutomaticallyRecommended() {
        // cash does not fit; D = 25k, allowance 12.5k
        val a = advise(tight(maxWait = 1), "40000",
            emi("14000", 3, "Short"),          // above the 12.5k allowance -> caution
            emi("7000", 6, "Mid"),             // suitable, total 42,000
            emi("3900", 12, "Long"))           // suitable, lowest EMI, total 46,800
        assertEquals(Suitability.CAUTION, a.emis[0].suitability)
        assertEquals(Suitability.SUITABLE, a.emis[1].suitability)
        assertEquals(Recommendation.CONSIDER_OTHER_EMI, a.recommendation)
        assertEquals("Mid", a.chosen!!.option.lender)
        assertTrue(a.reasons.any { it.contains("not the lowest monthly EMI") })
    }

    @Test fun whenCashIsOutOfReachAnAffordableEmiIsRecommended() {
        val a = advise(tight(maxWait = 1), "40000", emi("7000", 6))
        assertEquals(Decision.SKIP, a.cash.decision)
        assertEquals(Recommendation.CHOOSE_EMI, a.recommendation)
        assertTrue(a.reasons.any { it.contains("would not fit") })
    }

    @Test fun lowSavingsButWaitingIsCheaperThanAMoreExpensiveEmi() {
        val a = advise(tight(), "40000", emi("7000", 6))          // EMI total 42,000 > 40,000; cash reachable in ~2 months
        assertEquals(Decision.WAIT, a.cash.decision)
        assertEquals(Recommendation.POSTPONE, a.recommendation)
        assertTrue(a.reasons.any { it.contains("extra EMI cost") })
    }

    // ---- affordability gates ----
    @Test fun insufficientMonthlySurplusRulesOutEveryEmi() {
        val broke = FinancialProfile(netIncome = 40_000.0, fixedExpenses = 30_000.0, variableExpenses = 12_000.0, monthlySavingsTarget = 3_000.0,
            savings = 50_000.0, emergencyReserve = 40_000.0)       // D = -5,000
        val a = advise(broke, "20000", emi("3000", 8))
        assertEquals(Suitability.NOT_RECOMMENDED, a.emis.single().suitability)
        assertEquals(Recommendation.SKIP, a.recommendation)
        assertTrue(a.emis.single().reasons.first().contains("shortfall"))
    }

    @Test fun emiLargerThanWhatIsLeftEachMonthIsRejected() {
        val a = advise(tight(), "40000", emi("30000", 2))
        assertEquals(Suitability.NOT_RECOMMENDED, a.emis.single().suitability)
        assertTrue(a.emis.single().monthlyLeftAfter!!.signum() < 0)
    }

    @Test fun downPaymentThatBreachesTheEmergencyReserveIsRejected() {
        val low = FinancialProfile(netIncome = 80_000.0, fixedExpenses = 20_000.0, variableExpenses = 10_000.0, monthlySavingsTarget = 5_000.0,
            savings = 25_000.0, emergencyReserve = 20_000.0)       // only 5k above the reserve
        val a = advise(low, "60000", emi("5000", 10, a = "10000"))
        val p = a.emis.single()
        assertEquals(Suitability.NOT_RECOMMENDED, p.suitability)
        assertTrue(p.reasons.any { it.contains("emergency reserve") })
        assertEquals(d("15000.00"), p.savingsAfterUpfront)
    }

    @Test fun savingsAlreadyBelowReserveBlocksCashAndEmiWithUpfront() {
        val under = FinancialProfile(netIncome = 80_000.0, fixedExpenses = 20_000.0, variableExpenses = 10_000.0, monthlySavingsTarget = 5_000.0,
            savings = 5_000.0, emergencyReserve = 20_000.0)
        val a = advise(under, "30000", emi("5000", 6, f = "300"))
        assertEquals(Suitability.NOT_RECOMMENDED, a.emis.single().suitability)
        assertTrue(a.cash.reserveBreached)
    }

    @Test fun feesAndDownPaymentAreCountedInUpfrontCash() {
        val a = advise(rich(), "50000", emi("6000", 8, a = "5000", f = "500"))
        assertEquals(d("5500.00"), a.emis.single().cost!!.upfrontCash)
        assertEquals(d("494500.00"), a.emis.single().savingsAfterUpfront)
    }

    // ---- missing or unconfirmed data ----
    @Test fun screenshotOptionsAreIgnoredUntilConfirmed() {
        val a = advise(rich(ret = 12.0), "18999", emi("3166.50", 6, confirmed = false).copy(source = EmiSource.SCREENSHOT))
        assertEquals(Suitability.INCOMPLETE, a.emis.single().suitability)
        assertEquals(Recommendation.PAY_IN_FULL, a.recommendation)       // as if there were no EMI
        assertTrue(a.missing.single().contains("confirm"))
    }

    @Test fun incompleteOfferListsWhatIsMissing() {
        val a = advise(rich(), "18999", emi("2776", null, lender = "Axis"))
        assertEquals(Suitability.INCOMPLETE, a.emis.single().suitability)
        assertTrue(a.missing.single().contains("Tenure"))
    }

    @Test fun noEmiOptionsFallsBackToTheCashDecision() {
        val a = advise(rich(), "18999")
        assertTrue(a.emis.isEmpty())
        assertEquals(Recommendation.PAY_IN_FULL, a.recommendation)
    }

    @Test fun removingAnOfferChangesTheAnswer() {
        val offers = listOf(emi("3166.50", 6, "A", noCost = true), emi("1700", 12, "B", f = "400"))
        val both = PurchaseAdvisor.advise(rich(12.0), "x", d("18999"), Priority.NORMAL, offers)
        val onlyB = PurchaseAdvisor.advise(rich(12.0), "x", d("18999"), Priority.NORMAL, offers.drop(1))
        assertEquals("A", both.chosen!!.option.lender)
        assertEquals(1, onlyB.emis.size)
    }

    @Test fun changingThePriceRecalculatesAndWarnsAboutStaleQuotes() {
        val o = emi("3166.50", 6).copy(quotedForPrice = d("18999"))
        val a = PurchaseAdvisor.advise(rich(), "x", d("24999"), Priority.NORMAL, listOf(o))
        assertTrue(a.emis.single().issues.any { it.text.contains("quoted for") })
        assertEquals(d("-6000.00"), a.emis.single().cost!!.extraVsCash)  // follows the new price
        assertTrue(a.emis.single().issues.any { it.text.contains("far below") })
    }

    // ---- projection and combined load ----
    @Test fun projectionUsesTheSameHorizonAndTheSameBaselineForBoth() {
        val p = FinancialProfile(netIncome = 100_000.0, fixedExpenses = 40_000.0, variableExpenses = 10_000.0, monthlySavingsTarget = 10_000.0,
            savings = 200_000.0, emergencyReserve = 50_000.0)       // D = 40k, no return
        val a = advise(p, "30000", emi("5000", 6, a = "0"))
        val proj = a.emis.single().projection!!
        assertEquals(6, proj.months)
        assertEquals(d("230000.00"), proj.endCash)    // 200,000 - 30,000 + 6 x 10,000
        assertEquals(d("260000.00"), proj.endEmi)     // 200,000 + 6 x 10,000 (EMIs paid from the 40k surplus)
        assertEquals(0, proj.monthsBelowReserveEmi)
    }

    @Test fun projectionTakesEmiOverflowFromSavings() {
        val p = FinancialProfile(netIncome = 50_000.0, fixedExpenses = 30_000.0, variableExpenses = 10_000.0, monthlySavingsTarget = 5_000.0,
            savings = 100_000.0, emergencyReserve = 20_000.0)       // D = 5,000
        val a = advise(p, "30000", emi("8000", 4))
        val plan = a.emis.single()
        assertEquals(Suitability.NOT_RECOMMENDED, plan.suitability)    // D - M < 0
        assertEquals(d("108000.00"), plan.projection!!.endEmi)   // 100,000 + 4 x (5,000 planned - 3,000 EMI overflow)
    }

    @Test fun combinedEmiWarningWhenSeveralEmisExceedWhatIsLeft() {
        val p = tight(maxWait = 1)   // D = 25,000, cash out of reach so an EMI is the way in
        val a1 = advise(p, "40000", emi("14000", 3))
        val a2 = advise(p, "40000", emi("14000", 3))
        // each is above the 12.5k allowance (caution) and within D = 25k alone; together 28k exceeds D
        assertNotNull(a1.chosen); assertNotNull(a2.chosen)
        assertTrue(PurchaseAdvisor.combinedEmiWarning(p, listOf(a1, a2))!!.contains("add up to"))
        assertNull(PurchaseAdvisor.combinedEmiWarning(p, listOf(a1)))
    }
}
