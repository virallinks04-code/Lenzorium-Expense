package com.lenzorium.expenses.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class Frequency(val label: String, val unit: String) {
    DAILY("Daily", "day"),
    WEEKLY("Weekly", "week"),
    MONTHLY("Monthly", "month"),
    YEARLY("Yearly", "year"),
}

/**
 * A monthly spending limit. [categoryId] null means "all spending".
 * Only expenses count: transfers, loan repayments and loan charges are never spending.
 */
@Entity(tableName = "budgets")
data class Budget(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long? = null,
    val limitMinor: Long,
    /** Carry last month's unused (or overspent) amount into this month. */
    val rollover: Boolean = false,
    /** First month the budget applies to, as LocalDate.toEpochDay() of the 1st of that month. */
    val startMonthEpochDay: Long,
    val createdAt: Long,
)

/**
 * A bill, subscription or regular income. It never deducts money on its own: when it is due the
 * app reminds the user, who marks it paid (which records a normal entry) or skips it.
 */
@Entity(tableName = "recurring")
data class Recurring(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** EXPENSE or INCOME. */
    val type: TxnType = TxnType.EXPENSE,
    val amountMinor: Long,
    val categoryId: Long? = null,
    val accountId: Long? = null,
    val party: String = "",
    val frequency: Frequency = Frequency.MONTHLY,
    val intervalCount: Int = 1,
    /** First due date (LocalDate.toEpochDay()); later dates are counted from it so "31st" stays the month end. */
    val startEpochDay: Long,
    val endEpochDay: Long? = null,
    /** Next date waiting to be paid or skipped. */
    val nextDueEpochDay: Long,
    val remind: Boolean = true,
    val remindDaysBefore: Int = 1,
    val active: Boolean = true,
    val notes: String = "",
    val createdAt: Long,
)

/** A saved entry for one-tap logging ("Tea 150 from Cash"). Amount 0 means "ask each time". */
@Entity(tableName = "templates")
data class Template(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: TxnType = TxnType.EXPENSE,
    val amountMinor: Long = 0,
    val categoryId: Long? = null,
    val accountId: Long? = null,
    val party: String = "",
    val tags: String = "",
    val sortOrder: Int = 0,
)

/**
 * A savings goal. Money "set aside" for a goal stays in its account: goals earmark money,
 * they never create expenses or change any balance.
 */
@Entity(tableName = "goals")
data class Goal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val targetMinor: Long,
    val targetEpochDay: Long? = null,
    /** Account where the savings are kept, if any. */
    val accountId: Long? = null,
    val iconKey: String = "goal",
    val colorArgb: Long = 0xFF4A9EDBL,
    val archived: Boolean = false,
    val createdAt: Long,
)

/** Money set aside for (positive) or taken back from (negative) a goal. */
@Entity(tableName = "goal_entries", indices = [Index("goalId")])
data class GoalEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goalId: Long,
    val amountMinor: Long,
    val epochDay: Long,
    val note: String = "",
    val createdAt: Long,
)
