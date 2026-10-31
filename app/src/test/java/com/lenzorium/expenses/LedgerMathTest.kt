package com.lenzorium.expenses

import com.lenzorium.expenses.data.Loan
import com.lenzorium.expenses.data.PayMethod
import com.lenzorium.expenses.data.Txn
import com.lenzorium.expenses.data.TxnType
import com.lenzorium.expenses.domain.LedgerMath
import com.lenzorium.expenses.domain.LoanStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Amounts in these tests are whole units ×100 (2-decimal currency). */
class LedgerMathTest {
    private fun u(v: Long) = v * 100
    private var nextId = 1L
    private fun txn(type: TxnType, amount: Long, ts: Long = 1_000L, loanId: Long? = null) = Txn(
        id = nextId++, type = type, amountMinor = u(amount), timestamp = ts, paymentMethod = PayMethod.CASH,
        loanId = loanId, requestId = "r${nextId}", createdAt = 0L,
    )
    private fun loan(id: Long, amount: Long, opening: Boolean = false, receivedAt: Long = 1_000L, due: Long? = null) = Loan(
        id = id, lender = "Lender$id", title = "", principalMinor = u(amount), receivedAt = receivedAt,
        dueEpochDay = due, isOpening = opening, requestId = "l$id", createdAt = 0L,
    )
    private val fmt: (Long) -> String = { (it / 100).toString() }

    /** Spec section 13: the exact verification example. */
    @Test
    fun specVerificationExample() {
        val opening = u(1000)
        val l = loan(1, 300)
        val txns = listOf(
            txn(TxnType.INCOME, 500),
            txn(TxnType.LOAN_RECEIVED, 300, loanId = 1),
            txn(TxnType.EXPENSE, 200),
            txn(TxnType.LOAN_REPAYMENT, 100, ts = 2_000L, loanId = 1),
        )
        val available = LedgerMath.availableBalance(opening, txns)
        val debt = LedgerMath.outstandingDebt(listOf(l), txns)
        assertEquals(u(1500), available)
        assertEquals(u(200), debt)
        assertEquals(u(1300), available - debt)
    }

    /** Spec section 13: an existing (opening) loan changes debt without increasing available balance. */
    @Test
    fun openingLoanAddsDebtButNotCash() {
        val opening = u(1000)
        val existing = loan(1, 750, opening = true)
        val txns = emptyList<Txn>()
        assertEquals(u(1000), LedgerMath.availableBalance(opening, txns))
        assertEquals(u(750), LedgerMath.outstandingDebt(listOf(existing), txns))
        assertEquals(u(750), LedgerMath.openingDebt(listOf(existing)))
        assertEquals(u(250), LedgerMath.availableBalance(opening, txns) - LedgerMath.outstandingDebt(listOf(existing), txns))
    }

    @Test
    fun repaymentOfOpeningLoanReducesBothBalanceAndDebt() {
        val existing = loan(1, 750, opening = true)
        val txns = listOf(txn(TxnType.LOAN_REPAYMENT, 250, loanId = 1))
        assertEquals(u(750), LedgerMath.availableBalance(u(1000), txns))
        assertEquals(u(500), LedgerMath.outstandingDebt(listOf(existing), txns))
    }

    @Test
    fun borrowingIsNotIncomeAndRepaymentIsNotExpense() {
        val txns = listOf(
            txn(TxnType.INCOME, 500),
            txn(TxnType.LOAN_RECEIVED, 300, loanId = 1),
            txn(TxnType.EXPENSE, 200),
            txn(TxnType.LOAN_REPAYMENT, 100, ts = 2_000L, loanId = 1),
        )
        val p = LedgerMath.periodTotals(txns, 0L, 10_000L)
        assertEquals(u(500), p.income)
        assertEquals(u(200), p.expenses)
        assertEquals(u(300), p.borrowed)
        assertEquals(u(100), p.repayments)
    }

    @Test
    fun loanChargeIncreasesDebtOnly() {
        val l = loan(1, 300)
        val txns = listOf(txn(TxnType.LOAN_RECEIVED, 300, loanId = 1), txn(TxnType.LOAN_CHARGE, 20, ts = 1_500L, loanId = 1))
        assertEquals(u(300), LedgerMath.availableBalance(0L, txns))
        assertEquals(u(320), LedgerMath.outstandingDebt(listOf(l), txns))
    }

