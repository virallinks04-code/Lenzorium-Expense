package com.lenzorium.expenses.screenshots

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lenzorium.expenses.data.AppDatabase
import com.lenzorium.expenses.data.AppSettings
import com.lenzorium.expenses.data.CategoryKind
import com.lenzorium.expenses.data.Holder
import com.lenzorium.expenses.data.Repository
import com.lenzorium.expenses.data.SaveResult
import com.lenzorium.expenses.data.SettingsStore
import com.lenzorium.expenses.data.Txn
import com.lenzorium.expenses.data.TxnType
import com.lenzorium.expenses.domain.LedgerMath
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.util.UUID

/** Cash (and mobile wallets) can never go below zero; bank accounts may. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class CashGuardTest {
    private val cashId = AppDatabase.CASH_ID
    private val bankId = AppDatabase.BANK_ID

    @Test
    fun cashNeverGoesBelowZero() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        val repo = Repository(ctx, db, SettingsStore(ctx))
        repo.completeSetup(AppSettings(currencyCode = "PKR", trackingStartEpochDay = LocalDate.now().minusDays(30).toEpochDay()), null, emptyList(), 5_000_00, 1_000_00)
        val dao = db.dao()
        val food = dao.allCategories().first { it.kind == CategoryKind.EXPENSE }.id
        val salary = dao.allCategories().first { it.kind == CategoryKind.INCOME }.id
        val now = System.currentTimeMillis() - 60_000
        fun e(type: TxnType, amount: Long, acc: Long, cat: Long? = food, to: Long? = null) =
            Txn(type = type, amountMinor = amount, categoryId = cat, timestamp = now, requestId = UUID.randomUUID().toString(), createdAt = 0, accountId = acc, toAccountId = to)
        suspend fun cash() = LedgerMath.accountBalance(dao.account(cashId)!!, dao.allTxns())
        fun refused(r: SaveResult) = r is SaveResult.Error && r.message.contains("can't go below zero")

        // Spending more cash than you have is refused; exactly all of it is fine.
        assertTrue(refused(repo.saveEntry(e(TxnType.EXPENSE, 5_000_01, cashId), null)))
        assertEquals(5_000_00L, cash())
        assertTrue(repo.saveEntry(e(TxnType.EXPENSE, 4_000_00, cashId), null) is SaveResult.Ok)
        // Bank may go into overdraft.
        assertTrue(repo.saveEntry(e(TxnType.EXPENSE, 3_000_00, bankId), null) is SaveResult.Ok)
        // Transfers out of cash and giving cash for safekeeping are checked too.
        assertTrue(refused(repo.saveTransfer(e(TxnType.TRANSFER, 2_000_00, cashId, cat = null, to = bankId))))
        val pid = (repo.saveHolder(Holder(name = "Bilal", createdAt = 0)) as SaveResult.Ok).id
        assertTrue(refused(repo.saveSafe(e(TxnType.SAFE_GIVEN, 1_500_00, cashId, cat = null).copy(personId = pid))))
        // Adding cash first (income) makes it possible.
        val inc = repo.saveEntry(e(TxnType.INCOME, 2_000_00, cashId, cat = salary), null) as SaveResult.Ok
        assertTrue(repo.saveSafe(e(TxnType.SAFE_GIVEN, 1_500_00, cashId, cat = null).copy(personId = pid)) is SaveResult.Ok)
        assertEquals(1_500_00L, cash())
        // Deleting the income that paid for it would overdraw cash: refused, nothing changes.
        assertTrue(refused(repo.deleteTxn(inc.id)))
        assertEquals(1_500_00L, cash())
        // Editing an expense upwards beyond the cash available is refused.
        val exp = dao.allTxns().first { it.type == TxnType.EXPENSE && it.accountId == cashId }
        assertTrue(refused(repo.saveEntry(exp.copy(amountMinor = exp.amountMinor + 1_500_01), null)))
        assertTrue(repo.saveEntry(exp.copy(amountMinor = exp.amountMinor + 1_500_00), null) is SaveResult.Ok)
        assertEquals(0L, cash())
        // Lowering Cash's opening balance below what's been spent is refused.
        val acc = dao.account(cashId)!!
        assertTrue(refused(repo.updateAccount(acc.copy(openingMinor = acc.openingMinor - 1))))
        db.close()
    }
}
