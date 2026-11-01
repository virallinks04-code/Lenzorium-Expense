package com.lenzorium.expenses.domain

import com.lenzorium.expenses.data.Budget
import com.lenzorium.expenses.data.Frequency
import com.lenzorium.expenses.data.Goal
import com.lenzorium.expenses.data.GoalEntry
import com.lenzorium.expenses.data.Recurring
import com.lenzorium.expenses.data.Txn
import com.lenzorium.expenses.data.TxnType
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

data class BudgetStatus(
    val budget: Budget,
    /** This month's limit plus any rolled-over amount. */
    val available: Long,
    val carried: Long,
    val spent: Long,
) {
    val remaining: Long get() = available - spent
    /** 0..∞ share of the available amount spent (1.0 = exactly on budget). */
    val fraction: Float get() = if (available <= 0) (if (spent > 0) 1.5f else 0f) else spent.toFloat() / available.toFloat()
    val level: BudgetLevel get() = when {
        spent > available -> BudgetLevel.OVER
        fraction >= 0.8f -> BudgetLevel.NEAR
        else -> BudgetLevel.OK
    }
}

enum class BudgetLevel { OK, NEAR, OVER }

data class Occurrence(val rule: Recurring, val epochDay: Long)

data class GoalProgress(val goal: Goal, val saved: Long) {
    val remaining: Long get() = (goal.targetMinor - saved).coerceAtLeast(0)
    val fraction: Float get() = if (goal.targetMinor <= 0) 0f else (saved.toFloat() / goal.targetMinor).coerceIn(0f, 1f)
    val reached: Boolean get() = saved >= goal.targetMinor
    /** Amount to set aside each month to reach the target on time, or null without a target date. */
    fun monthlyNeeded(today: LocalDate): Long? {
        val d = goal.targetEpochDay?.let(LocalDate::ofEpochDay) ?: return null
        if (reached) return 0
        val months = (YearMonth.from(d).let { ym -> (ym.year - today.year) * 12 + (ym.monthValue - today.monthValue) }).coerceAtLeast(0) + 1
        return (remaining + months - 1) / months
    }
}

/** Budgets, bills and goals. Pure Kotlin so every rule is unit-tested. */
object PlanMath {
    private val zone: ZoneId get() = ZoneId.systemDefault()

    fun monthStart(ym: YearMonth): Long = ym.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
    fun monthEnd(ym: YearMonth): Long = ym.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()

    /** Spending that counts against [budget] between the two instants: expenses only, never transfers or loan entries. */
    fun spent(budget: Budget, txns: List<Txn>, start: Long, endExclusive: Long): Long =
        txns.sumOf { t ->
            if (t.type == TxnType.EXPENSE && t.timestamp >= start && t.timestamp < endExclusive &&
                (budget.categoryId == null || t.categoryId == budget.categoryId)
            ) t.amountMinor else 0L
        }

    /**
     * Status for the month [ym]. With rollover, last month's difference (unused amount, or overspend as a
     * negative) is carried in once; months before the budget started are never counted.
     */
    fun status(budget: Budget, txns: List<Txn>, ym: YearMonth): BudgetStatus {
        val startYm = YearMonth.from(LocalDate.ofEpochDay(budget.startMonthEpochDay))
        val spent = spent(budget, txns, monthStart(ym), monthEnd(ym))
        var carried = 0L
        if (budget.rollover && ym.isAfter(startYm)) {
            val prev = ym.minusMonths(1)
            carried = budget.limitMinor - spent(budget, txns, monthStart(prev), monthEnd(prev))
        }
        return BudgetStatus(budget, budget.limitMinor + carried, carried, spent)
    }

    /** Due date number [n] (0 = the first one), counted from the start so month ends stay month ends. */
    fun occurrence(rule: Recurring, n: Long): LocalDate {
        val start = LocalDate.ofEpochDay(rule.startEpochDay)
        val step = n * rule.intervalCount.coerceAtLeast(1)
        return when (rule.frequency) {
            Frequency.DAILY -> start.plusDays(step)
            Frequency.WEEKLY -> start.plusWeeks(step)
            Frequency.MONTHLY -> start.plusMonths(step)
            Frequency.YEARLY -> start.plusYears(step)
        }
    }

    /** First due date strictly after [epochDay], or null when the schedule has ended. */
    fun nextAfter(rule: Recurring, epochDay: Long): Long? {
        var n = estimateIndex(rule, epochDay)
        while (n > 0 && occurrence(rule, n - 1).toEpochDay() > epochDay) n--
        while (occurrence(rule, n).toEpochDay() <= epochDay) n++
        val next = occurrence(rule, n).toEpochDay()
        return if (rule.endEpochDay != null && next > rule.endEpochDay) null else next
    }

