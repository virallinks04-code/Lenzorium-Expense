package com.lenzorium.expenses.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.lenzorium.expenses.data.Account
import com.lenzorium.expenses.data.AccountKind
import com.lenzorium.expenses.data.Repository
import com.lenzorium.expenses.data.SaveResult
import com.lenzorium.expenses.data.Txn
import com.lenzorium.expenses.data.TxnType
import com.lenzorium.expenses.domain.LedgerMath
import com.lenzorium.expenses.domain.Money
import com.lenzorium.expenses.ui.LedgerState
import com.lenzorium.expenses.ui.LocalMessenger
import com.lenzorium.expenses.ui.Routes
import com.lenzorium.expenses.ui.confirmHaptic
import com.lenzorium.expenses.ui.components.AccountSelector
import com.lenzorium.expenses.ui.components.AmountField
import com.lenzorium.expenses.ui.components.ConfirmDialog
import com.lenzorium.expenses.ui.components.DateTimeField
import com.lenzorium.expenses.ui.components.Dates
import com.lenzorium.expenses.ui.components.EmptyState
import com.lenzorium.expenses.ui.components.ErrorBanner
import com.lenzorium.expenses.ui.components.FormSaveBar
import com.lenzorium.expenses.ui.components.FormSection
import com.lenzorium.expenses.ui.components.HeroAmountInput
import com.lenzorium.expenses.ui.components.IconBadge
import com.lenzorium.expenses.ui.components.ScreenScaffold
import com.lenzorium.expenses.ui.components.SectionCard
import com.lenzorium.expenses.ui.components.SectionTitle
import com.lenzorium.expenses.ui.components.TextInput
import com.lenzorium.expenses.ui.components.TxnRow
import com.lenzorium.expenses.ui.components.accountIcon
import com.lenzorium.expenses.ui.components.rememberDiscardGuard
import com.lenzorium.expenses.ui.negativeWarning
import com.lenzorium.expenses.ui.simpleFactory
import com.lenzorium.expenses.ui.theme.LocalMoneyColors
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

// ---------------- Transfer ----------------

class TransferViewModel(private val repo: Repository, private val editId: Long) : ViewModel() {
    var amount by mutableStateOf("")
    /** Amount that arrived, when the two accounts hold different currencies. */
    var received by mutableStateOf("")
    var fromId by mutableStateOf<Long?>(null)
    var toId by mutableStateOf<Long?>(null)
    var timestamp by mutableStateOf(System.currentTimeMillis())
    var notes by mutableStateOf("")
    var loaded by mutableStateOf(editId == 0L)
    var saving by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var confirmNegative by mutableStateOf<String?>(null)
    var done by mutableStateOf(false)
    private var original: Txn? = null
    private val requestId = UUID.randomUUID().toString()
    val isEdit get() = editId != 0L

    private fun snapshot() = listOf(amount, received, timestamp, notes).toString()
    private var initial: String? = if (editId == 0L) snapshot() else null
    val dirty: Boolean get() = loaded && !done && initial != null && snapshot() != initial

    init {
        if (editId != 0L) viewModelScope.launch {
            repo.txn(editId)?.let { t ->
                original = t
                amount = Money.toInput(t.amountMinor, repo.currencyOf(t.accountId).decimals)
                t.toAmountMinor?.let { received = Money.toInput(it, repo.currencyOf(t.toAccountId).decimals) }
                fromId = t.accountId; toId = t.toAccountId
                timestamp = t.timestamp; notes = t.notes
            }
            initial = snapshot()
            loaded = true
        }
    }

    fun swap() { val f = fromId; fromId = toId; toId = f }

    fun save(state: LedgerState, confirmed: Boolean) {
        if (saving || done) return
        error = null
        val fromCur = state.currencyOf(fromId)
        val toCur = state.currencyOf(toId)
        val cross = fromCur.code != toCur.code
        val minor = Money.parse(amount, fromCur.decimals)
        val got = if (cross) Money.parse(received, toCur.decimals) else null
        when {
            minor == null -> { error = if (amount.isBlank()) "Enter an amount." else "Enter a valid amount."; return }
            minor <= 0 -> { error = "Enter an amount greater than zero."; return }
            fromId == null || toId == null -> { error = "Choose both accounts."; return }
            fromId == toId -> { error = "Choose two different accounts."; return }
            cross && (got == null || got <= 0) -> { error = "Enter how much arrived in ${toCur.code}."; return }
        }
        val o = original
        val txn = Txn(
            id = o?.id ?: 0L,
            type = TxnType.TRANSFER,
            amountMinor = minor!!,
            timestamp = timestamp,
            notes = notes.trim(),
            requestId = o?.requestId ?: requestId,
            createdAt = o?.createdAt ?: System.currentTimeMillis(),
            accountId = fromId,
            toAccountId = toId,
            toAmountMinor = got,
        )
        if (!confirmed) negativeWarning(state, o, txn)?.let { confirmNegative = it; return }
        confirmNegative = null
        saving = true
        viewModelScope.launch {
            when (val r = repo.saveTransfer(txn)) {
                is SaveResult.Ok -> done = true
                is SaveResult.Error -> error = r.message
            }
            saving = false
        }
    }
}

