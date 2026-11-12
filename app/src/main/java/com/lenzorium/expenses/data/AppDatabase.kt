package com.lenzorium.expenses.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface LedgerDao {
    // Transactions
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC, id DESC")
    fun observeTxns(): Flow<List<Txn>>

    @Query("SELECT * FROM transactions")
    suspend fun allTxns(): List<Txn>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun txn(id: Long): Txn?

    @Query("SELECT * FROM transactions WHERE requestId = :requestId LIMIT 1")
    suspend fun txnByRequest(requestId: String): Txn?

    @Query("SELECT * FROM transactions WHERE loanId = :loanId")
    suspend fun txnsForLoan(loanId: Long): List<Txn>

    @Insert suspend fun insertTxn(t: Txn): Long
    @Insert suspend fun insertTxns(t: List<Txn>)
    @Update suspend fun updateTxn(t: Txn)
    @Delete suspend fun deleteTxn(t: Txn)

    @Query("DELETE FROM transactions WHERE loanId = :loanId")
    suspend fun deleteTxnsForLoan(loanId: Long)

    @Query("UPDATE transactions SET categoryId = :to WHERE categoryId = :from")
    suspend fun moveCategory(from: Long, to: Long)

    // Loans
    @Query("SELECT * FROM loans ORDER BY receivedAt DESC, id DESC")
    fun observeLoans(): Flow<List<Loan>>

    @Query("SELECT * FROM loans")
    suspend fun allLoans(): List<Loan>

    @Query("SELECT * FROM loans WHERE id = :id")
    suspend fun loan(id: Long): Loan?

    @Query("SELECT * FROM loans WHERE requestId = :requestId LIMIT 1")
    suspend fun loanByRequest(requestId: String): Loan?

    @Insert suspend fun insertLoan(l: Loan): Long
    @Insert suspend fun insertLoans(l: List<Loan>)
    @Update suspend fun updateLoan(l: Loan)
    @Delete suspend fun deleteLoan(l: Loan)

    // Categories
    @Query("SELECT * FROM categories ORDER BY kind, sortOrder, name")
    fun observeCategories(): Flow<List<Category>>

    @Query("SELECT * FROM categories")
    suspend fun allCategories(): List<Category>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun categoryCount(): Int

    @Insert suspend fun insertCategory(c: Category): Long
    @Insert suspend fun insertCategories(c: List<Category>)
    @Update suspend fun updateCategory(c: Category)
    @Delete suspend fun deleteCategory(c: Category)

    // Accounts
    @Query("SELECT * FROM accounts ORDER BY archived, sortOrder, id")
    fun observeAccounts(): Flow<List<Account>>

    @Query("SELECT * FROM accounts ORDER BY archived, sortOrder, id")
    suspend fun allAccounts(): List<Account>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun account(id: Long): Account?

    @Query("SELECT COUNT(*) FROM transactions WHERE accountId = :id OR toAccountId = :id")
    suspend fun accountUseCount(id: Long): Int

    @Insert suspend fun insertAccount(a: Account): Long
    @Insert suspend fun insertAccounts(a: List<Account>)
    @Update suspend fun updateAccount(a: Account)
    @Delete suspend fun deleteAccount(a: Account)

    // Budgets
    @Query("SELECT * FROM budgets ORDER BY categoryId IS NOT NULL, id")
    fun observeBudgets(): Flow<List<Budget>>
    @Query("SELECT * FROM budgets") suspend fun allBudgets(): List<Budget>
    @Insert suspend fun insertBudget(b: Budget): Long
    @Insert suspend fun insertBudgets(b: List<Budget>)
    @Update suspend fun updateBudget(b: Budget)
    @Query("DELETE FROM budgets WHERE id = :id") suspend fun deleteBudget(id: Long)
    @Query("DELETE FROM budgets WHERE categoryId = :categoryId") suspend fun deleteBudgetsForCategory(categoryId: Long)

    // Bills and regular income
    @Query("SELECT * FROM recurring ORDER BY active DESC, nextDueEpochDay, name")
    fun observeRecurring(): Flow<List<Recurring>>
    @Query("SELECT * FROM recurring") suspend fun allRecurring(): List<Recurring>
    @Query("SELECT * FROM recurring WHERE id = :id") suspend fun recurring(id: Long): Recurring?
    @Insert suspend fun insertRecurring(r: Recurring): Long
    @Insert suspend fun insertRecurringList(r: List<Recurring>)
    @Update suspend fun updateRecurring(r: Recurring)
    @Query("DELETE FROM recurring WHERE id = :id") suspend fun deleteRecurring(id: Long)
    @Query("UPDATE recurring SET categoryId = :to WHERE categoryId = :from") suspend fun moveRecurringCategory(from: Long, to: Long)

    // Quick-add templates
    @Query("SELECT * FROM templates ORDER BY sortOrder, id")
    fun observeTemplates(): Flow<List<Template>>
    @Query("SELECT * FROM templates") suspend fun allTemplates(): List<Template>
    @Query("SELECT * FROM templates WHERE id = :id") suspend fun template(id: Long): Template?
    @Insert suspend fun insertTemplate(t: Template): Long
    @Insert suspend fun insertTemplates(t: List<Template>)
    @Update suspend fun updateTemplate(t: Template)
    @Query("DELETE FROM templates WHERE id = :id") suspend fun deleteTemplate(id: Long)
    @Query("UPDATE templates SET categoryId = :to WHERE categoryId = :from") suspend fun moveTemplateCategory(from: Long, to: Long)

    // Goals
    @Query("SELECT * FROM goals ORDER BY archived, id")
    fun observeGoals(): Flow<List<Goal>>
    @Query("SELECT * FROM goals") suspend fun allGoals(): List<Goal>
    @Query("SELECT * FROM goals WHERE id = :id") suspend fun goal(id: Long): Goal?
    @Insert suspend fun insertGoal(g: Goal): Long
    @Insert suspend fun insertGoals(g: List<Goal>)
    @Update suspend fun updateGoal(g: Goal)
    @Query("DELETE FROM goals WHERE id = :id") suspend fun deleteGoal(id: Long)
    @Query("SELECT * FROM goal_entries ORDER BY epochDay DESC, id DESC")
    fun observeGoalEntries(): Flow<List<GoalEntry>>
    @Query("SELECT * FROM goal_entries") suspend fun allGoalEntries(): List<GoalEntry>
    @Insert suspend fun insertGoalEntry(e: GoalEntry): Long
    @Insert suspend fun insertGoalEntries(e: List<GoalEntry>)
    @Query("DELETE FROM goal_entries WHERE id = :id") suspend fun deleteGoalEntry(id: Long)
    @Query("DELETE FROM goal_entries WHERE goalId = :goalId") suspend fun deleteGoalEntries(goalId: Long)

    // Safekeeping
    @Query("SELECT * FROM safe_people ORDER BY name COLLATE NOCASE")
    fun observeHolders(): Flow<List<Holder>>
    @Query("SELECT * FROM safe_people") suspend fun allHolders(): List<Holder>
    @Query("SELECT * FROM safe_people WHERE id = :id") suspend fun holder(id: Long): Holder?
    @Insert suspend fun insertHolder(h: Holder): Long
    @Insert suspend fun insertHolders(h: List<Holder>)
    @Update suspend fun updateHolder(h: Holder)
    @Query("DELETE FROM safe_people WHERE id = :id") suspend fun deleteHolder(id: Long)
    @Query("SELECT * FROM transactions WHERE personId = :personId") suspend fun txnsForPerson(personId: Long): List<Txn>
    @Query("SELECT * FROM audit_log ORDER BY at DESC, id DESC")
    fun observeAudit(): Flow<List<AuditEntry>>
    @Query("SELECT * FROM audit_log") suspend fun allAudit(): List<AuditEntry>
    @Insert suspend fun insertAudit(a: AuditEntry): Long
    @Insert suspend fun insertAuditList(a: List<AuditEntry>)

    // Restore helpers
    @Query("DELETE FROM safe_people") suspend fun clearHolders()
    @Query("DELETE FROM audit_log") suspend fun clearAudit()
    @Query("DELETE FROM budgets") suspend fun clearBudgets()
    @Query("DELETE FROM recurring") suspend fun clearRecurring()
    @Query("DELETE FROM templates") suspend fun clearTemplates()
    @Query("DELETE FROM goals") suspend fun clearGoals()
    @Query("DELETE FROM goal_entries") suspend fun clearGoalEntries()
    @Query("DELETE FROM accounts") suspend fun clearAccounts()
    @Query("DELETE FROM transactions") suspend fun clearTxns()
    @Query("DELETE FROM loans") suspend fun clearLoans()
    @Query("DELETE FROM categories") suspend fun clearCategories()
}

