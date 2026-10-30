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

    // Restore helpers
    @Query("DELETE FROM transactions") suspend fun clearTxns()
    @Query("DELETE FROM loans") suspend fun clearLoans()
    @Query("DELETE FROM categories") suspend fun clearCategories()
}

@Database(entities = [Txn::class, Loan::class, Category::class], version = 1, exportSchema = true)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): LedgerDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "lenzorium.db").build()
    }
}
