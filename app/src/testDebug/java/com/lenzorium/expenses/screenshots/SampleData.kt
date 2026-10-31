package com.lenzorium.expenses.screenshots

import com.lenzorium.expenses.data.Account
import com.lenzorium.expenses.data.AccountKind
import com.lenzorium.expenses.data.AppSettings
import com.lenzorium.expenses.data.DefaultCategories
import com.lenzorium.expenses.data.Loan
import com.lenzorium.expenses.data.PayMethod
import com.lenzorium.expenses.data.Txn
import com.lenzorium.expenses.data.TxnType
import com.lenzorium.expenses.ui.LedgerState
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Realistic demo ledger used only for screenshots (never shipped in the app). */
object SampleData {
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.now(zone)
    private val monthStart = today.withDayOfMonth(1)

    private fun at(daysAgo: Long, h: Int, m: Int = 0, clampToMonth: Boolean = true): Long {
        var d = today.minusDays(daysAgo)
        if (clampToMonth && d.isBefore(monthStart)) d = monthStart
        return d.atTime(LocalTime.of(h, m)).atZone(zone).toInstant().toEpochMilli()
    }

    val categories = DefaultCategories.all.mapIndexed { i, c -> c.copy(id = i + 1L) }
    private fun cat(name: String) = categories.first { it.name == name }.id

    private var id = 0L
    val accounts = listOf(
        Account(1, "Cash", AccountKind.CASH, 15_000_00, false, 1),
        Account(2, "Bank", AccountKind.BANK, 70_000_00, false, 2),
        Account(3, "JazzCash", AccountKind.WALLET, 0, false, 3),
    )

    private fun t(type: TxnType, amount: Long, category: String?, ts: Long, party: String, method: PayMethod? = PayMethod.CARD, loanId: Long? = null, notes: String = "") =
        Txn(
            id = ++id, type = type, amountMinor = amount * 100, categoryId = category?.let { cat(it) }, timestamp = ts,
            party = party, notes = notes, loanId = loanId, requestId = "s$id", createdAt = 0,
            accountId = when {
                type == TxnType.LOAN_CHARGE -> null
                method == PayMethod.CASH -> 1L
                else -> 2L
            },
        )

    val loans = listOf(
        Loan(1, "Ali Raza", "Bike repair", 30_000_00, at(12, 18, clampToMonth = false), today.plusDays(20).toEpochDay(), "", false, "l1", 0),
        Loan(2, "HBL", "Personal loan", 120_000_00, at(60, 0, clampToMonth = false), today.plusMonths(5).toEpochDay(), "", true, "l2", 0),
        Loan(3, "Usman", "", 8_000_00, at(40, 12, clampToMonth = false), today.minusDays(5).toEpochDay(), "", false, "l3", 0),
    )

    val txns: List<Txn> = listOf(
        t(TxnType.EXPENSE, 1_250, "Food", at(0, 13, 10), "Savour Foods"),
        t(TxnType.EXPENSE, 450, "Transport", at(0, 9, 5), "Careem", PayMethod.CASH),
        t(TxnType.EXPENSE, 6_830, "Groceries", at(1, 19, 40), "Imtiaz Super Market"),
        t(TxnType.EXPENSE, 4_200, "Bills", at(1, 11, 0), "K-Electric", PayMethod.BANK),
        t(TxnType.EXPENSE, 900, "Food", at(2, 17, 30), "Chai Shai", PayMethod.CASH),
        t(TxnType.EXPENSE, 3_999, "Shopping", at(3, 16, 15), "Outfitters"),
        t(TxnType.EXPENSE, 1_800, "Entertainment", at(3, 21, 0), "Cinepax"),
        t(TxnType.LOAN_REPAYMENT, 10_000, null, at(5, 10, 0), "Ali Raza", PayMethod.BANK, loanId = 1),
        Txn(id = 100, type = TxnType.TRANSFER, amountMinor = 10_000_00, timestamp = at(4, 18, 30), notes = "ATM withdrawal", requestId = "s100", createdAt = 0, accountId = 2, toAccountId = 1),
        Txn(id = 101, type = TxnType.TRANSFER, amountMinor = 3_000_00, timestamp = at(9, 12, 0), notes = "Top-up", requestId = "s101", createdAt = 0, accountId = 2, toAccountId = 3),
        t(TxnType.EXPENSE, 2_350, "Health", at(6, 12, 20), "Dawaai"),
        t(TxnType.EXPENSE, 5_000, "Education", at(8, 20, 0), "Coursera"),
        t(TxnType.INCOME, 22_500, "Freelance", at(10, 15, 0), "Upwork", PayMethod.BANK),
        t(TxnType.LOAN_RECEIVED, 30_000, null, at(12, 18, 0, clampToMonth = false), "Ali Raza", PayMethod.CASH, loanId = 1, notes = "Bike repair"),
        t(TxnType.EXPENSE, 45_000, "Rent", at(29, 9, 0), "Landlord", PayMethod.BANK),
        t(TxnType.INCOME, 150_000, "Salary", at(30, 9, 0), "Employer", PayMethod.BANK),
        t(TxnType.LOAN_RECEIVED, 8_000, null, at(40, 12, 0, clampToMonth = false), "Usman", PayMethod.CASH, loanId = 3),
    ).sortedWith(compareByDescending<Txn> { it.timestamp }.thenByDescending { it.id })

    val settings = AppSettings(
        setupDone = true,
        currencyCode = "PKR",
        openingBalanceMinor = 85_000_00,
        trackingStartEpochDay = today.minusDays(60).toEpochDay(),
        lastBackupAt = System.currentTimeMillis() - 2L * 24 * 60 * 60 * 1000,
    )

    val state = LedgerState(loaded = true, settings = settings, txns = txns, loans = loans, categories = categories, accounts = accounts)
    val empty = LedgerState(loaded = true, settings = settings, txns = emptyList(), loans = emptyList(), categories = categories, accounts = accounts.take(2).map { it.copy(openingMinor = if (it.id == 1L) 15_000_00 else 70_000_00) })
}
