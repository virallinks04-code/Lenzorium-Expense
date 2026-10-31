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
) {
    val currency get() = settings.currency
    val categoryById: Map<Long, Category> by lazy { categories.associateBy { it.id } }
    val loanById: Map<Long, Loan> by lazy { loans.associateBy { it.id } }
    val accountById: Map<Long, Account> by lazy { accounts.associateBy { it.id } }
    val activeAccounts: List<Account> get() = accounts.filter { !it.archived }

    /** Opening balance of the ledger = sum of the accounts' opening balances. */
    val totalOpening: Long by lazy { LedgerMath.totalOpening(accounts) }
    val available: Long by lazy { LedgerMath.availableBalance(totalOpening, txns) }
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

class MainViewModel(val repo: Repository) : ViewModel() {
    val state: StateFlow<LedgerState> = combine(
        repo.settings.state, repo.txns, repo.loans, repo.categories, repo.accounts,
    ) { s, t, l, c, a -> LedgerState(true, s, t, l, c, a) }
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
