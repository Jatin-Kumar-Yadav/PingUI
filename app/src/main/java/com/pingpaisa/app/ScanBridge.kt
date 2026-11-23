package com.pingpaisa.app

import android.content.Context
import com.pingpaisa.app.data.ExpenseRepository
import com.pingpaisa.app.data.RecurringItem
import com.pingpaisa.app.data.Transaction
import com.pingpaisa.app.data.isIncome
import com.pingpaisa.app.finance.ExpenseHistoryProvider
import com.pingpaisa.app.finance.FinancialProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.roundToLong

/**
 * Step 48: connects the teammate's scanner to the app's own data, without changing his verdict logic.
 *  - [monthlyTotals] feeds his ExpenseHistoryProvider hook (real spending per month).
 *  - [prefill] fills his Financial profile form from what the app already knows.
 * Pure functions, unit-tested in ScanBridgeTest.
 */
internal object ScanBridge {

    /** Recurring types that are spending. SIPs are savings, as in the health score. */
    private val OUTFLOW_TYPES = setOf("EMI", "Other")

    /**
     * Money that left the account in each complete month (the current month is not complete),
     * oldest first: expense transactions plus EMI / "Other" recurring payments that did not show
     * up as a transaction that month (auto-debits often send no SMS). Starts at the month of the
     * first transaction, so months before the app was used are not counted as ₹0. At most [months].
     */
    fun monthlyTotals(all: List<Transaction>, recurring: List<RecurringItem>, today: LocalDate, months: Int = 6): List<Double> {
        val expenses = all.filter { !it.isIncome }.mapNotNull { t -> HomeSummary.parseDate(t.date)?.let { YearMonth.from(it) to t } }
        val first = all.mapNotNull { HomeSummary.parseDate(it.date) }.minOrNull()?.let(YearMonth::from) ?: return emptyList()
        val current = YearMonth.from(today)
        val list = generateSequence(first) { it.plusMonths(1) }.takeWhile { it < current }.toList().takeLast(months)
        return list.map { month ->
            val inMonth = expenses.filter { it.first == month }.map { it.second }
            val paidNames = inMonth.map { it.recipient.trim().lowercase() }.toSet()
            inMonth.sumOf { it.amount } + recurring
                .filter { it.type in OUTFLOW_TYPES && it.name.trim().lowercase() !in paidNames }
                .sumOf { it.amount }
        }
    }

    /**
     * A starting point for the scanner's Financial profile, used only when the user has never saved
     * one. Savings, emergency reserve and the risk setting are left for the user (the app can't know them).
     *  - income: the salary from sign-up
     *  - fixed expenses: EMI and "Other" recurring payments
     *  - variable expenses: everyday spending (payments to a recurring item's name left out), averaged
     *    over complete months; with no complete month yet, this month's pace for a full month
     *  - monthly savings target: SIPs
     */
    fun prefill(salary: Double, recurring: List<RecurringItem>, all: List<Transaction>, today: LocalDate): FinancialProfile {
        val recurringNames = recurring.map { it.name.trim().lowercase() }.toSet()
        val everyday = all.filter { !it.isIncome && it.recipient.trim().lowercase() !in recurringNames }
            .mapNotNull { t -> HomeSummary.parseDate(t.date)?.let { it to t.amount } }
        val current = YearMonth.from(today)
        val first = everyday.minOfOrNull { it.first }?.let(YearMonth::from)
        val complete = if (first == null) emptyList()
            else generateSequence(first) { it.plusMonths(1) }.takeWhile { it < current }.toList().takeLast(6)
        val variable = when {
            complete.isNotEmpty() -> complete.map { m -> everyday.filter { YearMonth.from(it.first) == m }.sumOf { it.second } }.average()
            else -> {
                val soFar = everyday.filter { YearMonth.from(it.first) == current && !it.first.isAfter(today) }.sumOf { it.second }
                soFar / today.dayOfMonth * current.lengthOfMonth()
            }
        }
        return FinancialProfile(
            netIncome = whole(salary),
            fixedExpenses = whole(recurring.filter { it.type in OUTFLOW_TYPES }.sumOf { it.amount }),
            variableExpenses = whole(variable),
            monthlySavingsTarget = whole(recurring.filter { it.type == "SIP" }.sumOf { it.amount })
        )
    }

    private fun whole(v: Double): Double = if (v.isFinite() && v > 0) v.roundToLong().toDouble() else 0.0
}

/**
 * The real history behind the scanner's ExpenseHistoryProvider hook (replaces NoExpenseHistory).
 * His calculator asks synchronously, so the totals are kept up to date in the background from the
 * database and the latest list is returned. Empty until the database has been read once.
 */
class TrackerExpenseHistory(context: Context) : ExpenseHistoryProvider {
    @Volatile private var latest: List<Double> = emptyList()

    init {
        val repo = ExpenseRepository.get(context)
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            combine(repo.observeTransactions(), repo.observeRecurringItems()) { all, recurring ->
                ScanBridge.monthlyTotals(all, recurring, LocalDate.now())
            }.collect { latest = it }
        }
    }

    override fun lastCompleteMonthTotals(): List<Double> = latest

    internal companion object {
        /** Reads the database once (not the screens' cached state) and builds the profile pre-fill. */
        suspend fun prefill(context: Context): FinancialProfile {
            val repo = ExpenseRepository.get(context)
            val salary = repo.getProfile()?.monthlySalary ?: 0.0
            return ScanBridge.prefill(salary, repo.observeRecurringItems().first(), repo.observeTransactions().first(), LocalDate.now())
        }
    }
}
