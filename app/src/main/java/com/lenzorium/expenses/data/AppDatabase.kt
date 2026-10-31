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

    // Restore helpers
    @Query("DELETE FROM accounts") suspend fun clearAccounts()
    @Query("DELETE FROM transactions") suspend fun clearTxns()
    @Query("DELETE FROM loans") suspend fun clearLoans()
    @Query("DELETE FROM categories") suspend fun clearCategories()
}

@Database(entities = [Txn::class, Loan::class, Category::class, Account::class], version = 2, exportSchema = true)
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

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "lenzorium.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
