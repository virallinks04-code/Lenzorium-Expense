package com.lenzorium.expenses

import com.lenzorium.expenses.backup.BackupCodec
import com.lenzorium.expenses.backup.BackupData
import com.lenzorium.expenses.backup.BackupFormatException
import com.lenzorium.expenses.data.AppSettings
import com.lenzorium.expenses.data.DefaultCategories
import com.lenzorium.expenses.data.Loan
import com.lenzorium.expenses.data.PayMethod
import com.lenzorium.expenses.data.Txn
import com.lenzorium.expenses.data.TxnType
import com.lenzorium.expenses.domain.Currencies
import com.lenzorium.expenses.domain.Money
import com.lenzorium.expenses.domain.Period
import com.lenzorium.expenses.domain.PeriodKind
import com.lenzorium.expenses.domain.ReportBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class MoneyAndBackupTest {
    @Test
    fun parsesAmounts() {
        assertEquals(125050L, Money.parse("1,250.50", 2))
        assertEquals(100L, Money.parse("1", 2))
        assertEquals(150L, Money.parse("1.5", 2))
        assertEquals(1500L, Money.parse("1500", 0))
        assertEquals(1234L, Money.parse("1.234", 3))
        assertNull(Money.parse("1.234", 2))
        assertNull(Money.parse("1.5", 0))
        assertNull(Money.parse("abc", 2))
        assertNull(Money.parse("", 2))
        assertNull(Money.parse(".", 2))
        assertEquals(0L, Money.parse("0", 2))
    }

    @Test
    fun formatsAmountsWithCurrency() {
        val pkr = Currencies.find("PKR")
        assertEquals("PKR 1,500.00", Money.format(150000, pkr))
        assertEquals("−PKR 25.50", Money.format(-2550, pkr))
        assertEquals("+PKR 5.00", Money.formatSigned(500, pkr))
        assertEquals("JPY 1,500", Money.format(1500, Currencies.find("JPY")))
        assertEquals("1250.50", Money.toInput(125050, 2))
    }

    private fun sample(): BackupData {
        val cats = DefaultCategories.all.mapIndexed { i, c -> c.copy(id = i + 1L) }
        val loans = listOf(
            Loan(id = 1, lender = "Ali", title = "Bike", principalMinor = 30000, receivedAt = 1000, dueEpochDay = 20000, isOpening = false, requestId = "l1", createdAt = 1),
            Loan(id = 2, lender = "Bank", title = "", principalMinor = 75000, receivedAt = 500, isOpening = true, requestId = "l2", createdAt = 1),
        )
        val txns = listOf(
            Txn(id = 1, type = TxnType.EXPENSE, amountMinor = 20000, categoryId = 1, timestamp = 2000, party = "Shop, \"Main\"", paymentMethod = PayMethod.CARD, notes = "line1\nline2", receiptFile = "r_abc.jpg", requestId = "t1", createdAt = 1),
            Txn(id = 2, type = TxnType.LOAN_RECEIVED, amountMinor = 30000, timestamp = 1000, party = "Ali", paymentMethod = PayMethod.CASH, loanId = 1, requestId = "t2", createdAt = 1),
            Txn(id = 3, type = TxnType.LOAN_REPAYMENT, amountMinor = 10000, timestamp = 3000, party = "Ali", paymentMethod = PayMethod.BANK, loanId = 1, requestId = "t3", createdAt = 1),
        )
        val settings = AppSettings(setupDone = true, currencyCode = "PKR", openingBalanceMinor = 100000, trackingStartEpochDay = 19000, pinHash = "h", pinSalt = "s")
        return BackupData(settings, cats, loans, txns, 123L)
    }

    @Test
    fun backupRoundTripKeepsEverything() {
        val d = sample()
        val back = BackupCodec.decode(BackupCodec.encode(d))
        assertEquals(d.categories, back.categories)
        assertEquals(d.loans, back.loans)
        assertEquals(d.txns, back.txns)
        assertEquals(d.settings, back.settings)
    }

    @Test
    fun corruptBackupIsRejected() {
        assertThrows(BackupFormatException::class.java) { BackupCodec.decode("not json") }
        assertThrows(BackupFormatException::class.java) { BackupCodec.decode("{\"format\":\"other\"}") }
        val broken = BackupCodec.encode(sample()).replace("\"loanId\":1", "\"loanId\":99")
        assertThrows(BackupFormatException::class.java) { BackupCodec.decode(broken) }
        val badFile = BackupCodec.encode(sample()).replace("r_abc.jpg", "../evil")
        assertThrows(BackupFormatException::class.java) { BackupCodec.decode(badFile) }
    }

    @Test
    fun reportOpeningAndClosingBalance() {
        val z = ZoneOffset.UTC
        fun ms(d: LocalDate, h: Int = 12) = d.atTime(h, 0).toInstant(z).toEpochMilli()
        val oct1 = LocalDate.of(2026, 10, 1)
        val txns = listOf(
            Txn(id = 1, type = TxnType.INCOME, amountMinor = 50000, timestamp = ms(LocalDate.of(2026, 9, 20)), requestId = "a", createdAt = 0),
            Txn(id = 2, type = TxnType.EXPENSE, amountMinor = 20000, categoryId = 1, timestamp = ms(LocalDate.of(2026, 10, 5)), requestId = "b", createdAt = 0),
            Txn(id = 3, type = TxnType.LOAN_RECEIVED, amountMinor = 30000, loanId = 1, timestamp = ms(LocalDate.of(2026, 10, 6)), requestId = "c", createdAt = 0),
            Txn(id = 4, type = TxnType.EXPENSE, amountMinor = 1000, categoryId = 1, timestamp = ms(LocalDate.of(2026, 11, 2)), requestId = "d", createdAt = 0),
        )
        val loans = listOf(Loan(id = 1, lender = "Ali", title = "", principalMinor = 30000, receivedAt = ms(LocalDate.of(2026, 10, 6)), requestId = "l", createdAt = 0))
        val r = ReportBuilder.build(Period.of(PeriodKind.MONTH, oct1), 100000, txns, loans, DefaultCategories.all.mapIndexed { i, c -> c.copy(id = i + 1L) }, z, LocalDate.of(2026, 11, 10))
        assertEquals(150000L, r.openingBalance)   // 1000 + 500 income in September
        assertEquals(160000L, r.closingBalance)   // − 200 expense + 300 borrowed
        assertEquals(20000L, r.totals.expenses)
        assertEquals(30000L, r.totals.borrowed)
        assertEquals(0L, r.totals.income)
        assertEquals(30000L, r.debtAtEnd)
        assertEquals(31, r.trendValues.size)
        assertEquals("Food", r.expenseByCategory.first().name)
    }
}
