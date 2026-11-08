package com.pingpaisa.app

import com.pingpaisa.app.data.Budget
import com.pingpaisa.app.data.RecurringItem
import com.pingpaisa.app.data.SavingsGoal
import com.pingpaisa.app.data.Transaction
import com.pingpaisa.app.data.isIncome
import com.pingpaisa.app.healthscore.Money
import com.pingpaisa.app.healthscore.VerdictEngine.Verdict
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** A figure shown under an answer, such as "After purchase ₹12,320" with a Safe / Tight / Risky chip. */
internal data class AiMetric(val label: String, val value: String, val verdict: Verdict? = null)

/** Where an answer can send the user. */
internal enum class AiLink(val label: String, val screen: Screen) {
    Health("Open financial health", Screen.Health),
    Forecast("Open forecast", Screen.Forecast),
    Budgets("Open budgets", Screen.Budgets),
    Recurring("Open recurring payments", Screen.Recurring),
    Transactions("Add an expense", Screen.Entry)
}

/** One answer from Paisa AI. [title] is the bold first line; [metric] and [link] are optional. */
internal data class AiReply(val title: String?, val text: String, val metric: AiMetric? = null, val link: AiLink? = null)

/** One bubble in the conversation: a question ([user]) or an answer ([reply]). */
internal data class ChatMessage(val user: Boolean, val text: String, val reply: AiReply?, val time: String)

/** What the real chat screen needs. Null on the screen means the design sample. */
internal data class ChatState(
    val greeting: String,
    val messages: List<ChatMessage>,
    val suggestions: List<String>,
    val onAsk: (String) -> Unit
)

/** What Paisa AI knows: the same numbers the other screens use. */
internal data class AiContext(
    val name: String,
    val today: LocalDate,
    val income: Double,              // salary plus extra income this month
    val outflow: Double,             // spending so far plus unpaid EMI / "Other" payments
    val projectedSpend: Double,      // month-end projection (see ForecastUi)
    val score: Int?,
    val riskWord: String?,           // "low risk" ...
    val scoreInsight: String,
    val transactions: List<Transaction>,
    val budgets: List<Budget>,
    val goals: List<SavingsGoal>,
    val recurring: List<RecurringItem>
)

/**
 * Paisa AI without any network: keyword rules over the user's own numbers. Every answer is
 * calculated from [AiContext], never invented. Questions it cannot place get a short list of
 * what it can answer.
 */
internal object PaisaAi {

    /** Part of income Paisa AI wants left after a purchase to call it Safe. */
    const val CUSHION = 0.10

    /** The chips above the text box: the design's three, plus an affordability example. */
    fun suggestions(monthName: String) = listOf("Where can I save?", "Explain my score", "$monthName forecast", "Can I afford ₹10,000?")

    fun greeting(name: String, monthName: String) =
        "Hi ${name.trim().substringBefore(' ').ifBlank { "there" }}! I’ve looked at your $monthName spending. What would you like to know?"

    fun answer(question: String, ctx: AiContext, locale: Locale = Locale.getDefault()): AiReply {
        val q = question.lowercase(locale).trim()
        fun has(vararg keys: String) = keys.any { Regex("\\b" + Regex.escape(it)).containsMatchIn(q) }
        val amount = parseAmount(q)

        return when {
            amount != null && has("afford", "buy", "purchase", "spend", "get ", "take", "worth", "can i", "should i") -> affordability(amount, ctx)
            has("where can i save", "how to save", "how can i save", "save more", "save money", "reduce", "cut down", "cut ") -> savingAdvice(ctx)
            has("score", "health", "risk") -> score(ctx)
            has("forecast", "projected", "end of the month", "end of month", "month end", "month-end", "on track") -> forecast(ctx)
            has("budget") -> budget(ctx)
            has("goal") -> goals(ctx)
            has("emi", "recurring", "subscription", "bills", "upcoming", "due", "rent") -> recurring(ctx)
            has("biggest", "largest", "highest", "most expensive", "top expense") -> biggest(ctx)
            has("income", "salary", "earn", "saved", "savings", "left") -> income(ctx)
            has("spent", "spend", "spending", "expense", "paid", "cost", "how much") -> spending(q, ctx, locale)
            has("hi", "hello", "hey", "help", "what can you") -> help(ctx)
            else -> help(ctx, notUnderstood = true)
        }
    }

    // --------------------------------------------------------------- rules

