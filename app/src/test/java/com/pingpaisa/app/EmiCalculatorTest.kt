package com.pingpaisa.app

import com.pingpaisa.app.finance.EmiCalculator
import com.pingpaisa.app.finance.EmiOption
import com.pingpaisa.app.finance.IssueLevel
import com.pingpaisa.app.finance.ReturnAssumption
import com.pingpaisa.app.finance.ReturnBasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class EmiCalculatorTest {
    private fun d(s: String) = BigDecimal(s)
    private val price = d("18999")
    private fun opt(m: String? = "3167", n: Int? = 6, a: String = "0", f: String = "0", cash: String = "0", lost: String = "0",
                    total: String? = null, noCost: Boolean = false, rate: String? = null, quoted: String? = null) =
        EmiOption(
            monthlyEmi = m?.let { d(it) }, tenureMonths = n, downPayment = d(a), processingFee = d(f), cashback = d(cash),
            cashDiscountLost = d(lost), totalPayable = total?.let { d(it) }, noCostAdvertised = noCost, annualRatePct = rate?.let { d(it) },
            quotedForPrice = quoted?.let { d(it) }
        )
    private fun issues(o: EmiOption, p: BigDecimal? = price) = EmiCalculator.validate(o, p)

    // ---- C_EMI = A + nM + F (+ lost discount - cashback), counted once ----
    @Test fun totalCostAddsEachComponentOnce() {
        val c = EmiCalculator.analyze(opt(a = "1000", f = "199", m = "3000", n = 6), price, null)!!
        assertEquals(d("19199.00"), c.totalCost)          // 1000 + 6*3000 + 199
        assertEquals(d("200.00"), c.extraVsCash)
        assertEquals(d("1199.00"), c.upfrontCash)
        assertEquals(d("18000.00"), c.emiTotal)
    }

    @Test fun cashbackAndLostDiscountAreAppliedOnce() {
        val c = EmiCalculator.analyze(opt(m = "3000", n = 6, cash = "500", lost = "700"), price, null)!!
        assertEquals(d("18200.00"), c.totalCost)           // 18000 - 500 + 700
        assertEquals(d("-799.00"), c.extraVsCash)
    }

    @Test fun truelyNoCostEmiHasZeroExtraAndZeroRate() {
        val o = opt(m = "3166.50", n = 6, noCost = true)
        val c = EmiCalculator.analyze(o, price, null)!!
        assertEquals(d("0.00"), c.extraVsCash)
        assertEquals(0.0, c.effectiveAnnualRatePct!!, 0.0)
        assertTrue(issues(o).none { it.level == IssueLevel.WARNING })
    }

    @Test fun noCostEmiWithFeesIsFlagged() {
        val o = opt(m = "3166.50", n = 6, f = "499", noCost = true)
        val w = issues(o).filter { it.level == IssueLevel.WARNING }
        assertTrue(w.any { it.text.contains("No Cost EMI") && it.text.contains("499") })
        val c = EmiCalculator.analyze(o, price, null)!!
        assertEquals(d("499.00"), c.extraVsCash)
        assertTrue(c.effectiveAnnualRatePct!! > 0.0)
    }

    @Test fun noCostEmiWithLostCashDiscountIsFlagged() {
        val o = opt(m = "3166.50", n = 6, lost = "1000", noCost = true)
        assertTrue(issues(o).any { it.level == IssueLevel.WARNING && it.text.contains("No Cost EMI") })
    }

    // ---- effective rate ----
    @Test fun effectiveRateOfAFeeLoadedOffer() {
        // pay 2000 x 6 = 12000 for a 12000 product plus a 300 fee: you effectively borrow 11700
        val c = EmiCalculator.analyze(opt(m = "2000", n = 6, f = "300"), d("12000"), null)!!
        assertEquals(d("300.00"), c.extraVsCash)
        assertTrue(c.effectiveAnnualRatePct!! in 5.0..15.0)
    }

    @Test fun effectiveRateMatchesTheStatedRateForAPlainLoan() {
        val m = EmiCalculator.amortised(100000.0, 12.0, 12)       // standard formula
        val c = EmiCalculator.analyze(opt(m = "%.2f".format(m), n = 12), d("100000"), null)!!
        // 12% a year nominal = 1% a month = 12.68% effective; the solver reports the effective yearly rate
        assertEquals((Math.pow(1.01, 12.0) - 1) * 100, c.effectiveAnnualRatePct!!, 0.05)
    }

    // ---- present value and return assumption ----
    @Test fun presentValueMatchesTheClosedForm() {
        val ret = ReturnAssumption(12.0)
        val c = EmiCalculator.analyze(opt(m = "9000", n = 12), d("100000"), ret)!!
        val r = Math.pow(1.12, 1.0 / 12) - 1
        val expected = 9000 * (1 - Math.pow(1 + r, -12.0)) / r
        assertEquals(expected, c.pvCost.toDouble(), 0.5)
        assertEquals(100000 - expected, c.netAdvantage.toDouble(), 0.5)
        assertEquals(ReturnBasis.PRESENT_VALUE, c.basis)
        assertEquals(d("108000.00"), c.totalCost)             // nominal cost is unchanged
        // paying 108,000 for a 100,000 product costs ~19% a year, which a 12% return does not beat
        assertTrue(c.netAdvantage.signum() < 0)
        val better = EmiCalculator.analyze(opt(m = "9000", n = 12), d("100000"), ReturnAssumption(25.0))!!
        assertTrue(better.netAdvantage.signum() > 0)           // a 25% return would make keeping the cash worthwhile
    }

    @Test fun withoutAReturnAssumptionAdvantageIsNominalAndNothingIsInvented() {
        val c = EmiCalculator.analyze(opt(m = "9000", n = 12), d("100000"), null)!!
        assertEquals(ReturnBasis.NOMINAL, c.basis)
        assertEquals(c.totalCost, c.pvCost)
        assertEquals(d("-8000.00"), c.netAdvantage)
        assertNull(c.retainedGrowth)
    }

    @Test fun highCostEmiLosesEvenWithAReturn() {
        val c = EmiCalculator.analyze(opt(m = "9500", n = 12), d("100000"), ReturnAssumption(6.0))!!
        assertTrue(c.netAdvantage.signum() < 0)
    }

    @Test fun retainedGrowthIsTheFutureValueGain() {
        val c = EmiCalculator.analyze(opt(m = "9000", n = 12, a = "10000"), d("100000"), ReturnAssumption(12.0))!!
        assertEquals(90000 * 0.12, c.retainedGrowth!!.toDouble(), 1.0)  // (P-A)((1+r)^n - 1) with r^12 = 1.12
    }

    // ---- validation ----
    @Test fun missingTenureOrEmiIsReportedNotGuessed() {
        assertTrue(issues(opt(n = null)).any { it.level == IssueLevel.MISSING && it.text.contains("Tenure") })
        assertTrue(issues(opt(m = null)).any { it.level == IssueLevel.MISSING && it.text.contains("Monthly EMI") })
        assertNull(EmiCalculator.analyze(opt(n = null), price, null))
        assertFalse(EmiCalculator.isUsable(opt(m = null), price))
    }

    @Test fun impossibleValuesAreErrors() {
        assertTrue(issues(opt(m = "0")).any { it.level == IssueLevel.ERROR })
        assertTrue(issues(opt(n = 0)).any { it.level == IssueLevel.ERROR })
        assertTrue(issues(opt(n = 500)).any { it.level == IssueLevel.ERROR })
        assertTrue(issues(opt(rate = "150")).any { it.level == IssueLevel.ERROR })
        assertTrue(issues(opt(f = "-5")).any { it.level == IssueLevel.ERROR })
        assertTrue(issues(opt(a = "18999")).any { it.level == IssueLevel.ERROR && it.text.contains("down payment", true) })
    }

    @Test fun quotedTotalIsReconciledNotAdded() {
        // 6 x 3167 = 19002; quoted total 19002 (EMIs only), or 19201 (with a 199 fee) -> both consistent
        assertTrue(issues(opt(total = "19002")).none { it.level == IssueLevel.WARNING })
        assertTrue(issues(opt(f = "199", total = "19201")).none { it.level == IssueLevel.WARNING })
        assertEquals(d("19201.00"), EmiCalculator.analyze(opt(f = "199", total = "19201"), price, null)!!.totalCost)   // still counted once
    }

    @Test fun quotedTotalThatDoesNotMatchIsWarnedAndPartsAreUsed() {
        val o = opt(total = "21000")
        assertTrue(issues(o).any { it.level == IssueLevel.WARNING && it.text.contains("doesn't match") })
        assertEquals(d("19002.00"), EmiCalculator.analyze(o, price, null)!!.totalCost)
    }

    @Test fun monthlyEmiIsDerivedFromTotalAndTenureWhenMissingAndFlagged() {
        val o = opt(m = null, n = 6, total = "19002")
        val r = EmiCalculator.resolve(o)
        assertEquals(d("3167.00"), r.monthlyEmi)
        assertTrue(issues(o).any { it.level == IssueLevel.INFO && it.text.contains("worked out") })
    }

    @Test fun priceChangedAfterQuoteIsFlaggedAndRecalculated() {
        val o = opt(quoted = "18999")
        assertTrue(issues(o, d("20999")).any { it.level == IssueLevel.WARNING && it.text.contains("quoted for") })
        // extra cost follows the new price
        assertEquals(d("-1998.00"), EmiCalculator.analyze(o, d("21000"), null)!!.extraVsCash)
    }

    @Test fun emiFarBelowThePriceIsQuestioned() {
        assertTrue(issues(opt(m = "1000", n = 6)).any { it.level == IssueLevel.WARNING && it.text.contains("far below") })
    }

    @Test fun rateInconsistencyIsOnlyInformational() {
        val i = issues(opt(m = "3167", n = 6, rate = "30"))
        assertTrue(i.none { it.level == IssueLevel.ERROR })
    }

    @Test fun monthlyRateConversionIsEffectiveNotNominal() {
        val r = ReturnAssumption(12.0).monthlyRate().toDouble()
        assertEquals(1.12, Math.pow(1 + r, 12.0), 1e-9)
        assertEquals(BigDecimal.ZERO, ReturnAssumption(0.0).monthlyRate())
        assertNotNull(ReturnAssumption(-5.0).monthlyRate())
    }
}