@Database(entities = [
        Txn::class, Loan::class, Category::class, Account::class,
        Budget::class, Recurring::class, Template::class, Goal::class, GoalEntry::class,
        Holder::class, AuditEntry::class,
    ],
    version = 4, exportSchema = true)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): LedgerDao

    companion object {
        const val CASH_ID = 1L
        const val BANK_ID = 2L

        /**
         * Version 2 adds accounts (Cash, Bank, ...) with their own balances.
         * Existing entries are assigned from their old payment label: Cash → Cash, Bank/Card → Bank.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `accounts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                        "`kind` TEXT NOT NULL, `openingMinor` INTEGER NOT NULL, `archived` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL)"
                )
                db.execSQL("INSERT INTO `accounts` (`id`,`name`,`kind`,`openingMinor`,`archived`,`sortOrder`) VALUES ($CASH_ID,'Cash','CASH',0,0,1), ($BANK_ID,'Bank','BANK',0,0,2)")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `accountId` INTEGER")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `toAccountId` INTEGER")
                db.execSQL(
                    "UPDATE `transactions` SET `accountId` = CASE " +
                        "WHEN `type` = 'LOAN_CHARGE' THEN NULL " +
                        "WHEN `paymentMethod` IN ('BANK','CARD') THEN $BANK_ID ELSE $CASH_ID END"
                )
            }
        }

        /**
         * Version 3 adds budgets, bills/regular income, quick-add templates, savings goals,
         * tags on entries and an optional currency per account. Nothing existing changes.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `tags` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `recurringId` INTEGER")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `toAmountMinor` INTEGER")
                db.execSQL("ALTER TABLE `accounts` ADD COLUMN `currency` TEXT")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `budgets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `categoryId` INTEGER, " +
                        "`limitMinor` INTEGER NOT NULL, `rollover` INTEGER NOT NULL, `startMonthEpochDay` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `recurring` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                        "`type` TEXT NOT NULL, `amountMinor` INTEGER NOT NULL, `categoryId` INTEGER, `accountId` INTEGER, `party` TEXT NOT NULL, " +
                        "`frequency` TEXT NOT NULL, `intervalCount` INTEGER NOT NULL, `startEpochDay` INTEGER NOT NULL, `endEpochDay` INTEGER, " +
                        "`nextDueEpochDay` INTEGER NOT NULL, `remind` INTEGER NOT NULL, `remindDaysBefore` INTEGER NOT NULL, " +
                        "`active` INTEGER NOT NULL, `notes` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `templates` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                        "`type` TEXT NOT NULL, `amountMinor` INTEGER NOT NULL, `categoryId` INTEGER, `accountId` INTEGER, `party` TEXT NOT NULL, " +
                        "`tags` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `goals` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                        "`targetMinor` INTEGER NOT NULL, `targetEpochDay` INTEGER, `accountId` INTEGER, `iconKey` TEXT NOT NULL, " +
                        "`colorArgb` INTEGER NOT NULL, `archived` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `goal_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `goalId` INTEGER NOT NULL, " +
                        "`amountMinor` INTEGER NOT NULL, `epochDay` INTEGER NOT NULL, `note` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_goal_entries_goalId` ON `goal_entries` (`goalId`)")
            }
        }

        /** Version 4 adds money given for safekeeping: people, an audit trail and two entry columns. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `personId` INTEGER")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `dueEpochDay` INTEGER")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `safe_people` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                        "`phone` TEXT NOT NULL, `notes` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `audit_log` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `txnId` INTEGER, `personId` INTEGER, " +
                        "`action` TEXT NOT NULL, `details` TEXT NOT NULL, `at` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_audit_log_personId` ON `audit_log` (`personId`)")
            }
        }

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "lenzorium.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
    }
}
