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
import com.lenzorium.expenses.ui.components.PayMethodSelector
import com.lenzorium.expenses.ui.components.ScreenScaffold
import com.lenzorium.expenses.ui.components.TextInput
import com.lenzorium.expenses.ui.simpleFactory
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

class EntryViewModel(private val repo: Repository, val type: TxnType, private val editId: Long) : ViewModel() {
    var amount by mutableStateOf("")
    var categoryId by mutableStateOf<Long?>(null)
    var timestamp by mutableStateOf(System.currentTimeMillis())
    var party by mutableStateOf("")
    var method by mutableStateOf(PayMethod.CASH)
    var notes by mutableStateOf("")
    var receipt by mutableStateOf<String?>(null)
    var loaded by mutableStateOf(editId == 0L)
    var saving by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    /** Projected balance awaiting confirmation when saving would make it negative. */
    var confirmNegative by mutableStateOf<Long?>(null)
    var done by mutableStateOf(false)

    private var original: Txn? = null
    private var originalReceipt: String? = null
    // One id per form: repeated taps or retries can never create a second record.
    private val requestId = UUID.randomUUID().toString()

    val isEdit get() = editId != 0L

    init {
        if (editId != 0L) viewModelScope.launch {
            repo.txn(editId)?.let { t ->
                original = t
                val d = repo.settings.current.currency.decimals
                amount = Money.toInput(t.amountMinor, d)
                categoryId = t.categoryId
                timestamp = t.timestamp
                party = t.party
                method = t.paymentMethod ?: PayMethod.CASH
                notes = t.notes
                receipt = t.receiptFile
                originalReceipt = t.receiptFile
            }
            loaded = true
        }
    }

    fun replaceReceipt(name: String?) {
        val prev = receipt
        if (prev != null && prev != originalReceipt) repo.receiptFile(prev).delete()
        receipt = name
    }

    fun save(currentAvailable: Long, confirmed: Boolean) {
        if (saving || done) return
        error = null
        val d = repo.settings.current.currency.decimals
        val minor = Money.parse(amount, d)
        when {
            minor == null -> { error = if (amount.isBlank()) "Enter an amount." else "Enter a valid amount (up to $d decimal places)."; return }
            minor <= 0 -> { error = "Enter an amount greater than zero."; return }
            categoryId == null -> { error = "Choose a category."; return }
        }
        val txn = Txn(
            id = original?.id ?: 0L,
            type = type,
            amountMinor = minor!!,
            categoryId = categoryId,
            timestamp = timestamp,
            party = party.trim(),
            paymentMethod = method,
            notes = notes.trim(),
            receiptFile = receipt,
            requestId = original?.requestId ?: requestId,
            createdAt = original?.createdAt ?: System.currentTimeMillis(),
        )
        val projected = currentAvailable - (original?.let { LedgerMath.cashDelta(it) } ?: 0L) + LedgerMath.cashDelta(txn)
        if (projected < 0 && projected < currentAvailable && !confirmed) {
            confirmNegative = projected
            return
        }
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

    LaunchedEffect(vm.done) { if (vm.done) nav.popBackStack() }
    LaunchedEffect(vm.loaded) { if (vm.loaded && !vm.isEdit) runCatching { focus.requestFocus() } }

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

    val title = (if (vm.isEdit) "Edit " else "Add ") + if (isExpense) "expense" else "income"
    ScreenScaffold(title, nav) { padding ->
        if (!vm.loaded) {
            Box(Modifier.padding(padding).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@ScreenScaffold
        }
        Column(
            Modifier.padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AmountField(vm.amount, { vm.amount = it }, c, modifier = Modifier.focusRequester(focus), isError = vm.error != null && Money.parse(vm.amount, c.decimals).let { it == null || it <= 0 })
            CategoryPicker(cats, vm.categoryId, { vm.categoryId = it }, onAddCustom = { addCat = true })
            TextInput(vm.party, { vm.party = it }, if (isExpense) "Merchant or place (optional)" else "Source (optional)", placeholder = if (isExpense) "e.g. Imtiaz, petrol pump, pharmacy" else "e.g. employer or client")
            PayMethodSelector(vm.method, { vm.method = it })
            DateTimeField(vm.timestamp, { vm.timestamp = it }, minDate = LocalDate.ofEpochDay(state.settings.trackingStartEpochDay), showTime = true, label = "Date and time")
            TextInput(vm.notes, { vm.notes = it }, "Notes (optional)", singleLine = false)

            if (isExpense) {
                Column {
                    Text("Receipt (optional)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    val r = vm.receipt
                    if (r != null) {
                        Box {
                            AsyncImage(
                                model = repo.receiptFile(r),
                                contentDescription = "Receipt photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(120.dp),
                            )
                            FilledTonalIconButton(onClick = { vm.replaceReceipt(null) }, modifier = Modifier.align(Alignment.TopEnd).size(32.dp)) {
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
                            }) { Icon(Icons.Rounded.PhotoCamera, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Take photo") }
                            OutlinedButton(onClick = {
                                gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }) { Icon(Icons.Rounded.PhotoLibrary, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Choose") }
                        }
                    }
                }
            }

            ErrorBanner(vm.error)
            Button(
                onClick = { vm.save(state.available, confirmed = false) },
                enabled = !vm.saving && !importing,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (vm.saving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text(if (vm.isEdit) "Save changes" else if (isExpense) "Save expense" else "Save income")
            }
        }
    }

    vm.confirmNegative?.let { projected ->
        ConfirmDialog(
            title = "Balance will go negative",
            message = "After saving, your available balance will be ${Money.format(projected, c)}. Save anyway?",
            confirmLabel = "Save anyway",
            onConfirm = { vm.save(state.available, confirmed = true) },
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
