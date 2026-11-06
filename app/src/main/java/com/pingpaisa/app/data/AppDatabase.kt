package com.pingpaisa.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.pingpaisa.app.healthscore.HealthScoreHistory
import com.pingpaisa.app.healthscore.HealthScoreHistoryDao

/**
 * The single on-device database for PingPaisa.
 *
 * Tables ported from Smart_Expense_Tracker: transactions, merchant_overrides,
 * recurring_items, health_score_history.
 * New tables: user_profile, budgets, savings_goals.
 *
 * Version starts at 1 because this is a new app (com.pingpaisa.app); the old
 * tracker's database lives in a different app and is not carried over.
 */
@Database(
    entities = [
        Transaction::class,
        MerchantOverride::class,
        RecurringItem::class,
        HealthScoreHistory::class,
        UserProfile::class,
        Budget::class,
        SavingsGoal::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao
    abstract fun merchantOverrideDao(): MerchantOverrideDao
    abstract fun recurringItemDao(): RecurringItemDao
    abstract fun healthScoreHistoryDao(): HealthScoreHistoryDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun budgetDao(): BudgetDao
    abstract fun savingsGoalDao(): SavingsGoalDao

    companion object {
        private const val DATABASE_NAME = "pingpaisa_db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** One shared instance for the whole app, including the notification listener. */
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    // Development only, same as the old tracker: a schema change wipes the
                    // data instead of crashing. Replace with real migrations before release,
                    // otherwise users would lose their account and transactions on update.
                    .fallbackToDestructiveMigration(true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
