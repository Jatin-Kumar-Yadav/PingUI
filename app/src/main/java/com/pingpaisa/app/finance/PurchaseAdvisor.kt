package com.pingpaisa.app.finance

import com.pingpaisa.app.util.Money
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Compares, for ONE product, paying in full against each confirmed EMI offer, then recommends.
 * Reuses [AffordabilityCalculator] for the cash decision and the monthly allowance, so the cash side
 * is exactly what the rest of the app shows. Pure Kotlin: no Android, fully unit-tested.
 *
 *   D = I - E - T                    monthly money left after expenses and planned savings
 *   S_a = max(0, S - R)              savings above the protected reserve
 *   B_m = alpha * max(0, D)          monthly allowance (the user's policy share)
 *   B_safe = B_m + beta * S_a
 * EMI offer (M per month for n months, down payment A, fees F):
 *   C_EMI = A + nM + F + lost cash discount - cashback ;  dC = C_EMI - P
 *   monthly test:  D - M >= 0 (must), M <= B_m (comfortable)
 *   up-front test: S - (A+F) >= R (must), A+F <= B_safe (comfortable)
 *   PV_EMI = A + F + lost - cashback + sum M/(1+r)^t ;  advantage = P - PV_EMI   (r = 0 without a return assumption)
 */
enum class Suitability { SUITABLE, CAUTION, NOT_RECOMMENDED, INCOMPLETE }

enum class Recommendation(val title: String) {
    PAY_IN_FULL("Pay in full"),
    CHOOSE_EMI("Choose EMI"),
    CONSIDER_OTHER_EMI("Consider a different EMI offer"),
    POSTPONE("Postpone the purchase"),
    SKIP("Skip for now")
}

data class CashPlan(
    val price: BigDecimal,
    val decision: Decision,
    val fits: Boolean,
    val fromMonthly: BigDecimal,       // part paid out of this month's leftover money (up to B_m)
    val fromSavings: BigDecimal,       // part that has to come out of savings
    val savingsAfter: BigDecimal,
    val reserveBreached: Boolean,
    val leftThisMonthAfter: BigDecimal, // D - fromMonthly
    val monthsToSave: Int?,
    val reason: String
)

data class SavingsProjection(
    val months: Int,
    val endCash: BigDecimal,
    val endEmi: BigDecimal,
    val lowestEmi: BigDecimal,
    val monthsBelowReserveEmi: Int,
    val rate: BigDecimal
)

data class EmiPlan(
    val option: EmiOption,
    val issues: List<EmiIssue>,
    val cost: EmiCost?,
    val suitability: Suitability,
    val monthlyLeftAfter: BigDecimal?,     // D - M
    val savingsAfterUpfront: BigDecimal?,  // S - (A+F)
    val projection: SavingsProjection?,
    val reasons: List<String>
) { val usable: Boolean get() = cost != null }

data class Advice(
    val price: BigDecimal,
    val cash: CashPlan,
    val emis: List<EmiPlan>,
    val recommendation: Recommendation,
    val chosenEmiId: String?,
    val reasons: List<String>,
    val assumptions: List<String>,
    val missing: List<String>,
    val basis: ReturnBasis,
    val monthlyLeft: BigDecimal,           // D
    val monthlyAllowance: BigDecimal       // B_m
) {
    val chosen: EmiPlan? get() = emis.firstOrNull { it.option.id == chosenEmiId }
}

object PurchaseAdvisor {
    private val MC = MathContext(30, RoundingMode.HALF_EVEN)
    private val EPS = BigDecimal("0.01")

    /** Offers that still need the user's attention before they can be used. */
    fun usableOptions(options: List<EmiOption>) = options.filter { it.confirmed }

    fun advise(
        profile: FinancialProfile, productId: String, price: BigDecimal, priority: Priority,
        options: List<EmiOption>, history: List<Double> = emptyList()
    ): Advice {
        val b = AffordabilityCalculator.budget(profile, history)
        val ret = profile.returnAssumption()
        val d = b.disposable.toMoneyDecimal()
        val bm = (if (b.hasShortfall) 0.0 else b.monthlyAllowance).toMoneyDecimal()
        val bSafe = (if (b.hasShortfall) 0.0 else b.safeBudget).toMoneyDecimal()
        val s = b.savings.toMoneyDecimal()
        val reserve = b.reserve.toMoneyDecimal()
        val tol = BigDecimal.ONE.max(price.multiply(BigDecimal("0.001")))

        // ---- A. pay in full (decision comes from the shared calculator) ----
        val line = AffordabilityCalculator.assess(profile, listOf(Candidate(productId, price.toDouble(), priority)), history).line(productId)!!
        val fits = line.decision == Decision.BUY_NOW || line.decision == Decision.BUY_WITH_CAUTION
        val fromMonthly = price.min(bm)
        val fromSavings = price - fromMonthly
        val savingsAfter = s - fromSavings
        val cash = CashPlan(
            price = price, decision = line.decision, fits = fits, fromMonthly = fromMonthly.paise(), fromSavings = fromSavings.paise(),
            savingsAfter = savingsAfter.paise(), reserveBreached = savingsAfter < reserve - EPS,
            leftThisMonthAfter = (d - fromMonthly).paise(), monthsToSave = line.monthsToSave, reason = line.reason
        )

        // ---- B. each EMI offer ----
        val plans = options.map { planFor(it, price, ret, b, d, bm, bSafe, s, reserve, profile) }

        // ---- C. recommendation ----
        val eligible = plans.filter { it.suitability == Suitability.SUITABLE || it.suitability == Suitability.CAUTION }
        val best = eligible.sortedWith(
            compareBy<EmiPlan>({ it.suitability.ordinal }, { it.cost!!.pvCost }, { it.option.tenureMonths })
        ).firstOrNull()
        val reasons = ArrayList<String>()
        var rec: Recommendation
        var chosen: EmiPlan? = null

        fun emiWins(p: EmiPlan) = p.suitability == Suitability.SUITABLE &&
            (p.cost!!.netAdvantage > tol || (cash.decision != Decision.BUY_NOW && p.cost.netAdvantage >= -tol))

        if (fits) {
            if (best != null && emiWins(best)) { rec = Recommendation.CHOOSE_EMI; chosen = best }
            else {
                rec = Recommendation.PAY_IN_FULL
                reasons += if (cash.decision == Decision.BUY_NOW) "It fits this month's allowance of ${Money.format(bm)} without touching savings."
                else "It fits your safe budget, using ${Money.format(cash.fromSavings)} of savings above your ${Money.format(reserve)} reserve."
                if (best != null) reasons += if (best.cost!!.netAdvantage > tol)
                    "The best EMI (${label(best)}) would cost less, but it stretches your monthly budget, so paying in full is the safer choice."
                else "The best EMI (${label(best)}) costs ${costVsCash(best, ret != null)}, so paying in full is the better deal."
                else if (plans.isNotEmpty()) reasons += "None of the EMI offers are both usable and affordable under your plan."
            }
        } else {
            if (best != null) {
                if (cash.decision == Decision.WAIT && best.cost!!.extraVsCash > tol) {
                    rec = Recommendation.POSTPONE
                    reasons += "Waiting about ${cash.monthsToSave} month${if (cash.monthsToSave == 1) "" else "s"} lets you pay cash and avoid ${Money.format(best.cost.extraVsCash)} of extra EMI cost."
                    reasons += "If you can't wait, ${label(best)} is the EMI that fits you best."
                } else { rec = Recommendation.CHOOSE_EMI; chosen = best }
            } else if (cash.decision == Decision.WAIT) {
                rec = Recommendation.POSTPONE
                reasons += cash.reason
            } else {
                rec = Recommendation.SKIP
                reasons += cash.reason
            }
        }
        val ch = chosen
        if (ch != null) {
            val c = ch.cost!!
            if (plans.count { it.suitability == Suitability.SUITABLE || it.suitability == Suitability.CAUTION } >= 2) {
                val lowestMonthly = eligible.minWith(compareBy<EmiPlan>({ it.option.resolvedEmi() }, { it.option.tenureMonths }))
                if (lowestMonthly.option.id != ch.option.id) {
                    rec = Recommendation.CONSIDER_OTHER_EMI
                    val diff = lowestMonthly.cost!!.totalCost - c.totalCost
                    reasons += if (diff.signum() > 0) "${label(ch)} is not the lowest monthly EMI, but it costs ${Money.format(diff)} less overall than ${label(lowestMonthly)}."
                    else "${label(ch)} is not the lowest monthly EMI, but ${label(lowestMonthly)} fits your budget less well."
                }
            }
            reasons += "${label(ch)}: ${Money.format(ch.option.resolvedEmi())} × ${ch.option.tenureMonths} months" +
                (if (c.upfrontCash.signum() > 0) " after ${Money.format(c.upfrontCash)} up front" else "") +
                ". Total ${Money.format(c.totalCost)} (${costVsCash(ch, ret != null)})."
            reasons += when {
                !fits -> "Paying cash would not fit your safe budget; this EMI does."
                cash.fromSavings.signum() > 0 -> "The monthly EMI fits, and it keeps ${Money.format(cash.fromSavings)} of your savings untouched."
                else -> "The monthly EMI fits your plan."
            }
            ch.suitability.takeIf { it == Suitability.CAUTION }?.let { reasons += "Use it with caution: ${ch.reasons.firstOrNull { r -> r.startsWith("Caution") }?.removePrefix("Caution: ") ?: "it stretches your plan."}" }
        }
        if (rec == Recommendation.SKIP || rec == Recommendation.POSTPONE) {
            plans.filter { it.suitability == Suitability.NOT_RECOMMENDED }.take(2).forEach { reasons += "${label(it)}: ${it.reasons.firstOrNull { r -> r.startsWith("Not") }?.removePrefix("Not recommended: ") ?: "not affordable."}" }
        }

        // ---- D. what we could not judge, and what we assumed ----
        val missing = ArrayList<String>()
        plans.filter { it.suitability == Suitability.INCOMPLETE }.forEach { p ->
            val gaps = p.issues.filter { it.level == IssueLevel.MISSING || it.level == IssueLevel.ERROR }.joinToString(" ") { it.text }
            missing += "${label(p)}: " + (if (!p.option.confirmed) "confirm the details read from the screenshot. " else "") + gaps
        }
        val assumptions = ArrayList<String>()
        if (plans.any { it.usable }) {
            if (ret == null) assumptions += "No expected return was entered in your Financial profile, so EMI and cash are compared on their plain total cost. Add a return to also compare present value."
            else assumptions += "Money you keep is assumed to earn ${"%.1f".format(ret.annualNetPct)}% a year after tax (your own figure, not a guarantee), compounded monthly."
            assumptions += "Income and expenses are assumed to stay as entered for the whole EMI tenure; leftover money is assumed spent as usual."
            assumptions += "Fees and cashback are treated as paid or received at the start; EMIs are paid at the end of each month."
        }
        if (plans.any { it.issues.any { i -> i.level == IssueLevel.WARNING } }) assumptions += "Some offers have warnings - check them before relying on the numbers."

        return Advice(
            price = price, cash = cash, emis = plans, recommendation = rec, chosenEmiId = chosen?.option?.id,
            reasons = reasons, assumptions = assumptions, missing = missing,
            basis = if (ret != null) ReturnBasis.PRESENT_VALUE else ReturnBasis.NOMINAL, monthlyLeft = d.paise(), monthlyAllowance = bm.paise()
        )
    }

    /** Several products: each is judged alone, so warn when the EMIs recommended together would not fit. */
    fun combinedEmiWarning(profile: FinancialProfile, advices: Collection<Advice>, history: List<Double> = emptyList()): String? {
        val chosen = advices.mapNotNull { it.chosen }
        if (chosen.size < 2) return null
        val total = chosen.fold(BigDecimal.ZERO) { acc, p -> acc + p.option.resolvedEmi() }
        val d = AffordabilityCalculator.budget(profile, history).disposable.toMoneyDecimal()
        return if (total > d) "The ${chosen.size} recommended EMIs add up to ${Money.format(total)} a month, more than the ${Money.format(d)} you have left each month. Take fewer."
        else null
    }

    // ------------------------------------------------------------------

    private fun planFor(
        raw: EmiOption, price: BigDecimal, ret: ReturnAssumption?, b: Budget,
        d: BigDecimal, bm: BigDecimal, bSafe: BigDecimal, s: BigDecimal, reserve: BigDecimal, profile: FinancialProfile
    ): EmiPlan {
        val issues = EmiCalculator.validate(raw, price)
        if (!raw.confirmed) {
            return EmiPlan(raw, issues, null, Suitability.INCOMPLETE, null, null, null, listOf("Read from a screenshot - confirm or correct it first."))
        }
        val cost = EmiCalculator.analyze(raw, price, ret)
            ?: return EmiPlan(raw, issues, null, Suitability.INCOMPLETE, null, null, null, issues.filter { it.level == IssueLevel.MISSING || it.level == IssueLevel.ERROR }.map { it.text })
        val o = EmiCalculator.resolve(raw)
        val m = o.monthlyEmi!!
        val n = o.tenureMonths!!
        val reasons = ArrayList<String>()
        var worst = Suitability.SUITABLE
        fun lower(x: Suitability) { if (x.ordinal > worst.ordinal) worst = x }

        // monthly cash-flow test (held for the whole tenure)
        val dAfter = (d - m).paise()
        when {
            d.signum() < 0 -> { lower(Suitability.NOT_RECOMMENDED); reasons += "Not recommended: your plan already runs a monthly shortfall of ${Money.format(-d)}; a ${Money.format(m)} EMI makes it ${Money.format(-dAfter)}." }
            dAfter.signum() < 0 -> { lower(Suitability.NOT_RECOMMENDED); reasons += "Not recommended: the ${Money.format(m)} EMI is more than the ${Money.format(d)} you have left each month (short by ${Money.format(-dAfter)} for $n months)." }
            m > bm + EPS -> { lower(Suitability.CAUTION); reasons += "Caution: the ${Money.format(m)} EMI is above the ${Money.format(bm)} a month your policy allows (${(b.alpha * 100).toInt()}% of what's left), though it leaves ${Money.format(dAfter)} a month." }
            else -> reasons += "The ${Money.format(m)} EMI fits within your ${Money.format(bm)} monthly allowance, leaving ${Money.format(dAfter)} a month."
        }
        // up-front test
        val upfront = cost.upfrontCash
        val savingsAfter = (s - upfront).paise()
        if (upfront.signum() > 0) {
            when {
                savingsAfter < reserve - EPS -> { lower(Suitability.NOT_RECOMMENDED); reasons += "Not recommended: the ${Money.format(upfront)} paid up front would take savings below your ${Money.format(reserve)} emergency reserve." }
                upfront > bSafe + EPS -> { lower(Suitability.CAUTION); reasons += "Caution: ${Money.format(upfront)} up front is more than your ${Money.format(bSafe)} safe budget." }
                else -> reasons += "${Money.format(upfront)} up front leaves ${Money.format(savingsAfter)} in savings."
            }
        }
        // month-by-month savings projection over the tenure
        val proj = project(profile, price, o, m, n, ret, d, s, reserve)
        if (proj.monthsBelowReserveEmi > 0 && worst != Suitability.NOT_RECOMMENDED) {
            lower(Suitability.NOT_RECOMMENDED)
            reasons += "Not recommended: over the tenure your savings would sit below the emergency reserve for ${proj.monthsBelowReserveEmi} month(s)."
        }
        return EmiPlan(raw, issues, cost, worst, dAfter, savingsAfter, proj, reasons)
    }

    /**
     * Same horizon for both choices. Each month: savings grow at the monthly rate, the planned contribution T is added
     * (or a baseline deficit is taken off), and in the EMI scenario any part of the EMI that D cannot cover comes out of savings.
     */
    private fun project(
        profile: FinancialProfile, price: BigDecimal, o: EmiOption, m: BigDecimal, n: Int, ret: ReturnAssumption?,
        d: BigDecimal, s: BigDecimal, reserve: BigDecimal
    ): SavingsProjection {
        val r = ret?.monthlyRate() ?: BigDecimal.ZERO
        val growth = BigDecimal.ONE + r
        val t = profile.monthlySavingsTarget.toMoneyDecimal()
        val baseline = t + d.min(BigDecimal.ZERO)
        val coveredByD = d.max(BigDecimal.ZERO)
        val topUp = (m - coveredByD).max(BigDecimal.ZERO)
        var cash = s - price
        var emi = s - (o.downPayment + o.fees + o.cashDiscountLost - o.cashback)
        var low = emi
        var below = 0
        repeat(n) {
            cash = cash.multiply(growth, MC) + baseline
            emi = emi.multiply(growth, MC) + baseline - topUp
            if (emi < low) low = emi
            if (emi < reserve - EPS) below++
        }
        return SavingsProjection(n, cash.paise(), emi.paise(), low.paise(), below, r)
    }

    private fun EmiOption.resolvedEmi(): BigDecimal = EmiCalculator.resolve(this).monthlyEmi ?: BigDecimal.ZERO

    private fun label(p: EmiPlan): String {
        val o = p.option
        val name = o.lender.ifBlank { "EMI offer" }
        return if (o.tenureMonths != null) "$name (${o.tenureMonths} mo)" else name
    }

    private fun costVsCash(p: EmiPlan, hasReturn: Boolean): String {
        val c = p.cost!!
        val extra = c.extraVsCash
        val nominal = when {
            extra.signum() > 0 -> "${Money.format(extra)} more than paying cash"
            extra.signum() < 0 -> "${Money.format(-extra)} less than paying cash"
            else -> "the same as paying cash"
        }
        return if (hasReturn) "$nominal; ${if (c.netAdvantage.signum() >= 0) "worth ${Money.format(c.netAdvantage)} more" else "worth ${Money.format(-c.netAdvantage)} less"} than cash in today's money"
        else nominal
    }
}
