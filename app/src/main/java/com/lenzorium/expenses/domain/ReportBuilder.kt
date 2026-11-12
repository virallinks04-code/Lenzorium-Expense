package com.lenzorium.expenses.domain

import com.lenzorium.expenses.data.Category
import com.lenzorium.expenses.data.Loan
import com.lenzorium.expenses.data.Txn
import com.lenzorium.expenses.data.TxnType
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

enum class PeriodKind(val label: String) { DAY("Day"), WEEK("Week"), MONTH("Month"), CUSTOM("Custom") }

data class Period(val kind: PeriodKind, val start: LocalDate, val endInclusive: LocalDate) {
    val label: String
        get() {
            val f = DateTimeFormatter.ofPattern("d MMM yyyy")
            return when (kind) {
                PeriodKind.DAY -> start.format(f)
                PeriodKind.MONTH -> start.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
                else -> "${start.format(DateTimeFormatter.ofPattern("d MMM"))} – ${endInclusive.format(f)}"
            }
        }

    fun shift(direction: Int): Period = when (kind) {
        PeriodKind.DAY -> of(kind, start.plusDays(direction.toLong()))
        PeriodKind.WEEK -> of(kind, start.plusWeeks(direction.toLong()))
        PeriodKind.MONTH -> of(kind, start.plusMonths(direction.toLong()))
        PeriodKind.CUSTOM -> {
            val len = ChronoUnit.DAYS.between(start, endInclusive) + 1
            Period(kind, start.plusDays(len * direction), endInclusive.plusDays(len * direction))
        }
    }

    companion object {
        fun of(kind: PeriodKind, anchor: LocalDate): Period = when (kind) {
            PeriodKind.DAY -> Period(kind, anchor, anchor)
            PeriodKind.WEEK -> {
                val s = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                Period(kind, s, s.plusDays(6))
            }
            PeriodKind.MONTH -> {
                val s = anchor.withDayOfMonth(1)
                Period(kind, s, s.plusMonths(1).minusDays(1))
            }
            PeriodKind.CUSTOM -> Period(kind, anchor, anchor)
        }
    }
}

data class CategoryAmount(val name: String, val colorArgb: Long, val amount: Long)

data class LenderDebt(
    val lender: String,
    val loans: Int,
    /** Outstanding counting only entries up to the end of the selected period. */
    val atPeriodEnd: Long,
    /** Outstanding today, counting every entry. */
    val current: Long,
)

data class ReportData(
    val period: Period,
    val startMillis: Long,
    val endMillis: Long,
    val totals: PeriodTotals,
    val openingBalance: Long,
    val closingBalance: Long,
    val debtAtEnd: Long,
    val debtNow: Long,
    val expenseByCategory: List<CategoryAmount>,
    val incomeByCategory: List<CategoryAmount>,
    val trendLabels: List<String>,
    val trendValues: List<Long>,
    val trendUnit: String,
    val lenders: List<LenderDebt>,
    val txns: List<Txn>,
    /** Each account's balance at the end of the period. */
    val accountClosing: List<AccountClosing> = emptyList(),
    /** Accounts in another currency (kept out of the totals). */
    val foreign: Set<Long> = emptySet(),
    val accountCurrency: Map<Long, CurrencyInfo> = emptyMap(),
    val personNames: Map<Long, String> = emptyMap(),
    /** Money still with other people for safekeeping at the end of the period. */
    val safeOutstandingAtEnd: Long = 0L,
    val accountNames: Map<Long, String> = emptyMap(),
) {
    /** "Cash", or "Bank → Cash" for a transfer. */
    fun accountLabel(t: Txn): String =
        if (t.type == TxnType.TRANSFER) "${accountNames[t.accountId] ?: "?"} → ${accountNames[t.toAccountId] ?: "?"}"
        else t.accountId?.let { accountNames[it] } ?: t.paymentMethod?.label ?: ""
}

/** Closing balance of one account; [currency] is set only when it differs from the main currency. */
data class AccountClosing(val name: String, val minor: Long, val currency: CurrencyInfo? = null)

/** Display amount for a report line: signed in the main currency, or in the account's own currency. */
fun ReportData.amountText(t: Txn, c: CurrencyInfo): String {
    val own = t.accountId?.let { accountCurrency[it] }
    return when {
        t.type == TxnType.LOAN_CHARGE -> "debt +" + Money.number(t.amountMinor, c.decimals)
        t.type == TxnType.TRANSFER -> {
            val to = t.toAccountId?.let { accountCurrency[it] } ?: c
            val from = own ?: c
            if (from.code != to.code) Money.format(t.amountMinor, from) + " → " + Money.format(LedgerMath.received(t), to) else Money.format(t.amountMinor, from)
        }
        own != null -> Money.formatSigned(if (t.type == TxnType.INCOME || t.type == TxnType.LOAN_RECEIVED) t.amountMinor else -t.amountMinor, own)
        else -> Money.formatSigned(LedgerMath.cashDelta(t, foreign), c)
    }
}

