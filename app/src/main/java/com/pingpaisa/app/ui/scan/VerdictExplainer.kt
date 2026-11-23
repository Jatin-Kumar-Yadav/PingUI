package com.pingpaisa.app.ui.scan

import com.pingpaisa.app.data.Product
import com.pingpaisa.app.finance.Advice
import com.pingpaisa.app.finance.Assessment
import com.pingpaisa.app.finance.Decision
import com.pingpaisa.app.finance.EmiCalculator
import com.pingpaisa.app.finance.Recommendation
import com.pingpaisa.app.util.Money
import kotlin.math.roundToInt

data class ItemExplain(val name: String, val price: Double?, val label: String, val kind: VerdictKind, val sentence: String)
data class PaymentExplain(val name: String, val headline: String, val kind: VerdictKind, val lines: List<String>, val missing: List<String>)
data class Explanation(
    val points: List<VerdictBullet>,
    val items: List<ItemExplain>,
    val payments: List<PaymentExplain>,
    val warning: String?,
    val footnote: String
)

/**
 * The "Why this verdict" page in everyday words: the handful of facts the verdict rests on, one sentence per product and a
 * plain payment recommendation. No formulas or symbols - the numbers are the same ones the calculator used.
 */
object VerdictExplainer {
    fun explain(a: Assessment, products: List<Product>, advices: Map<String, Advice>, combinedWarning: String? = null): Explanation {
        val b = a.budget
        val points = ArrayList<VerdictBullet>()

        points += when {
            b.hasShortfall -> VerdictBullet(Mark.BAD, "Your usual spending and savings plan already come to ${Money.format(-b.disposable)} more than you earn each month.")
            b.disposable < 0.5 -> VerdictBullet(Mark.WARN, "Nothing is left each month after your usual spending and your savings plan.")
            else -> VerdictBullet(Mark.OK, "About ${Money.format(b.disposable)} is left each month after your usual spending and your savings plan.")
        }
        points += if (b.spendableSavings > 0.5)
            VerdictBullet(Mark.OK, "You hold ${Money.format(b.spendableSavings)} in savings above your emergency fund.")
        else VerdictBullet(Mark.WARN, "Your savings are at or below your emergency fund, so none of it is free to spend.")
        points += if (b.safeBudget > 0.5)
            VerdictBullet(Mark.OK, "A comfortable amount to spend on purchases right now is about ${Money.format(b.safeBudget)}.")
        else VerdictBullet(Mark.WARN, "There is no spare room in your budget for extra purchases right now.")

        if (a.pricedCount > 0) {
            val share = a.incomeShareAll * 100
            val shareText = if (share < 1.0) "less than 1%" else "about ${share.roundToInt()}%"
            points += VerdictBullet(Mark.OK, (if (a.pricedCount == 1) "This item costs " else "These items cost ") +
                "${Money.format(a.totalAll)}, $shareText of one month's income.")
            points += if (a.reserveProtected) VerdictBullet(Mark.OK, "Your emergency fund stays protected.")
            else VerdictBullet(Mark.BAD, "Buying this would eat into your emergency fund.")
        }

        val items = products.mapNotNull { p ->
            val l = a.line(p.id) ?: return@mapNotNull null
            val (label, kind) = when (l.decision) {
                Decision.BUY_NOW -> "You can buy this now" to VerdictKind.GREEN
                Decision.BUY_WITH_CAUTION -> "Okay, but with care" to VerdictKind.AMBER
                Decision.WAIT -> ("Better to wait" + (l.monthsToSave?.let { " (about $it month${if (it == 1) "" else "s"})" } ?: "")) to VerdictKind.RED
                Decision.SKIP -> "Skip for now" to VerdictKind.RED
                Decision.NEEDS_PRICE -> "Needs a price" to VerdictKind.NEUTRAL
            }
            val sentence = when (l.decision) {
                Decision.BUY_NOW -> "It fits within the money you have spare this month, and your savings are not touched."
                Decision.BUY_WITH_CAUTION -> "It fits, but ${Money.format(l.fromSavings)} would come out of savings above your emergency fund."
                Decision.WAIT -> "It is more than you can comfortably spend right now." +
                    (l.monthsToSave?.let { " Saving up for it would take about $it month${if (it == 1) "" else "s"}." } ?: "")
                Decision.SKIP -> if (b.hasShortfall) "Your monthly plan is already stretched, so there is no room for it."
                else "It does not fit your budget even after ${b.maxWaitMonths} months of saving."
                Decision.NEEDS_PRICE -> "Add its price to include it in the verdict."
            }
            ItemExplain(p.name, l.price, label, kind, sentence)
        }

        val payments = products.mapNotNull { p ->
            val adv = advices[p.id] ?: return@mapNotNull null
            val kind = when (adv.recommendation) {
                Recommendation.PAY_IN_FULL, Recommendation.CHOOSE_EMI, Recommendation.CONSIDER_OTHER_EMI -> VerdictKind.GREEN
                Recommendation.POSTPONE -> VerdictKind.AMBER
                Recommendation.SKIP -> VerdictKind.RED
            }
            val chosen = adv.chosen
            val who = chosen?.let { " - " + it.option.lender.ifBlank { "EMI offer" } + (it.option.tenureMonths?.let { n -> " ($n months)" } ?: "") }.orEmpty()
            val lines = ArrayList<String>()
            if (chosen != null) {
                EmiCalculator.resolve(chosen.option).monthlyEmi?.let { m ->
                    lines += "You would pay ${Money.format(m)} a month" + (chosen.option.tenureMonths?.let { " for $it months" } ?: "") + "."
                }
                chosen.cost?.extraVsCash?.let { x ->
                    lines += when {
                        x.signum() > 0 -> "In total that is ${Money.format(x)} more than paying cash."
                        x.signum() < 0 -> "In total that is ${Money.format(x.negate())} less than paying cash."
                        else -> "In total it costs the same as paying cash."
                    }
                }
            }
            lines += adv.reasons.take(3)
            PaymentExplain(p.name, adv.recommendation.title + who, kind, lines.distinct(), adv.missing)
        }

        val footnote = (if (b.usesHistory) "Your usual spending is the average of your last ${b.historyMonths} full months. "
        else "Your usual spending is the figure you entered in your financial profile, so treat this as an estimate, not a forecast. ") +
            "With several items, must-haves are covered first, then cheaper ones, each only if it still fits."
        return Explanation(points, items, payments, combinedWarning, footnote)
    }
}
