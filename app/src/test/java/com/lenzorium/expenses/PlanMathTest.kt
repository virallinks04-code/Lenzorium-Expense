package com.lenzorium.expenses

import com.lenzorium.expenses.backup.BackupCodec
import com.lenzorium.expenses.backup.BackupData
import com.lenzorium.expenses.data.Account
import com.lenzorium.expenses.data.AccountKind
import com.lenzorium.expenses.data.AppSettings
import com.lenzorium.expenses.data.Budget
import com.lenzorium.expenses.data.DefaultCategories
import com.lenzorium.expenses.data.Frequency
import com.lenzorium.expenses.data.Goal
import com.lenzorium.expenses.data.GoalEntry
import com.lenzorium.expenses.data.Recurring
import com.lenzorium.expenses.data.Tags
import com.lenzorium.expenses.data.Template
import com.lenzorium.expenses.data.Txn
import com.lenzorium.expenses.data.TxnType
import com.lenzorium.expenses.domain.BudgetLevel
import com.lenzorium.expenses.domain.LedgerMath
import com.lenzorium.expenses.domain.PlanMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class PlanMathTest {
    private val zone = ZoneId.systemDefault()
    private val nov = YearMonth.of(2026, 11)
    private fun at(d: LocalDate, h: Int = 12) = d.atTime(h, 0).atZone(zone).toInstant().toEpochMilli()
    private var id = 1L
    private fun t(type: TxnType, amount: Long, day: LocalDate, cat: Long? = 1, acc: Long? = 1, to: Long? = null, party: String = "", tags: String = "") =
        Txn(id = id++, type = type, amountMinor = amount, categoryId = cat, timestamp = at(day), party = party, requestId = "r$id", createdAt = 0, accountId = acc, toAccountId = to, tags = tags, loanId = if (type.isLoanRelated) 1 else null)
    private fun budget(cat: Long?, limit: Long, rollover: Boolean = false, start: YearMonth = nov.minusMonths(3)) =
        Budget(id = id++, categoryId = cat, limitMinor = limit, rollover = rollover, startMonthEpochDay = start.atDay(1).toEpochDay(), createdAt = 0)

    @Test
    fun budgetCountsOnlyExpenses() {
        val d = nov.atDay(5)
        val txns = listOf(
            t(TxnType.EXPENSE, 300, d),
            t(TxnType.EXPENSE, 200, d, cat = 2),
            t(TxnType.TRANSFER, 5000, d, cat = null, acc = 1, to = 2),
            t(TxnType.LOAN_REPAYMENT, 1000, d, cat = null),
            t(TxnType.LOAN_CHARGE, 50, d, cat = null, acc = null),
            t(TxnType.INCOME, 9000, d),
            t(TxnType.EXPENSE, 700, nov.minusMonths(1).atDay(28)), // last month: not counted
        )
        val all = PlanMath.status(budget(null, 1000), txns, nov)
        assertEquals(500L, all.spent)
        assertEquals(500L, all.remaining)
        val food = PlanMath.status(budget(1, 1000), txns, nov)
        assertEquals(300L, food.spent)
    }

    @Test
    fun budgetLevelsAt80And100Percent() {
        val d = nov.atDay(3)
        assertEquals(BudgetLevel.OK, PlanMath.status(budget(null, 1000), listOf(t(TxnType.EXPENSE, 799, d)), nov).level)
        assertEquals(BudgetLevel.NEAR, PlanMath.status(budget(null, 1000), listOf(t(TxnType.EXPENSE, 800, d)), nov).level)
        assertEquals(BudgetLevel.NEAR, PlanMath.status(budget(null, 1000), listOf(t(TxnType.EXPENSE, 1000, d)), nov).level)
        assertEquals(BudgetLevel.OVER, PlanMath.status(budget(null, 1000), listOf(t(TxnType.EXPENSE, 1001, d)), nov).level)
    }

    @Test
    fun rolloverCarriesLastMonthOnce() {
        val oct = nov.minusMonths(1)
        val txns = listOf(t(TxnType.EXPENSE, 600, oct.atDay(10)), t(TxnType.EXPENSE, 100, nov.atDay(2)))
        val s = PlanMath.status(budget(null, 1000, rollover = true), txns, nov)
        assertEquals(400L, s.carried)
        assertEquals(1400L, s.available)
        assertEquals(1300L, s.remaining)
        // Overspending last month reduces this month.
        val over = PlanMath.status(budget(null, 1000, rollover = true), listOf(t(TxnType.EXPENSE, 1250, oct.atDay(10))), nov)
        assertEquals(-250L, over.carried)
        assertEquals(750L, over.available)
        // Nothing is carried into the budget's first month.
        val first = PlanMath.status(budget(null, 1000, rollover = true, start = nov), txns, nov)
        assertEquals(0L, first.carried)
    }

    @Test
    fun backdatedOrRecategorisedEntryMovesBetweenBudgets() {
        val e = t(TxnType.EXPENSE, 400, nov.atDay(4), cat = 1)
        val food = budget(1, 1000); val shop = budget(2, 1000)
        assertEquals(400L, PlanMath.status(food, listOf(e), nov).spent)
        val moved = e.copy(categoryId = 2)
        assertEquals(0L, PlanMath.status(food, listOf(moved), nov).spent)
        assertEquals(400L, PlanMath.status(shop, listOf(moved), nov).spent)
        val backdated = e.copy(timestamp = at(nov.minusMonths(1).atDay(30)))
        assertEquals(0L, PlanMath.status(food, listOf(backdated), nov).spent)
    }

    private fun rule(freq: Frequency, start: LocalDate, interval: Int = 1, end: LocalDate? = null, type: TxnType = TxnType.EXPENSE, amount: Long = 100) =
        Recurring(id = id++, name = "R", type = type, amountMinor = amount, categoryId = 1, accountId = 1, frequency = freq, intervalCount = interval,
            startEpochDay = start.toEpochDay(), endEpochDay = end?.toEpochDay(), nextDueEpochDay = start.toEpochDay(), createdAt = 0)

    @Test
    fun monthlyOn31stStaysAtMonthEnd() {
        val r = rule(Frequency.MONTHLY, LocalDate.of(2026, 1, 31))
        val feb = PlanMath.nextAfter(r, LocalDate.of(2026, 1, 31).toEpochDay())!!
        assertEquals(LocalDate.of(2026, 2, 28), LocalDate.ofEpochDay(feb))
        val mar = PlanMath.nextAfter(r, feb)!!
        assertEquals(LocalDate.of(2026, 3, 31), LocalDate.ofEpochDay(mar))
    }

    @Test
    fun scheduleStepsAndEndDate() {
        val start = LocalDate.of(2026, 11, 2)
        val weekly2 = rule(Frequency.WEEKLY, start, interval = 2)
        assertEquals(start.plusWeeks(2), LocalDate.ofEpochDay(PlanMath.nextAfter(weekly2, start.toEpochDay())!!))
        val yearly = rule(Frequency.YEARLY, LocalDate.of(2024, 2, 29))
        assertEquals(LocalDate.of(2025, 2, 28), LocalDate.ofEpochDay(PlanMath.nextAfter(yearly, LocalDate.of(2024, 2, 29).toEpochDay())!!))
        val ending = rule(Frequency.MONTHLY, start, end = start.plusMonths(1))
        assertNotNull(PlanMath.nextAfter(ending, start.toEpochDay()))
        assertNull(PlanMath.nextAfter(ending, start.plusMonths(1).toEpochDay()))
        // Far in the future: still exact.
        val daily = rule(Frequency.DAILY, start)
        assertEquals(start.plusDays(1001), LocalDate.ofEpochDay(PlanMath.nextAfter(daily, start.plusDays(1000).toEpochDay())!!))
    }

    @Test
    fun upcomingAndCashOutlook() {
        val today = LocalDate.of(2026, 11, 1)
        val rent = rule(Frequency.MONTHLY, today.minusDays(2), amount = 5000).copy(nextDueEpochDay = today.minusDays(2).toEpochDay()) // overdue
        val netflix = rule(Frequency.WEEKLY, today.plusDays(3), amount = 100)
        val salary = rule(Frequency.MONTHLY, today.plusDays(10), type = TxnType.INCOME, amount = 20000)
        val off = rule(Frequency.DAILY, today).copy(active = false)
        val list = PlanMath.upcoming(listOf(rent, netflix, salary, off), today.plusDays(30).toEpochDay())
        val rentDates = list.filter { it.rule.id == rent.id }.map { LocalDate.ofEpochDay(it.epochDay) }
        assertEquals(listOf(today.minusDays(2), today.minusDays(2).plusMonths(1)), rentDates)
        assertEquals(4, list.count { it.rule.id == netflix.id })
        val net = PlanMath.expectedNet(listOf(rent, netflix, salary), today.plusDays(30).toEpochDay())
        assertEquals(20000L - 2 * 5000 - 4 * 100, net)
        val due = PlanMath.dueForReminder(listOf(rent, netflix.copy(remindDaysBefore = 3), salary), today.toEpochDay())
        assertEquals(setOf(rent.id, netflix.id), due.map { it.id }.toSet())
    }

    @Test
    fun goalsNeverChangeBalances() {
        val g = Goal(id = 1, name = "Phone", targetMinor = 120000, targetEpochDay = LocalDate.of(2027, 4, 15).toEpochDay(), createdAt = 0)
        val p = PlanMath.goalProgress(g, listOf(GoalEntry(1, 1, 30000, 0, "", 0), GoalEntry(2, 1, -5000, 0, "", 0), GoalEntry(3, 2, 999, 0, "", 0)))
        assertEquals(25000L, p.saved)
        assertEquals(95000L, p.remaining)
        // Nov 2026 .. Apr 2027 = 6 months, rounded up.
        assertEquals(15834L, p.monthlyNeeded(LocalDate.of(2026, 11, 1)))
        // Ledger has no idea goals exist.
        val txns = listOf(t(TxnType.INCOME, 1000, nov.atDay(1)))
        assertEquals(1000L, LedgerMath.availableBalance(0, txns))
    }

    @Test
    fun merchantsTagsAndSuggestions() {
        val d = nov.atDay(6)
        val txns = listOf(
            t(TxnType.EXPENSE, 500, d, cat = 3, party = "KFC", tags = "family"),
            t(TxnType.EXPENSE, 300, d.plusDays(1), cat = 3, party = "kfc ", tags = "family,weekend"),
            t(TxnType.EXPENSE, 200, d, cat = 4, party = "Daraz"),
        )
        val top = PlanMath.topMerchants(txns, PlanMath.monthStart(nov), PlanMath.monthEnd(nov))
        assertEquals(800L, top.first().second)
        assertEquals(3L, PlanMath.suggestCategory(txns, "KFC", TxnType.EXPENSE))
        assertEquals(listOf("family" to 800L, "weekend" to 300L), PlanMath.byTag(txns, PlanMath.monthStart(nov), PlanMath.monthEnd(nov)))
        assertEquals("family,trip", Tags.join(listOf("#Family", "trip", "family", "  ")))
    }

    @Test
    fun duplicateDetection() {
        val a = t(TxnType.EXPENSE, 500, nov.atDay(6))
        val b = a.copy(id = 0, requestId = "other", timestamp = a.timestamp + 60_000)
        assertNotNull(PlanMath.likelyDuplicate(listOf(a), b))
        assertNull(PlanMath.likelyDuplicate(listOf(a), b.copy(timestamp = a.timestamp + 10 * 60_000)))
        assertNull(PlanMath.likelyDuplicate(listOf(a), b.copy(amountMinor = 501)))
    }

    @Test
    fun backupVersion3RoundTrip() {
        val cats = DefaultCategories.all.mapIndexed { i, c -> c.copy(id = i + 1L) }
        val accounts = listOf(Account(1, "Cash", AccountKind.CASH, 1000, false, 1), Account(2, "UK Bank", AccountKind.BANK, 500, false, 2, currency = "GBP"))
        val d = BackupData(
            AppSettings(setupDone = true, accountsMigrated = true, remindersEnabled = false), cats, emptyList(),
            listOf(t(TxnType.EXPENSE, 100, nov.atDay(1), tags = "family,trip").copy(recurringId = 7)), 1L, accounts,
            budgets = listOf(Budget(1, null, 5000, true, 20000, 0), Budget(2, 1, 900, false, 20000, 0)),
            recurring = listOf(rule(Frequency.MONTHLY, LocalDate.of(2026, 1, 31)).copy(id = 7, endEpochDay = 21000, notes = "n")),
            templates = listOf(Template(1, "Tea", TxnType.EXPENSE, 150, 1, 1, "Dhaba", "daily", 1)),
            goals = listOf(Goal(1, "Phone", 1000, 21000, 2, "goal", 0xFF112233, false, 5)),
            goalEntries = listOf(GoalEntry(1, 1, 300, 20500, "first", 6)),
        )
        val back = BackupCodec.decode(BackupCodec.encode(d))
        assertEquals(d.txns, back.txns)
        assertEquals(d.accounts, back.accounts)
        assertEquals(d.budgets, back.budgets)
        assertEquals(d.recurring, back.recurring)
        assertEquals(d.templates, back.templates)
        assertEquals(d.goals, back.goals)
        assertEquals(d.goalEntries, back.goalEntries)
        assertEquals(false, back.settings.remindersEnabled)
    }
}
