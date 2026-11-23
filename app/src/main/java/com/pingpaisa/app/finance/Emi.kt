package com.pingpaisa.app.finance

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.util.UUID

/**
 * EMI terms and the arithmetic on them. No Android, no UI: everything here is unit-tested.
 *
 * Money is BigDecimal (rounded to paise only when shown or compared). The only floating point is
 * the monthly discount rate and the effective-rate solver, which are estimates by nature.
 */
enum class EmiSource { MANUAL, SCREENSHOT }

data class EmiOption(
    val id: String = UUID.randomUUID().toString(),
    val lender: String = "",
    val monthlyEmi: BigDecimal? = null,
    val tenureMonths: Int? = null,
    val annualRatePct: BigDecimal? = null,
    val downPayment: BigDecimal = BigDecimal.ZERO,
    val processingFee: BigDecimal = BigDecimal.ZERO,
    val otherCharges: BigDecimal = BigDecimal.ZERO,
    /** What the lender/merchant quotes as total payable. Reconciled against the parts, never added to them. */
    val totalPayable: BigDecimal? = null,
    /** Cashback / instant discount / interest subsidy that only applies when choosing this EMI. Subtracted once. */
    val cashback: BigDecimal = BigDecimal.ZERO,
    /** Cash discount you would give up by choosing EMI. Enter it only if it is NOT already inside the EMI amounts. */
    val cashDiscountLost: BigDecimal = BigDecimal.ZERO,
    val noCostAdvertised: Boolean = false,
    val source: EmiSource = EmiSource.MANUAL,
    /** Options read from a screenshot are candidates until the user confirms them. Only confirmed options are used. */
    val confirmed: Boolean = true,
    /** The cash price this quote was read/entered for; a later price change is flagged. */
    val quotedForPrice: BigDecimal? = null,
    /** True when a screenshot said "from ₹X/m" (the lowest instalment across tenures, so tenure is unknown). */
    val fromLowest: Boolean = false,
    /** Text the options were read from (screenshot OCR), shown so the user can check it. */
    val evidence: String = ""
) {
    val fees: BigDecimal get() = processingFee + otherCharges
}

enum class IssueLevel { MISSING, ERROR, WARNING, INFO }
data class EmiIssue(val level: IssueLevel, val text: String)

enum class ReturnBasis { NOMINAL, PRESENT_VALUE }

/** Net (after-tax) yearly return the user expects on money they keep. Never invented: null means "not provided". */
data class ReturnAssumption(val annualNetPct: Double) {
    /** Effective monthly rate: (1+a)^(1/12)-1. */
    fun monthlyRate(): BigDecimal =
        if (annualNetPct == 0.0) BigDecimal.ZERO
        else BigDecimal(Math.pow(1.0 + annualNetPct / 100.0, 1.0 / 12.0) - 1.0, MC)
}

private val MC = MathContext(30, RoundingMode.HALF_EVEN)
private val HUNDRED = BigDecimal(100)
internal fun BigDecimal.paise(): BigDecimal = setScale(2, RoundingMode.HALF_UP)
internal fun Double.toMoneyDecimal(): BigDecimal = BigDecimal.valueOf(this).paise()

data class EmiCost(
    val emiTotal: BigDecimal,          // n * M
    val upfrontCash: BigDecimal,       // A + F   (cash needed on day one)
    val totalCost: BigDecimal,         // C_EMI = A + n*M + F + lost discount - cashback
    val extraVsCash: BigDecimal,       // delta C = C_EMI - P
    val effectiveAnnualRatePct: Double?,   // what the financing really costs per year; null when it cannot be solved
    val basis: ReturnBasis,
    val pvCost: BigDecimal,            // PV_EMI (equals totalCost when no return assumption)
    val netAdvantage: BigDecimal,      // P - PV_EMI ; > 0 favours EMI
    val retainedGrowth: BigDecimal?    // (P-A) * ((1+r)^n - 1), only with a return assumption
)

object EmiCalculator {
    const val MAX_TENURE = 120

    fun isUsable(o: EmiOption, price: BigDecimal?): Boolean =
        validate(o, price).none { it.level == IssueLevel.MISSING || it.level == IssueLevel.ERROR }

