package com.pingpaisa.app.ui.scan

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pingpaisa.app.finance.Advice
import com.pingpaisa.app.finance.Decision
import com.pingpaisa.app.finance.EmiPlan
import com.pingpaisa.app.finance.IssueLevel
import com.pingpaisa.app.finance.Recommendation
import com.pingpaisa.app.finance.ReturnBasis
import com.pingpaisa.app.ui.theme.PP
import com.pingpaisa.app.util.Money
import java.math.BigDecimal

/**
 * "Pay in full vs EMI" for one product, inside the existing verdict details sheet.
 * Every number comes from [Advice] (PurchaseAdvisor), so what is shown can be traced to the inputs and formulas.
 */
@Composable
fun PaymentComparison(productName: String, a: Advice) {
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Text("${productName.take(40).uppercase()}: ${Money.format(a.price)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PP.TextHi)
        Spacer(Modifier.height(8.dp))

        // Option 1 - pay in full
        val c = a.cash
        val (cashLabel, cashColor) = when (c.decision) {
            Decision.BUY_NOW -> "Affordable" to PP.Accent
            Decision.BUY_WITH_CAUTION -> "Affordable with caution" to Amber
            Decision.WAIT -> "Not recommended now" to PP.Error
            else -> "Not recommended" to PP.Error
        }
        OptionCard("Option 1 — Pay in full", cashLabel, cashColor) {
            Line("Pay today", Money.format(c.price))
            if (c.fromSavings.signum() > 0) Line("Taken from savings", Money.format(c.fromSavings))
            Line("Savings remaining", Money.format(c.savingsAfter), valueColor = if (c.reserveBreached) PP.Error else Color.Unspecified)
            Line("Left this month after paying", Money.format(c.leftThisMonthAfter), valueColor = if (c.leftThisMonthAfter.signum() < 0) PP.Error else Color.Unspecified)
            if (c.reserveBreached) Note("This would take savings below your emergency reserve.", PP.Error)
            if (!c.fits && c.reason.isNotBlank()) Note(c.reason, PP.TextLo)
        }

        // Option 2.. - each EMI offer
        a.emis.forEachIndexed { i, p -> EmiCard(i + 2, p, a) }

        // Final recommendation
        Spacer(Modifier.height(6.dp))
        val recColor = when (a.recommendation) {
            Recommendation.PAY_IN_FULL, Recommendation.CHOOSE_EMI, Recommendation.CONSIDER_OTHER_EMI -> PP.Accent
            Recommendation.POSTPONE -> Amber
            Recommendation.SKIP -> PP.Error
        }
        Column(Modifier.fillMaxWidth().background(PP.Field, RoundedCornerShape(14.dp)).padding(12.dp)) {
            Text("FINAL RECOMMENDATION", fontSize = 10.sp, letterSpacing = 1.2.sp, color = PP.TextLo)
            val who = a.chosen?.let { " — " + it.option.lender.ifBlank { "EMI offer" } + (it.option.tenureMonths?.let { n -> " ($n months)" } ?: "") }.orEmpty()
            Text(a.recommendation.title + who, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = recColor, modifier = Modifier.padding(top = 2.dp))
            a.reasons.forEach { Text("• $it", fontSize = 12.sp, color = PP.TextHi, modifier = Modifier.padding(top = 4.dp)) }
            if (a.missing.isNotEmpty()) {
                Text("Not judged yet", fontSize = 11.sp, color = Amber, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 8.dp))
                a.missing.forEach { Text("• $it", fontSize = 11.sp, color = Amber, modifier = Modifier.padding(top = 2.dp)) }
            }
            if (a.assumptions.isNotEmpty()) {
                Text("Assumptions", fontSize = 11.sp, color = PP.TextLo, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 8.dp))
                a.assumptions.forEach { Text("• $it", fontSize = 10.sp, color = PP.TextLo, lineHeight = 14.sp, modifier = Modifier.padding(top = 2.dp)) }
            }
        }
    }
}

