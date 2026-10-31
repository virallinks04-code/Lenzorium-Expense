package com.lenzorium.expenses.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.lenzorium.expenses.data.CategoryKind
import com.lenzorium.expenses.data.PayMethod
import com.lenzorium.expenses.data.Repository
import com.lenzorium.expenses.data.SaveResult
import com.lenzorium.expenses.data.Txn
import com.lenzorium.expenses.data.TxnType
import com.lenzorium.expenses.domain.LedgerMath
import com.lenzorium.expenses.domain.Money
import com.lenzorium.expenses.ui.LedgerState
import com.lenzorium.expenses.ui.ReceiptImport
import com.lenzorium.expenses.ui.components.AmountField
import com.lenzorium.expenses.ui.components.CategoryPicker
import com.lenzorium.expenses.ui.components.ConfirmDialog
import com.lenzorium.expenses.ui.components.DateTimeField
import com.lenzorium.expenses.ui.components.ErrorBanner
import com.lenzorium.expenses.ui.components.AccountSelector
import com.lenzorium.expenses.ui.negativeWarning
import com.lenzorium.expenses.ui.components.ScreenScaffold
import com.lenzorium.expenses.ui.components.TextInput
import com.lenzorium.expenses.ui.simpleFactory
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import com.lenzorium.expenses.ui.LocalMessenger
import com.lenzorium.expenses.ui.confirmHaptic
import com.lenzorium.expenses.ui.components.CategoryGrid
import com.lenzorium.expenses.ui.components.FormSaveBar
import com.lenzorium.expenses.ui.components.FormSection
import com.lenzorium.expenses.ui.components.HeroAmountInput
import com.lenzorium.expenses.ui.components.rememberDiscardGuard
import com.lenzorium.expenses.ui.theme.LocalMoneyColors
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

class EntryViewModel(private val repo: Repository, val type: TxnType, private val editId: Long) : ViewModel() {
    var amount by mutableStateOf("")
    var categoryId by mutableStateOf<Long?>(null)
    var timestamp by mutableStateOf(System.currentTimeMillis())
    var party by mutableStateOf("")
    var accountId by mutableStateOf<Long?>(null)
    var notes by mutableStateOf("")
    var receipt by mutableStateOf<String?>(null)
    var loaded by mutableStateOf(editId == 0L)
    var saving by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    /** Projected balance awaiting confirmation when saving would make it negative. */
    var confirmNegative by mutableStateOf<String?>(null)
    var done by mutableStateOf(false)

    private var original: Txn? = null
    private var originalReceipt: String? = null
    // One id per form: repeated taps or retries can never create a second record.
    private val requestId = UUID.randomUUID().toString()

    val isEdit get() = editId != 0L

    private fun snapshot() = listOf(amount, categoryId, timestamp, party, notes, receipt).toString()
    private var initial: String? = if (editId == 0L) snapshot() else null
    val dirty: Boolean get() = loaded && !done && initial != null && snapshot() != initial

    init {
        if (editId != 0L) viewModelScope.launch {
            repo.txn(editId)?.let { t ->
                original = t
                val d = repo.settings.current.currency.decimals
                amount = Money.toInput(t.amountMinor, d)
                categoryId = t.categoryId
                timestamp = t.timestamp
                party = t.party
                accountId = t.accountId
                notes = t.notes
                receipt = t.receiptFile
                originalReceipt = t.receiptFile
            }
            initial = snapshot()
            loaded = true
        }
    }

    /** Pre-selects an account for a new entry without counting as a user change. */
    fun setDefaultAccount(id: Long?) { accountId = id }

    fun replaceReceipt(name: String?) {
        val prev = receipt
        if (prev != null && prev != originalReceipt) repo.receiptFile(prev).delete()
        receipt = name
    }

    fun save(state: LedgerState, confirmed: Boolean) {
        if (saving || done) return
        error = null
        val d = repo.settings.current.currency.decimals
        val minor = Money.parse(amount, d)
        when {
            minor == null -> { error = if (amount.isBlank()) "Enter an amount." else "Enter a valid amount (up to $d decimal places)."; return }
            minor <= 0 -> { error = "Enter an amount greater than zero."; return }
            categoryId == null -> { error = "Choose a category."; return }
            accountId == null -> { error = if (type == TxnType.EXPENSE) "Choose where the money came from." else "Choose where the money went."; return }
        }
        val txn = Txn(
            id = original?.id ?: 0L,
            type = type,
            amountMinor = minor!!,
            categoryId = categoryId,
            timestamp = timestamp,
            party = party.trim(),
            paymentMethod = original?.paymentMethod,
            notes = notes.trim(),
            receiptFile = receipt,
            requestId = original?.requestId ?: requestId,
            createdAt = original?.createdAt ?: System.currentTimeMillis(),
            accountId = accountId,
        )
        if (!confirmed) negativeWarning(state, original, txn)?.let { confirmNegative = it; return }
        confirmNegative = null
        saving = true
        viewModelScope.launch {
            val oldReceipt = originalReceipt?.takeIf { it != receipt }
            when (val r = repo.saveEntry(txn, oldReceipt)) {
                is SaveResult.Ok -> done = true
                is SaveResult.Error -> error = r.message
            }
            saving = false
        }
    }

    override fun onCleared() {
        // Abandoned form: remove a photo that was attached but never saved.
        if (!done) receipt?.takeIf { it != originalReceipt }?.let { repo.receiptFile(it).delete() }
    }
}