    /**
     * If the monthly EMI is missing but the quoted total and tenure are known, derive it - and say so.
     * Total is read as "everything you pay" (down payment + EMIs + fees).
     */
    fun resolve(o: EmiOption): EmiOption {
        val n = o.tenureMonths
        val total = o.totalPayable
        if (o.monthlyEmi == null && n != null && n > 0 && total != null) {
            val m = (total - o.downPayment - o.fees).divide(BigDecimal(n), 2, RoundingMode.HALF_UP)
            if (m.signum() > 0) return o.copy(monthlyEmi = m)
        }
        return o
    }

    fun validate(raw: EmiOption, price: BigDecimal?): List<EmiIssue> {
        val out = ArrayList<EmiIssue>()
        val o = resolve(raw)
        val derived = raw.monthlyEmi == null && o.monthlyEmi != null
        val m = o.monthlyEmi
        val n = o.tenureMonths

        if (m == null) out += EmiIssue(IssueLevel.MISSING, "Monthly EMI is needed.")
        else if (m.signum() <= 0) out += EmiIssue(IssueLevel.ERROR, "Monthly EMI must be more than zero.")
        if (n == null) out += EmiIssue(IssueLevel.MISSING, if (raw.fromLowest) "Tenure is needed - the screenshot only showed the lowest EMI." else "Tenure (months) is needed.")
        else if (n !in 1..MAX_TENURE) out += EmiIssue(IssueLevel.ERROR, "Tenure must be between 1 and $MAX_TENURE months.")
        val rate = o.annualRatePct
        if (rate != null && (rate.signum() < 0 || rate > HUNDRED)) out += EmiIssue(IssueLevel.ERROR, "Interest rate must be between 0% and 100%.")
        listOf("Down payment" to o.downPayment, "Processing fee" to o.processingFee, "Other charges" to o.otherCharges,
            "Cashback / discount" to o.cashback, "Lost cash discount" to o.cashDiscountLost).forEach { (label, v) ->
            if (v.signum() < 0) out += EmiIssue(IssueLevel.ERROR, "$label can't be negative.")
        }
        if (o.totalPayable != null && o.totalPayable.signum() <= 0) out += EmiIssue(IssueLevel.ERROR, "Total payable must be more than zero.")
        if (price != null && price.signum() > 0 && o.downPayment >= price) out += EmiIssue(IssueLevel.ERROR, "The down payment is the whole price, so there is nothing left to pay in EMIs.")
        if (derived) out += EmiIssue(IssueLevel.INFO, "Monthly EMI was worked out from the total payable and tenure - please confirm it.")
        if (out.any { it.level == IssueLevel.MISSING || it.level == IssueLevel.ERROR }) return out

        m!!; n!!
        val emiTotal = m.multiply(BigDecimal(n))
        val parts = o.downPayment + emiTotal + o.fees
        // Reconcile the quoted total instead of adding it to anything.
        if (o.totalPayable != null && !derived) {
            val tol = tolerance(n, o.totalPayable)
            val matches = listOf(emiTotal, emiTotal + o.fees, parts).any { (it - o.totalPayable).abs() <= tol }
            if (!matches) out += EmiIssue(
                IssueLevel.WARNING,
                "Quoted total ${fmt(o.totalPayable)} doesn't match the parts: EMIs ${fmt(emiTotal)}, plus fees ${fmt(o.fees)}, plus down payment ${fmt(o.downPayment)} = ${fmt(parts)}. " +
                    "The parts are used - please check the numbers."
            )
        }
        if (price != null && price.signum() > 0) {
            val cost = totalCost(o, m, n)
            val extra = cost - price
            if (o.quotedForPrice != null && (o.quotedForPrice - price).abs() > BigDecimal.ONE) {
                out += EmiIssue(IssueLevel.WARNING, "This EMI was quoted for ${fmt(o.quotedForPrice)} but the price is now ${fmt(price)}. Check it still applies.")
            }
            if (o.noCostAdvertised && extra > BigDecimal(maxOf(2, n))) {
                out += EmiIssue(IssueLevel.WARNING, "Advertised as No Cost EMI, but you would pay ${fmt(extra)} more than the cash price (fees, a lost discount or charges on interest).")
            }
            if (cost < price.multiply(BigDecimal("0.90"))) {
                out += EmiIssue(IssueLevel.WARNING, "The total through EMI (${fmt(cost)}) is far below the price (${fmt(price)}). Check the tenure, down payment or cashback.")
            }
            if (rate != null && !o.noCostAdvertised && rate.signum() > 0) {
                val financed = (price - o.downPayment).toDouble()
                val expected = amortised(financed, rate.toDouble(), n)
                if (expected > 0 && Math.abs(m.toDouble() - expected) / expected > 0.05) {
                    out += EmiIssue(IssueLevel.INFO, "At ${rate.stripTrailingZeros().toPlainString()}% a year, ${fmt(BigDecimal(financed))} over $n months is about ${fmt(BigDecimal(expected))}/month; you entered ${fmt(m)}. Fees or a different rate basis can explain a gap.")
                }
            }
        }
        return out
    }

