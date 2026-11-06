package com.pingpaisa.app

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pingpaisa.app.data.Budget
import com.pingpaisa.app.data.Categorizer
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
import com.pingpaisa.app.security.PasswordHasher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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

/** Outcome of a password check on the login screen. */
enum class LoginResult { Success, WrongPassword, NoAccount }

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

    // ------------------------------------------------------------ session lock

    /**
     * True once the password was entered (or the account was just created) in this
     * session. Memory only, never saved: a fresh start of the app always asks again.
     */
    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()
    private var backgroundedAt = 0L

    /** Called when the app leaves the screen (not for rotation). */
    fun onAppBackgrounded() { backgroundedAt = SystemClock.elapsedRealtime() }

    /** Called when the app comes back: locks again if it was away for [RELOCK_AFTER_MS] or more. */
    fun onAppForegrounded() {
        if (backgroundedAt != 0L && SystemClock.elapsedRealtime() - backgroundedAt >= RELOCK_AFTER_MS) _unlocked.value = false
        backgroundedAt = 0L
    }

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

    /** Numbers for the Home screen; null until the account and health score have loaded. */
    val homeSummary: StateFlow<HomeSummary?> = combine(transactions, budgets, health) { all, budgetList, h ->
        h?.let { HomeSummary.build(all, budgetList, it.monthlyIncome, it.raw.expense, LocalDate.now()) }
    }.flowOn(Dispatchers.Default).state(null)

    /** The transaction opened from a list, shown on the Detail screen. Memory only. */
    private val _selectedTransactionId = MutableStateFlow<Int?>(null)
    val selectedTransactionId: StateFlow<Int?> = _selectedTransactionId.asStateFlow()
    fun openTransaction(id: Int) { _selectedTransactionId.value = id }

    // ----------------------------------------------------------------- actions

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    fun addExpense(amount: Double, paidTo: String, category: String? = null) =
        launch { repo.addManualExpense(amount, paidTo, category) }

    fun addIncome(amount: Double, from: String) = launch { repo.addManualIncome(amount, from) }

    fun recategorize(recipient: String, category: String) = launch { repo.recategorize(recipient, category) }

    /**
     * "Save changes" on the Detail screen. A new [category] is remembered for the merchant and
     * applied to all its payments (tracker behaviour); the note belongs to this transaction only.
     */
    fun saveDetail(transaction: Transaction, category: String?, note: String) = launch {
        if (category != null && category != transaction.category) repo.recategorize(transaction.recipient, category)
        if (note.trim() != transaction.note) repo.updateNote(transaction.id, note)
    }

    /** Categories the user can pick: the dataset's, plus any custom ones already in use. */
    val categoryChoices: StateFlow<List<String>> = combine(expenses, merchantOverrides) { list, overrides ->
        pickableCategories(Categorizer.getAllCategories(), list.map { it.category } + overrides.map { it.category })
    }.flowOn(Dispatchers.Default).state(emptyList())

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

    /**
     * Sign up. The password is hashed on a background thread (it is deliberately slow),
     * then saved. [onDone] gets true when the account was created, or false if one
     * already exists on this phone. The plain password is wiped from memory afterwards.
     */
    fun createAccount(name: String, salary: Double, password: String, onDone: (created: Boolean) -> Unit) {
        viewModelScope.launch {
            val chars = password.toCharArray()
            val created = try {
                val hashed = withContext(Dispatchers.Default) { PasswordHasher.hash(chars) }
                // Unlock before the insert so the new account doesn't flash the login screen;
                // undone below if an account already existed.
                _unlocked.value = true
                repo.createAccount(name, salary, hashed)
            } finally {
                chars.fill('\u0000')
            }
            if (!created) _unlocked.value = false
            onDone(created)
        }
    }

    /**
     * Login. Re-hashes [password] with the account's salt on a background thread and
     * compares it with the stored hash. The plain password is wiped afterwards.
     */
    fun login(password: String, onResult: (LoginResult) -> Unit) {
        viewModelScope.launch {
            val profile = repo.getProfile()
            if (profile == null) { onResult(LoginResult.NoAccount); return@launch }
            val chars = password.toCharArray()
            val ok = try {
                withContext(Dispatchers.Default) {
                    PasswordHasher.verify(chars, profile.passwordHash, profile.passwordSalt)
                }
            } finally {
                chars.fill('\u0000')
            }
            if (ok) _unlocked.value = true
            onResult(if (ok) LoginResult.Success else LoginResult.WrongPassword)
        }
    }

    fun recordScore(score: Int, risk: ScoreCalculator.RiskLevel) = launch { repo.recordScore(score, risk.name) }

    /**
     * Called when the Financial health screen shows a real score: saves a snapshot once a day,
     * or again if the score changed. Loan-simulator "what if" scores are never recorded.
     */
    fun recordScoreIfNeeded(score: Int, risk: ScoreCalculator.RiskLevel) = launch {
        if (HealthUi.shouldRecord(score, repo.latestScore(), LocalDate.now(), java.time.ZoneId.systemDefault()))
            repo.recordScore(score, risk.name)
    }

    companion object {
        /** Away from the app this long and the password is asked again. */
        const val RELOCK_AFTER_MS = 2 * 60_000L
    }
}
