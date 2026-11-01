package com.lenzorium.expenses.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.RequestQuote
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.lenzorium.expenses.data.Loan
import com.lenzorium.expenses.data.PayMethod
import com.lenzorium.expenses.data.Repository
import com.lenzorium.expenses.data.SaveResult
import com.lenzorium.expenses.data.Txn
import com.lenzorium.expenses.data.TxnType
import com.lenzorium.expenses.domain.LedgerMath
import com.lenzorium.expenses.domain.LoanStatus
import com.lenzorium.expenses.domain.LoanSummary
import com.lenzorium.expenses.domain.Money
import com.lenzorium.expenses.ui.LedgerState
import com.lenzorium.expenses.ui.Routes
import com.lenzorium.expenses.ui.components.AmountField
import com.lenzorium.expenses.ui.components.ConfirmDialog
import com.lenzorium.expenses.ui.components.DatePickDialog
import com.lenzorium.expenses.ui.components.DateTimeField
import com.lenzorium.expenses.ui.components.Dates
import com.lenzorium.expenses.ui.components.EmptyState
import com.lenzorium.expenses.ui.components.ErrorBanner
import com.lenzorium.expenses.ui.components.IconBadge
import com.lenzorium.expenses.ui.components.MessageDialog
import com.lenzorium.expenses.ui.components.AccountSelector
import com.lenzorium.expenses.ui.negativeWarning
import com.lenzorium.expenses.ui.components.ScreenScaffold
import com.lenzorium.expenses.ui.components.SectionCard
import com.lenzorium.expenses.ui.components.SectionTitle
import com.lenzorium.expenses.ui.components.StatTile
import com.lenzorium.expenses.ui.components.TextInput
import com.lenzorium.expenses.ui.components.TxnRow
import com.lenzorium.expenses.ui.simpleFactory
import com.lenzorium.expenses.ui.theme.LocalMoneyColors
import androidx.compose.ui.platform.LocalView
import com.lenzorium.expenses.ui.LocalMessenger
import com.lenzorium.expenses.ui.confirmHaptic
import com.lenzorium.expenses.ui.components.FormSaveBar
import com.lenzorium.expenses.ui.components.FormSection
import com.lenzorium.expenses.ui.components.HeroAmountInput
import com.lenzorium.expenses.ui.components.rememberDiscardGuard
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

// ---------------- Loans list ----------------

@Composable
fun LoansScreen(state: LedgerState, nav: NavController) {
    val c = state.currency
    val m = LocalMoneyColors.current
    val (open, closed) = state.loanSummaries.partition { it.outstanding > 0 }
    ScreenScaffold("Loans", nav) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("Outstanding debt", Money.format(state.debt, c), Modifier.weight(1f), valueColor = m.loan)
                    StatTile("Repaid so far", Money.format(state.loanSummaries.sumOf { it.repaid }, c), Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { nav.navigate(Routes.loanForm()) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Take loan")
                    }
                    OutlinedButton(onClick = { nav.navigate(Routes.repay()) }, enabled = open.isNotEmpty(), modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Payments, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Repay")
                    }
                }
                TextButton(onClick = { nav.navigate(Routes.loanForm(existing = true)) }) {
                    Text("+ Add a loan you already had")
                }
            }
            if (state.loans.isEmpty()) {
                item {
                    SectionCard {
                        EmptyState(
                            Icons.Rounded.AccountBalance,
                            "No loans",
                            "When you borrow money, record it with \"Take loan\". It's added to your balance and your debt, and you can track repayments here.",
                        )
                    }
                }
            }
            if (open.isNotEmpty()) {
                item { SectionTitle("Active") }
                items(open.sortedWith(compareBy<LoanSummary>({ it.loan.dueEpochDay ?: Long.MAX_VALUE })), key = { it.loan.id }) { s ->
                    LoanCard(s, state, nav)
                }
            }
            if (closed.isNotEmpty()) {
                item { SectionTitle("Repaid") }
                items(closed, key = { it.loan.id }) { s -> LoanCard(s, state, nav) }
            }
        }
    }
}

