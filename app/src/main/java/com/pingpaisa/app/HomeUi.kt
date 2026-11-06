package com.pingpaisa.app

import com.pingpaisa.app.data.isIncome
import com.pingpaisa.app.healthscore.Money
import com.pingpaisa.app.healthscore.ScoreCalculator
import java.time.LocalDateTime
import java.util.Locale

internal data class HomeCategory(val label: String, val amount: String, val icon: Int, val tone: Tone)

/** [id] is null only for the sample rows used in previews. */
internal data class HomeRecent(
    val id: Int?, val name: String, val note: String, val amount: String, val icon: Int, val tone: Tone
)

/** Everything the Home screen shows, already turned into display text. */
internal data class HomeUi(
    val greeting: String,        // "GOOD MORNING"
    val title: String,           // "Namaste, Janvi"
    val subtitle: String,
    val monthLabel: String,      // "November"
    val spent: String,           // "₹12,430"
    val changeLead: String,
    val changeStrong: String,    // "8% less" (empty when there is nothing to compare)
    val changeTrail: String,
    val changeUp: Boolean,       // spending went up: shown in a warm colour instead of green
    val incomeStat: String,
    val savingsStat: String,
    val budgetStat: String,
    val categories: List<HomeCategory>,
    val recent: List<HomeRecent>,
    val isEmpty: Boolean         // no transactions at all yet
) {
    companion object {
        const val MAX_CATEGORIES = 6

        fun from(
            s: HomeSummary,
            name: String,
            risk: ScoreCalculator.RiskLevel?,
            now: LocalDateTime = LocalDateTime.now(),
            locale: Locale = Locale.getDefault()
        ): HomeUi {
            val greeting = when {
                now.hour < 12 -> "GOOD MORNING"
                now.hour < 17 -> "GOOD AFTERNOON"
                else -> "GOOD EVENING"
            }
            val firstName = name.trim().substringBefore(' ')
            val pct = s.changePercent
            val change = when {
                s.spent == 0.0 && pct == null -> Triple("No spending recorded yet this month", "", "")
                pct == null -> Triple("Total spent this month", "", "")
                pct < 0 -> Triple("Total spent ", "${-pct}% less", " than ${s.previousMonthName}")
                pct > 0 -> Triple("Total spent ", "$pct% more", " than ${s.previousMonthName}")
                else -> Triple("Total spent ", "the same", " as ${s.previousMonthName}")
            }
            return HomeUi(
                greeting = greeting,
                title = if (firstName.isEmpty()) "Namaste" else "Namaste, $firstName",
                subtitle = when {
                    !s.hasTransactions -> "Add your first expense to get started."
                    risk == ScoreCalculator.RiskLevel.LOW -> "Your money is looking healthy this month."
                    risk == ScoreCalculator.RiskLevel.MEDIUM -> "Keep an eye on your spending this month."
                    risk == ScoreCalculator.RiskLevel.HIGH -> "Spending is running high this month."
                    else -> "Here's your month at a glance."
                },
                monthLabel = s.monthName,
                spent = Money.inr(s.spent),
                changeLead = change.first, changeStrong = change.second, changeTrail = change.third,
                changeUp = (pct ?: 0) > 0,
                incomeStat = Money.compact(s.income),
                savingsStat = Money.compactSigned(s.savings),
                budgetStat = s.budgetLeft?.let { Money.compactSigned(it) } ?: "Not set",
                categories = s.categories.take(MAX_CATEGORIES).map {
                    val style = categoryStyle(it.category)
                    HomeCategory(it.category, Money.compact(it.amount), style.icon, style.tone)
                },
                recent = s.recent.map { t ->
                    val style = categoryStyle(t.category, t.isIncome)
                    HomeRecent(
                        id = t.id,
                        name = t.recipient,
                        note = (if (t.isIncome) "Income" else t.category) + " • " + HomeSummary.friendlyDate(t.date, s.today, locale),
                        amount = amountText(t),
                        icon = style.icon, tone = style.tone
                    )
                },
                isEmpty = !s.hasTransactions
            )
        }

        /** The design's original sample content, used by Android Studio previews and the screenshot tests. */
        val Sample = HomeUi(
            greeting = "GOOD MORNING", title = "Namaste, Aanya", subtitle = "Your money is looking healthy today.",
            monthLabel = "June", spent = "₹42,680",
            changeLead = "Total spent ", changeStrong = "8% less", changeTrail = " than May", changeUp = false,
            incomeStat = "₹85K", savingsStat = "₹32K", budgetStat = "₹18K",
            categories = listOf(
                HomeCategory("Food", "", R.drawable.ic_pingpaisa_food, Tone.Peach),
                HomeCategory("Travel", "", R.drawable.ic_pingpaisa_car, Tone.Sage),
                HomeCategory("Shopping", "", R.drawable.ic_pingpaisa_bag, Tone.Pink),
                HomeCategory("Bills", "", R.drawable.ic_pingpaisa_bill, Tone.Lavender)
            ),
            recent = listOf(
                HomeRecent(null, "Third Wave Coffee", "Food • Today, 9:42 AM", "−₹320", R.drawable.ic_pingpaisa_coffee, Tone.Peach),
                HomeRecent(null, "Uber India", "Travel • Yesterday", "−₹486", R.drawable.ic_pingpaisa_car, Tone.Sage)
            ),
            isEmpty = false
        )
    }
}
