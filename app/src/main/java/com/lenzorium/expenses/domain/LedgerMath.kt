package com.lenzorium.expenses.domain

import com.lenzorium.expenses.data.Account
import com.lenzorium.expenses.data.Loan
import com.lenzorium.expenses.data.Txn
import com.lenzorium.expenses.data.TxnType

enum class LoanStatus(val label: String) {
    ACTIVE("Active"),
    PARTIALLY_REPAID("Partially repaid"),
    REPAID("Repaid"),
}

data class LoanSummary(
    val loan: Loan,
    val borrowed: Long,
    val charges: Long,
    val repaid: Long,
    val outstanding: Long,
    val status: LoanStatus,
    val overdue: Boolean,
)

data class PeriodTotals(
    val income: Long,
    val expenses: Long,
    val borrowed: Long,
    val repayments: Long,
    val charges: Long,
    /** categoryId (null = uncategorised) -> total expense */
    val expensesByCategory: Map<Long?, Long>,
    val incomeByCategory: Map<Long?, Long>,
    /** Money moved into (+) or out of (−) the main currency through transfers with foreign-currency accounts. */
    val exchangeNet: Long = 0L,
    /** Money given for safekeeping / collected back in the period (neither expense nor income). */
    val safeGiven: Long = 0L,
    val safeReturned: Long = 0L,
)

/**
 * All balance rules live here, independent of Android, so they can be unit-tested.
 *
 * Available Balance = Opening Balance + Income + New Loans Received − Expenses − Loan Repayments
 * Outstanding Debt  = Opening Debt + New Borrowing + Recorded Loan Charges − Repayments
 * Balance After Clearing Debt = Available Balance − Outstanding Debt
 */
object LedgerMath {

    /**
     * Effect of a transaction on total available money in the main currency. Loan charges change debt only;
     * transfers just move money. Accounts in [foreign] hold another currency and are kept out of the total:
     * their own entries count 0, and a transfer to/from one counts as money leaving/arriving (an exchange).
     */
    fun cashDelta(t: Txn, foreign: Set<Long> = emptySet()): Long = when (t.type) {
        TxnType.TRANSFER -> {
            val fromForeign = t.accountId in foreign
            val toForeign = t.toAccountId in foreign
            when {
                !fromForeign && toForeign -> -t.amountMinor
                fromForeign && !toForeign -> t.toAmountMinor ?: t.amountMinor
                else -> 0L
            }
        }
        TxnType.LOAN_CHARGE -> 0L
        else -> if (t.accountId != null && t.accountId in foreign) 0L else when (t.type) {
            TxnType.INCOME, TxnType.LOAN_RECEIVED, TxnType.SAFE_RETURNED -> t.amountMinor
            else -> -t.amountMinor
        }
    }

    /** Amount that arrives in the destination account of a transfer (differs only across currencies). */
    fun received(t: Txn): Long = t.toAmountMinor ?: t.amountMinor

    /** Effect of a transaction on one account's balance. */
    fun accountDelta(t: Txn, accountId: Long): Long = when (t.type) {
        TxnType.INCOME, TxnType.LOAN_RECEIVED, TxnType.SAFE_RETURNED -> if (t.accountId == accountId) t.amountMinor else 0L
        TxnType.EXPENSE, TxnType.LOAN_REPAYMENT, TxnType.SAFE_GIVEN -> if (t.accountId == accountId) -t.amountMinor else 0L
        TxnType.TRANSFER ->
            (if (t.toAccountId == accountId) received(t) else 0L) - (if (t.accountId == accountId) t.amountMinor else 0L)
        TxnType.LOAN_CHARGE -> 0L
    }

    /** Account balance = its opening balance + money in − money out (entries with timestamp < [beforeMillis] if given). */
    fun accountBalance(account: Account, txns: List<Txn>, beforeMillis: Long? = null): Long {
        var total = account.openingMinor
        for (t in txns) {
            if (beforeMillis == null || t.timestamp < beforeMillis) total = Math.addExact(total, accountDelta(t, account.id))
        }
        return total
    }

    /** Opening balance of the whole ledger: the sum of every account's opening balance. */
    fun totalOpening(accounts: List<Account>): Long = accounts.sumOf { it.openingMinor }

    /** Available balance including every transaction with timestamp < [beforeMillis] (all if null). */
    fun availableBalance(openingBalance: Long, txns: List<Txn>, beforeMillis: Long? = null, foreign: Set<Long> = emptySet()): Long {
        var total = openingBalance
        for (t in txns) {
            if (beforeMillis == null || t.timestamp < beforeMillis) total = Math.addExact(total, cashDelta(t, foreign))
        }
        return total
    }

    /** Opening debt: loans that existed before tracking began. */
    fun openingDebt(loans: List<Loan>): Long = loans.filter { it.isOpening }.sumOf { it.principalMinor }

