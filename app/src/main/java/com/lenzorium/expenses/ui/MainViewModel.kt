package com.lenzorium.expenses.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lenzorium.expenses.data.Account
import com.lenzorium.expenses.data.AppSettings
import com.lenzorium.expenses.data.Category
import com.lenzorium.expenses.data.Loan
import com.lenzorium.expenses.data.Repository
import com.lenzorium.expenses.data.Txn
import com.lenzorium.expenses.data.TxnType
import com.lenzorium.expenses.data.Budget
import com.lenzorium.expenses.data.Goal
import com.lenzorium.expenses.data.GoalEntry
import com.lenzorium.expenses.data.Recurring
import com.lenzorium.expenses.data.Template
import com.lenzorium.expenses.domain.BudgetStatus
import com.lenzorium.expenses.domain.GoalProgress
import com.lenzorium.expenses.domain.Occurrence
import com.lenzorium.expenses.domain.PlanMath
import java.time.YearMonth
import com.lenzorium.expenses.domain.LedgerMath
import com.lenzorium.expenses.domain.LoanSummary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Everything the screens read, recomputed whenever the database or settings change. */
data class LedgerState(
    val loaded: Boolean = false,
    val settings: AppSettings = AppSettings(),
    val txns: List<Txn> = emptyList(),
    val loans: List<Loan> = emptyList(),
    val categories: List<Category> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val plans: PlanData = PlanData(),
) {
    val budgets get() = plans.budgets
    val recurring get() = plans.recurring
    val templates get() = plans.templates
    val goals get() = plans.goals
    val today: Long get() = LocalDate.now().toEpochDay()
    val thisMonth: YearMonth get() = YearMonth.now()

    val budgetStatuses: List<BudgetStatus> by lazy { budgets.map { PlanMath.status(it, ledgerTxns, thisMonth) } }
    val overallBudget: BudgetStatus? get() = budgetStatuses.firstOrNull { it.budget.categoryId == null }
    val categoryBudgets: List<BudgetStatus> get() = budgetStatuses.filter { it.budget.categoryId != null }
    /** Bills/income due within the next 30 days, overdue first. */
    val upcoming: List<Occurrence> by lazy { PlanMath.upcoming(recurring, today + 30) }
    val dueNow: List<Recurring> get() = recurring.filter { it.active && it.nextDueEpochDay <= today }
    val goalProgress: List<GoalProgress> by lazy { goals.filter { !it.archived }.map { PlanMath.goalProgress(it, plans.goalEntries) } }
    val setAside: Long get() = goalProgress.sumOf { it.saved }
    val monthSpent: Long by lazy { PlanMath.monthlyExpenses(ledgerTxns, thisMonth, 1).first().second }
    val lastMonthSpent: Long by lazy { PlanMath.monthlyExpenses(ledgerTxns, thisMonth.minusMonths(1), 1).first().second }
    val merchantsExpense: List<String> by lazy { PlanMath.merchants(txns, TxnType.EXPENSE) }
    val merchantsIncome: List<String> by lazy { PlanMath.merchants(txns, TxnType.INCOME) }
    val allTags: List<String> by lazy { txns.flatMap { it.tagList }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key } }

    val currency get() = settings.currency
    val categoryById: Map<Long, Category> by lazy { categories.associateBy { it.id } }
    val loanById: Map<Long, Loan> by lazy { loans.associateBy { it.id } }
    val accountById: Map<Long, Account> by lazy { accounts.associateBy { it.id } }
    val activeAccounts: List<Account> get() = accounts.filter { !it.archived }

    /** Accounts holding a currency other than the main one. They are never added into the main totals. */
    val foreignIds: Set<Long> by lazy { accounts.filter { isForeign(it) }.map { it.id }.toSet() }
    fun isForeign(a: Account): Boolean = a.currency != null && a.currency != settings.currencyCode
    fun currencyOf(accountId: Long?): com.lenzorium.expenses.domain.CurrencyInfo =
        accountId?.let { accountById[it] }?.currency?.let { com.lenzorium.expenses.domain.Currencies.find(it) } ?: currency
    /** Currency an entry's amount is written in (the source account's for transfers). */
    fun currencyFor(t: Txn): com.lenzorium.expenses.domain.CurrencyInfo = if (t.type == TxnType.LOAN_CHARGE) currency else currencyOf(t.accountId)
    /** Accounts in the main currency: used for loans, bills, goals and budgets. */
    val activeLedgerAccounts: List<Account> get() = activeAccounts.filter { it.id !in foreignIds }
    /** Entries that count in the main currency (foreign-account entries left out; transfers kept). */
    val ledgerTxns: List<Txn> by lazy { if (foreignIds.isEmpty()) txns else txns.filter { it.type == TxnType.TRANSFER || it.accountId == null || it.accountId !in foreignIds } }

    /** Opening balance of the ledger = sum of the main-currency accounts' opening balances. */
    val totalOpening: Long by lazy { LedgerMath.totalOpening(accounts.filter { it.id !in foreignIds }) }
    val available: Long by lazy { LedgerMath.availableBalance(totalOpening, txns, foreign = foreignIds) }
    val accountBalances: Map<Long, Long> by lazy { accounts.associate { it.id to LedgerMath.accountBalance(it, txns) } }
    fun balanceOf(id: Long): Long = accountBalances[id] ?: 0L
    fun accountName(id: Long?): String? = id?.let { accountById[it]?.name }
    /** "Cash", or "Bank → Cash" for transfers. */
    fun accountLabel(t: Txn): String? =
        if (t.type == com.lenzorium.expenses.data.TxnType.TRANSFER) "${accountName(t.accountId) ?: "?"} → ${accountName(t.toAccountId) ?: "?"}"
        else accountName(t.accountId)
    val debt: Long by lazy { LedgerMath.outstandingDebt(loans, txns) }
    val afterDebt: Long get() = available - debt

    val loanSummaries: List<LoanSummary> by lazy {
        val today = LocalDate.now().toEpochDay()
        loans.map { LedgerMath.loanSummary(it, txns, today) }
    }
    fun summary(loanId: Long): LoanSummary? = loanSummaries.firstOrNull { it.loan.id == loanId }
}

data class PlanData(
    val budgets: List<Budget> = emptyList(),
    val recurring: List<Recurring> = emptyList(),
    val templates: List<Template> = emptyList(),
    val goals: List<Goal> = emptyList(),
    val goalEntries: List<GoalEntry> = emptyList(),
)

class MainViewModel(val repo: Repository) : ViewModel() {
    private val plans = combine(repo.budgets, repo.recurring, repo.templates, repo.goals, repo.goalEntries) { b, r, t, g, e -> PlanData(b, r, t, g, e) }
    private val ledger = combine(
        repo.settings.state, repo.txns, repo.loans, repo.categories, repo.accounts,
    ) { s, t, l, c, a -> LedgerState(true, s, t, l, c, a) }
    val state: StateFlow<LedgerState> = combine(ledger, plans) { l, p -> l.copy(plans = p) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, LedgerState(settings = repo.settings.current))

    init {
        viewModelScope.launch {
            repo.ensureDefaultCategories()
            repo.ensureAccounts()
            repo.cleanOrphanReceipts()
        }
    }

    companion object {
        fun factory(repo: Repository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(repo) as T
        }
    }
}

/** Small helper for ViewModels with constructor arguments. */
inline fun <reified VM : ViewModel> simpleFactory(crossinline builder: () -> VM) = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = builder() as T
}