@Composable
private fun LoanCard(s: LoanSummary, state: LedgerState, nav: NavController) {
    val c = state.currency
    val m = LocalMoneyColors.current
    SectionCard(Modifier.clickable { nav.navigate(Routes.loan(s.loan.id)) }) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(s.loan.lender, style = MaterialTheme.typography.titleMedium)
                    if (s.loan.title.isNotBlank()) Text(s.loan.title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StatusPill(s)
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    Money.format(s.outstanding, c),
                    style = MaterialTheme.typography.titleLarge,
                    color = if (s.outstanding > 0) m.loan else m.income,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(6.dp))
                Text(if (s.outstanding > 0) "owed" else "settled", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 2.dp))
            }
            Spacer(Modifier.height(10.dp))
            val total = (s.borrowed + s.charges).coerceAtLeast(1)
            Box(Modifier.fillMaxWidth().height(8.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(4.dp))) {
                Box(Modifier.fillMaxWidth((s.repaid.toFloat() / total).coerceIn(0f, 1f)).fillMaxHeight().background(m.income, RoundedCornerShape(4.dp)))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "${Money.format(s.repaid, c)} repaid of ${Money.format(s.borrowed + s.charges, c)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            s.loan.dueEpochDay?.let { due ->
                if (s.outstanding > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        (if (s.overdue) "Overdue · was due " else "Due ") + Dates.epochDay(due),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (s.overdue) m.expense else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (s.overdue) FontWeight.SemiBold else null,
                    )
                }
            }
        }
    }
}

@Composable
private fun Amount(label: String, value: String, modifier: Modifier, bold: Boolean = false) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = if (bold) FontWeight.Bold else null)
    }
}