    private fun estimateIndex(rule: Recurring, epochDay: Long): Long {
        val days = epochDay - rule.startEpochDay
        if (days <= 0) return 0
        val per = rule.intervalCount.coerceAtLeast(1) * when (rule.frequency) {
            Frequency.DAILY -> 1.0; Frequency.WEEKLY -> 7.0; Frequency.MONTHLY -> 30.44; Frequency.YEARLY -> 365.25
        }
        return (days / per).toLong().coerceAtLeast(0)
    }

    /** Every unpaid due date of active rules from the current one up to [untilEpochDay] (inclusive). */
    fun upcoming(rules: List<Recurring>, untilEpochDay: Long): List<Occurrence> {
        val out = ArrayList<Occurrence>()
        for (r in rules) {
            if (!r.active) continue
            var d: Long? = r.nextDueEpochDay
            var guard = 0
            while (d != null && d <= untilEpochDay && guard++ < 400) {
                if (r.endEpochDay != null && d > r.endEpochDay) break
                out += Occurrence(r, d)
                d = nextAfter(r, d)
            }
        }
        return out.sortedWith(compareBy({ it.epochDay }, { it.rule.name }))
    }

    /** Money expected out minus money expected in from bills/regular income until [untilEpochDay]. */
    fun expectedNet(rules: List<Recurring>, untilEpochDay: Long): Long =
        upcoming(rules, untilEpochDay).sumOf { if (it.rule.type == TxnType.INCOME) it.rule.amountMinor else -it.rule.amountMinor }

    /** Rules whose reminder should show on [today]: due within their reminder window, or overdue. */
    fun dueForReminder(rules: List<Recurring>, today: Long): List<Recurring> =
        rules.filter { it.active && it.remind && it.nextDueEpochDay - it.remindDaysBefore <= today }

    fun goalProgress(goal: Goal, entries: List<GoalEntry>): GoalProgress =
        GoalProgress(goal, entries.filter { it.goalId == goal.id }.sumOf { it.amountMinor })

    /** Spending by month for the last [months] months ending with [ym] (oldest first). */
    fun monthlyExpenses(txns: List<Txn>, ym: YearMonth, months: Int): List<Pair<YearMonth, Long>> =
        (months - 1 downTo 0).map { back ->
            val m = ym.minusMonths(back.toLong())
            val s = monthStart(m); val e = monthEnd(m)
            m to txns.sumOf { if (it.type == TxnType.EXPENSE && it.timestamp >= s && it.timestamp < e) it.amountMinor else 0L }
        }

    /** Top merchants/payees by spending in the range. */
    fun topMerchants(txns: List<Txn>, start: Long, endExclusive: Long, limit: Int = 5): List<Pair<String, Long>> =
        txns.asSequence()
            .filter { it.type == TxnType.EXPENSE && it.timestamp >= start && it.timestamp < endExclusive && it.party.isNotBlank() }
            .groupBy { it.party.trim().lowercase() }
            .map { (_, list) -> list.first().party.trim() to list.sumOf { it.amountMinor } }
            .sortedByDescending { it.second }
            .take(limit)

    /** Spending by tag in the range. An entry with two tags counts under both. */
    fun byTag(txns: List<Txn>, start: Long, endExclusive: Long): List<Pair<String, Long>> {
        val m = HashMap<String, Long>()
        for (t in txns) {
            if (t.type != TxnType.EXPENSE || t.timestamp < start || t.timestamp >= endExclusive) continue
            for (tag in t.tagList) m[tag] = (m[tag] ?: 0L) + t.amountMinor
        }
        return m.entries.sortedByDescending { it.value }.map { it.key to it.value }
    }

    /** Category most recently used with this merchant, so typing "KFC" can pick "Food". */
    fun suggestCategory(txns: List<Txn>, party: String, type: TxnType): Long? {
        val p = party.trim().lowercase()
        if (p.isEmpty()) return null
        return txns.filter { it.type == type && it.party.trim().lowercase() == p && it.categoryId != null }
            .maxByOrNull { it.timestamp }?.categoryId
    }

    /** Distinct names previously used for this kind of entry, most frequent first. */
    fun merchants(txns: List<Txn>, type: TxnType): List<String> =
        txns.asSequence().filter { it.type == type && it.party.isNotBlank() }
            .groupBy { it.party.trim().lowercase() }
            .entries.sortedByDescending { it.value.size }
            .map { it.value.maxBy { t -> t.timestamp }.party.trim() }

    /** Possible duplicate: same type, amount and account within two minutes of another entry. */
    fun likelyDuplicate(txns: List<Txn>, candidate: Txn): Txn? =
        txns.firstOrNull {
            it.id != candidate.id && it.type == candidate.type && it.amountMinor == candidate.amountMinor &&
                it.accountId == candidate.accountId && kotlin.math.abs(it.timestamp - candidate.timestamp) < 120_000 &&
                it.requestId != candidate.requestId
        }
}
