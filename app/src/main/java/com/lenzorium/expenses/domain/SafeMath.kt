package com.lenzorium.expenses.domain

import com.lenzorium.expenses.data.Holder
import com.lenzorium.expenses.data.Txn
import com.lenzorium.expenses.data.TxnType

/** One person's safekeeping position. Remaining = total given − total recovered. */
data class HolderSummary(
    val holder: Holder,
    val given: Long,
    val returned: Long,
    val entries: List<Txn>,
    /** Earliest expected return date among the money still outstanding, if any was set. */
    val expectedBy: Long?,
    val lastActivity: Long,
) {
    val remaining: Long get() = given - returned
    val settled: Boolean get() = remaining <= 0 && given > 0
    val fraction: Float get() = if (given <= 0) 0f else (returned.toFloat() / given).coerceIn(0f, 1f)
    fun overdue(today: Long): Boolean = remaining > 0 && expectedBy != null && expectedBy < today
}

data class SafeTotals(val given: Long, val returned: Long, val peopleHolding: Int) {
    val outstanding: Long get() = given - returned
}

/** Money given for safekeeping: pure Kotlin rules, unit-tested. */
object SafeMath {
    fun given(txns: List<Txn>, personId: Long): Long = txns.sumOf { if (it.type == TxnType.SAFE_GIVEN && it.personId == personId) it.amountMinor else 0L }
    fun returned(txns: List<Txn>, personId: Long): Long = txns.sumOf { if (it.type == TxnType.SAFE_RETURNED && it.personId == personId) it.amountMinor else 0L }
    fun remaining(txns: List<Txn>, personId: Long): Long = given(txns, personId) - returned(txns, personId)

    fun summaries(holders: List<Holder>, txns: List<Txn>): List<HolderSummary> {
        val byPerson = txns.filter { it.type.isSafekeeping && it.personId != null }.groupBy { it.personId!! }
        return holders.map { h ->
            val list = (byPerson[h.id] ?: emptyList()).sortedWith(compareByDescending<Txn> { it.timestamp }.thenByDescending { it.id })
            val g = list.sumOf { if (it.type == TxnType.SAFE_GIVEN) it.amountMinor else 0L }
            val r = list.sumOf { if (it.type == TxnType.SAFE_RETURNED) it.amountMinor else 0L }
            val expected = if (g > r) list.filter { it.type == TxnType.SAFE_GIVEN }.mapNotNull { it.dueEpochDay }.minOrNull() else null
            HolderSummary(h, g, r, list, expected, list.maxOfOrNull { it.timestamp } ?: h.createdAt)
        }
    }

    fun totals(summaries: List<HolderSummary>): SafeTotals =
        SafeTotals(summaries.sumOf { it.given }, summaries.sumOf { it.returned }, summaries.count { it.remaining > 0 })

    /**
     * Checks a change against the rule "recoveries never exceed what was given".
     * [all] is every entry; [old] is the entry being replaced (null when adding) and [new] the new version
     * (null when deleting). Returns an error message or null when allowed.
     */
    fun check(all: List<Txn>, old: Txn?, new: Txn?, fmt: (Long) -> String): String? {
        val affected = setOfNotNull(old?.personId, new?.personId)
        val after = all.filter { it.id != old?.id } + listOfNotNull(new)
        for (p in affected) {
            val g = given(after, p); val r = returned(after, p)
            if (r > g) {
                val before = all.filter { it.id != old?.id }
                val left = given(before, p) - returned(before, p)
                return when {
                    new == null -> "Can't delete this: ${fmt(r)} has already been received back, which would be more than the ${fmt(g)} left given. Delete or reduce those recoveries first."
                    new.type == TxnType.SAFE_RETURNED -> "That's more than is outstanding. At most ${fmt(left.coerceAtLeast(0))} can be received back."
                    else -> "The total given can't be less than the ${fmt(r)} already received back."
                }
            }
        }
        return null
    }
}