@Composable
fun EntryScreen(repo: Repository, state: LedgerState, type: TxnType, editId: Long, nav: NavController) {
    val vm: EntryViewModel = viewModel(factory = simpleFactory { EntryViewModel(repo, type, editId) })
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = state.currency
    val isExpense = type == TxnType.EXPENSE
    val kind = if (isExpense) CategoryKind.EXPENSE else CategoryKind.INCOME
    val cats = state.categories.filter { it.kind == kind }
    var addCat by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val focus = remember { FocusRequester() }

    val messenger = LocalMessenger.current
    val view = LocalView.current
    val accent = if (isExpense) LocalMoneyColors.current.expense else LocalMoneyColors.current.income
    LaunchedEffect(vm.done) {
        if (vm.done) {
            view.confirmHaptic()
            val amount = Money.parse(vm.amount, c.decimals)?.let { Money.format(it, c) } ?: ""
            messenger.show(if (vm.isEdit) "Changes saved" else "${if (isExpense) "Expense" else "Income"} of $amount saved")
            nav.popBackStack()
        }
    }
    LaunchedEffect(vm.loaded) { if (vm.loaded && !vm.isEdit) runCatching { focus.requestFocus() } }
    LaunchedEffect(vm.loaded, state.accounts) {
        if (vm.loaded && !vm.isEdit && vm.accountId == null) vm.setDefaultAccount(state.activeAccounts.firstOrNull()?.id)
    }
    val back = rememberDiscardGuard(vm.dirty) { nav.popBackStack() }

    fun importUri(uri: Uri?) {
        if (uri == null) return
        importing = true
        scope.launch {
            val name = ReceiptImport.importImage(context, uri, repo.receiptsDir)
            importing = false
            if (name == null) vm.error = "Couldn't read that photo. Try another one."
            else vm.replaceReceipt(name)
        }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { importUri(it) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) importUri(cameraUri) }

    val title = (if (vm.isEdit) "Edit " else "Add ") + if (isExpense) "Expense" else "Income"
    ScreenScaffold(
        title, nav, onBack = back,
        bottomBar = {
            if (vm.loaded) FormSaveBar(
                if (vm.isEdit) "Save changes" else if (isExpense) "Save expense" else "Save income",
                saving = vm.saving, enabled = !importing,
            ) { vm.save(state, confirmed = false) }
        },
    ) { padding ->
        if (!vm.loaded) {
            Box(Modifier.padding(padding).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@ScreenScaffold
        }
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HeroAmountInput(
                vm.amount, { vm.amount = it }, c, accent,
                label = if (isExpense) "You spent" else "You received",
                modifier = Modifier.focusRequester(focus).padding(vertical = 12.dp),
                isError = vm.error != null && Money.parse(vm.amount, c.decimals).let { it == null || it <= 0 },
            )
            FormSection {
                CategoryGrid(cats, vm.categoryId, { vm.categoryId = it }, onAddCustom = { addCat = true })
            }
            FormSection {
                TextInput(vm.party, { vm.party = it }, if (isExpense) "Merchant or place" else "Source", placeholder = if (isExpense) "e.g. Imtiaz, petrol pump, pharmacy" else "e.g. employer or client")
                AccountSelector(
                    state.activeAccounts + listOfNotNull(vm.accountId?.let { id -> state.accountById[id]?.takeIf { it.archived } }),
                    vm.accountId, { vm.accountId = it }, state.accountBalances, c,
                    label = if (isExpense) "Paid from" else "Received into",
                )
                DateTimeField(vm.timestamp, { vm.timestamp = it }, minDate = LocalDate.ofEpochDay(state.settings.trackingStartEpochDay), showTime = true, label = "Date and time")
                TextInput(vm.notes, { vm.notes = it }, "Notes", singleLine = false)
            }

            if (isExpense) {
                FormSection {
                    Column {
                        Text("Receipt", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        val r = vm.receipt
                        if (r != null) {
                            Box {
                                AsyncImage(
                                    model = repo.receiptFile(r),
                                    contentDescription = "Receipt photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(120.dp).clip(MaterialTheme.shapes.medium),
                                )
                                FilledTonalIconButton(onClick = { vm.replaceReceipt(null) }, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(32.dp)) {
                                    Icon(Icons.Rounded.Close, "Remove receipt", Modifier.size(18.dp))
                                }
                            }
                        } else if (importing) {
                            CircularProgressIndicator(Modifier.size(32.dp))
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = {
                                    val u = ReceiptImport.newCameraUri(context); cameraUri = u
                                    runCatching { camera.launch(u) }.onFailure { vm.error = "No camera app is available." }
                                }, modifier = Modifier.weight(1f)) { Icon(Icons.Rounded.PhotoCamera, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Camera") }
                                OutlinedButton(onClick = {
                                    gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                }, modifier = Modifier.weight(1f)) { Icon(Icons.Rounded.PhotoLibrary, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Gallery") }
                            }
                        }
                    }
                }
            }

            ErrorBanner(vm.error)
            Spacer(Modifier.height(8.dp))
        }
    }

    vm.confirmNegative?.let { msg ->
        ConfirmDialog(
            title = "Balance will go negative",
            message = msg,
            confirmLabel = "Save anyway",
            onConfirm = { vm.save(state, confirmed = true) },
            onDismiss = { vm.confirmNegative = null },
        )
    }
    if (addCat) {
        CategoryEditDialog(
            kind = kind,
            existing = null,
            onSave = { name, icon, color ->
                scope.launch {
                    when (val r = repo.addCategory(name, kind, icon, color)) {
                        is SaveResult.Ok -> { vm.categoryId = r.id; addCat = false }
                        is SaveResult.Error -> vm.error = r.message
                    }
                }
            },
            onDismiss = { addCat = false },
        )
    }
}
