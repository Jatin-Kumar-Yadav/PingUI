package com.pingpaisa.app

import com.pingpaisa.app.healthscore.LoanEligibility
import com.pingpaisa.app.healthscore.LoanMath
import com.pingpaisa.app.healthscore.LoanQuote
import com.pingpaisa.app.healthscore.LoanQuotes
import com.pingpaisa.app.healthscore.Money
import com.pingpaisa.app.healthscore.ScoreCalculator
import com.pingpaisa.app.healthscore.VerdictEngine
import java.util.Locale
import kotlin.math.roundToInt

/** One bank in the comparison list. [verdict] is "SAFE", "TIGHT" or "RISKY". */
internal data class QuoteRowUi(
    val bank: String, val rateText: String, val emiText: String, val interestText: String,
    val verdict: String, val selected: Boolean
)

/**
 * Everything the Loan simulator shows for one amount and tenure, worked out with the
 * tracker's loan engine (LoanMath, LoanEligibility, LoanQuotes, VerdictEngine). Plain
 * Kotlin so it can be unit-tested.
 */
internal data class LoanUi(
    val hasIncome: Boolean,
    val canBorrow: Boolean,            // the limit allows at least ₹20K
    val minAmount: Float,
    val maxAmount: Float,              // the borrowing limit, rounded down to ₹10K
    val amount: Float,                 // the chosen amount, kept inside the limit
    val amountText: String,            // "₹3,00,000"
    val minLabel: String, val maxLabel: String,
    val limitNote: String,             // how the limit was worked out
    val rateText: String,              // "11%"
    val rateAssumed: Boolean,
    val tenureMonths: Int,
    val emiText: String,               // "₹9,822"
    val incomeShare: String,           // "This EMI would use 9% of your monthly income."
    val scoreBefore: Int,
    val scoreAfter: Int,
    val riskAfter: String,             // "Low risk"
    val planText: String,              // the repayment plan summary
    val quotes: List<QuoteRowUi> = emptyList(),   // safest and cheapest first
    val selectedBank: String? = null,  // whose numbers the EMI, impact and verdict show
    val verdict: String = "",          // "SAFE", "TIGHT" or "RISKY"
    val verdictTitle: String = "",
    val verdictReasons: List<String> = emptyList()
) {
    companion object {
        const val MIN_LOAN = 10_000f
        const val STEP = 10_000f
        const val MAX_TENURE_YEARS = 30
        const val MAX_BANK_NAME = 24

        /**
         * A quote typed in the form, the tracker's rules: a name (no "|" or line breaks, which
         * the saved format uses) and a rate of 0.1% to 60% a year. Null when it isn't valid.
         */
        fun quoteFrom(name: String, rateText: String): LoanQuote? {
            val cleanName = name.replace("|", " ").replace("\n", " ").trim()
            val rate = rateText.toDoubleOrNull() ?: return null
            if (cleanName.isEmpty() || cleanName.length > MAX_BANK_NAME || rate !in 0.1..60.0) return null
            return LoanQuote(cleanName, rate)
        }

        fun quoteErrors(name: String, rateText: String): Pair<String?, String?> = Pair(
            when {
                name.isBlank() -> "Enter the bank's name"
                name.trim().length > MAX_BANK_NAME -> "Keep it under $MAX_BANK_NAME characters"
                else -> null
            },
            if ((rateText.toDoubleOrNull() ?: -1.0) in 0.1..60.0) null else "Enter a yearly rate from 0.1% to 60%"
        )

        /** Adding a bank that is already listed replaces it (any case), as in the tracker. */
        fun withQuote(quotes: List<LoanQuote>, quote: LoanQuote): List<LoanQuote> =
            quotes.filter { !it.bankName.equals(quote.bankName, ignoreCase = true) } + quote

        private fun pct(x: Double) = if (x >= 1.0) "100%+" else "${(x * 100).roundToInt()}%"
        private fun rate(r: Double) = String.format(Locale.US, "%.1f", r).removeSuffix(".0") + "%"

        /**
         * @param baseline this month's real numbers (the same the health score uses)
         * @param requested the amount on the slider (kept inside the limit here)
         * @param quotes the user's bank quotes; without any, the tracker's assumed [annualRate] is used
         * @param selectedBank the quote the user tapped; otherwise the best one in the comparison
         */
        fun from(
            baseline: ScoreCalculator.RawFactors,
            requested: Float,
            tenureMonths: Int,
            quotes: List<LoanQuote> = emptyList(),
            selectedBank: String? = null,
            annualRate: Double = LoanEligibility.DEFAULT_RATE
        ): LoanUi {
            // The limit uses the highest quoted rate, so it holds whichever bank is chosen (tracker rule).
            val limitRate = quotes.maxOfOrNull { it.annualRate } ?: annualRate
            val limits = LoanEligibility.limits(baseline, tenureMonths, limitRate)
            val max = (limits.maxLoan / STEP).toInt() * STEP
            val safe = (limits.safeLoan / STEP).toInt() * STEP
            val canBorrow = baseline.income > 0 && max >= 2 * STEP
            val amount = if (canBorrow) ((requested / STEP).roundToInt() * STEP).coerceIn(MIN_LOAN, max) else 0f

            val results = if (canBorrow && quotes.isNotEmpty())
                LoanQuotes.compare(baseline, amount.toDouble(), tenureMonths, quotes) else emptyList()
            val chosen = results.firstOrNull { it.quote.bankName == selectedBank } ?: results.firstOrNull()
            val rateUsed = chosen?.quote?.annualRate ?: annualRate
            val rateAssumed = chosen == null
            val emi = chosen?.emi ?: if (canBorrow) LoanMath.emi(amount.toDouble(), tenureMonths, annualRate) else 0.0
            val verdict = chosen?.verdict ?: VerdictEngine.evaluate(baseline, emi)
            val years = tenureMonths / 12
            val existingEmi = baseline.totalEmiAmount
            val share = if (baseline.income > 0) emi / baseline.income else 0.0
            val totalShare = if (baseline.income > 0) (emi + existingEmi) / baseline.income else 0.0
            val totalPaid = emi * tenureMonths

            return LoanUi(
                hasIncome = baseline.income > 0,
                canBorrow = canBorrow,
                minAmount = MIN_LOAN,
                maxAmount = if (canBorrow) max else 2 * STEP,
                amount = amount,
                amountText = Money.inr(amount.toDouble()),
                minLabel = Money.compact(MIN_LOAN.toDouble()),
                maxLabel = Money.compact(max.toDouble()),
                limitNote = "Up to ${Money.compact(max.toDouble())} keeps all your EMIs within 50% of income, " +
                    "comfortably up to ${if (safe >= MIN_LOAN) Money.compact(safe.toDouble()) else "nothing"} (35%). " +
                    "Worked out at ${if (quotes.isEmpty()) "an assumed ${rate(limitRate)}" else "your highest quote, ${rate(limitRate)},"} over " +
                    "${if (years == 1) "1 year" else "$years years"}.",
                rateText = rate(rateUsed),
                rateAssumed = rateAssumed,
                tenureMonths = tenureMonths,
                emiText = Money.inr(emi),
                incomeShare = "This EMI would use ${pct(share)} of your monthly income" +
                    (if (existingEmi > 0) " (${pct(totalShare)} with your other EMIs)." else "."),
                scoreBefore = verdict.before.score,
                scoreAfter = verdict.after.score,
                riskAfter = when (verdict.after.risk) {
                    ScoreCalculator.RiskLevel.LOW -> "Low risk"
                    ScoreCalculator.RiskLevel.MEDIUM -> "Medium risk"
                    ScoreCalculator.RiskLevel.HIGH -> "High risk"
                },
                planText = "$tenureMonths monthly payments of ${Money.inr(emi)}: " +
                    "${Money.inr(totalPaid)} in all, of which ${Money.inr(totalPaid - amount)} is interest.",
                quotes = results.map { r ->
                    QuoteRowUi(r.quote.bankName, rate(r.quote.annualRate), Money.inr(r.emi),
                        Money.compact(r.totalInterest) + " interest", r.verdict.verdict.name, r === chosen)
                },
                selectedBank = chosen?.quote?.bankName,
                verdict = if (canBorrow) verdict.verdict.name else "",
                verdictTitle = when (verdict.verdict) {
                    VerdictEngine.Verdict.SAFE -> "Looks affordable"
                    VerdictEngine.Verdict.TIGHT -> "Manageable, but tight"
                    VerdictEngine.Verdict.RISKY -> "Risky for your budget"
                } + (chosen?.let { " with ${it.quote.bankName}" } ?: ""),
                verdictReasons = if (canBorrow) verdict.reasons else emptyList()
            )
        }

        /** Whole years typed in the custom tenure box: 1 to 30, otherwise null. */
        fun customYears(text: String): Int? = text.toIntOrNull()?.takeIf { it in 1..MAX_TENURE_YEARS }
    }
}
