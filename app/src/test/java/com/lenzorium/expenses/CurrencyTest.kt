package com.lenzorium.expenses

import com.lenzorium.expenses.data.Account
import com.lenzorium.expenses.data.AccountKind
import com.lenzorium.expenses.data.Txn
import com.lenzorium.expenses.data.TxnType
import com.lenzorium.expenses.domain.LedgerMath
import org.junit.Assert.assertEquals
import org.junit.Test

/** Accounts in another currency are never added to the main total; exchanges move money in or out of it. */
class CurrencyTest {
    private val pkr = Account(1, "Bank", AccountKind.BANK, 100_000_00, false, 1)
    private val gbp = Account(2, "UK", AccountKind.BANK, 50_00, false, 2, currency = "GBP")
    private val foreign = setOf(2L)
    private fun t(id: Long, type: TxnType, amount: Long, acc: Long?, to: Long? = null, received: Long? = null, ts: Long = 1000) =
        Txn(id = id, type = type, amountMinor = amount, timestamp = ts, requestId = "r$id", createdAt = 0, accountId = acc, toAccountId = to, toAmountMinor = received)

    @Test
    fun exchangeAndForeignEntries() {
        val txns = listOf(
            t(1, TxnType.TRANSFER, 37_000_00, acc = 1, to = 2, received = 100_00),   // PKR 37,000 → £100
            t(2, TxnType.EXPENSE, 12_50, acc = 2),                                  // £12.50 spent in the UK
            t(3, TxnType.EXPENSE, 1_000_00, acc = 1),                               // PKR 1,000 spent
            t(4, TxnType.TRANSFER, 20_00, acc = 2, to = 1, received = 7_400_00),    // £20 → PKR 7,400
        )
        // Main total (PKR only): 100,000 − 37,000 − 1,000 + 7,400
        assertEquals(69_400_00L, LedgerMath.availableBalance(pkr.openingMinor, txns, foreign = foreign))
        // Each account in its own currency.
        assertEquals(69_400_00L, LedgerMath.accountBalance(pkr, txns))
        assertEquals(50_00L + 100_00 - 12_50 - 20_00, LedgerMath.accountBalance(gbp, txns))
        // Reports: the £12.50 is not a PKR expense; exchanges are reported separately.
        val p = LedgerMath.periodTotals(txns, 0, 10_000, foreign)
        assertEquals(1_000_00L, p.expenses)
        assertEquals(-37_000_00L + 7_400_00, p.exchangeNet)
        // Same-currency transfers still change nothing.
        assertEquals(0L, LedgerMath.cashDelta(t(5, TxnType.TRANSFER, 500, acc = 1, to = 3), foreign))
    }
}

class BackupCryptoTest {
    private fun roundTrip(size: Int, password: String = "s3cret pass") {
        val data = ByteArray(size) { (it * 31 + 7).toByte() }
        val bytes = java.io.ByteArrayOutputStream()
        com.lenzorium.expenses.backup.BackupCrypto.encrypt(bytes, password).use { it.write(data) }
        val all = bytes.toByteArray()
        org.junit.Assert.assertTrue(com.lenzorium.expenses.backup.BackupCrypto.isEncrypted(all))
        val input = java.io.ByteArrayInputStream(all).also { it.skip(6) }
        val back = com.lenzorium.expenses.backup.BackupCrypto.decrypt(input, password).readBytes()
        org.junit.Assert.assertArrayEquals(data, back)
    }

    @org.junit.Test fun smallAndMultiChunkFilesRoundTrip() { roundTrip(0); roundTrip(1000); roundTrip((1 shl 20) * 2 + 123) }

    @org.junit.Test fun wrongPasswordTamperingAndTruncationAreRejected() {
        val bytes = java.io.ByteArrayOutputStream()
        com.lenzorium.expenses.backup.BackupCrypto.encrypt(bytes, "right").use { it.write(ByteArray(5000) { 1 }) }
        val all = bytes.toByteArray()
        fun fails(b: ByteArray, pw: String) = runCatching {
            com.lenzorium.expenses.backup.BackupCrypto.decrypt(java.io.ByteArrayInputStream(b).also { it.skip(6) }, pw).readBytes()
        }.isFailure
        org.junit.Assert.assertTrue(fails(all, "wrong"))
        org.junit.Assert.assertTrue(fails(all.copyOf().also { it[it.size - 3] = (it[it.size - 3] + 1).toByte() }, "right"))
        org.junit.Assert.assertTrue(fails(all.copyOf(all.size - 10), "right"))
    }
}
