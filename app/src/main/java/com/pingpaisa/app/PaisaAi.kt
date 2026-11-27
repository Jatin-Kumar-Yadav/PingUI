package com.pingpaisa.app

import com.pingpaisa.app.data.Budget
import com.pingpaisa.app.data.Categorizer
import com.pingpaisa.app.data.RecurringItem
import com.pingpaisa.app.data.SavingsGoal
import com.pingpaisa.app.data.Transaction
import com.pingpaisa.app.data.isIncome
import com.pingpaisa.app.healthscore.LoanQuote
import com.pingpaisa.app.healthscore.Money
import com.pingpaisa.app.healthscore.ScoreCalculator
import com.pingpaisa.app.finance.AffordabilityCalculator
import com.pingpaisa.app.finance.Candidate
import com.pingpaisa.app.healthscore.VerdictEngine.Verdict
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

/** A figure shown under an answer, such as "After purchase ₹12,320" with a Safe / Tight / Risky chip. */
internal data class AiMetric(val label: String, val value: String, val verdict: Verdict? = null)

internal enum class AiActionType { EXPENSE, INCOME, SET_BUDGET, CREATE_GOAL, UPDATE_GOAL }

/** A write is only executed after the user taps the confirmation button in the chat. */
internal data class AiAction(
    val id: String = UUID.randomUUID().toString(),
    val type: AiActionType,
    val amount: Double,
    val description: String,
    val category: String?,
    val date: String,
    val targetId: Int? = null
)

/** Where an answer can send the user. */
internal enum class AiLink(val label: String, val screen: Screen) {
    Health("Open financial health", Screen.Health),
    Forecast("Open forecast", Screen.Forecast),
    Budgets("Open budgets", Screen.Budgets),
    Recurring("Open recurring payments", Screen.Recurring),
    Loan("Open loan simulator", Screen.Loan),
    CashFlow("Open cash flow", Screen.Flow),
    Scanner("Open price scanner", Screen.Scan),
    Settings("Open settings", Screen.Settings),
    Profile("Open financial profile", Screen.FinancialProfile),
    Transactions("View transactions", Screen.Transactions),
    MoneyMap("Open your money map", Screen.Heatmap),
    AddExpense("Add an expense", Screen.Entry)
}

/** One answer from Paisa AI. [title] is the bold first line; [metric] and [link] are optional. */
internal data class AiReply(val title: String?, val text: String, val metric: AiMetric? = null, val link: AiLink? = null, val action: AiAction? = null, val transactionId: Int? = null)

/** One bubble in the conversation: a question ([user]) or an answer ([reply]). */
internal data class ChatMessage(val user: Boolean, val text: String, val reply: AiReply?, val time: String)