    @Test
    fun loanStatusTransitions() {
        val l = loan(1, 300)
        val received = txn(TxnType.LOAN_RECEIVED, 300, loanId = 1)
        assertEquals(LoanStatus.ACTIVE, LedgerMath.loanSummary(l, listOf(received), 0).status)
        val part = txn(TxnType.LOAN_REPAYMENT, 100, ts = 2_000L, loanId = 1)
        val s1 = LedgerMath.loanSummary(l, listOf(received, part), 0)
        assertEquals(LoanStatus.PARTIALLY_REPAID, s1.status)
        assertEquals(u(200), s1.outstanding)
        val rest = txn(TxnType.LOAN_REPAYMENT, 200, ts = 3_000L, loanId = 1)
        val s2 = LedgerMath.loanSummary(l, listOf(received, part, rest), 0)
        assertEquals(LoanStatus.REPAID, s2.status)
        assertEquals(0L, s2.outstanding)
    }

    @Test
    fun overdueFlag() {
        val l = loan(1, 300, due = 100L)
        assertTrue(LedgerMath.loanSummary(l, emptyList(), todayEpochDay = 101L).overdue)
        assertTrue(!LedgerMath.loanSummary(l, emptyList(), todayEpochDay = 100L).overdue)
        val paid = listOf(txn(TxnType.LOAN_REPAYMENT, 300, ts = 2_000L, loanId = 1))
        assertTrue(!LedgerMath.loanSummary(l, paid, todayEpochDay = 200L).overdue)
    }

    @Test
    fun repaymentExceedingDebtIsRejected() {
        val l = loan(1, 300)
        val over = listOf(txn(TxnType.LOAN_REPAYMENT, 301, ts = 2_000L, loanId = 1))
        assertNotNull(LedgerMath.validateLoanTimeline(l, over, fmt))
        val exact = listOf(txn(TxnType.LOAN_REPAYMENT, 300, ts = 2_000L, loanId = 1))
        assertNull(LedgerMath.validateLoanTimeline(l, exact, fmt))
    }

    @Test
    fun backdatedRepaymentCannotExceedDebtAtThatTime() {
        // 300 borrowed, a 50 fee added on day 3, then 350 repaid on day 4: valid.
        val l = loan(1, 300, receivedAt = 1_000L)
        val fee = txn(TxnType.LOAN_CHARGE, 50, ts = 3_000L, loanId = 1)
        val repay = txn(TxnType.LOAN_REPAYMENT, 350, ts = 4_000L, loanId = 1)
        assertNull(LedgerMath.validateLoanTimeline(l, listOf(fee, repay), fmt))
        // Same repayment backdated to before the fee existed: only 300 was owed then.
        val backdated = repay.copy(timestamp = 2_000L)
        assertNotNull(LedgerMath.validateLoanTimeline(l, listOf(fee, backdated), fmt))
        // Repayment dated before the loan was received.
        val early = txn(TxnType.LOAN_REPAYMENT, 10, ts = 500L, loanId = 1)
        assertNotNull(LedgerMath.validateLoanTimeline(l, listOf(early), fmt))
    }

    @Test
    fun editingOrDeletingRecalculates() {
        val e = txn(TxnType.EXPENSE, 200)
        val before = LedgerMath.availableBalance(u(1000), listOf(e))
        assertEquals(u(800), before)
        val edited = e.copy(amountMinor = u(150))
        assertEquals(u(850), LedgerMath.availableBalance(u(1000), listOf(edited)))
        assertEquals(u(1000), LedgerMath.availableBalance(u(1000), emptyList()))
    }

    @Test
    fun balanceAsOfDateForReports() {
        val txns = listOf(txn(TxnType.INCOME, 500, ts = 1_000L), txn(TxnType.EXPENSE, 200, ts = 5_000L))
        assertEquals(u(1000), LedgerMath.availableBalance(u(1000), txns, beforeMillis = 1_000L))
        assertEquals(u(1500), LedgerMath.availableBalance(u(1000), txns, beforeMillis = 5_000L))
        assertEquals(u(1300), LedgerMath.availableBalance(u(1000), txns, beforeMillis = 5_001L))
    }

