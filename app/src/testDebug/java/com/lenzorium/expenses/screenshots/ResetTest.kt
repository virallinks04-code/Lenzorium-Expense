package com.lenzorium.expenses.screenshots

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lenzorium.expenses.data.AppDatabase
import com.lenzorium.expenses.data.AppSettings
import com.lenzorium.expenses.data.Budget
import com.lenzorium.expenses.data.Repository
import com.lenzorium.expenses.data.SettingsStore
import com.lenzorium.expenses.data.Txn
import com.lenzorium.expenses.data.TxnType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate

/** "Clear all entries" keeps the set-up; "Reset app" removes everything and returns to the welcome screen. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ResetTest {
    private fun fresh(): Pair<Repository, AppDatabase> {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        val repo = Repository(ctx, db, SettingsStore(ctx))
        runBlocking {
            repo.completeSetup(AppSettings(currencyCode = "PKR", trackingStartEpochDay = LocalDate.now().minusDays(30).toEpochDay()), "1234", emptyList(), 5_000_00, 10_000_00)
            repo.settings.update { it.copy(profileName = "Ahmed") }
            val dao = db.dao()
            val food = dao.allCategories().first { it.name == "Food" }.id
            dao.insertTxn(Txn(type = TxnType.EXPENSE, amountMinor = 500_00, categoryId = food, timestamp = System.currentTimeMillis() - 60_000, requestId = "a", createdAt = 0, accountId = 1, receiptFile = "r1.jpg"))
            repo.receiptFile("r1.jpg").writeText("photo")
            dao.insertBudget(Budget(categoryId = null, limitMinor = 1000_00, startMonthEpochDay = 0, createdAt = 0))
        }
        return repo to db
    }

    @Test
    fun clearEntriesKeepsSetup() = runBlocking {
        val (repo, db) = fresh()
        val dao = db.dao()
        repo.clearEntries()
        assertEquals(0, dao.allTxns().size)
        assertEquals(0, dao.allLoans().size)
        assertFalse(repo.receiptFile("r1.jpg").exists())
        assertEquals(1, dao.allBudgets().size)
        assertTrue(dao.allCategories().isNotEmpty())
        assertEquals(5_000_00L, dao.account(AppDatabase.CASH_ID)!!.openingMinor)
        assertTrue(repo.settings.current.setupDone)
        assertEquals("Ahmed", repo.settings.current.profileName)
        assertTrue(repo.settings.checkPin("1234"))
        db.close()
    }

    @Test
    fun resetAllStartsFromScratch() = runBlocking {
        val (repo, db) = fresh()
        val dao = db.dao()
        repo.resetAll()
        assertEquals(0, dao.allTxns().size + dao.allBudgets().size + dao.allCategories().size + dao.allAccounts().size)
        assertFalse(File(ApplicationProvider.getApplicationContext<Context>().filesDir, "receipts/r1.jpg").exists())
        val s = repo.settings.current
        assertFalse(s.setupDone)
        assertNull(s.pinHash)
        assertEquals("", s.profileName)
        // Setting up again works and seeds defaults.
        repo.completeSetup(AppSettings(currencyCode = "GBP", trackingStartEpochDay = LocalDate.now().toEpochDay()), null, emptyList())
        assertTrue(dao.allCategories().isNotEmpty())
        assertEquals(2, dao.allAccounts().size)
        assertEquals("GBP", repo.settings.current.currencyCode)
        db.close()
    }
}