object ReportBuilder {
    fun build(
        period: Period,
        openingBalance: Long,
        txns: List<Txn>,
        loans: List<Loan>,
        categories: List<Category>,
        zone: ZoneId = ZoneId.systemDefault(),
        today: LocalDate = LocalDate.now(zone),
        accounts: List<com.lenzorium.expenses.data.Account> = emptyList(),
        ledgerCurrency: String? = null,
        holders: List<com.lenzorium.expenses.data.Holder> = emptyList(),
    ): ReportData {
        val foreign = accounts.filter { it.currency != null && ledgerCurrency != null && it.currency != ledgerCurrency }.map { it.id }.toSet()
        val start = period.start.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = period.endInclusive.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val totals = LedgerMath.periodTotals(txns, start, end, foreign)
        val cats = categories.associateBy { it.id }
        fun toList(m: Map<Long?, Long>) = m.entries
            .map { (id, v) -> val c = id?.let { cats[it] }; CategoryAmount(c?.name ?: "Uncategorised", c?.colorArgb ?: 0xFF9E9E9EL, v) }
            .sortedByDescending { it.amount }

        val inPeriod = txns.filter { it.timestamp in start until end }.sortedBy { it.timestamp }
        val expenses = inPeriod.filter { it.type == TxnType.EXPENSE && it.accountId !in foreign }

        // Spending trend buckets
        val days = ChronoUnit.DAYS.between(period.start, period.endInclusive) + 1
        val labels = ArrayList<String>()
        val values = ArrayList<Long>()
        val unit: String
        fun localDate(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
        when {
            days == 1L -> {
                unit = "by hour"
                val buckets = LongArray(24)
                expenses.forEach { buckets[Instant.ofEpochMilli(it.timestamp).atZone(zone).hour] += it.amountMinor }
                for (h in 0 until 24) { labels += "${h}h"; values += buckets[h] }
            }
            days <= 62L -> {
                unit = "by day"
                val map = expenses.groupBy { localDate(it.timestamp) }.mapValues { e -> e.value.sumOf { it.amountMinor } }
                var d = period.start
                val f = if (days <= 7) DateTimeFormatter.ofPattern("EEE") else DateTimeFormatter.ofPattern("d")
                while (!d.isAfter(period.endInclusive)) { labels += d.format(f); values += map[d] ?: 0L; d = d.plusDays(1) }
            }
            else -> {
                unit = "by month"
                val map = expenses.groupBy { localDate(it.timestamp).withDayOfMonth(1) }.mapValues { e -> e.value.sumOf { it.amountMinor } }
                var m = period.start.withDayOfMonth(1)
                val f = DateTimeFormatter.ofPattern("MMM")
                while (!m.isAfter(period.endInclusive)) { labels += m.format(f); values += map[m] ?: 0L; m = m.plusMonths(1) }
            }
        }

        val todayEpoch = today.toEpochDay()
        val lenders = loans.groupBy { it.lender.trim().lowercase() }.map { (_, group) ->
            LenderDebt(
                lender = group.first().lender,
                loans = group.size,
                atPeriodEnd = group.sumOf { LedgerMath.loanSummary(it, txns, todayEpoch, end).outstanding },
                current = group.sumOf { LedgerMath.loanSummary(it, txns, todayEpoch).outstanding },
            )
        }.filter { it.atPeriodEnd != 0L || it.current != 0L }.sortedByDescending { it.current }

        return ReportData(
            period = period,
            startMillis = start,
            endMillis = end,
            totals = totals,
            openingBalance = LedgerMath.availableBalance(openingBalance, txns, start, foreign),
            closingBalance = LedgerMath.availableBalance(openingBalance, txns, end, foreign),
            debtAtEnd = LedgerMath.outstandingDebt(loans, txns, end),
            debtNow = LedgerMath.outstandingDebt(loans, txns),
            expenseByCategory = toList(totals.expensesByCategory),
            incomeByCategory = toList(totals.incomeByCategory),
            trendLabels = labels,
            trendValues = values,
            trendUnit = unit,
            lenders = lenders,
            txns = inPeriod,
            accountClosing = accounts
                .map { AccountClosing(it.name, LedgerMath.accountBalance(it, txns, end), if (it.id in foreign) Currencies.find(it.currency!!) else null) to it.archived }
                .filter { (a, archived) -> !archived || a.minor != 0L }
                .map { it.first },
            accountNames = accounts.associate { it.id to it.name },
            personNames = holders.associate { it.id to it.name },
            safeOutstandingAtEnd = txns.sumOf { t ->
                if (t.timestamp >= end) 0L else when (t.type) { TxnType.SAFE_GIVEN -> t.amountMinor; TxnType.SAFE_RETURNED -> -t.amountMinor; else -> 0L }
            },
            foreign = foreign,
            accountCurrency = accounts.filter { it.id in foreign }.associate { it.id to Currencies.find(it.currency!!) },
        )
    }
}