    /** Outstanding debt counting events with timestamp < [beforeMillis] (all if null). */
    fun outstandingDebt(loans: List<Loan>, txns: List<Txn>, beforeMillis: Long? = null): Long {
        fun inRange(ts: Long) = beforeMillis == null || ts < beforeMillis
        var debt = 0L
        for (l in loans) {
            // Opening loans count from the start; new loans when received.
            if (l.isOpening || inRange(l.receivedAt)) debt += l.principalMinor
        }
        for (t in txns) {
            if (!inRange(t.timestamp)) continue
            when (t.type) {
                TxnType.LOAN_CHARGE -> debt += t.amountMinor
                TxnType.LOAN_REPAYMENT -> debt -= t.amountMinor
                else -> Unit
            }
        }
        return debt
    }

    fun loanSummary(loan: Loan, txns: List<Txn>, todayEpochDay: Long, beforeMillis: Long? = null): LoanSummary {
        var charges = 0L
        var repaid = 0L
        for (t in txns) {
            if (t.loanId != loan.id) continue
            if (beforeMillis != null && t.timestamp >= beforeMillis) continue
            when (t.type) {
                TxnType.LOAN_CHARGE -> charges += t.amountMinor
                TxnType.LOAN_REPAYMENT -> repaid += t.amountMinor
                else -> Unit
            }
        }
        val counted = loan.isOpening || beforeMillis == null || loan.receivedAt < beforeMillis
        val borrowed = if (counted) loan.principalMinor else 0L
        val outstanding = borrowed + charges - repaid
        val status = when {
            outstanding <= 0L -> LoanStatus.REPAID
            repaid > 0L -> LoanStatus.PARTIALLY_REPAID
            else -> LoanStatus.ACTIVE
        }
        val overdue = outstanding > 0 && loan.dueEpochDay != null && loan.dueEpochDay < todayEpochDay
        return LoanSummary(loan, borrowed, charges, repaid, outstanding, status, overdue)
    }

    /**
     * Walks a loan's history in date order and checks that no repayment ever exceeds what was owed
     * at that point, and that nothing is dated before the money was received.
     * Returns a human-readable problem, or null when the history is valid.
     */
    fun validateLoanTimeline(loan: Loan, loanTxns: List<Txn>, format: (Long) -> String): String? {
        val events = loanTxns
            .filter { it.type == TxnType.LOAN_REPAYMENT || it.type == TxnType.LOAN_CHARGE }
            // Same moment: charges first, then repayments (most permissive, still never over-repays).
            .sortedWith(compareBy<Txn>({ it.timestamp }, { if (it.type == TxnType.LOAN_CHARGE) 0 else 1 }, { it.id }))
        var owed = loan.principalMinor
        for (e in events) {
            if (!loan.isOpening && e.timestamp < loan.receivedAt) {
                return "A ${e.type.label.lowercase()} is dated before the loan was received."
            }
            if (e.type == TxnType.LOAN_CHARGE) {
                owed += e.amountMinor
            } else {
                if (e.amountMinor > owed) {
                    return "This repayment of ${format(e.amountMinor)} exceeds the ${format(owed)} owed on that date."
                }
                owed -= e.amountMinor
            }
        }
        return null
    }

    /** Totals for transactions with start <= timestamp < endExclusive. */
    fun periodTotals(txns: List<Txn>, start: Long, endExclusive: Long, foreign: Set<Long> = emptySet()): PeriodTotals {
        var exchange = 0L
        var safeGiven = 0L
        var safeReturned = 0L
        var income = 0L
        var expenses = 0L
        var borrowed = 0L
        var repayments = 0L
        var charges = 0L
        val byCat = HashMap<Long?, Long>()
        val incByCat = HashMap<Long?, Long>()
        for (t in txns) {
            if (t.timestamp < start || t.timestamp >= endExclusive) continue
            if (t.type == TxnType.TRANSFER) { exchange += cashDelta(t, foreign); continue }
            if (t.accountId != null && t.accountId in foreign) continue
            when (t.type) {
                TxnType.EXPENSE -> {
                    expenses += t.amountMinor
                    byCat[t.categoryId] = (byCat[t.categoryId] ?: 0L) + t.amountMinor
                }
                TxnType.INCOME -> {
                    income += t.amountMinor
                    incByCat[t.categoryId] = (incByCat[t.categoryId] ?: 0L) + t.amountMinor
                }
                TxnType.LOAN_RECEIVED -> borrowed += t.amountMinor
                TxnType.LOAN_REPAYMENT -> repayments += t.amountMinor
                TxnType.LOAN_CHARGE -> charges += t.amountMinor
                TxnType.SAFE_GIVEN -> safeGiven += t.amountMinor
                TxnType.SAFE_RETURNED -> safeReturned += t.amountMinor
                TxnType.TRANSFER -> Unit
            }
        }
        return PeriodTotals(income, expenses, borrowed, repayments, charges, byCat, incByCat, exchange, safeGiven, safeReturned)
    }
}
