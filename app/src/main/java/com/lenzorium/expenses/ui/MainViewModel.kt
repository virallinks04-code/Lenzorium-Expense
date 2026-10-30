package com.lenzorium.expenses.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
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
) {
    val currency get() = settings.currency
    val categoryById: Map<Long, Category> by lazy { categories.associateBy { it.id } }
    val loanById: Map<Long, Loan> by lazy { loans.associateBy { it.id } }

    val available: Long by lazy { LedgerMath.availableBalance(settings.openingBalanceMinor, txns) }
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
        repo.settings.state, repo.txns, repo.loans, repo.categories,
    ) { s, t, l, c -> LedgerState(true, s, t, l, c) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, LedgerState(settings = repo.settings.current))

    init {
        viewModelScope.launch { repo.ensureDefaultCategories() }
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