    /** "Can I spend ₹20,000 on a phone?": compares the purchase with what is left of this month's income. */
    private fun affordability(price: Double, ctx: AiContext): AiReply {
        if (ctx.income <= 0) return AiReply("I need your income first.", "Add your salary in Settings and I can check whether ${Money.inr(price)} fits.")
        val left = ctx.income - ctx.projectedSpend - price
        val keep = ctx.income * CUSHION
        val safeUpTo = ctx.income - ctx.projectedSpend - keep
        val share = (left / ctx.income * 100).roundToInt()
        val pace = "Your income is ${Money.inr(ctx.income)} and you are on track to spend ${Money.inr(ctx.projectedSpend)} this month."
        return when {
            left < 0 -> AiReply("Not this month.",
                "$pace Adding ${Money.inr(price)} would put you ${Money.inr(-left)} over your income." +
                    if (safeUpTo > 0) " Up to ${Money.inr(roundDown(safeUpTo))} would still be safe." else " Waiting until next month is safer.",
                AiMetric("After purchase", Money.inr(left), Verdict.RISKY), AiLink.Forecast)
            left < keep -> AiReply("Possible, but tight.",
                "$pace After ${Money.inr(price)} you would keep only ${Money.inr(left)} ($share% of income)." +
                    if (safeUpTo > 0) " ${Money.inr(roundDown(safeUpTo))} would leave a 10% cushion." else "",
                AiMetric("After purchase", Money.inr(left), Verdict.TIGHT), AiLink.Forecast)
            else -> AiReply("Yes, you can afford it.",
                "$pace After ${Money.inr(price)} you would still keep ${Money.inr(left)} ($share% of income).",
                AiMetric("After purchase", Money.inr(left), Verdict.SAFE), AiLink.Forecast)
        }
    }

    private fun savingAdvice(ctx: AiContext): AiReply {
        val spent = thisMonthExpenses(ctx)
        if (spent.isEmpty()) return AiReply("Nothing to trim yet.", "No spending is recorded this month. Once it is, I can show where most of it goes.", link = AiLink.Transactions)
        val total = spent.sumOf { it.amount }
        val top = spent.groupBy { it.category }.map { (c, l) -> c to l.sumOf { it.amount } }.sortedByDescending { it.second }.take(3)
        val lines = top.joinToString("; ") { "${it.first} ${Money.inr(it.second)} (${(it.second / total * 100).roundToInt()}%)" }
        val cut = top.first().second * 0.15
        return AiReply("Start with ${top.first().first}.",
            "Your biggest spends this month: $lines. Spending 15% less on ${top.first().first} would save about ${Money.inr(cut)} a month.",
            link = if (ctx.budgets.none { it.category == top.first().first }) AiLink.Budgets else null)
    }

    private fun score(ctx: AiContext): AiReply =
        if (ctx.score == null) AiReply("Your score isn’t ready yet.", "Try again in a moment.")
        else AiReply("Your score is ${ctx.score}/100${ctx.riskWord?.let { " ($it)" } ?: ""}.", ctx.scoreInsight, link = AiLink.Health)

    private fun forecast(ctx: AiContext): AiReply {
        val month = ctx.today.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
        val text = "You are on track to spend ${Money.inr(ctx.projectedSpend)} by the end of $month." + when {
            ctx.income <= 0 -> ""
            ctx.projectedSpend > ctx.income -> " That is ${Money.inr(ctx.projectedSpend - ctx.income)} more than your income."
            else -> " That would leave about ${Money.inr(ctx.income - ctx.projectedSpend)} of your ${Money.inr(ctx.income)} income."
        }
        return AiReply("$month forecast", text, link = AiLink.Forecast)
    }

    private fun budget(ctx: AiContext): AiReply {
        if (ctx.budgets.isEmpty()) return AiReply("You have no budgets yet.", "Set a monthly limit for a category and I can warn you before you cross it.", link = AiLink.Budgets)
        val rows = BudgetsUi.from(ctx.budgets, ctx.transactions, ctx.today).rows
        val over = rows.filter { it.percent >= 100 }
        val near = rows.filter { it.percent in 80..99 }
        val text = when {
            over.isNotEmpty() -> "Over budget: " + over.joinToString { "${it.category} (${it.percent}%)" } + "." +
                if (near.isNotEmpty()) " Close to the limit: " + near.joinToString { "${it.category} (${it.percent}%)" } + "." else ""
            near.isNotEmpty() -> "Close to the limit: " + near.joinToString { "${it.category} (${it.percent}%)" } + "."
            else -> "All ${rows.size} budgets are under 80% used."
        }
        return AiReply(if (over.isNotEmpty()) "You’ve crossed a budget." else "Budgets look fine.", text, link = AiLink.Budgets)
    }

    private fun goals(ctx: AiContext): AiReply {
        val rows = GoalsUi.from(ctx.goals)
        if (rows.isEmpty()) return AiReply("No savings goal yet.", "Add one on the Budgets screen and I can track it.", link = AiLink.Budgets)
        return AiReply("Your savings goals", rows.joinToString(" ") { "${it.name}: ${it.percent}% (${it.savedText}, ${it.toGoText})." }, link = AiLink.Budgets)
    }

    private fun recurring(ctx: AiContext): AiReply {
        if (ctx.recurring.isEmpty()) return AiReply("No recurring payments saved.", "Add your EMIs, rent or subscriptions and I can include them.", link = AiLink.Recurring)
        val today = ctx.today.dayOfMonth
        val sorted = ctx.recurring.sortedBy { (it.dayOfMonth - today + 31) % 31 }
        val list = sorted.take(4).joinToString("; ") { "${it.name} ${Money.inr(it.amount)} on day ${it.dayOfMonth}" }
        return AiReply("${ctx.recurring.size} recurring ${if (ctx.recurring.size == 1) "payment" else "payments"}",
            "Next up: $list. Together they come to ${Money.inr(ctx.recurring.sumOf { it.amount })} a month.", link = AiLink.Recurring)
    }