    /** C_EMI = A + n*M + F + (cash discount lost) - cashback. Each component appears once. */
    fun totalCost(o: EmiOption, m: BigDecimal, n: Int): BigDecimal =
        (o.downPayment + m.multiply(BigDecimal(n)) + o.fees + o.cashDiscountLost - o.cashback).paise()

    /** Null when the option isn't usable yet. [price] is the current cash price P. */
    fun analyze(raw: EmiOption, price: BigDecimal, ret: ReturnAssumption?): EmiCost? {
        val o = resolve(raw)
        val m = o.monthlyEmi ?: return null
        val n = o.tenureMonths ?: return null
        if (!isUsable(raw, price)) return null

        val emiTotal = m.multiply(BigDecimal(n)).paise()
        val upfront = (o.downPayment + o.fees).paise()
        val total = totalCost(o, m, n)
        val extra = (total - price).paise()

        // Day-one cash difference: what the EMI buyer does not have to pay now compared with the cash buyer.
        val financed = price - (o.downPayment + o.fees + o.cashDiscountLost - o.cashback)
        val rate = effectiveAnnualRate(financed.toDouble(), m.toDouble(), n, emiTotal.toDouble())

        val r = ret?.monthlyRate() ?: BigDecimal.ZERO
        val basis = if (ret != null) ReturnBasis.PRESENT_VALUE else ReturnBasis.NOMINAL
        val pvInstalments = presentValueOfInstalments(m, n, r)
        val pv = (o.downPayment + o.fees + o.cashDiscountLost - o.cashback + pvInstalments).paise()
        val growth = ret?.let {
            val retained = (price - o.downPayment).max(BigDecimal.ZERO)
            (retained.multiply(BigDecimal.ONE.add(r).pow(n, MC) - BigDecimal.ONE)).paise()
        }
        return EmiCost(emiTotal, upfront, total, extra, rate, basis, pv, (price - pv).paise(), growth)
    }

    /** sum_{t=1..n} M / (1+r)^t */
    fun presentValueOfInstalments(m: BigDecimal, n: Int, r: BigDecimal): BigDecimal {
        if (r.signum() == 0) return m.multiply(BigDecimal(n))
        val v = BigDecimal.ONE.divide(BigDecimal.ONE.add(r), MC)
        val vn = v.pow(n, MC)
        return m.multiply(BigDecimal.ONE - vn).divide(r, MC)
    }

    /**
     * The yearly rate that makes the instalments worth exactly the cash you did not have to pay on day one.
     * 0.0 when the instalments add up to no more than that (no financing cost); null when it cannot be solved.
     */
    fun effectiveAnnualRate(financed: Double, m: Double, n: Int, emiTotal: Double): Double? {
        if (financed <= 0.0) return null
        if (emiTotal <= financed + 0.005) return 0.0
        fun f(r: Double) = if (r == 0.0) m * n - financed else m * (1 - Math.pow(1 + r, -n.toDouble())) / r - financed
        var lo = 0.0; var hi = 1.0
        if (f(hi) > 0) return null
        repeat(80) { val mid = (lo + hi) / 2; if (f(mid) > 0) lo = mid else hi = mid }
        val monthly = (lo + hi) / 2
        return (Math.pow(1 + monthly, 12.0) - 1) * 100
    }

    /** Standard amortised instalment, used only to sanity-check a stated rate. */
    fun amortised(principal: Double, annualPct: Double, n: Int): Double {
        val r = annualPct / 1200.0
        return if (r == 0.0) principal / n else principal * r / (1 - Math.pow(1 + r, -n.toDouble()))
    }

    private fun tolerance(n: Int, total: BigDecimal): BigDecimal =
        BigDecimal(maxOf(2, n)).max(total.multiply(BigDecimal("0.005")))

    private fun fmt(v: BigDecimal) = com.pingpaisa.app.util.Money.format(v)
}
