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
import com.lenzorium.expenses.ui.PlanData
import com.lenzorium.expenses.data.Budget
import com.lenzorium.expenses.data.Frequency
import com.lenzorium.expenses.data.Goal
import com.lenzorium.expenses.data.GoalEntry
import com.lenzorium.expenses.data.Recurring
import com.lenzorium.expenses.data.Template
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
        Account(4, "UK Account", AccountKind.BANK, 250_00, false, 4, currency = "GBP"),
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
        t(TxnType.EXPENSE, 1_250, "Food", at(0, 13, 10), "Savour Foods").copy(tags = "family"),
        t(TxnType.EXPENSE, 450, "Transport", at(0, 9, 5), "Careem", PayMethod.CASH),
        t(TxnType.EXPENSE, 6_830, "Groceries", at(1, 19, 40), "Imtiaz Super Market"),
        t(TxnType.EXPENSE, 4_200, "Bills", at(1, 11, 0), "K-Electric", PayMethod.BANK),
        t(TxnType.EXPENSE, 900, "Food", at(2, 17, 30), "Chai Shai", PayMethod.CASH),
        t(TxnType.EXPENSE, 3_999, "Shopping", at(3, 16, 15), "Outfitters"),
        t(TxnType.EXPENSE, 1_800, "Entertainment", at(3, 21, 0), "Cinepax").copy(tags = "family,weekend"),
        t(TxnType.LOAN_REPAYMENT, 10_000, null, at(5, 10, 0), "Ali Raza", PayMethod.BANK, loanId = 1),
        Txn(id = 100, type = TxnType.TRANSFER, amountMinor = 10_000_00, timestamp = at(4, 18, 30), notes = "ATM withdrawal", requestId = "s100", createdAt = 0, accountId = 2, toAccountId = 1),
        Txn(id = 201, type = TxnType.SAFE_GIVEN, amountMinor = 50_000_00, timestamp = at(9, 10, 0), party = "Keep for Eid shopping", requestId = "s201", createdAt = 0, accountId = 1, personId = 1, dueEpochDay = today.plusDays(20).toEpochDay()),
        Txn(id = 202, type = TxnType.SAFE_RETURNED, amountMinor = 10_000_00, timestamp = at(6, 18, 0), requestId = "s202", createdAt = 0, accountId = 1, personId = 1),
        Txn(id = 203, type = TxnType.SAFE_RETURNED, amountMinor = 15_000_00, timestamp = at(3, 18, 0), requestId = "s203", createdAt = 0, accountId = 2, personId = 1),
        Txn(id = 204, type = TxnType.SAFE_RETURNED, amountMinor = 5_000_00, timestamp = at(1, 18, 0), requestId = "s204", createdAt = 0, accountId = 1, personId = 1),
        Txn(id = 205, type = TxnType.SAFE_GIVEN, amountMinor = 20_000_00, timestamp = at(9, 12, 0), party = "Shop float", requestId = "s205", createdAt = 0, accountId = 2, personId = 2, dueEpochDay = today.minusDays(1).toEpochDay()),
        Txn(id = 206, type = TxnType.SAFE_GIVEN, amountMinor = 8_000_00, timestamp = at(8, 12, 0), requestId = "s206", createdAt = 0, accountId = 1, personId = 3),
        Txn(id = 207, type = TxnType.SAFE_RETURNED, amountMinor = 8_000_00, timestamp = at(2, 12, 0), requestId = "s207", createdAt = 0, accountId = 1, personId = 3),
        Txn(id = 102, type = TxnType.TRANSFER, amountMinor = 37_000_00, toAmountMinor = 100_00, timestamp = at(7, 11, 0), notes = "Sent to UK", requestId = "s102", createdAt = 0, accountId = 2, toAccountId = 4),
        Txn(id = 103, type = TxnType.EXPENSE, amountMinor = 12_50, categoryId = cat("Food"), timestamp = at(6, 13, 0), party = "Pret", requestId = "s103", createdAt = 0, accountId = 4),
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
        profileName = "Ahmed Khan",
    )

    private val month1 = monthStart.toEpochDay()
    private val td = today.toEpochDay()
    val plans = PlanData(
        budgets = listOf(
            Budget(1, null, 120_000_00, false, month1, 0),
            Budget(2, cat("Food"), 3_000_00, false, month1, 0),
            Budget(3, cat("Shopping"), 5_000_00, true, month1, 0),
            Budget(4, cat("Transport"), 4_000_00, false, month1, 0),
        ),
        recurring = listOf(
            Recurring(1, "Netflix", TxnType.EXPENSE, 1_100_00, cat("Entertainment"), 2, "Netflix", Frequency.MONTHLY, 1, td - 31, null, td, true, 1, true, "", 0),
            Recurring(2, "PTCL Internet", TxnType.EXPENSE, 3_500_00, cat("Bills"), 2, "PTCL", Frequency.MONTHLY, 1, td - 34, null, td - 3, true, 1, true, "", 0),
            Recurring(3, "Rent", TxnType.EXPENSE, 45_000_00, cat("Rent"), 2, "Landlord", Frequency.MONTHLY, 1, td - 29 + 30, null, td + 1, true, 3, true, "", 0),
            Recurring(4, "Salary", TxnType.INCOME, 150_000_00, cat("Salary"), 2, "Employer", Frequency.MONTHLY, 1, td - 30 + 30, null, td + 2, true, 0, true, "", 0),
            Recurring(5, "School fees", TxnType.EXPENSE, 12_000_00, cat("Education"), 2, "", Frequency.MONTHLY, 3, td + 12, null, td + 12, true, 7, true, "", 0),
        ),
        templates = listOf(
            Template(1, "Chai", TxnType.EXPENSE, 150_00, cat("Food"), 1, "Chai Shai", "", 1),
            Template(2, "Petrol", TxnType.EXPENSE, 0, cat("Transport"), 1, "Shell", "", 2),
            Template(3, "Careem", TxnType.EXPENSE, 0, cat("Transport"), 2, "Careem", "", 3),
        ),
        goals = listOf(
            Goal(1, "New phone", 150_000_00, today.plusMonths(5).toEpochDay(), 2, "goal", 0xFF4A9EDBL, false, 0),
            Goal(2, "Emergency fund", 100_000_00, null, 2, "goal", 0xFF1E9185L, false, 0),
            Goal(3, "Eid shopping", 20_000_00, today.plusMonths(1).toEpochDay(), 1, "goal", 0xFFCCA34CL, false, 0),
        ),
        goalEntries = listOf(
            GoalEntry(1, 1, 30_000_00, td - 20, "Bonus", 0),
            GoalEntry(2, 1, 15_000_00, td - 5, "", 0),
            GoalEntry(3, 2, 35_000_00, td - 40, "", 0),
            GoalEntry(4, 3, 20_000_00, td - 2, "", 0),
        ),
    )

    private val holders = listOf(
        com.lenzorium.expenses.data.Holder(1, "Bilal Ahmed", "0300 1234567", "Brother", 0),
        com.lenzorium.expenses.data.Holder(2, "Usman (shop)", "0321 7654321", "", 0),
        com.lenzorium.expenses.data.Holder(3, "Ayesha Khan", "", "", 0),
    )
    private val audit = listOf(
        com.lenzorium.expenses.data.AuditEntry(1, 201, 1, "CREATED", "Gave Rs 50,000.00 to Bilal Ahmed from Cash", System.currentTimeMillis() - 9 * 86_400_000L),
        com.lenzorium.expenses.data.AuditEntry(2, 203, 1, "EDITED", "Edited: amount Rs 12,000.00 → Rs 15,000.00", System.currentTimeMillis() - 3 * 86_400_000L),
    )
    val state = LedgerState(loaded = true, settings = settings, txns = txns.sortedByDescending { it.timestamp }, loans = loans, categories = categories, accounts = accounts, plans = plans.copy(holders = holders, audit = audit))
    val empty = LedgerState(loaded = true, settings = settings, txns = emptyList(), loans = emptyList(), categories = categories, accounts = accounts.take(2).map { it.copy(openingMinor = if (it.id == 1L) 15_000_00 else 70_000_00) })
}