@Composable
fun StatusPill(s: LoanSummary) {
    val m = LocalMoneyColors.current
    val (text, color) = when {
        s.overdue -> "Overdue" to m.expense
        s.status == LoanStatus.REPAID -> "Repaid" to m.income
        s.status == LoanStatus.PARTIALLY_REPAID -> "Partially repaid" to m.loan
        else -> "Active" to MaterialTheme.colorScheme.primary
    }
    Row(
        Modifier.background(color.copy(alpha = 0.14f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (s.overdue) { Icon(Icons.Rounded.Warning, null, tint = color, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)) }
        Text(text, color = color, style = MaterialTheme.typography.labelMedium)
    }
    if (s.overdue && s.status != LoanStatus.REPAID) {
        // Overdue loans still show their repayment status beside the warning.
        Spacer(Modifier.width(6.dp))
        Text(s.status.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------------- Loan detail ----------------

@Composable
fun LoanDetailScreen(state: LedgerState, repo: Repository, loanId: Long, nav: NavController) {
    val s = state.summary(loanId)
    LaunchedEffect(s, state.loaded) { if (s == null && state.loaded) nav.popBackStack() }
    if (s == null) return
    val c = state.currency
    val m = LocalMoneyColors.current
    val scope = rememberCoroutineScope()
    val history = state.txns.filter { it.loanId == loanId }
    val messenger = LocalMessenger.current
    var confirmDelete by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    ScreenScaffold(
        s.loan.lender, nav,
        actions = {
            IconButton(onClick = { nav.navigate(Routes.loanForm(loanId)) }) { Icon(Icons.Rounded.Edit, "Edit loan") }
            IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, "Delete loan") }
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                SectionCard {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(s.loan.title.ifBlank { "Loan" }, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    if (s.loan.isOpening) "Existing loan, owed when tracking began" else "Received ${Dates.date(s.loan.receivedAt)}",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            StatusPill(s)
                        }
                        Spacer(Modifier.height(14.dp))
                        Text("Outstanding", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(Money.format(s.outstanding, c), style = MaterialTheme.typography.headlineMedium, color = if (s.outstanding > 0) m.loan else m.income)
                        Spacer(Modifier.height(12.dp))
                        Row {
                            Amount(if (s.loan.isOpening) "Owed at start" else "Borrowed", Money.format(s.borrowed, c), Modifier.weight(1f))
                            if (s.charges > 0) Amount("Charges", Money.format(s.charges, c), Modifier.weight(1f))
                            Amount("Total repaid", Money.format(s.repaid, c), Modifier.weight(1f))
                        }
                        s.loan.dueEpochDay?.let {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "Due date: ${Dates.epochDay(it)}" + if (s.overdue) " · overdue" else "",
                                color = if (s.overdue) m.expense else MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        if (s.loan.notes.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(s.loan.notes, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { nav.navigate(Routes.repay(loanId)) }, enabled = s.outstanding > 0, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Payments, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Repay")
                    }
                    OutlinedButton(onClick = { nav.navigate(Routes.charge(loanId)) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.RequestQuote, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Add charge")
                    }
                }
            }
            item { SectionTitle("History") }
            if (history.isEmpty()) {
                item {
                    SectionCard {
                        EmptyState(Icons.Rounded.Payments, "No repayments yet", "Repayments you record for this loan will be listed here.")
                    }
                }
            } else {
                item {
                    SectionCard {
                        Column(Modifier.padding(vertical = 4.dp)) {
                            history.forEach { t -> TxnRow(t, null, c, onClick = { nav.navigate(Routes.txn(t.id)) }, showDate = true, account = state.accountLabel(t)) }
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        val linked = history.size
        val cashEffect = history.sumOf { LedgerMath.cashDelta(it) }
        ConfirmDialog(
            title = "Delete this loan and its history?",
            message = buildString {
                append("This permanently removes the loan from ${s.loan.lender}")
                if (linked > 0) append(" and its $linked linked ${if (linked == 1) "entry" else "entries"} (money received, repayments and charges)")
                append(". ")
                if (cashEffect != 0L) append("Your available balance will change by ${Money.formatSigned(-cashEffect, c)}. ")
                append("If you've simply paid it off, keep it instead — it will show as Repaid.")
            },
            confirmLabel = "Delete everything",
            destructive = true,
            onConfirm = {
                confirmDelete = false
                scope.launch {
                    val snapshot = repo.loanSnapshot(loanId)
                    when (val r = repo.deleteLoan(loanId)) {
                        is SaveResult.Error -> error = r.message
                        is SaveResult.Ok -> {
                            snapshot?.let { (l, h) -> messenger.show("Loan from ${l.lender} deleted", "Undo") { repo.restoreLoan(l, h) } }
                            nav.popBackStack()
                        }
                    }
                }
            },
            onDismiss = { confirmDelete = false },
        )
    }
    error?.let { MessageDialog("Can't delete", it) { error = null } }
}

// ---------------- Loan form ----------------

class LoanFormViewModel(private val repo: Repository, private val editId: Long, existingFlag: Boolean) : ViewModel() {
    var lender by mutableStateOf("")
    var title by mutableStateOf("")
    var amount by mutableStateOf("")
    var receivedAt by mutableStateOf(System.currentTimeMillis())
    var dueEpochDay by mutableStateOf<Long?>(null)
    var notes by mutableStateOf("")
    var accountId by mutableStateOf<Long?>(null)
    var isOpening by mutableStateOf(existingFlag)
    var loaded by mutableStateOf(editId == 0L)
    var saving by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var confirmNegative by mutableStateOf<String?>(null)
    var savedId by mutableStateOf<Long?>(null)
    private var original: Loan? = null
    private val requestId = UUID.randomUUID().toString()
    val isEdit get() = editId != 0L

    private fun snapshot() = listOf(lender, title, amount, receivedAt, dueEpochDay, notes, accountId).toString()
    private var initial: String? = if (editId == 0L) snapshot() else null
    val dirty: Boolean get() = loaded && savedId == null && initial != null && snapshot() != initial
    fun markClean() { initial = snapshot() }

    init {
        if (editId != 0L) viewModelScope.launch {
            repo.loan(editId)?.let { l ->
                original = l
                lender = l.lender; title = l.title; notes = l.notes
                amount = Money.toInput(l.principalMinor, repo.settings.current.currency.decimals)
                receivedAt = l.receivedAt; dueEpochDay = l.dueEpochDay; isOpening = l.isOpening
            }
            initial = snapshot()
            loaded = true
        }
    }

    fun save(state: LedgerState, confirmed: Boolean) {
        if (saving || savedId != null) return
        error = null
        val minor = Money.parse(amount, repo.settings.current.currency.decimals)
        when {
            lender.isBlank() -> { error = "Enter the lender's name."; return }
            minor == null -> { error = "Enter a valid amount."; return }
            minor <= 0 -> { error = "Enter an amount greater than zero."; return }
            !isOpening && accountId == null -> { error = "Choose where the money went."; return }
        }
        val o = original
        val loan = Loan(
            id = o?.id ?: 0L,
            lender = lender.trim(),
            title = title.trim(),
            principalMinor = minor!!,
            receivedAt = if (isOpening) (o?.receivedAt ?: receivedAt) else receivedAt,
            dueEpochDay = dueEpochDay,
            notes = notes.trim(),
            isOpening = o?.isOpening ?: isOpening,
            requestId = o?.requestId ?: requestId,
            createdAt = o?.createdAt ?: System.currentTimeMillis(),
        )
        // Lowering a received loan's amount (or moving it to another account) takes money back out.
        if (!loan.isOpening && o != null && !confirmed) {
            val oldTxn = state.txns.firstOrNull { it.loanId == o.id && it.type == TxnType.LOAN_RECEIVED }
            if (oldTxn != null) {
                val newTxn = oldTxn.copy(amountMinor = loan.principalMinor, timestamp = loan.receivedAt, accountId = accountId)
                val oldAcc = oldTxn.accountId
                val warn = negativeWarning(state, oldTxn, newTxn)
                    ?: if (oldAcc != null && oldAcc != accountId) negativeWarning(state, oldTxn, oldTxn.copy(amountMinor = 0)) else null
                if (warn != null) { confirmNegative = warn; return }
            }
        }
        confirmNegative = null
        saving = true
        viewModelScope.launch {
            when (val r = repo.saveLoan(loan, if (loan.isOpening) null else accountId)) {
                is SaveResult.Ok -> savedId = r.id
                is SaveResult.Error -> error = r.message
            }
            saving = false
        }
    }
}

@Composable
fun LoanFormScreen(repo: Repository, state: LedgerState, editId: Long, existing: Boolean, nav: NavController) {
    val vm: LoanFormViewModel = viewModel(factory = simpleFactory { LoanFormViewModel(repo, editId, existing) })
    val c = state.currency
    var pickDue by remember { mutableStateOf(false) }
    val messenger = LocalMessenger.current
    val view = LocalView.current
    LaunchedEffect(vm.savedId) {
        if (vm.savedId != null) {
            view.confirmHaptic()
            messenger.show(if (vm.isEdit) "Loan updated" else "Loan from ${vm.lender.trim()} saved")
            nav.popBackStack()
        }
    }
    LaunchedEffect(vm.loaded, state.accounts) {
        // Pre-fill the account from the linked "received" entry when editing; default to the first account otherwise.
        if (vm.loaded && vm.accountId == null) {
            if (vm.isEdit) state.txns.firstOrNull { it.loanId == editId && it.type == TxnType.LOAN_RECEIVED }?.accountId?.let { vm.accountId = it }
            else vm.accountId = state.activeLedgerAccounts.firstOrNull()?.id
            vm.markClean()
        }
    }
    val back = rememberDiscardGuard(vm.dirty) { nav.popBackStack() }
    val title = when {
        vm.isEdit -> "Edit Loan"
        vm.isOpening -> "Existing Loan"
        else -> "Take Loan"
    }
    ScreenScaffold(
        title, nav, onBack = back,
        bottomBar = { if (vm.loaded) FormSaveBar(if (vm.isEdit) "Save changes" else "Save loan", vm.saving) { vm.save(state, false) } },
    ) { padding ->
        if (!vm.loaded) { CircularProgressIndicator(Modifier.padding(padding).padding(24.dp)); return@ScreenScaffold }
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HeroAmountInput(
                vm.amount, { vm.amount = it }, c, LocalMoneyColors.current.loan,
                label = if (vm.isOpening) "Amount still owed" else "Amount received",
                modifier = Modifier.padding(vertical = 12.dp),
            )
            if (vm.isOpening) {
                InfoNote("An existing loan adds to your outstanding debt only. Your available balance doesn't change, because that money is already part of it.")
            } else if (!vm.isEdit) {
                InfoNote("Borrowed money is added to your available balance and to your debt. It is not counted as income.")
            }
            FormSection {
                TextInput(vm.lender, { vm.lender = it }, "Lender's name")
                val lenders = state.loans.map { it.lender }.distinct().filter { it != vm.lender }.take(6)
                if (lenders.isNotEmpty() && !vm.isEdit) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        lenders.forEach { l -> SuggestionChip(onClick = { vm.lender = l }, label = { Text(l) }) }
                    }
                }
                TextInput(vm.title, { vm.title = it }, "Loan title or reference", placeholder = "e.g. Car repair loan")
            }
            FormSection {
                if (!vm.isOpening) {
                    DateTimeField(vm.receivedAt, { vm.receivedAt = it }, minDate = LocalDate.ofEpochDay(state.settings.trackingStartEpochDay), label = "Date received")
                    AccountSelector(
                        state.activeLedgerAccounts + listOfNotNull(vm.accountId?.let { id -> state.accountById[id]?.takeIf { it.archived } }),
                        vm.accountId, { vm.accountId = it }, state.accountBalances, c, label = "Received into",
                    )
                }
                Column {
                    Text("Due date", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { pickDue = true }) { Text(vm.dueEpochDay?.let { Dates.epochDay(it) } ?: "Set due date (optional)") }
                        if (vm.dueEpochDay != null) IconButton(onClick = { vm.dueEpochDay = null }) { Icon(Icons.Rounded.Close, "Clear due date") }
                    }
                }
                TextInput(vm.notes, { vm.notes = it }, "Notes", singleLine = false)
            }
            ErrorBanner(vm.error)
            Spacer(Modifier.height(8.dp))
        }
    }
    if (pickDue) DatePickDialog(vm.dueEpochDay?.let { LocalDate.ofEpochDay(it) } ?: LocalDate.now().plusMonths(1), { vm.dueEpochDay = it.toEpochDay() }, { pickDue = false })
    vm.confirmNegative?.let {
        ConfirmDialog("Balance will go negative", it, "Save anyway",
            onConfirm = { vm.save(state, true) }, onDismiss = { vm.confirmNegative = null })
    }
}

@Composable
fun InfoNote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.small).padding(12.dp),
    )
}

// ---------------- Repayment / charge form ----------------

class LoanEventViewModel(private val repo: Repository, val type: TxnType, initialLoan: Long, private val editId: Long) : ViewModel() {
    var loanId by mutableStateOf(initialLoan.takeIf { it != 0L })
    var amount by mutableStateOf("")
    var timestamp by mutableStateOf(System.currentTimeMillis())
    var accountId by mutableStateOf<Long?>(null)
    var notes by mutableStateOf("")
    var loaded by mutableStateOf(editId == 0L)
    var saving by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var confirmNegative by mutableStateOf<String?>(null)
    var done by mutableStateOf(false)
    var original: Txn? = null
        private set
    private val requestId = UUID.randomUUID().toString()
    val isEdit get() = editId != 0L

    private fun snapshot() = listOf(loanId, amount, timestamp, notes).toString()
    private var initial: String? = if (editId == 0L) snapshot() else null
    val dirty: Boolean get() = loaded && !done && initial != null && snapshot() != initial

    init {
        if (editId != 0L) viewModelScope.launch {
            repo.txn(editId)?.let { t ->
                original = t
                loanId = t.loanId
                amount = Money.toInput(t.amountMinor, repo.settings.current.currency.decimals)
                timestamp = t.timestamp
                accountId = t.accountId
                notes = t.notes
            }
            initial = snapshot()
            loaded = true
        }
    }

    fun save(state: LedgerState, owed: Long?, confirmed: Boolean) {
        if (saving || done) return
        error = null
        val minor = Money.parse(amount, repo.settings.current.currency.decimals)
        when {
            loanId == null -> { error = "Choose a loan."; return }
            minor == null -> { error = "Enter a valid amount."; return }
            minor <= 0 -> { error = "Enter an amount greater than zero."; return }
            type == TxnType.LOAN_REPAYMENT && accountId == null -> { error = "Choose where you paid from."; return }
            type == TxnType.LOAN_REPAYMENT && owed != null && minor > owed ->
                { error = "That's more than the ${Money.format(owed, repo.settings.current.currency)} still owed."; return }
        }
        val o = original
        val txn = Txn(
            id = o?.id ?: 0L,
            type = type,
            amountMinor = minor!!,
            timestamp = timestamp,
            accountId = if (type == TxnType.LOAN_REPAYMENT) accountId else null,
            notes = notes.trim(),
            loanId = loanId,
            requestId = o?.requestId ?: requestId,
            createdAt = o?.createdAt ?: System.currentTimeMillis(),
        )
        if (type == TxnType.LOAN_REPAYMENT) {
            if (!confirmed) negativeWarning(state, o, txn)?.let { confirmNegative = it; return }
        }
        confirmNegative = null
        saving = true
        viewModelScope.launch {
            when (val r = repo.saveLoanEvent(txn)) {
                is SaveResult.Ok -> done = true
                is SaveResult.Error -> error = r.message
            }
            saving = false
        }
    }
}

@Composable
fun LoanEventScreen(repo: Repository, state: LedgerState, type: TxnType, loanId: Long, editId: Long, nav: NavController) {
    val vm: LoanEventViewModel = viewModel(factory = simpleFactory { LoanEventViewModel(repo, type, loanId, editId) })
    val c = state.currency
    val isRepay = type == TxnType.LOAN_REPAYMENT
    val messenger = LocalMessenger.current
    val view = LocalView.current
    LaunchedEffect(vm.done) {
        if (vm.done) {
            view.confirmHaptic()
            val left = vm.loanId?.let { state.summary(it) }
            messenger.show(
                when {
                    vm.isEdit -> "Changes saved"
                    !isRepay -> "Charge added to loan"
                    left != null && left.outstanding <= 0 -> "Repayment saved · loan fully repaid"
                    else -> "Repayment saved"
                }
            )
            nav.popBackStack()
        }
    }
    val back = rememberDiscardGuard(vm.dirty) { nav.popBackStack() }
    LaunchedEffect(vm.loaded, state.accounts) {
        if (vm.loaded && !vm.isEdit && vm.accountId == null) vm.accountId = state.activeLedgerAccounts.firstOrNull()?.id
    }

    val selectable = state.loanSummaries.filter { it.outstanding > 0 || it.loan.id == vm.loanId }
    val selected = vm.loanId?.let { state.summary(it) }
    // What can be repaid with this entry: current outstanding plus this entry's own amount when editing.
    val owed = selected?.let { s ->
        val o = vm.original
        s.outstanding + if (o != null && o.loanId == s.loan.id) (if (isRepay) o.amountMinor else -o.amountMinor) else 0L
    }
    val title = (if (vm.isEdit) "Edit " else "") + if (isRepay) "Repay Loan" else "Fee or Interest"

    ScreenScaffold(
        title, nav, onBack = back,
        bottomBar = {
            if (vm.loaded && selectable.isNotEmpty()) FormSaveBar(
                if (vm.isEdit) "Save changes" else if (isRepay) "Save repayment" else "Add charge", vm.saving,
            ) { vm.save(state, if (isRepay) owed else null, false) }
        },
    ) { padding ->
        if (!vm.loaded) { CircularProgressIndicator(Modifier.padding(padding).padding(24.dp)); return@ScreenScaffold }
        if (selectable.isEmpty()) {
            Column(Modifier.padding(padding)) {
                EmptyState(Icons.Rounded.AccountBalance, "No loans to repay", "You don't owe anything right now. Record borrowed money with \"Take loan\".") {
                    Button(onClick = { nav.popBackStack(); nav.navigate(Routes.loanForm()) }) { Text("Take loan") }
                }
            }
            return@ScreenScaffold
        }
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HeroAmountInput(
                vm.amount, { vm.amount = it }, c, LocalMoneyColors.current.loan,
                label = if (isRepay) "Repayment amount" else "Charge amount",
                modifier = Modifier.padding(vertical = 12.dp),
            )
            if (isRepay && owed != null && owed > 0) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                    AssistChip(onClick = { vm.amount = Money.toInput(owed, c.decimals) }, label = { Text("Pay in full") })
                    if (owed >= 2) AssistChip(onClick = { vm.amount = Money.toInput(owed / 2, c.decimals) }, label = { Text("Half") })
                }
            }
            if (!isRepay) InfoNote("Fees or interest you've agreed to pay increase what you owe on this loan. Your available balance only changes when you repay.")
            FormSection {
                LoanPicker(selectable, vm.loanId, c, enabled = !vm.isEdit) { vm.loanId = it }
                if (owed != null) {
                    Text(
                        "Still owed: ${Money.format(owed, c)}",
                        style = MaterialTheme.typography.titleSmall,
                        color = LocalMoneyColors.current.loan,
                    )
                }
            }
            FormSection {
                DateTimeField(vm.timestamp, { vm.timestamp = it }, minDate = LocalDate.ofEpochDay(state.settings.trackingStartEpochDay), label = if (isRepay) "Payment date" else "Date")
                if (isRepay) AccountSelector(
                    state.activeLedgerAccounts + listOfNotNull(vm.accountId?.let { id -> state.accountById[id]?.takeIf { it.archived } }),
                    vm.accountId, { vm.accountId = it }, state.accountBalances, c, label = "Paid from",
                )
                TextInput(vm.notes, { vm.notes = it }, if (isRepay) "Notes" else "Description", singleLine = false, placeholder = if (isRepay) null else "e.g. Late fee, monthly interest")
            }
            if (isRepay) Text("Repayments reduce your balance and your debt. They're shown separately from everyday expenses.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ErrorBanner(vm.error)
            Spacer(Modifier.height(8.dp))
        }
    }
    vm.confirmNegative?.let {
        ConfirmDialog("Balance will go negative", it, "Save anyway",
            onConfirm = { vm.save(state, owed, true) }, onDismiss = { vm.confirmNegative = null })
    }
}

@Composable
private fun LoanPicker(options: List<LoanSummary>, selectedId: Long?, c: com.lenzorium.expenses.domain.CurrencyInfo, enabled: Boolean, onSelect: (Long) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val sel = options.firstOrNull { it.loan.id == selectedId }
    fun label(s: LoanSummary) = listOf(s.loan.lender, s.loan.title).filter { it.isNotBlank() }.joinToString(" · ")
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (enabled) expanded = it }) {
        OutlinedTextField(
            value = sel?.let { label(it) } ?: "",
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text("Loan") },
            placeholder = { Text("Choose a loan") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { s ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(label(s))
                            Text("Owed ${Money.format(s.outstanding, c)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    onClick = { onSelect(s.loan.id); expanded = false },
                )
            }
        }
    }
}