@Composable
private fun EmiCard(number: Int, p: EmiPlan, a: Advice) {
    val o = p.option
    val title = "Option $number — EMI" + o.lender.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
    OptionCard(title, suitabilityLabel(p.suitability), suitabilityColor(p.suitability)) {
        val cost = p.cost
        if (cost == null) {
            p.reasons.forEach { Note(it, Amber) }
            return@OptionCard
        }
        val m = EmiCalcView.monthly(p)
        if (cost.upfrontCash.signum() > 0 || o.downPayment.signum() > 0) Line("Down payment", Money.format(o.downPayment))
        Line("Monthly EMI", Money.format(m))
        Line("Tenure", "${o.tenureMonths} months")
        if (o.fees.signum() > 0) Line("Processing fee & charges", Money.format(o.fees))
        if (o.cashback.signum() > 0) Line("Cashback / discount", "−" + Money.format(o.cashback))
        if (o.cashDiscountLost.signum() > 0) Line("Cash discount lost", Money.format(o.cashDiscountLost))
        Line("Total payable", Money.format(cost.totalCost), bold = true)
        val extra = cost.extraVsCash
        Line(
            "Extra cost vs paying cash",
            (if (extra.signum() > 0) "+" else "") + Money.format(extra),
            valueColor = if (extra.signum() > 0) PP.Error else if (extra.signum() < 0) PP.Accent else Color.Unspecified
        )
        cost.effectiveAnnualRatePct?.let { Line("Real yearly cost of borrowing", if (it < 0.05) "0%" else "%.1f%%".format(it)) }
        p.monthlyLeftAfter?.let { Line("Left each month after EMI", Money.format(it), valueColor = if (it.signum() < 0) PP.Error else Color.Unspecified) }
        p.savingsAfterUpfront?.let { if (cost.upfrontCash.signum() > 0) Line("Savings after paying up front", Money.format(it)) }
        p.projection?.let { pr ->
            Line("Savings after ${pr.months} months — EMI", Money.format(pr.endEmi))
            Line("Savings after ${pr.months} months — pay in full", Money.format(pr.endCash))
        }
        if (cost.basis == ReturnBasis.PRESENT_VALUE) {
            cost.retainedGrowth?.let { Line("Growth on the cash you keep", Money.format(it)) }
            val adv = cost.netAdvantage
            Line(
                "In today's money, EMI is",
                (if (adv.signum() >= 0) "${Money.format(adv)} better" else "${Money.format(-adv)} worse") + " than cash",
                valueColor = if (adv.signum() >= 0) PP.Accent else PP.Error
            )
        } else Note("Return on the cash you keep: add an expected yearly return in your Financial profile to include it.", PP.TextLo)
        p.reasons.forEach { Note(it, if (it.startsWith("Not")) PP.Error else if (it.startsWith("Caution")) Amber else PP.TextLo) }
        p.issues.filter { it.level == IssueLevel.WARNING }.forEach { Note("! ${it.text}", Amber) }
    }
}

/** Resolved monthly EMI (derived from total/tenure when it was not typed). */
private object EmiCalcView {
    fun monthly(p: EmiPlan): BigDecimal = com.pingpaisa.app.finance.EmiCalculator.resolve(p.option).monthlyEmi ?: BigDecimal.ZERO
}

@Composable
private fun OptionCard(title: String, result: String, resultColor: Color, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp).background(PP.Surface, RoundedCornerShape(14.dp)).padding(12.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(title, Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PP.TextHi)
            Text(result, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = resultColor)
        }
        Spacer(Modifier.height(4.dp))
        content()
    }
}

@Composable
private fun Note(text: String, color: Color) =
    Text(text, fontSize = 11.sp, color = color, lineHeight = 15.sp, modifier = Modifier.padding(top = 3.dp))
