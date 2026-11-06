package com.pingpaisa.app.data

import android.content.Context
import com.pingpaisa.app.healthscore.HealthScoreHistory
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The one place the app reads and writes money data. Screens (through the
 * ViewModel) and the notification listener both go through here, so the
 * rules live in a single spot.
 *
 * The "use the remembered merchant category, otherwise auto-categorize" rule
 * was copied twice in Smart_Expense_Tracker (PaymentNotificationListener and
 * ManualEntryDialog); it is now [categorize].
 */
class ExpenseRepository(private val db: AppDatabase) {

    private val transactions = db.transactionDao()
    private val overrides = db.merchantOverrideDao()
    private val recurring = db.recurringItemDao()
    private val profiles = db.userProfileDao()
    private val budgets = db.budgetDao()
    private val goals = db.savingsGoalDao()
    private val scoreHistory = db.healthScoreHistoryDao()

    // ---------------------------------------------------------------- live data

    fun observeTransactions(): Flow<List<Transaction>> = transactions.observeAll()
    fun observeTransaction(id: Int): Flow<Transaction?> = transactions.observeById(id)
    fun observeRecurringItems(): Flow<List<RecurringItem>> = recurring.observeAll()
    fun observeMerchantOverrides(): Flow<List<MerchantOverride>> = overrides.observeAll()
    fun observeProfile(): Flow<UserProfile?> = profiles.observe()
    fun observeBudgets(): Flow<List<Budget>> = budgets.observeAll()
    fun observeSavingsGoals(): Flow<List<SavingsGoal>> = goals.observeAll()
    fun observeScoreHistory(): Flow<List<HealthScoreHistory>> = scoreHistory.observeAll()

    // ----------------------------------------------------------- categorizing

    /** A category the user chose for this merchant before wins; otherwise the dataset decides. */
    suspend fun categorize(recipient: String): CategoryResult {
        val override = overrides.getOverride(recipient.trim().lowercase())
        return if (override != null) CategoryResult(override, "Miscellaneous")
        else Categorizer.categorize(recipient)
    }

    /**
     * User picked a category for a merchant (Transaction detail screen).
     * Same behaviour as the old tracker: remembered for future payments and
     * applied to every past payment to that merchant.
     */
    suspend fun recategorize(recipient: String, category: String) {
        overrides.setOverride(MerchantOverride(recipient = recipient.trim().lowercase(), category = category))
        transactions.updateCategoryForRecipient(recipient, category)
    }

    // ------------------------------------------------------------ transactions

    /** A payment parsed from a bank SMS notification. Duplicates (same UPI ref) are ignored. */
    suspend fun saveParsedPayment(parsed: Transaction): Transaction {
        val result = categorize(parsed.recipient)
        val finalTxn = parsed.copy(category = result.category, subcategory = result.subcategory)
        transactions.insert(finalTxn)
        return finalTxn
    }

    /**
     * Cash or any payment the user types in (Add an expense screen). If
     * [category] is null it is auto-categorized, as in the old ManualEntryDialog.
     */
    suspend fun addManualExpense(amount: Double, paidTo: String, category: String? = null) {
        val result = if (category != null) CategoryResult(category, "Miscellaneous") else categorize(paidTo)
        transactions.insert(
            Transaction(
                amount = amount,
                recipient = paidTo.trim(),
                date = today(),
                refNumber = manualRef(),
                rawText = "Manual entry",
                source = SOURCE_MANUAL,
                category = result.category,
                subcategory = result.subcategory,
                type = Transaction.TYPE_EXPENSE
            )
        )
    }

    /** Extra money coming in (bonus, refund, freelance...). The monthly salary lives in UserProfile. */
    suspend fun addManualIncome(amount: Double, from: String) {
        transactions.insert(
            Transaction(
                amount = amount,
                recipient = from.trim(),
                date = today(),
                refNumber = manualRef(),
                rawText = "Manual entry",
                source = SOURCE_MANUAL,
                category = INCOME_CATEGORY,
                subcategory = "Other income",
                type = Transaction.TYPE_INCOME
            )
        )
    }

    // --------------------------------------------------------------- recurring

    suspend fun addRecurring(name: String, amount: Double, dayOfMonth: Int, type: String) {
        recurring.insert(
            RecurringItem(
                name = name.trim(),
                amount = amount,
                // Same rule as the old AddRecurringDialog.
                category = if (type == "EMI" || type == "SIP") "Financial Services" else "Miscellaneous",
                dayOfMonth = dayOfMonth,
                type = type
            )
        )
    }

    suspend fun deleteRecurring(item: RecurringItem) = recurring.delete(item)

    // ------------------------------------------------------- budgets and goals

    suspend fun setBudget(category: String, monthlyLimit: Double) = budgets.upsert(Budget(category, monthlyLimit))
    suspend fun deleteBudget(budget: Budget) = budgets.delete(budget)

    suspend fun addSavingsGoal(name: String, target: Double) = goals.insert(SavingsGoal(name = name.trim(), targetAmount = target))
    suspend fun updateSavingsGoal(goal: SavingsGoal) = goals.update(goal)
    suspend fun deleteSavingsGoal(goal: SavingsGoal) = goals.delete(goal)

    // ------------------------------------------------------------ score history

    /** Only real dashboard scores are recorded, never loan-simulator "what if" scores. */
    suspend fun recordScore(score: Int, riskLevel: String) =
        scoreHistory.insert(HealthScoreHistory(score = score, riskLevel = riskLevel))

    // ----------------------------------------------------------------- profile

    suspend fun hasAccount(): Boolean = profiles.exists()
    suspend fun updateNameAndSalary(name: String, salary: Double) = profiles.updateNameAndSalary(name.trim(), salary)

    // ----------------------------------------------------------------- helpers

    private fun today(): String = SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(Date())
    private fun manualRef(): String = "MANUAL-${System.currentTimeMillis()}"

    companion object {
        const val SOURCE_MANUAL = "manual"
        const val INCOME_CATEGORY = "Income"

        @Volatile
        private var INSTANCE: ExpenseRepository? = null

        /** Shared by the ViewModel and the notification listener. */
        fun get(context: Context): ExpenseRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: ExpenseRepository(AppDatabase.getDatabase(context)).also { INSTANCE = it }
            }
    }
}