/** What the real chat screen needs. Null on the screen means the design sample. */
internal data class ChatState(
    val greeting: String,
    val messages: List<ChatMessage>,
    val suggestions: List<String>,
    val confirmedActionIds: Set<String> = emptySet(),
    val onConfirmAction: (AiAction) -> Unit = {},
    val onOpenTransaction: (Int) -> Unit = {},
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
    val recurring: List<RecurringItem>,
    val loanBaseline: ScoreCalculator.RawFactors? = null,
    val loanQuotes: List<LoanQuote> = emptyList(),
    val monthlySalary: Double = 0.0,
    val scannerPrice: Double? = null,
    val scannerName: String? = null,
    val scannerDecision: String? = null,
    val scannerReason: String? = null,
    val scannerMetric: AiMetric? = null
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
    fun suggestions(monthName: String) = listOf("Budget status", "Show my heatmap", "$monthName forecast", "How much did I save?")

    fun greeting(name: String, monthName: String) =
        "Hi ${name.trim().substringBefore(' ').ifBlank { "there" }}! I’ve looked at your $monthName spending. What would you like to know?"

    fun answer(question: String, ctx: AiContext, previousQuestion: String? = null, locale: Locale = Locale.getDefault()): AiReply {
        if (!BuildConfig.PAISA_AI_ENHANCED) return answerBasic(question, ctx, locale)
        var q = question.lowercase(locale).trim()
        fun has(vararg keys: String) = keys.any { Regex("\\b" + Regex.escape(it)).containsMatchIn(q) }
        val amount = parseAmount(q)

        if (hasUnresolvedExplicitRange(q, ctx.today)) return AiReply("Which dates should I use?", "Give both dates with a year, for example “between 15/01/2026 and 22/05/2026”. I won’t guess a year for an exact range.", link = AiLink.Transactions)

        if (previousQuestion != null && q.startsWith("what about")) {
            val prior = previousQuestion.lowercase(locale)
            val category = ctx.budgets.map { it.category }.firstOrNull { c -> categoryWords(c).any { q.contains(it) } }
                ?: findCategory(q, ctx.transactions, locale)
            if (category != null && prior.contains("budget")) q = "budget for ${category.lowercase(locale)}"
        }

        // Resolve short follow-ups such as “what about last month?” using the last user turn.
        if (previousQuestion != null && isPeriodOnlyFollowUp(q)) {
            val prior = previousQuestion.lowercase(locale)
            val category = ctx.transactions.map { it.category }.distinct()
                .firstOrNull { c -> categoryWords(c).any { Regex("\\b${Regex.escape(it)}\\b").containsMatchIn(prior) } }
            val merchant = ctx.transactions.map { it.recipient }.distinct()
                .firstOrNull { it.isNotBlank() && prior.contains(it.lowercase(locale)) }
            q = when {
                category != null -> "how much did i spend on ${category.lowercase(locale)} $q"
                merchant != null -> "how much did i spend at ${merchant.lowercase(locale)} $q"
                else -> q
            }
            if (q.contains("month before that") || q.contains("previous month")) q = q.replace("month before that", "month before last").replace("previous month", "last month")
        }

        navigationReply(q)?.let { return it }

        return when {
            (q.contains("can i afford") || q.contains("should i buy") || q.contains("afford this") || q == "can i afford it" || q == "should i buy it") && ctx.scannerPrice != null -> scannerAffordability(ctx)
            isBudgetActionRequest(q) -> budgetAction(q, amount, ctx, locale)
            isGoalActionRequest(q) -> goalAction(q, amount, ctx)
            isActionRequest(q) -> createAction(q, amount, ctx, locale)
            has("compare", "compared", "versus", "vs", "more than last", "less than last") -> compareSpending(q, ctx, locale)
            has("loan", "emi for", "loan emi", "interest on loan") -> loan(q, amount, ctx)
            amount != null && has("afford", "buy", "purchase", "spend", "get ", "take", "worth", "can i", "should i") -> affordability(amount, ctx)
            (has("predict", "forecast", "expected", "estimate", "will spend", "going to spend") ||
                q.contains("next week") || q.contains("next month") || q.contains("tomorrow") || Regex("next \\d+ days").containsMatchIn(q)) &&
                has("food", "dining", "grocery", "groceries", "shopping", "travel", "transport", "fuel", "entertainment", "eat out", "eating out") -> categoryForecast(q, ctx, locale)
            has("increased the most", "increased most", "increasing the most", "category increased", "spending trend") -> categoryTrend(ctx, locale)
            has("too much", "overspending", "over spending", "spending too much", "sustainable") -> categoryConcern(q, ctx, locale)
            has("where should i cut", "cut expenses", "reduce spending", "reduce dining", "reduce shopping", "reduce food", "if i reduce", "by reducing", "category can i reduce") -> savingPlan(q, ctx, locale)
            has("where does", "where is", "money map", "money goes", "biggest portion", "largest portion", "top category") -> moneyMap(ctx)
            has("heatmap", "heat map", "spending map", "spending pattern") -> heatmap(ctx)
            has("where can i save", "how to save", "how can i save", "save more", "save money", "reduce", "cut down", "cut ") -> savingAdvice(ctx)
            has("score", "health", "risk") -> score(ctx)
            has("forecast", "projected", "end of the month", "end of month", "month end", "month-end", "on track") -> forecast(ctx)
            has("budget", "overspending", "over budget", "budget left", "running low") -> budget(q, ctx, locale)
            has("goal", "goals", "save for") -> goals(q, ctx)
            has("emi", "recurring", "subscription", "bills", "upcoming", "due", "rent") -> recurring(ctx)
            has("biggest", "largest", "highest", "most expensive", "top expense") -> biggest(q, ctx, locale)
            has("transaction", "transactions", "merchant", "at amazon", "at flipkart", "at myntra", "show me", "find my", "recent") && (amount != null || has("transaction", "transactions", "merchant", "at amazon", "at flipkart", "at myntra", "show me", "find my", "recent")) -> searchTransactions(q, ctx, locale)
            has("income", "salary", "earn", "saved", "savings", "left", "cash flow", "cashflow", "net") -> income(q, ctx)
            has("spent", "spend", "spending", "expense", "paid", "cost", "how much") ||
                (q.startsWith("what about") && findCategory(q, ctx.transactions, locale) != null) -> spending(q, ctx, locale)
            has("hi", "hello", "hey", "help", "what can you") -> help(ctx)
            else -> help(ctx, notUnderstood = true)
        }
    }

    /** Small offline fallback retained behind the build flag for a safe rollback. */
    private fun answerBasic(question: String, ctx: AiContext, locale: Locale): AiReply {
        val q = question.lowercase(locale).trim()
        fun has(vararg words: String) = words.any { Regex("\\b${Regex.escape(it)}\\b").containsMatchIn(q) }
        val amount = parseAmount(q)
        return when {
            amount != null && has("afford", "buy", "purchase", "spend", "can i", "should i") -> affordability(amount, ctx)
            has("save", "reduce", "cut") -> savingAdvice(ctx)
            has("score", "health", "risk") -> score(ctx)
            has("forecast", "projected", "month end") -> forecast(ctx)
            has("budget") -> budget(q, ctx, locale)
            has("goal") -> goals(q, ctx)
            has("emi", "recurring", "subscription", "bills", "due") -> recurring(ctx)
            has("biggest", "largest", "highest") -> biggest(q, ctx, locale)
            has("spent", "spend", "spending", "expense", "paid", "how much") -> spending(q, ctx, locale)
            has("income", "salary", "earn", "saved", "savings", "left") -> income(q, ctx)
            else -> help(ctx, notUnderstood = true)
        }
    }

    private fun navigationReply(q: String): AiReply? {
        if (!Regex("\\b(open|navigate|go to|take me to)\\b").containsMatchIn(q)) return null
        if (q.contains("ai") || q.contains("chat")) return AiReply("You’re already in PaisaAI.", "Ask me a question or use one of the suggested prompts.")
        val link = when {
            q.contains("scanner") || q.contains("scan price") -> AiLink.Scanner
            q.contains("transaction") -> AiLink.Transactions
            q.contains("budget") || q.contains("goal") -> AiLink.Budgets
            q.contains("cash flow") || q.contains("cashflow") -> AiLink.CashFlow
            q.contains("recurring") || q.contains("subscription") -> AiLink.Recurring
            q.contains("loan") -> AiLink.Loan
            q.contains("forecast") -> AiLink.Forecast
            q.contains("heatmap") || q.contains("heat map") || q.contains("money map") -> AiLink.MoneyMap
            q.contains("health") || q.contains("score") -> AiLink.Health
            q.contains("setting") -> AiLink.Settings
            q.contains("profile") -> AiLink.Profile
            q.contains("entry") || (q.contains("expense") && (q.contains("add") || q.contains("new"))) -> AiLink.AddExpense
            q.contains("expense") -> AiLink.Transactions
            else -> null
        } ?: return null
        return AiReply(link.label.removePrefix("Open ") + ".", "I’ll take you to the existing ${link.label.removePrefix("Open ").lowercase()} screen.", link = link)
    }

    private fun loan(q: String, requested: Double?, ctx: AiContext): AiReply {
        val baseline = ctx.loanBaseline
            ?: return AiReply("Loan numbers are still loading.", "Open the Loan Simulator to see your current estimate.", link = AiLink.Loan)
        if (requested == null) return AiReply("What loan amount should I check?", "I can estimate the EMI and interest using the Loan Simulator’s saved bank quotes.", link = AiLink.Loan)
        val term = Regex("\\b(\\d{1,2})\\s*(months?|mos?)\\b").find(q)?.groupValues?.get(1)?.toIntOrNull()
            ?: Regex("\\b(\\d{1,2})\\s*(years?|yrs?)\\b").find(q)?.let { it.groupValues[1].toIntOrNull()?.times(12) }
            ?: 36
        val tenure = term.coerceIn(12, LoanUi.MAX_TENURE_YEARS * 12)
        val ui = LoanUi.from(baseline, requested.toFloat(), tenure, ctx.loanQuotes)
        if (!ui.hasIncome) return AiReply("Add your monthly income first.", "The Loan Simulator needs income to estimate eligibility and affordability.", link = AiLink.Loan)
        if (!ui.canBorrow) return AiReply("There may not be room for another loan right now.", ui.limitNote, link = AiLink.Loan)
        val amountText = Money.inr(requested)
        val clamped = ui.amount.toDouble() + 0.01 < requested
        val text = buildString {
            append(if (clamped) "The simulator’s estimate supports ${ui.amountText}, below the requested $amountText. " else "")
            append("At ${ui.rateText} for ${ui.tenureMonths} months, the estimated EMI is ${ui.emiText}. ")
            append(ui.planText).append(' ').append(ui.incomeShare)
            if (ui.verdictReasons.isNotEmpty()) append(" ").append(ui.verdictReasons.joinToString(" "))
        }
        return AiReply(ui.verdictTitle, text, link = AiLink.Loan)
    }

    private fun isBudgetActionRequest(q: String) =
        Regex("\\b(set|create|change|update|increase|reduce|lower)\\b.*\\b(budget|limit)\\b|\\b(budget|limit)\\b.*\\b(to|at)\\b").containsMatchIn(q)

    private fun isGoalActionRequest(q: String) =
        Regex("\\b(create|add|start)\\b.*\\b(goal|target)\\b").containsMatchIn(q)

    private fun budgetAction(q: String, amount: Double?, ctx: AiContext, locale: Locale): AiReply {
        if (amount == null) return AiReply("What monthly limit should I use?", "Tell me the category and amount. I’ll show the current limit before changing it.")
        val category = findCategory(q, ctx.transactions, locale)
            ?: ctx.budgets.map { it.category }.firstOrNull { c -> categoryWords(c).any { q.contains(it) } }
            ?: return AiReply("Which category is this for?", "Name a category such as food, shopping or transport, and I’ll prepare the budget preview.")
        val current = ctx.budgets.firstOrNull { it.category.equals(category, true) }
        val isDelta = q.contains(" by ") && Regex("\\b(increase|raise|reduce|lower)\\b").containsMatchIn(q)
        if (isDelta && current == null) return AiReply("There is no $category budget to adjust yet.", "Tell me the monthly limit you want to set, and I’ll prepare a new budget preview.", link = AiLink.Budgets)
        val proposed = when {
            !isDelta -> amount
            Regex("\\b(reduce|lower)\\b").containsMatchIn(q) -> (current!!.monthlyLimit - amount).coerceAtLeast(0.0)
            else -> current!!.monthlyLimit + amount
        }
        val action = AiAction(type = AiActionType.SET_BUDGET, amount = proposed, description = category, category = category, date = "")
        val currentText = current?.let { "Current monthly limit: ${Money.inr(it.monthlyLimit)}. " } ?: "There is no budget for $category yet. "
        val changeText = if (isDelta) "Adjustment: ${Money.inr(amount)}. Proposed monthly limit: ${Money.inr(proposed)}. " else "New monthly limit: ${Money.inr(proposed)}. "
        return AiReply("Review $category budget", "$currentText$changeText Tap confirm to save.", action = action, link = AiLink.Budgets)
    }

    private fun goalAction(q: String, amount: Double?, ctx: AiContext): AiReply {
        if (amount == null) return AiReply("What is the goal amount?", "Tell me the target amount and what you’re saving for. I’ll show a preview before adding it.")
        val name = Regex("\\b(?:for|called|named)\\s+(.+)$").find(q)?.groupValues?.get(1)
            ?.replace(Regex("\\b(goal|target|please|today)\\b"), "")?.trim()?.trimEnd('.', ',', '?', '!')
            ?.take(60).orEmpty()
        if (name.isBlank()) return AiReply("What are you saving for?", "Name the goal, such as a trip or a new phone, and I’ll prepare it.")
        val existing = ctx.goals.firstOrNull { it.name.equals(name, true) }
        val action = AiAction(
            type = if (existing == null) AiActionType.CREATE_GOAL else AiActionType.UPDATE_GOAL,
            amount = amount, description = existing?.name ?: name, category = null, date = "", targetId = existing?.id
        )
        val preview = if (existing == null) "Create “$name” with a target of ${Money.inr(amount)}?"
            else "“${existing.name}” currently targets ${Money.inr(existing.targetAmount)} with ${Money.inr(existing.savedAmount)} saved. Change its target to ${Money.inr(amount)}?"
        return AiReply("Review savings goal", "$preview No deadline is stored for savings goals yet. Tap confirm to save.", action = action, link = AiLink.Budgets)
    }

    private fun isActionRequest(q: String): Boolean =
        Regex("^(please )?(add|record|log)\\b").containsMatchIn(q) ||
            Regex("\\b(add|record|log) (an? )?(expense|income|transaction|purchase)\\b").containsMatchIn(q) ||
            Regex("\\bi (spent|paid|received|earned)\\b").containsMatchIn(q)

    private fun createAction(q: String, amount: Double?, ctx: AiContext, locale: Locale): AiReply {
        if (amount == null) return AiReply("What amount should I record?", "Send the amount and what it was for. I’ll show a preview before saving anything.")
        val isIncome = Regex("\\b(income|received|earned|salary|refund)\\b").containsMatchIn(q)
        val type = if (isIncome) AiActionType.INCOME else AiActionType.EXPENSE
        val description = extractDescription(q)
        if (description.isBlank()) return AiReply("What was this ${if (isIncome) "income" else "purchase"} for?", "Tell me the merchant or a short description, and I’ll prepare a preview.")
        val category = if (isIncome) null else findCategory(q, ctx.transactions, locale)
        val date = when {
            Regex("\\byesterday\\b").containsMatchIn(q) -> ctx.today.minusDays(1)
            else -> ctx.today
        }.format(DateTimeFormatter.ofPattern("dd/MM/yy", locale))
        val action = AiAction(type = type, amount = amount, description = description, category = category, date = date)
        val kind = if (isIncome) "income" else "expense"
        val categoryText = category?.let { "\nCategory: $it" }.orEmpty()
        val possibleDuplicate = ctx.transactions.firstOrNull { t ->
            t.amount == amount && t.date == date && t.isIncome == isIncome &&
                (t.recipient.contains(description, true) || description.contains(t.recipient, true))
        }
        val duplicateNote = possibleDuplicate?.let { "\nPossible duplicate: ${it.recipient} already has a ${Money.inr(it.amount)} transaction on $date." }.orEmpty()
        val title = if (possibleDuplicate == null) "Review this $kind" else "Check for a duplicate"
        return AiReply(title, "${Money.inr(amount)} — $description$categoryText\nDate: $date$duplicateNote\nTap confirm to save it to your transactions.", action = action)
    }

    private fun extractDescription(q: String): String {
        val amountText = Regex("(₹|rs\\.?|inr)?\\s*\\d[\\d,]*(?:\\.\\d+)?\\s*(k|thousand|lakhs?|lacs?|lac|crores?|cr)?", RegexOption.IGNORE_CASE)
        val cleaned = amountText.replace(q, " ")
            .replace(Regex("\\b(add|record|log|expense|income|of|i|spent|paid|received|earned|today|yesterday|please|for|at|from|on|a|an|the)\\b", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("\\s+"), " ").trim(' ', '.', ',', '!', '?')
        return cleaned.take(60).ifBlank { "" }
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

    private fun categoryAverages(ctx: AiContext): Map<String, Double> {
        val end = YearMonth.from(ctx.today).minusMonths(1)
        val months = (0..2).map { end.minusMonths(it.toLong()) }
        val rows = ctx.transactions.mapNotNull { t -> HomeSummary.parseDate(t.date)?.let { it to t } }
            .filter { (date, t) -> !t.isIncome && YearMonth.from(date) in months }
        return rows.groupBy { it.second.category }.mapValues { (_, tx) ->
            tx.sumOf { it.second.amount } / months.size
        }
    }

    private fun categoryTrend(ctx: AiContext, locale: Locale): AiReply {
        val current = YearMonth.from(ctx.today).minusMonths(1)
        val previous = current.minusMonths(1)
        val rows = ctx.transactions.mapNotNull { t -> HomeSummary.parseDate(t.date)?.let { it to t } }
            .filter { (_, t) -> !t.isIncome }
        val totals = rows.groupBy({ YearMonth.from(it.first) }, { it.second })
        val currentBy = totals[current].orEmpty().groupBy { it.category }.mapValues { (_, tx) -> tx.sumOf { it.amount } }
        val previousBy = totals[previous].orEmpty().groupBy { it.category }.mapValues { (_, tx) -> tx.sumOf { it.amount } }
        val increased = (currentBy.keys + previousBy.keys).distinct().map { c -> c to ((currentBy[c] ?: 0.0) - (previousBy[c] ?: 0.0)) }
            .filter { it.second > 0 }.sortedByDescending { it.second }.take(3)
        if (increased.isEmpty()) return AiReply("No category rose between those months.", "I found no increase in recorded spending from ${previous.month.getDisplayName(TextStyle.FULL, locale)} to ${current.month.getDisplayName(TextStyle.FULL, locale)}.", link = AiLink.MoneyMap)
        val text = increased.joinToString("; ") { (category, delta) ->
            "$category rose by ${Money.inr(delta)} (${Money.inr(previousBy[category] ?: 0.0)} to ${Money.inr(currentBy[category] ?: 0.0)})"
        }
        return AiReply("Largest recent increase", text + ". Compared with the two most recent complete calendar months.", link = AiLink.MoneyMap)
    }

    private fun categoryConcern(q: String, ctx: AiContext, locale: Locale): AiReply {
        val category = findCategory(q, ctx.transactions, locale)
            ?: return moneyMap(ctx)
        val thisMonth = thisMonthExpenses(ctx).filter { it.category.equals(category, true) }.sumOf { it.amount }
        val budget = ctx.budgets.firstOrNull { it.category.equals(category, true) }
        val average = categoryAverages(ctx)[category]
        val detail = buildString {
            append("This month: ${Money.inr(thisMonth)}.")
            if (budget != null) {
                val left = budget.monthlyLimit - thisMonth
                append(" Budget: ${Money.inr(budget.monthlyLimit)}; ${if (left >= 0) "${Money.inr(left)} remains." else "over by ${Money.inr(-left)}."}")
            }
            if (average != null) append(" Average across the last three complete months: ${Money.inr(average)} per month.")
        }
        val title = when {
            budget != null && thisMonth > budget.monthlyLimit -> "$category is over its budget."
            average != null && thisMonth > average * 1.2 -> "$category is running above its recent average."
            else -> "I don’t see a clear overspend signal for $category."
        }
        return AiReply(title, detail, link = if (budget != null) AiLink.Budgets else AiLink.MoneyMap)
    }

    private fun savingPlan(q: String, ctx: AiContext, locale: Locale): AiReply {
        val averages = categoryAverages(ctx).filterKeys { !it.contains("rent", true) && !it.contains("utility", true) && !it.contains("medical", true) }
        if (averages.isEmpty()) return AiReply("I need more spending history to suggest cuts.", "There isn’t enough categorized spending in the last three complete months yet.", link = AiLink.Transactions)
        val category = findCategory(q, ctx.transactions, locale)
        val percent = Regex("\\b(\\d{1,2})\\s*%").find(q)?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(1, 90)
        val target = parseAmount(q)
        if (category != null && percent != null) {
            val baseline = averages[category] ?: return AiReply("Not enough recent $category history.", "I couldn’t estimate a monthly baseline for that category.", link = AiLink.Transactions)
            val saved = baseline * percent / 100.0
            return AiReply("A $percent% reduction in $category could save about ${Money.inr(saved)} per month.", "Recent baseline: ${Money.inr(baseline)} per month, averaged across the last three complete months.", link = AiLink.Budgets)
        }
        val ranked = averages.entries.sortedByDescending { it.value }.take(3)
        if (target != null) {
            val available = ranked.sumOf { it.value * 0.15 }
            val targetShare = if (available > 0) (target / available).coerceAtMost(1.0) else 1.0
            val lines = ranked.joinToString("; ") { (name, avg) -> "$name: ${Money.inr(avg * 0.15 * targetShare)} (15% of ${Money.inr(avg)} baseline, scaled to target)" }
            val gap = (target - available).coerceAtLeast(0.0)
            return AiReply("To target ${Money.inr(target)} per month, review these categories.", "$lines. These cuts total about ${Money.inr(available.coerceAtMost(target))} at the suggested 15% level." + if (gap > 0) " They leave a ${Money.inr(gap)} monthly gap, so the full target may require a larger change." else "", link = AiLink.MoneyMap)
        }
        val lines = ranked.joinToString("; ") { (name, avg) -> "$name: a 15% cut could free ${Money.inr(avg * 0.15)} per month" }
        return AiReply("Start with your highest recent spending categories.", lines + ". Estimates use the last three complete calendar months.", link = AiLink.MoneyMap)
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

    private fun budget(q: String, ctx: AiContext, locale: Locale): AiReply {
        if (ctx.budgets.isEmpty()) return AiReply("You have no budgets yet.", "Set a monthly limit for a category and I can warn you before you cross it.", link = AiLink.Budgets)
        val rows = BudgetsUi.from(ctx.budgets, ctx.transactions, ctx.today).rows
        val category = findCategory(q, ctx.transactions, locale)
        if (category != null) {
            val row = rows.firstOrNull { it.category.equals(category, true) }
                ?: return AiReply("No $category budget is set.", "You’ve spent ${Money.inr(thisMonthExpenses(ctx).filter { it.category.equals(category, true) }.sumOf { it.amount })} on $category this month. You can set a limit from Budgets.", link = AiLink.Budgets)
            val left = row.limit - row.spent
            val status = when {
                left < 0 -> "You are ${Money.inr(-left)} over the limit."
                row.percent >= 80 -> "You’ve used ${row.percent}% of it, so you’re getting close to the limit."
                else -> "You’ve used ${row.percent}% of it so far."
            }
            return AiReply("${Money.inr(left.coerceAtLeast(0.0))} left in your $category budget.", "Monthly limit ${Money.inr(row.limit)}; spent ${Money.inr(row.spent)}. $status", link = AiLink.Budgets)
        }
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

    private fun goals(q: String, ctx: AiContext): AiReply {
        val amount = parseAmount(q)
        if (amount != null && Regex("\\b(can i save|save for|afford to save)\\b").containsMatchIn(q)) {
            val monthlyRoom = (ctx.income - ctx.projectedSpend).coerceAtLeast(0.0)
            if (monthlyRoom <= 0) return AiReply("There may not be room this month.", "Your projected spending is at or above your income. Review the forecast before adding a savings target.", link = AiLink.Forecast)
            val months = kotlin.math.ceil(amount / monthlyRoom).toInt().coerceAtLeast(1)
            return AiReply("A ${Money.inr(amount)} target may take about $months ${if (months == 1) "month" else "months"}.", "That assumes you can put aside about ${Money.inr(monthlyRoom)} per month based on this month’s projected income and spending. This is an estimate, not a guarantee.", link = AiLink.Budgets)
        }
        val rows = GoalsUi.from(ctx.goals)
        if (rows.isEmpty()) return AiReply("No savings goal yet.", "Add one on the Budgets screen and I can track it.", link = AiLink.Budgets)
        if (amount == null && Regex("\\b(monthly|each month|per month|every month)\\b").containsMatchIn(q)) {
            val goal = ctx.goals.firstOrNull { q.contains(it.name, true) } ?: ctx.goals.singleOrNull()
            if (goal != null) return AiReply("Your goal has no deadline yet.", "${goal.name} has ${Money.inr((goal.targetAmount - goal.savedAmount).coerceAtLeast(0.0))} left. Tell me when you want to reach it and I can calculate a monthly contribution.", link = AiLink.Budgets)
        }
        if (amount != null && Regex("\\b(month|monthly|each month|per month)\\b").containsMatchIn(q)) {
            val goal = ctx.goals.firstOrNull { q.contains(it.name, true) } ?: ctx.goals.singleOrNull()
            if (goal != null) {
                val months = kotlin.math.ceil((goal.targetAmount - goal.savedAmount).coerceAtLeast(0.0) / amount).toInt()
                return AiReply("About $months ${if (months == 1) "month" else "months"} to reach ${goal.name}.", "At ${Money.inr(amount)} per month, you’d cover the remaining ${Money.inr((goal.targetAmount - goal.savedAmount).coerceAtLeast(0.0))}. This assumes regular contributions and no withdrawals.", link = AiLink.Budgets)
            }
        }
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

    private fun biggest(q: String, ctx: AiContext, locale: Locale): AiReply {
        val period = resolvePeriod(q, ctx.today)
        val category = findCategory(q, ctx.transactions, locale)
        val top = ctx.transactions.filter { !it.isIncome && (category == null || it.category.equals(category, true)) }
            .mapNotNull { t -> HomeSummary.parseDate(t.date)?.let { it to t } }
            .filter { period.contains(it.first) }
            .sortedByDescending { it.second.amount }.take(5)
        if (top.isEmpty()) return AiReply("Nothing recorded.", "No expenses were recorded ${if (category == null) "" else "for $category "}${period.label}.", link = AiLink.Transactions)
        val label = if (category == null) "expense" else "$category expense"
        return AiReply("Your largest $label${if (top.size > 1) "s" else ""} ${period.label}", top.joinToString("; ") { (_, t) ->
            "${Money.inr(t.amount)} to ${t.recipient} on ${t.date} (${t.category})"
        }, link = AiLink.Transactions, transactionId = top.singleOrNull()?.second?.id)
    }

    private fun spending(q: String, ctx: AiContext, locale: Locale): AiReply {
        val today = ctx.today
        val expenses = ctx.transactions.filter { !it.isIncome }.mapNotNull { t -> HomeSummary.parseDate(t.date)?.let { it to t } }
        val period = resolvePeriod(q, today)
        val picked = expenses.filter { period.contains(it.first) }
        val category = findCategory(q, ctx.transactions, locale)
        val merchant = findMerchant(q, ctx.transactions, locale) ?: if (category == null) merchantPhrase(q) else null
        val rows = picked.filter { (date, t) ->
            (category == null || t.category.equals(category, true)) &&
                (merchant == null || t.recipient.contains(merchant, true))
        }
        val what = category ?: "in total"
        if (rows.isEmpty()) return AiReply("Nothing recorded.", "I found no spending ${if (category != null) "on $category " else ""}${if (merchant != null) "at $merchant " else ""}${period.label}.", link = AiLink.Transactions)
        val total = rows.sumOf { it.second.amount }
        val extra = if (category == null) {
            val top = rows.groupBy { it.second.category }.maxBy { (_, l) -> l.sumOf { it.second.amount } }
            " Most of it went on ${top.key}."
        } else ""
        val singleId = rows.singleOrNull()?.second?.id
        return AiReply("You spent ${Money.inr(total)} ${if (category != null) "on $what " else ""}${if (merchant != null) "at $merchant " else ""}${period.label}.",
            "That is ${rows.size} ${if (rows.size == 1) "payment" else "payments"}.$extra", link = AiLink.Transactions, transactionId = singleId)
    }

    private data class Period(val label: String, val start: LocalDate, val end: LocalDate) {
        fun contains(date: LocalDate) = !date.isBefore(start) && !date.isAfter(end)
    }

    private fun resolvePeriod(q: String, today: LocalDate): Period {
        parseExplicitRange(q, today)?.let { return it }
        val month = YearMonth.from(today)
        val dayCount = durationCount(q, "days?")
        val weekCount = durationCount(q, "weeks?")
        return when {
            dayCount != null -> {
                val end = if (q.contains("previous ") || q.contains("past ")) today.minusDays(1) else today
                Period("the ${dayCount}-day period", end.minusDays((dayCount - 1).coerceAtLeast(0)), end)
            }
            weekCount != null -> Period("the last ${weekCount} weeks", today.minusDays(weekCount * 7 - 1), today)
            Regex("\\bday before yesterday\\b").containsMatchIn(q) -> Period("the day before yesterday", today.minusDays(2), today.minusDays(2))
            Regex("\\byesterday\\b").containsMatchIn(q) -> Period("yesterday", today.minusDays(1), today.minusDays(1))
            Regex("\\btoday\\b").containsMatchIn(q) -> Period("today", today, today)
            Regex("\\blast week\\b").containsMatchIn(q) -> {
                val start = today.with(java.time.DayOfWeek.MONDAY).minusWeeks(1); Period("last week", start, start.plusDays(6))
            }
            Regex("\\b(this week|past 7 days|last 7 days|coming week)\\b").containsMatchIn(q) -> Period("in the last 7 days", today.minusDays(6), today)
            Regex("\\bmonth before last|two months ago\\b").containsMatchIn(q) -> {
                val target = month.minusMonths(2); Period(target.month.getDisplayName(TextStyle.FULL, Locale.getDefault()), target.atDay(1), target.atEndOfMonth())
            }
            Regex("\\blast month\\b").containsMatchIn(q) -> {
                val target = month.minusMonths(1); Period("last month", target.atDay(1), target.atEndOfMonth())
            }
            Regex("\\bthis year\\b").containsMatchIn(q) -> Period("this year", today.withDayOfYear(1), today)
            Regex("\\blast year\\b").containsMatchIn(q) -> {
                val year = today.minusYears(1).year
                Period("last year", LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31))
            }
            else -> Period("this month", month.atDay(1), today)
        }
    }

    private fun parseExplicitRange(q: String, today: LocalDate): Period? {
        val numeric = Regex("\\b(?:between|from)\\s+(\\d{1,2})[./-](\\d{1,2})[./-](\\d{4})\\s+(?:and|to|until|through)\\s+(\\d{1,2})[./-](\\d{1,2})[./-](\\d{4})\\b", RegexOption.IGNORE_CASE).find(q)
        if (numeric != null) return runCatching {
            val start = LocalDate.of(numeric.groupValues[3].toInt(), numeric.groupValues[2].toInt(), numeric.groupValues[1].toInt())
            val end = LocalDate.of(numeric.groupValues[6].toInt(), numeric.groupValues[5].toInt(), numeric.groupValues[4].toInt())
            if (end.isBefore(start) || end.isAfter(today)) null else Period("from ${start.format(DateTimeFormatter.ofPattern("d MMM uuuu"))} to ${end.format(DateTimeFormatter.ofPattern("d MMM uuuu"))}", start, end)
        }.getOrNull()
        val named = Regex("\\b(?:between|from)\\s+(\\d{1,2})\\s+([a-z]+)\\s+(\\d{4})\\s+(?:and|to|until|through)\\s+(\\d{1,2})\\s+([a-z]+)\\s+(\\d{4})\\b", RegexOption.IGNORE_CASE).find(q)
        if (named != null) return runCatching {
            fun monthNumber(value: String): Int = java.time.Month.entries.first { it.getDisplayName(TextStyle.FULL, Locale.ENGLISH).lowercase().startsWith(value.lowercase()) || it.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).lowercase() == value.lowercase() }.value
            val start = LocalDate.of(named.groupValues[3].toInt(), monthNumber(named.groupValues[2]), named.groupValues[1].toInt())
            val end = LocalDate.of(named.groupValues[6].toInt(), monthNumber(named.groupValues[5]), named.groupValues[4].toInt())
            if (end.isBefore(start) || end.isAfter(today)) null else Period("from ${start.format(DateTimeFormatter.ofPattern("d MMM uuuu"))} to ${end.format(DateTimeFormatter.ofPattern("d MMM uuuu"))}", start, end)
        }.getOrNull()
        return null
    }

    private fun hasUnresolvedExplicitRange(q: String, today: LocalDate): Boolean {
        val explicit = Regex("\\b(?:between|from)\\s+.+?\\s+(?:and|to|until|through)\\s+.+", RegexOption.IGNORE_CASE).containsMatchIn(q)
        return explicit && parseExplicitRange(q, today) == null
    }

    private fun scannerAffordability(ctx: AiContext): AiReply {
        val price = ctx.scannerPrice ?: return AiReply("No scanned price selected.", "Open the scanner and select a product with a confirmed price.", link = AiLink.Scanner)
        val decision = ctx.scannerDecision ?: "needs review"
        val name = ctx.scannerName?.takeIf { it.isNotBlank() } ?: "Selected product"
        val explanation = ctx.scannerReason?.takeIf { it.isNotBlank() } ?: "Open the scanner to review the assessment."
        return AiReply("${Money.inr(price)} for $name", "The scanner classifies it as $decision. $explanation", link = AiLink.Scanner,
            metric = ctx.scannerMetric)
    }

    private fun durationCount(q: String, unit: String): Long? {
        val token = Regex("\\b(?:last|previous|past)\\s+(\\d+|one|two|three|four|five|six|seven|eight|nine|ten)\\s+$unit\\b")
            .find(q)?.groupValues?.get(1) ?: return null
        return token.toLongOrNull() ?: mapOf("one" to 1L, "two" to 2L, "three" to 3L, "four" to 4L, "five" to 5L,
            "six" to 6L, "seven" to 7L, "eight" to 8L, "nine" to 9L, "ten" to 10L)[token]
    }

    private fun isPeriodOnlyFollowUp(q: String): Boolean =
        Regex("^(what about |and |how about )?(last month|this month|the month before that|month before last|yesterday|today|last week|this week|the previous month)[?!. ]*$").containsMatchIn(q.trim())

    private fun findCategory(q: String, transactions: List<Transaction>, locale: Locale): String? =
        (transactions.map { it.category } + Categorizer.getAllCategories()).distinct().sortedByDescending { it.length }
            .firstOrNull { c ->
                val normalized = q.lowercase(locale)
                categoryWords(c).any { Regex("\\b${Regex.escape(it)}\\b").containsMatchIn(normalized) } ||
                    categoryAliases(c).any { Regex("\\b${Regex.escape(it)}\\b").containsMatchIn(normalized) }
            }

    private fun categoryAliases(category: String): List<String> {
        val name = category.lowercase()
        return when {
            "food" in name || "dining" in name -> listOf("groceries", "grocery", "restaurant", "restaurants", "eating out", "takeaway", "takeout", "meal")
            "transport" in name || "travel" in name -> listOf("fuel", "petrol", "diesel", "cab", "taxi", "uber", "ola", "commute")
            "shopping" in name -> listOf("clothes", "clothing", "fashion", "amazon", "flipkart", "myntra")
            "bill" in name || "utilities" in name -> listOf("electricity", "internet", "mobile recharge", "phone bill")
            "entertain" in name -> listOf("movies", "movie", "netflix", "music", "games")
            "health" in name || "medical" in name -> listOf("medicine", "doctor", "pharmacy", "hospital")
            else -> emptyList()
        }
    }

    private fun findMerchant(q: String, transactions: List<Transaction>, locale: Locale): String? =
        transactions.map { it.recipient }.distinct().filter { it.isNotBlank() }.sortedByDescending { it.length }
            .firstOrNull { q.lowercase(locale).contains(it.lowercase(locale)) }

    private fun merchantPhrase(q: String): String? {
        val match = Regex("\\b(?:at|from) ([a-z0-9][a-z0-9 &'._-]{1,35})", RegexOption.IGNORE_CASE).find(q) ?: return null
        return match.groupValues[1]
            .split(Regex("\\b(?:this month|last month|today|yesterday|last week|this week|transaction|transactions|please)\\b", RegexOption.IGNORE_CASE))
            .firstOrNull()?.trim()?.trimEnd('.', ',', '?', '!')?.takeIf { it.isNotBlank() }
    }

    private fun compareSpending(q: String, ctx: AiContext, locale: Locale): AiReply {
        val category = findCategory(q, ctx.transactions, locale)
        val all = ctx.transactions.filter { !it.isIncome && (category == null || it.category.equals(category, true)) }
            .mapNotNull { t -> HomeSummary.parseDate(t.date)?.let { it to t } }
        val currentMonth = YearMonth.from(ctx.today)
        val thisTotal = all.filter { YearMonth.from(it.first) == currentMonth }.sumOf { it.second.amount }
        val lastMonth = currentMonth.minusMonths(1)
        val lastTotal = all.filter { YearMonth.from(it.first) == lastMonth }.sumOf { it.second.amount }
        val delta = thisTotal - lastTotal
        val phrase = category?.let { " for $it" }.orEmpty()
        val detail = if (delta > 0) "That is ${Money.inr(delta)} more so far." else "That is ${Money.inr(-delta)} less so far."
        return AiReply("Spending$phrase: ${Money.inr(thisTotal)} this month vs ${Money.inr(lastTotal)} last month.", detail, link = AiLink.Transactions)
    }

    private fun searchTransactions(q: String, ctx: AiContext, locale: Locale): AiReply {
        val amount = parseAmount(q)
        val category = findCategory(q, ctx.transactions, locale)
        val merchant = findMerchant(q, ctx.transactions, locale) ?: if (category == null) merchantPhrase(q) else null
        val period = resolvePeriod(q, ctx.today)
        val wantsLatest = q.contains("last transaction") || q.contains("last payment") || q.contains("most recent")
        val filtered = ctx.transactions.mapNotNull { t -> HomeSummary.parseDate(t.date)?.let { it to t } }
            .filter { (date, t) -> (wantsLatest || period.contains(date)) && (category == null || t.category.equals(category, true)) &&
                (merchant == null || t.recipient.contains(merchant, true)) && (amount == null || kotlin.math.abs(t.amount - amount) < 0.01) }
            .sortedByDescending { it.first }
        if (filtered.isEmpty()) return AiReply("No matching transactions.", "I couldn't find a recorded transaction matching those details in ${period.label}.", link = AiLink.Transactions)
        val results = if (wantsLatest) filtered.take(1) else filtered.take(5)
        val lines = results.joinToString("; ") { (_, t) -> "${Money.inr(t.amount)} to ${t.recipient} (${t.category}, ${t.date})" }
        val openId = results.singleOrNull()?.second?.id
        return AiReply(if (wantsLatest) "Your most recent matching transaction:" else "${filtered.size} matching ${if (filtered.size == 1) "transaction" else "transactions"}.", lines, link = AiLink.Transactions, transactionId = openId)
    }

    private fun categoryForecast(q: String, ctx: AiContext, locale: Locale): AiReply {
        val category = findCategory(q, ctx.transactions, locale)
            ?: return AiReply("I need more history for that category.", "Once you have transactions categorized there, I can estimate future spending.", link = AiLink.Transactions)
        val days = when {
            q.contains("tomorrow") -> 1
            q.contains("next week") || q.contains("seven days") || q.contains("7 days") -> 7
            q.contains("next month") -> 30
            Regex("next \\d+ days").containsMatchIn(q) -> Regex("next (\\d+) days").find(q)?.groupValues?.get(1)?.toIntOrNull() ?: 7
            else -> 7
        }
        val since = ctx.today.minusDays(89)
        val history = ctx.transactions.filter { !it.isIncome && it.category.equals(category, true) }
            .mapNotNull { t -> HomeSummary.parseDate(t.date)?.let { it to t } }
            .filter { (date, _) -> !date.isBefore(since) && !date.isAfter(ctx.today) }
        val activeDays = history.map { it.first }.distinct().size
        if (history.isEmpty() || activeDays < 2) return AiReply("Not enough history yet.", "I need at least a few recorded $category purchases before making a useful estimate.", link = AiLink.Transactions)
        val dailyAverage = history.sumOf { it.second.amount } / 90.0
        val estimate = dailyAverage * days
        val span = when (days) { 1 -> "tomorrow"; 7 -> "next 7 days"; 30 -> "next 30 days"; else -> "next $days days" }
        return AiReply("Estimated $category spending: ${Money.inr(estimate)} over the $span.", "This is a rough estimate from ${Money.inr(history.sumOf { it.second.amount })} recorded in the last 90 days. Actual spending may differ.", link = AiLink.Forecast)
    }

    private fun moneyMap(ctx: AiContext): AiReply {
        val rows = thisMonthExpenses(ctx)
        if (rows.isEmpty()) return AiReply("No spending map yet.", "There are no expenses recorded this month to compare.", link = AiLink.MoneyMap)
        val total = rows.sumOf { it.amount }
        val top = rows.groupBy { it.category }.mapValues { (_, tx) -> tx.sumOf { it.amount } }
            .entries.sortedByDescending { it.value }.take(4)
        return AiReply("Most of your money goes to ${top.first().key}.", top.joinToString("; ") { "${it.key}: ${Money.inr(it.value)} (${(it.value / total * 100).roundToInt()}%)" } + ".", link = AiLink.MoneyMap)
    }

    private fun heatmap(ctx: AiContext): AiReply {
        val month = HeatmapUi.from(ctx.transactions, ctx.today).months.last()
        if (month.activeDays == "0 active days") return AiReply("No spending heatmap yet.", "There are no expenses recorded for ${month.tab}.", link = AiLink.MoneyMap)
        val highest = month.cells.firstOrNull { it.title == "Highest spend day" }
        val highText = highest?.let { " Highest spending day: ${it.label} (${it.amount})." }.orEmpty()
        return AiReply("${month.tab} heatmap: ${month.total} across ${month.activeDays}.", "${month.insight}$highText Open the map to explore each day.", link = AiLink.MoneyMap)
    }

    private fun income(q: String, ctx: AiContext): AiReply {
        val period = resolvePeriod(q, ctx.today)
        val currentMonth = YearMonth.from(ctx.today)
        val isCurrentMonth = period.start == currentMonth.atDay(1) && period.end == ctx.today
        val dated = ctx.transactions.mapNotNull { t -> HomeSummary.parseDate(t.date)?.let { it to t } }
            .filter { period.contains(it.first) }
        val expenses = if (isCurrentMonth) ctx.outflow else dated.filter { !it.second.isIncome }.sumOf { it.second.amount }
        val recordedIncome = dated.filter { it.second.isIncome }.sumOf { it.second.amount }
        val firstRecordedMonth = ctx.transactions.mapNotNull { HomeSummary.parseDate(it.date)?.let { date -> YearMonth.from(date) } }.minOrNull()
        val salaryEstimate = if (isCurrentMonth) 0.0 else {
            generateSequence(YearMonth.from(period.start)) { it.plusMonths(1) }
                .takeWhile { it.atDay(1) <= period.end }
                .filter { it == currentMonth || (firstRecordedMonth != null && it >= firstRecordedMonth) }
                .sumOf { month ->
                    val begin = maxOf(month.atDay(1), period.start)
                    val finish = minOf(month.atEndOfMonth(), period.end)
                    val coveredDays = (finish.toEpochDay() - begin.toEpochDay() + 1).coerceAtLeast(0)
                    ctx.monthlySalary * coveredDays / month.lengthOfMonth()
                }
        }
        val income = if (isCurrentMonth) ctx.income else recordedIncome + salaryEstimate
        if (income <= 0 && expenses <= 0) return AiReply("No cash-flow data for ${period.label}.", "I couldn’t find recorded income or expenses in that period.", link = AiLink.CashFlow)
        val net = income - expenses
        val title = if (net >= 0) "Net cash flow ${period.label}: ${Money.inr(net)} positive." else "Net cash flow ${period.label}: ${Money.inr(-net)} negative."
        val percent = if (income > 0) " That is ${(net / income * 100).roundToInt()}% of income kept." else ""
        val salaryNote = if (!isCurrentMonth && salaryEstimate > 0) " Salary for this period is estimated from your current monthly profile." else ""
        val outflowNote = if (isCurrentMonth) " This month’s outflow includes unpaid recurring commitments, consistent with the Cash Flow screen." else ""
        val singleExpenseId = dated.filter { !it.second.isIncome }.singleOrNull()?.second?.id
        return AiReply(title, "Income ${Money.inr(income)}; outflow ${Money.inr(expenses)}.$percent$salaryNote$outflowNote", link = AiLink.CashFlow,
            transactionId = singleExpenseId)
    }

    private fun help(ctx: AiContext, notUnderstood: Boolean = false) = AiReply(
        if (notUnderstood) "I didn’t catch that." else "Here’s what I can do.",
        "I answer from your own numbers, offline. Try: “Can I afford ₹20,000?”, “How much did I spend on food?”, " +
            "“Where can I save?”, “Explain my score”, “Am I on budget?”, “Show my heatmap”, “What’s my biggest expense?” or “What bills are due?”")

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