@Composable
fun TransferScreen(repo: Repository, state: LedgerState, editId: Long, presetFrom: Long, nav: NavController) {
    val vm: TransferViewModel = viewModel(factory = simpleFactory { TransferViewModel(repo, editId) })
    val c = state.currencyOf(vm.fromId)
    val toCur = state.currencyOf(vm.toId)
    val cross = c.code != toCur.code
    val messenger = LocalMessenger.current
    val view = LocalView.current
    LaunchedEffect(vm.loaded, state.accounts) {
        if (vm.loaded && !vm.isEdit && vm.fromId == null && state.activeAccounts.size >= 2) {
            // Default: Bank → Cash, the most common move (an ATM withdrawal).
            val active = state.activeAccounts
            val from = active.firstOrNull { it.id == presetFrom } ?: active.firstOrNull { it.kind == AccountKind.BANK } ?: active[0]
            vm.fromId = from.id
            vm.toId = (active.firstOrNull { it.kind == AccountKind.CASH && it.id != from.id } ?: active.first { it.id != from.id }).id
        }
    }
    LaunchedEffect(vm.done) {
        if (vm.done) {
            view.confirmHaptic()
            messenger.show(if (vm.isEdit) "Transfer updated" else "Moved ${Money.parse(vm.amount, c.decimals)?.let { Money.format(it, c) } ?: ""} to ${state.accountName(vm.toId) ?: ""}")
            nav.popBackStack()
        }
    }
    val back = rememberDiscardGuard(vm.dirty) { nav.popBackStack() }
    val pickable = state.activeAccounts + state.accounts.filter { it.archived && (it.id == vm.fromId || it.id == vm.toId) }

    ScreenScaffold(
        if (vm.isEdit) "Edit Transfer" else "Transfer", nav, onBack = back,
        bottomBar = {
            if (vm.loaded && state.activeAccounts.size >= 2) FormSaveBar(if (vm.isEdit) "Save changes" else "Save transfer", vm.saving) { vm.save(state, false) }
        },
    ) { padding ->
        if (!vm.loaded) { CircularProgressIndicator(Modifier.padding(padding).padding(24.dp)); return@ScreenScaffold }
        if (state.activeAccounts.size < 2) {
            Column(Modifier.padding(padding)) {
                EmptyState(Icons.Rounded.SwapHoriz, "Add another account first", "Transfers move money between two of your accounts, for example from Bank to Cash.") {
                    OutlinedButton(onClick = { nav.navigate(Routes.ACCOUNTS) }) { Text("Manage accounts") }
                }
            }
            return@ScreenScaffold
        }
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HeroAmountInput(vm.amount, { vm.amount = it }, c, MaterialTheme.colorScheme.primary, label = if (cross) "Amount sent" else "Amount to move", modifier = Modifier.padding(vertical = 12.dp))
            FormSection {
                AccountSelector(pickable, vm.fromId, { id -> if (id == vm.toId) vm.swap() else vm.fromId = id }, state.accountBalances, c, label = "From")
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    FilledTonalIconButton(onClick = { vm.swap() }) { Icon(Icons.Rounded.SwapVert, "Swap accounts") }
                }
                AccountSelector(pickable, vm.toId, { id -> if (id == vm.fromId) vm.swap() else vm.toId = id }, state.accountBalances, c, label = "To")
            }
            if (cross) {
                FormSection {
                    AmountField(vm.received, { vm.received = it }, toCur, label = "Amount received in ${toCur.code}")
                    val sent = Money.parse(vm.amount, c.decimals); val got = Money.parse(vm.received, toCur.decimals)
                    if (sent != null && got != null && sent > 0 && got > 0) {
                        val rate = java.math.BigDecimal(sent).movePointLeft(c.decimals)
                            .divide(java.math.BigDecimal(got).movePointLeft(toCur.decimals), 4, java.math.RoundingMode.HALF_UP)
                        Text("Rate used: 1 ${toCur.code} = ${rate.stripTrailingZeros().toPlainString()} ${c.code}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        "Different currencies are never added together. Your ${state.currency.code} total changes by the amount that leaves or arrives in ${state.currency.code}.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            FormSection {
                DateTimeField(vm.timestamp, { vm.timestamp = it }, minDate = LocalDate.ofEpochDay(state.settings.trackingStartEpochDay), label = "Date and time")
                TextInput(vm.notes, { vm.notes = it }, "Notes", singleLine = false, placeholder = "e.g. ATM withdrawal")
            }
            Text(
                if (cross) "Currency exchanges aren't income or expenses." else "A transfer only moves money between your own accounts. Your total balance, income and expenses don't change.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ErrorBanner(vm.error)
            Spacer(Modifier.height(8.dp))
        }
    }
    vm.confirmNegative?.let {
        ConfirmDialog("Balance will go negative", it, "Save anyway", onConfirm = { vm.save(state, true) }, onDismiss = { vm.confirmNegative = null })
    }
}

// ---------------- Accounts list ----------------

@Composable
fun AccountsScreen(state: LedgerState, repo: Repository, nav: NavController) {
    val c = state.currency
    var editing by remember { mutableStateOf<Account?>(null) }
    var adding by remember { mutableStateOf(false) }
    val (active, archived) = state.accounts.partition { !it.archived }
    ScreenScaffold(
        "Accounts", nav,
        floatingActionButton = { FloatingActionButton(onClick = { adding = true }) { Icon(Icons.Rounded.Add, "Add account") } },
    ) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                SectionCard {
                    Column(Modifier.padding(16.dp)) {
                        Text("Total available", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(Money.format(state.available, c), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(if (state.foreignIds.isEmpty()) "The sum of all your accounts" else "The sum of your ${c.code} accounts. Other currencies are shown separately.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            items(active, key = { it.id }) { a -> AccountRow(a, state, onClick = { nav.navigate(Routes.account(a.id)) }) }
            item {
                OutlinedButton(onClick = { nav.navigate(Routes.transfer()) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.SwapHoriz, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Move money between accounts")
                }
            }
            if (archived.isNotEmpty()) {
                item { SectionTitle("Archived") }
                items(archived, key = { it.id }) { a -> AccountRow(a, state, onClick = { nav.navigate(Routes.account(a.id)) }) }
            }
        }
    }
    if (adding || editing != null) AccountEditDialog(state, repo, editing) { adding = false; editing = null }
}

@Composable
private fun AccountRow(a: Account, state: LedgerState, onClick: () -> Unit) {
    val bal = state.balanceOf(a.id)
    SectionCard(Modifier.clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(accountIcon(a.kind), MaterialTheme.colorScheme.primary, size = 44)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(a.name, style = MaterialTheme.typography.titleMedium)
                Text(a.kind.label + (a.currency?.takeIf { a.id in state.foreignIds }?.let { " · $it" } ?: "") + if (a.archived) " · archived" else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                Money.format(bal, state.currencyOf(a.id)),
                style = MaterialTheme.typography.titleMedium,
                color = if (bal < 0) LocalMoneyColors.current.expense else MaterialTheme.colorScheme.onSurface,
            )
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
fun AccountEditDialog(state: LedgerState, repo: Repository, existing: Account?, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var currencyCode by remember { mutableStateOf(existing?.currency ?: state.settings.currencyCode) }
    var pickCurrency by remember { mutableStateOf(false) }
    val c = com.lenzorium.expenses.domain.Currencies.find(currencyCode)
    val locked = existing != null && state.txns.any { it.accountId == existing.id || it.toAccountId == existing.id }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var kind by remember { mutableStateOf(existing?.kind ?: AccountKind.BANK) }
    var opening by remember { mutableStateOf(existing?.let { Money.toInput(it.openingMinor, c.decimals) } ?: "") }
    var archived by remember { mutableStateOf(existing?.archived ?: false) }
    var err by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(if (existing == null) "New account" else "Edit account") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextInput(name, { name = it }, "Name", placeholder = "e.g. Meezan Bank, JazzCash")
                Text("Type", style = MaterialTheme.typography.labelLarge)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AccountKind.entries.forEach { k ->
                        FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(k.label) }, leadingIcon = { Icon(accountIcon(k), null, Modifier.size(18.dp)) })
                    }
                }
                Row(Modifier.fillMaxWidth().clickable(enabled = !locked) { pickCurrency = true }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Currency", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (locked) "Fixed: this account has entries" else if (currencyCode == state.settings.currencyCode) "Main currency" else "Kept separate from your ${state.settings.currencyCode} total",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text("${c.code} · ${c.name}", style = MaterialTheme.typography.titleSmall, color = if (locked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
                }
                AmountField(opening, { opening = it }, c, label = "Balance on ${Dates.epochDay(state.settings.trackingStartEpochDay)}")
                Text(
                    "This is what the account held when you started tracking. Later entries are added on top.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (existing != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Archived", style = MaterialTheme.typography.titleSmall)
                            Text("Hide from pickers; past entries keep it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = archived, onCheckedChange = { archived = it })
                    }
                    TextButton(onClick = { confirmDelete = true }) { Text("Delete account", color = MaterialTheme.colorScheme.error) }
                }
                ErrorBanner(err)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val v = if (opening.isBlank()) 0L else Money.parse(opening, c.decimals)
                if (v == null) err = "Enter a valid opening balance." else scope.launch {
                    val r = if (existing == null) repo.addAccount(name, kind, v, currencyCode)
                    else repo.updateAccount(existing.copy(name = name, kind = kind, openingMinor = v, archived = archived, currency = currencyCode))
                    when (r) {
                        is SaveResult.Ok -> onDone()
                        is SaveResult.Error -> err = r.message
                    }
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Cancel") } },
    )
    if (pickCurrency) {
        AlertDialog(
            onDismissRequest = { pickCurrency = false },
            title = { Text("Account currency") },
            text = {
                androidx.compose.foundation.lazy.LazyColumn(Modifier.height(360.dp)) {
                    items(com.lenzorium.expenses.domain.Currencies.all, key = { it.code }) { cur ->
                        Row(Modifier.fillMaxWidth().clickable { currencyCode = cur.code; pickCurrency = false }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(cur.code, style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(56.dp))
                            Text(cur.name + if (cur.code == state.settings.currencyCode) " (main)" else "", modifier = Modifier.weight(1f))
                            if (cur.code == currencyCode) Icon(Icons.Rounded.Check, "Selected", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickCurrency = false }) { Text("Close") } },
        )
    }
    if (confirmDelete && existing != null) {
        ConfirmDialog(
            "Delete \"${existing.name}\"?", "Only accounts with no entries can be deleted.", "Delete", destructive = true,
            onConfirm = {
                confirmDelete = false
                scope.launch {
                    when (val r = repo.deleteAccount(existing)) {
                        is SaveResult.Ok -> onDone()
                        is SaveResult.Error -> err = r.message
                    }
                }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

// ---------------- Account detail ----------------

@Composable
fun AccountDetailScreen(state: LedgerState, repo: Repository, accountId: Long, nav: NavController) {
    val a = state.accountById[accountId]
    LaunchedEffect(a, state.loaded) { if (a == null && state.loaded && state.accounts.isNotEmpty()) nav.popBackStack() }
    if (a == null) return
    val c = state.currencyOf(a.id)
    var editing by remember { mutableStateOf(false) }
    val entries = state.txns.filter { it.accountId == accountId || it.toAccountId == accountId }
    val today = LocalDate.now()
    val monthStart = Dates.startOf(today.withDayOfMonth(1))
    val monthIn = entries.filter { it.timestamp >= monthStart }.sumOf { maxOf(0L, LedgerMath.accountDelta(it, accountId)) }
    val monthOut = entries.filter { it.timestamp >= monthStart }.sumOf { maxOf(0L, -LedgerMath.accountDelta(it, accountId)) }
    val m = LocalMoneyColors.current

    ScreenScaffold(a.name, nav, actions = { IconButton(onClick = { editing = true }) { Icon(Icons.Rounded.Edit, "Edit account") } }) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                SectionCard {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(accountIcon(a.kind), MaterialTheme.colorScheme.primary, size = 44)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Balance now", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                val bal = state.balanceOf(a.id)
                                Text(Money.format(bal, c), style = MaterialTheme.typography.headlineMedium, color = if (bal < 0) m.expense else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(10.dp))
                        Row {
                            Column(Modifier.weight(1f)) {
                                Text("In this month", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(Money.format(monthIn, c), style = MaterialTheme.typography.titleSmall, color = m.income)
                            }
                            Column(Modifier.weight(1f)) {
                                Text("Out this month", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(Money.format(monthOut, c), style = MaterialTheme.typography.titleSmall, color = m.expense)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Opening balance ${Money.format(a.openingMinor, c)} on ${Dates.epochDay(state.settings.trackingStartEpochDay)}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                OutlinedButton(onClick = { nav.navigate(Routes.transfer(from = a.id)) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.SwapHoriz, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Move money from ${a.name}")
                }
            }
            item { SectionTitle("History") }
            if (entries.isEmpty()) {
                item { SectionCard { EmptyState(accountIcon(a.kind), "No entries yet", "Entries paid from or received into ${a.name} will appear here.") } }
            } else {
                item {
                    SectionCard {
                        Column(Modifier.padding(vertical = 4.dp)) {
                            entries.forEach { t ->
                                TxnRow(t, t.categoryId?.let { state.categoryById[it] }, state.currencyFor(t), toCurrency = state.currencyOf(t.toAccountId), person = t.personId?.let { state.holderById[it]?.name }, onClick = { nav.navigate(Routes.txn(t.id)) }, showDate = true, account = state.accountLabel(t))
                            }
                        }
                    }
                }
            }
        }
    }
    if (editing) AccountEditDialog(state, repo, a) { editing = false }
}