    private fun biggest(ctx: AiContext): AiReply {
        val top = thisMonthExpenses(ctx).maxByOrNull { it.amount }
            ?: return AiReply("Nothing yet.", "No spending is recorded this month.")
        return AiReply("Your biggest expense this month", "${Money.inr(top.amount)} to ${top.recipient} on ${top.date} (${top.category}).")
    }

    private fun spending(q: String, ctx: AiContext, locale: Locale): AiReply {
        val today = ctx.today
        val expenses = ctx.transactions.filter { !it.isIncome }.mapNotNull { t -> HomeSummary.parseDate(t.date)?.let { it to t } }
        val (label, picked) = when {
            Regex("\\byesterday").containsMatchIn(q) -> "yesterday" to expenses.filter { it.first == today.minusDays(1) }
            Regex("\\btoday").containsMatchIn(q) -> "today" to expenses.filter { it.first == today }
            Regex("\\b(this )?week").containsMatchIn(q) -> "in the last 7 days" to expenses.filter { !it.first.isAfter(today) && it.first.isAfter(today.minusDays(7)) }
            q.contains("last month") -> "last month" to expenses.filter { YearMonth.from(it.first) == YearMonth.from(today).minusMonths(1) }
            else -> "this month" to expenses.filter { YearMonth.from(it.first) == YearMonth.from(today) }
        }
        val categories = picked.map { it.second.category }.distinct()
        val category = categories.firstOrNull { c -> categoryWords(c).any { q.contains(it) } }
        val rows = if (category == null) picked else picked.filter { it.second.category == category }
        val what = category ?: "in total"
        if (rows.isEmpty()) return AiReply("Nothing recorded.", "I found no spending ${if (category != null) "on $category " else ""}$label.")
        val total = rows.sumOf { it.second.amount }
        val extra = if (category == null) {
            val top = rows.groupBy { it.second.category }.maxBy { (_, l) -> l.sumOf { it.second.amount } }
            " Most of it went on ${top.key}."
        } else ""
        return AiReply("You spent ${Money.inr(total)} ${if (category != null) "on $what " else ""}$label.",
            "That is ${rows.size} ${if (rows.size == 1) "payment" else "payments"}.$extra")
    }

    private fun income(ctx: AiContext): AiReply {
        if (ctx.income <= 0) return AiReply("No income set.", "Add your salary in Settings so I can work out what you keep.")
        val kept = ctx.income - ctx.outflow
        return AiReply("This month you have kept ${Money.inr(kept)} so far.",
            "Income ${Money.inr(ctx.income)}, spending and unpaid EMIs ${Money.inr(ctx.outflow)}" +
                if (kept > 0) " — that is ${(kept / ctx.income * 100).roundToInt()}% of your income." else ".", link = AiLink.Health)
    }

    private fun help(ctx: AiContext, notUnderstood: Boolean = false) = AiReply(
        if (notUnderstood) "I didn’t catch that." else "Here’s what I can do.",
        "I answer from your own numbers, offline. Try: “Can I afford ₹20,000?”, “How much did I spend on food?”, " +
            "“Where can I save?”, “Explain my score”, “Am I on budget?”, “What’s my biggest expense?” or “What bills are due?”")

    // ------------------------------------------------------------- helpers

    private fun thisMonthExpenses(ctx: AiContext): List<Transaction> {
        val month = YearMonth.from(ctx.today)
        return ctx.transactions.filter { t ->
            !t.isIncome && HomeSummary.parseDate(t.date)?.let { YearMonth.from(it) == month } == true
        }
    }

    /** Words of a category name that can appear in a question: "Food & Dining" -> food, dining. */
    private fun categoryWords(category: String): List<String> =
        category.lowercase().split(Regex("[^a-z]+")).filter { it.length >= 4 }

    private fun roundDown(value: Double): Double = Math.floor(value / 100) * 100

    /**
     * The first amount in the text: "₹20,000", "rs 5000", "20k", "1.5 lakh", "2 crore". A bare small
     * number such as "3" is ignored, since it is more likely a count or a date than money.
     */
    fun parseAmount(q: String): Double? {
        val match = Regex("""(₹|rs\.?|inr)?\s*(\d[\d,]*(?:\.\d+)?)\s*(k|thousand|lakhs?|lacs?|lac|crores?|cr)?\b""")
            .findAll(q.lowercase()).firstOrNull { m ->
                val digits = m.groupValues[2].replace(",", "")
                m.groupValues[1].isNotEmpty() || m.groupValues[3].isNotEmpty() || digits.substringBefore('.').length >= 3
            } ?: return null
        val base = match.groupValues[2].replace(",", "").toDoubleOrNull() ?: return null
        val multiplier = when (match.groupValues[3]) {
            "k", "thousand" -> 1e3
            "lakh", "lakhs", "lac", "lacs" -> 1e5
            "crore", "crores", "cr" -> 1e7
            else -> 1.0
        }
        return (base * multiplier).takeIf { it > 0 }
    }
}
