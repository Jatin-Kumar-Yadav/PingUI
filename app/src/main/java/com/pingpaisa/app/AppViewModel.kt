package com.pingpaisa.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pingpaisa.app.data.Budget
import com.pingpaisa.app.data.DetectedRecurring
import com.pingpaisa.app.data.ExpenseRepository
import com.pingpaisa.app.data.MerchantOverride
import com.pingpaisa.app.data.RecurringDetector
import com.pingpaisa.app.data.RecurringItem
import com.pingpaisa.app.data.SavingsGoal
import com.pingpaisa.app.data.Transaction
import com.pingpaisa.app.data.UserProfile
import com.pingpaisa.app.data.isIncome
import com.pingpaisa.app.healthscore.HealthScoreHistory
import com.pingpaisa.app.healthscore.InsightEngine
import com.pingpaisa.app.healthscore.ScoreCalculator
import com.pingpaisa.app.healthscore.SpendingHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Whether an account exists yet. Loading is separate so the app never flashes sign-up on start. */
sealed interface AccountState {
    data object Loading : AccountState
    data object NoAccount : AccountState
    data class Ready(val profile: UserProfile) : AccountState
}

/** Everything the Financial health, Loan and Home screens need about this month. */
data class HealthState(
    val monthlyIncome: Double,        // salary + extra income received this month
    val thisMonthSpend: Double,       // tracked expenses this month
    val raw: ScoreCalculator.RawFactors,
    val result: ScoreCalculator.ScoreResult,
    val insight: InsightEngine.Insight
)

/**
 * The single state holder for every screen. It turns the database into live
 * values and does the same health-score maths the old tracker did in its
 * MainActivity, so screens only display data and call actions.
 */
class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = ExpenseRepository.get(application)

    private fun <T> Flow<T>.state(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    // ------------------------------------------------------------- raw tables

    val transactions: StateFlow<List<Transaction>> = repo.observeTransactions().state(emptyList())
    val recurringItems: StateFlow<List<RecurringItem>> = repo.observeRecurringItems().state(emptyList())
    val merchantOverrides: StateFlow<List<MerchantOverride>> = repo.observeMerchantOverrides().state(emptyList())
    val budgets: StateFlow<List<Budget>> = repo.observeBudgets().state(emptyList())
    val savingsGoals: StateFlow<List<SavingsGoal>> = repo.observeSavingsGoals().state(emptyList())
    val scoreHistory: StateFlow<List<HealthScoreHistory>> = repo.observeScoreHistory().state(emptyList())

    val account: StateFlow<AccountState> = repo.observeProfile()
        .map { if (it == null) AccountState.NoAccount else AccountState.Ready(it) }
        .state(AccountState.Loading)

    // ---------------------------------------------------------- derived values

    /** Money going out. The score engine must only see these (income would count as spending). */
    val expenses: StateFlow<List<Transaction>> =
        repo.observeTransactions().map { list -> list.filter { !it.isIncome } }.state(emptyList())

    /** Extra money coming in (not the salary). */
    val incomeTransactions: StateFlow<List<Transaction>> =
        repo.observeTransactions().map { list -> list.filter { it.isIncome } }.state(emptyList())

    /** Auto-detected monthly payments, from expenses only (same rules as the tracker). */
    val detectedRecurring: StateFlow<List<DetectedRecurring>> =
        expenses.map { RecurringDetector.detect(it) }.flowOn(Dispatchers.Default).state(emptyList())

    /**
     * The health score, recalculated whenever transactions, recurring items or the
     * salary change. Same inputs as the tracker's MainActivity, except income now
     * comes from the saved salary plus this month's extra income instead of a
     * temporary text box.
     */
    val health: StateFlow<HealthState?> = combine(
        expenses, incomeTransactions, recurringItems, detectedRecurring, account
    ) { expenseList, incomeList, recurringList, detected, accountState ->
        val salary = (accountState as? AccountState.Ready)?.profile?.monthlySalary ?: 0.0
        val extraIncome = SpendingHistory.thisMonthTransactions(incomeList).sumOf { it.amount }
        val monthlyIncome = salary + extraIncome

        val raw = ScoreCalculator.RawFactors(
            income = monthlyIncome,
            expense = SpendingHistory.monthlyOutflow(expenseList, recurringList),
            totalEmiAmount = recurringList.filter { it.type == "EMI" }.sumOf { it.amount },
            monthlyTotals = SpendingHistory.recentMonthlyTotals(expenseList),
            recurringReliability = SpendingHistory.recurringReliability(expenseList, detected)
        )
        val result = ScoreCalculator.calculate(raw)
        HealthState(
            monthlyIncome = monthlyIncome,
            thisMonthSpend = SpendingHistory.thisMonthSpend(expenseList),
            raw = raw,
            result = result,
            insight = InsightEngine.fallback(raw, result)
        )
    }.flowOn(Dispatchers.Default).state(null)   // date parsing over all transactions: keep it off the UI thread

    // ----------------------------------------------------------------- actions

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    fun addExpense(amount: Double, paidTo: String, category: String? = null) =
        launch { repo.addManualExpense(amount, paidTo, category) }

    fun addIncome(amount: Double, from: String) = launch { repo.addManualIncome(amount, from) }

    fun recategorize(recipient: String, category: String) = launch { repo.recategorize(recipient, category) }

    fun observeTransaction(id: Int): Flow<Transaction?> = repo.observeTransaction(id)

    fun addRecurring(name: String, amount: Double, dayOfMonth: Int, type: String) =
        launch { repo.addRecurring(name, amount, dayOfMonth, type) }

    fun deleteRecurring(item: RecurringItem) = launch { repo.deleteRecurring(item) }

    fun setBudget(category: String, monthlyLimit: Double) = launch { repo.setBudget(category, monthlyLimit) }
    fun deleteBudget(budget: Budget) = launch { repo.deleteBudget(budget) }

    fun addSavingsGoal(name: String, target: Double) = launch { repo.addSavingsGoal(name, target) }
    fun updateSavingsGoal(goal: SavingsGoal) = launch { repo.updateSavingsGoal(goal) }
    fun deleteSavingsGoal(goal: SavingsGoal) = launch { repo.deleteSavingsGoal(goal) }

    fun updateNameAndSalary(name: String, salary: Double) = launch { repo.updateNameAndSalary(name, salary) }

    fun recordScore(score: Int, risk: ScoreCalculator.RiskLevel) = launch { repo.recordScore(score, risk.name) }
}