    @Test
    fun debtAsOfDateIgnoresLaterLoans() {
        val l1 = loan(1, 300, receivedAt = 1_000L)
        val l2 = loan(2, 200, receivedAt = 9_000L)
        val opening = loan(3, 100, opening = true, receivedAt = 50_000L)
        val txns = listOf(txn(TxnType.LOAN_REPAYMENT, 50, ts = 2_000L, loanId = 1))
        assertEquals(u(350), LedgerMath.outstandingDebt(listOf(l1, l2, opening), txns, beforeMillis = 5_000L))
        assertEquals(u(550), LedgerMath.outstandingDebt(listOf(l1, l2, opening), txns))
    }

    // ---- Accounts (Cash / Bank) ----
    private val cash = com.lenzorium.expenses.data.Account(id = 1, name = "Cash", kind = com.lenzorium.expenses.data.AccountKind.CASH, openingMinor = u(400))
    private val bank = com.lenzorium.expenses.data.Account(id = 2, name = "Bank", kind = com.lenzorium.expenses.data.AccountKind.BANK, openingMinor = u(600))
    private fun on(acc: Long, t: Txn) = t.copy(accountId = acc)

    /** The spec example split across Cash and Bank: account balances must add up to the same totals. */
    @Test
    fun accountsAddUpToAvailableBalance() {
        val l = loan(1, 300)
        val txns = listOf(
            on(2, txn(TxnType.INCOME, 500)),                         // salary into Bank
            on(1, txn(TxnType.LOAN_RECEIVED, 300, loanId = 1)),      // loan received in Cash
            on(1, txn(TxnType.EXPENSE, 200)),                        // spent from Cash
            on(2, txn(TxnType.LOAN_REPAYMENT, 100, ts = 2_000L, loanId = 1)), // repaid from Bank
        )
        val accounts = listOf(cash, bank)
        val opening = LedgerMath.totalOpening(accounts)
        assertEquals(u(1000), opening)
        assertEquals(u(500), LedgerMath.accountBalance(cash, txns))   // 400 + 300 − 200
        assertEquals(u(1000), LedgerMath.accountBalance(bank, txns))  // 600 + 500 − 100
        val available = LedgerMath.availableBalance(opening, txns)
        assertEquals(u(1500), available)
        assertEquals(available, accounts.sumOf { LedgerMath.accountBalance(it, txns) })
        assertEquals(u(200), LedgerMath.outstandingDebt(listOf(l), txns))
    }

    /** A transfer (e.g. ATM withdrawal) moves money between accounts without changing the total, income or expenses. */
    @Test
    fun transferMovesMoneyButNotTotal() {
        val atm = txn(TxnType.TRANSFER, 250).copy(accountId = 2, toAccountId = 1)
        val txns = listOf(atm)
        assertEquals(u(650), LedgerMath.accountBalance(cash, txns))
        assertEquals(u(350), LedgerMath.accountBalance(bank, txns))
        assertEquals(u(1000), LedgerMath.availableBalance(LedgerMath.totalOpening(listOf(cash, bank)), txns))
        val p = LedgerMath.periodTotals(txns, 0L, 10_000L)
        assertEquals(0L, p.income); assertEquals(0L, p.expenses); assertEquals(0L, p.borrowed); assertEquals(0L, p.repayments)
    }

    @Test
    fun accountBalanceAsOfDate() {
        val txns = listOf(on(1, txn(TxnType.EXPENSE, 100, ts = 5_000L)))
        assertEquals(u(400), LedgerMath.accountBalance(cash, txns, beforeMillis = 5_000L))
        assertEquals(u(300), LedgerMath.accountBalance(cash, txns))
    }

    @Test
    fun exactDecimalArithmetic() {
        // 0.10 + 0.20 must be exactly 0.30 (floating point would drift).
        val txns = listOf(
            Txn(id = 1, type = TxnType.INCOME, amountMinor = 10, timestamp = 1, requestId = "a", createdAt = 0),
            Txn(id = 2, type = TxnType.INCOME, amountMinor = 20, timestamp = 1, requestId = "b", createdAt = 0),
        )
        assertEquals(30L, LedgerMath.availableBalance(0L, txns))
    }
}
