package com.pingpaisa.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.pingpaisa.app.healthscore.HealthScoreHistory
import com.pingpaisa.app.healthscore.HealthScoreHistoryDao

/**
 * The single on-device database for PingPaisa.
 *
 * Tables ported from Smart_Expense_Tracker: transactions, merchant_overrides,
 * recurring_items, health_score_history.
 * New tables: user_profile, budgets, savings_goals.
 *
 * Version history (every change needs a Migration below, so upgrading the app
 * never deletes anyone's account or transactions):
 *  1  first PingPaisa schema
 *  2  transactions.note (the note on the Transaction detail screen)
 *
 * The schema of each version is exported to app/schemas/ so future migrations
 * can be checked against it.
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
    version = 2,
    exportSchema = true
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

        /** v1 → v2: add the note column. Existing rows get an empty note; nothing else changes. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `note` TEXT NOT NULL DEFAULT ''")
            }
        }

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
                    .addMigrations(MIGRATION_1_2)
                    // No silent wipe on upgrade any more: a missing migration now fails loudly during
                    // development instead of deleting the user's account. Only installing an OLDER build
                    // over a newer database (a developer action) still starts fresh.
                    .fallbackToDestructiveMigrationOnDowngrade(true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
