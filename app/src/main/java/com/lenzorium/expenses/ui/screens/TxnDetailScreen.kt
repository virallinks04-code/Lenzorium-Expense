package com.lenzorium.expenses.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.lenzorium.expenses.data.Repository
import com.lenzorium.expenses.data.SaveResult
import com.lenzorium.expenses.data.TxnType
import com.lenzorium.expenses.domain.LedgerMath
import com.lenzorium.expenses.domain.Money
import com.lenzorium.expenses.ui.LedgerState
import com.lenzorium.expenses.ui.Routes
import com.lenzorium.expenses.ui.components.ConfirmDialog
import com.lenzorium.expenses.ui.components.Dates
import com.lenzorium.expenses.ui.components.IconBadge
import com.lenzorium.expenses.ui.components.MessageDialog
import com.lenzorium.expenses.ui.components.ScreenScaffold
import com.lenzorium.expenses.ui.components.SectionCard
import com.lenzorium.expenses.ui.components.txnAmountText
import com.lenzorium.expenses.ui.components.txnColor
import com.lenzorium.expenses.ui.components.txnIcon
import com.lenzorium.expenses.ui.components.txnTitle
import kotlinx.coroutines.launch

@Composable
fun TxnDetailScreen(state: LedgerState, repo: Repository, id: Long, nav: NavController) {
    val t = state.txns.firstOrNull { it.id == id }
    var gone by remember { mutableStateOf(false) }
    LaunchedEffect(t, state.loaded) { if (t == null && state.loaded) { gone = true; nav.popBackStack() } }
    if (t == null) return
    val c = state.currency
    val cat = t.categoryId?.let { state.categoryById[it] }
    val loan = t.loanId?.let { state.loanById[it] }
    val scope = rememberCoroutineScope()
    val messenger = com.lenzorium.expenses.ui.LocalMessenger.current
    var confirmDelete by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showReceipt by remember { mutableStateOf(false) }

    ScreenScaffold(t.type.label, nav) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(txnIcon(t, cat), if (t.type.isLoanRelated) txnColor(t.type) else cat?.let { Color(it.colorArgb) } ?: txnColor(t.type), size = 52)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(txnTitle(t, cat), style = MaterialTheme.typography.titleLarge)
                    Text(txnAmountText(t, c), style = MaterialTheme.typography.headlineMedium, color = txnColor(t.type))
                }
            }
            SectionCard {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Field("Type", t.type.label)
                    Field("Date and time", Dates.dateTime(t.timestamp))
                    if (cat != null) Field("Category", cat.name)
                    when (t.type) {
                        TxnType.EXPENSE -> Field("Merchant or place", t.party.ifBlank { "—" })
                        TxnType.INCOME -> Field("Source", t.party.ifBlank { "—" })
                        TxnType.TRANSFER -> Unit
                        else -> Field("Lender", t.party)
                    }
                    when (t.type) {
                        TxnType.TRANSFER -> {
                            Field("From", state.accountName(t.accountId) ?: "—")
                            Field("To", state.accountName(t.toAccountId) ?: "—")
                        }
                        TxnType.EXPENSE, TxnType.LOAN_REPAYMENT -> Field("Paid from", state.accountName(t.accountId) ?: t.paymentMethod?.label ?: "—")
                        TxnType.INCOME, TxnType.LOAN_RECEIVED -> Field("Received into", state.accountName(t.accountId) ?: t.paymentMethod?.label ?: "—")
                        TxnType.LOAN_CHARGE -> Unit
                    }
                    when (t.type) {
                        TxnType.LOAN_CHARGE -> Field("Effect", "Adds to the loan's debt. Available balance is not changed.")
                        TxnType.TRANSFER -> Field("Effect", "Moves money between your accounts. Your total balance doesn't change.")
                        else -> Field("Effect on available balance", Money.formatSigned(LedgerMath.cashDelta(t), c))
                    }
                    if (t.notes.isNotBlank()) Field("Notes", t.notes)
                    if (t.tagList.isNotEmpty()) Field("Tags", t.tagList.joinToString("  ") { "#$it" })
                    t.recurringId?.let { rid -> state.recurring.firstOrNull { it.id == rid }?.let { Field("Bill", it.name) } }
                }
            }
            if (loan != null) {
                SectionCard {
                    Row(Modifier.fillMaxWidth().clickable { nav.navigate(Routes.loan(loan.id)) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Linked loan", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(listOf(loan.lender, loan.title).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.titleMedium)
                            state.summary(loan.id)?.let { Text("Outstanding: ${Money.format(it.outstanding, c)} · ${it.status.label}", style = MaterialTheme.typography.bodySmall) }
                        }
                        TextButton(onClick = { nav.navigate(Routes.loan(loan.id)) }) { Text("Open") }
                    }
                }
            }
            t.receiptFile?.let { name ->
                SectionCard {
                    Column(Modifier.padding(16.dp)) {
                        Text("Receipt", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        AsyncImage(
                            model = repo.receiptFile(name),
                            contentDescription = "Receipt photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp).clickable { showReceipt = true },
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { nav.navigate(Routes.editTxn(t.type, t.id, t.loanId)) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Edit, null); Spacer(Modifier.width(6.dp))
                    Text(if (t.type == TxnType.LOAN_RECEIVED) "Edit loan" else "Edit")
                }
                if (t.type != TxnType.LOAN_RECEIVED) {
                    OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error); Spacer(Modifier.width(6.dp))
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            if (t.type == TxnType.LOAN_RECEIVED) {
                Text(
                    "This is the money received for a loan. To remove it, delete the loan itself from its loan page — that also removes its repayment history.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (confirmDelete) {
        val after = state.available - LedgerMath.cashDelta(t)
        val warn = if (after < 0 && LedgerMath.cashDelta(t) > 0) "\n\nYour available balance will become ${Money.format(after, c)}." else ""
        ConfirmDialog(
            title = "Delete this ${t.type.label.lowercase()}?",
            message = "${txnTitle(t, cat)} · ${Money.format(t.amountMinor, c)}. This can't be undone.$warn",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                confirmDelete = false
                val deleted = t
                scope.launch {
                    when (val r = repo.deleteTxn(deleted.id)) {
                        is SaveResult.Error -> error = r.message
                        is SaveResult.Ok -> messenger.show("${deleted.type.label} deleted", "Undo") { repo.restoreTxn(deleted) }
                    }
                }
            },
            onDismiss = { confirmDelete = false },
        )
    }
    error?.let { MessageDialog("Can't delete", it) { error = null } }
    if (showReceipt && t.receiptFile != null) {
        Dialog(onDismissRequest = { showReceipt = false }) {
            AsyncImage(model = repo.receiptFile(t.receiptFile), contentDescription = "Receipt photo", modifier = Modifier.fillMaxWidth().clickable { showReceipt = false })
        }
    }
}

@Composable
fun Field(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
    HorizontalDivider()
}
