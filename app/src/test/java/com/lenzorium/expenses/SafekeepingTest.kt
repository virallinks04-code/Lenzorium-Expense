package com.lenzorium.expenses

import com.lenzorium.expenses.backup.BackupCodec
import com.lenzorium.expenses.backup.BackupData
import com.lenzorium.expenses.data.Account
import com.lenzorium.expenses.data.AccountKind
import com.lenzorium.expenses.data.AppSettings
import com.lenzorium.expenses.data.AuditEntry
import com.lenzorium.expenses.data.DefaultCategories
import com.lenzorium.expenses.data.Holder
import com.lenzorium.expenses.data.Txn
import com.lenzorium.expenses.data.TxnType
import com.lenzorium.expenses.domain.LedgerMath
import com.lenzorium.expenses.domain.SafeMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SafekeepingTest {
    private val cash = Account(1, "Cash", AccountKind.CASH, 100_000_00, false, 1)
    private val bank = Account(2, "Bank", AccountKind.BANK, 200_000_00, false, 2)
    private fun t(id: Long, type: TxnType, amount: Long, acc: Long, person: Long = 1, ts: Long = id * 1000) =
        Txn(id = id, type = type, amountMinor = amount, timestamp = ts, requestId = "r$id", createdAt = 0, accountId = acc, personId = person)
    private fun fmt(v: Long) = "Rs ${v / 100}"

    /** The example from the request: 50,000 given, 10,000 + 15,000 + 5,000 back → 20,000 remaining. */
    @Test
    fun partialRecoveriesLeaveTheRightRemaining() {
        val txns = listOf(
            t(1, TxnType.SAFE_GIVEN, 50_000_00, acc = 1),
            t(2, TxnType.SAFE_RETURNED, 10_000_00, acc = 1),
            t(3, TxnType.SAFE_RETURNED, 15_000_00, acc = 2),
            t(4, TxnType.SAFE_RETURNED, 5_000_00, acc = 1),
        )
        assertEquals(20_000_00L, SafeMath.remaining(txns, 1))
        val s = SafeMath.summaries(listOf(Holder(1, "Bilal", createdAt = 0)), txns).single()
        assertEquals(50_000_00L, s.given); assertEquals(30_000_00L, s.returned); assertEquals(20_000_00L, s.remaining)
        val tot = SafeMath.totals(listOf(s))
        assertEquals(20_000_00L, tot.outstanding); assertEquals(1, tot.peopleHolding)
    }

    @Test
    fun cashAndBankMoveButItIsNeverExpenseOrIncome() {
        val txns = listOf(
            t(1, TxnType.SAFE_GIVEN, 50_000_00, acc = 1),      // from Cash
            t(2, TxnType.SAFE_RETURNED, 10_000_00, acc = 2),   // back into Bank
        )
        assertEquals(100_000_00L - 50_000_00, LedgerMath.accountBalance(cash, txns))
        assertEquals(200_000_00L + 10_000_00, LedgerMath.accountBalance(bank, txns))
        assertEquals(300_000_00L - 40_000_00, LedgerMath.availableBalance(300_000_00, txns))
        val p = LedgerMath.periodTotals(txns, 0, Long.MAX_VALUE)
        assertEquals(0L, p.expenses); assertEquals(0L, p.income)
        assertEquals(50_000_00L, p.safeGiven); assertEquals(10_000_00L, p.safeReturned)
        // Available + still with others = everything we own in cash terms.
        assertEquals(300_000_00L, LedgerMath.availableBalance(300_000_00, txns) + SafeMath.remaining(txns, 1))
    }

    @Test
    fun recoveriesCanNeverExceedWhatWasGiven() {
        val given = t(1, TxnType.SAFE_GIVEN, 50_000_00, acc = 1)
        val back = t(2, TxnType.SAFE_RETURNED, 30_000_00, acc = 1)
        val all = listOf(given, back)
        // Receiving more than the 20,000 outstanding is refused; exactly 20,000 is fine.
        assertNotNull(SafeMath.check(all, null, t(3, TxnType.SAFE_RETURNED, 20_000_01, acc = 1), ::fmt))
        assertNull(SafeMath.check(all, null, t(3, TxnType.SAFE_RETURNED, 20_000_00, acc = 1), ::fmt))
        // Editing the gift below what's already been returned is refused.
        assertNotNull(SafeMath.check(all, given, given.copy(amountMinor = 29_999_00), ::fmt))
        assertNull(SafeMath.check(all, given, given.copy(amountMinor = 30_000_00), ::fmt))
        // Deleting the gift while recoveries exist is refused; deleting a recovery is fine.
        assertNotNull(SafeMath.check(all, given, null, ::fmt))
        assertNull(SafeMath.check(all, back, null, ::fmt))
        // Editing a recovery up to the full amount is fine (its own old value doesn't count against it).
        assertNull(SafeMath.check(all, back, back.copy(amountMinor = 50_000_00), ::fmt))
        // Moving a recovery to someone who never received money is refused.
        assertNotNull(SafeMath.check(all, back, back.copy(personId = 2), ::fmt))
    }

    @Test
    fun overdueAndSettled() {
        val today = 20_000L
        val txns = listOf(t(1, TxnType.SAFE_GIVEN, 1000, acc = 1).copy(dueEpochDay = today - 1))
        val s = SafeMath.summaries(listOf(Holder(1, "A", createdAt = 0)), txns).single()
        assertEquals(true, s.overdue(today))
        val done = SafeMath.summaries(listOf(Holder(1, "A", createdAt = 0)), txns + t(2, TxnType.SAFE_RETURNED, 1000, acc = 1)).single()
        assertEquals(true, done.settled); assertEquals(false, done.overdue(today))
    }

    @Test
    fun backupKeepsPeopleEntriesAndAuditTrail() {
        val cats = DefaultCategories.all.mapIndexed { i, c -> c.copy(id = i + 1L) }
        val txns = listOf(
            t(1, TxnType.SAFE_GIVEN, 50_000_00, acc = 1).copy(party = "Eid", dueEpochDay = 21_000),
            t(2, TxnType.SAFE_RETURNED, 10_000_00, acc = 2),
        )
        val d = BackupData(
            AppSettings(setupDone = true, accountsMigrated = true), cats, emptyList(), txns, 1L, listOf(cash, bank),
            holders = listOf(Holder(1, "Bilal", "0300 1234567", "brother", 5)),
            audit = listOf(AuditEntry(1, 1, 1, "CREATED", "Gave Rs 50,000", 9)),
        )
        val back = BackupCodec.decode(BackupCodec.encode(d))
        assertEquals(d.txns, back.txns); assertEquals(d.holders, back.holders); assertEquals(d.audit, back.audit)
    }
}
