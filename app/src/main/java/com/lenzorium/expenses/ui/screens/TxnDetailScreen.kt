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
    var pinOk by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showReceipt by remember { mutableStateOf(false) }

    ScreenScaffold(t.type.label, nav) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(txnIcon(t, cat), if (t.type.isLoanRelated) txnColor(t.type) else cat?.let { Color(it.colorArgb) } ?: txnColor(t.type), size = 52)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(txnTitle(t, cat, t.personId?.let { state.holderById[it]?.name }), style = MaterialTheme.typography.titleLarge)
                    Text(txnAmountText(t, state.currencyFor(t), state.currencyOf(t.toAccountId)), style = MaterialTheme.typography.headlineMedium, color = txnColor(t.type))
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
                        TxnType.SAFE_GIVEN, TxnType.SAFE_RETURNED -> {
                            val h = t.personId?.let { state.holderById[it] }
                            Field("Person", (h?.name ?: "—") + (h?.phone?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""))
                            if (t.party.isNotBlank()) Field("Purpose", t.party)
                            t.dueEpochDay?.let { Field("Expected back by", Dates.epochDay(it)) }
                            t.personId?.let { pid -> state.safeSummaries.firstOrNull { it.holder.id == pid } }?.let { sm ->
                                Field("Still with ${sm.holder.name}", Money.format(sm.remaining, c) + " of " + Money.format(sm.given, c) + " given")
                            }
                        }
                        else -> Field("Lender", t.party)
                    }
                    when (t.type) {
                        TxnType.TRANSFER -> {
                            Field("From", state.accountName(t.accountId) ?: "—")
                            Field("To", state.accountName(t.toAccountId) ?: "—")
                        }
                        TxnType.EXPENSE, TxnType.LOAN_REPAYMENT, TxnType.SAFE_GIVEN -> Field(if (t.type == TxnType.SAFE_GIVEN) "Given from" else "Paid from", state.accountName(t.accountId) ?: t.paymentMethod?.label ?: "—")
                        TxnType.INCOME, TxnType.LOAN_RECEIVED, TxnType.SAFE_RETURNED -> Field("Received into", state.accountName(t.accountId) ?: t.paymentMethod?.label ?: "—")
                        TxnType.LOAN_CHARGE -> Unit
                    }
                    when (t.type) {
                        TxnType.LOAN_CHARGE -> Field("Effect", "Adds to the loan's debt. Available balance is not changed.")
                        TxnType.SAFE_GIVEN -> Field("Effect", "Leaves your ${state.accountName(t.accountId) ?: "account"} balance (${Money.formatSigned(-t.amountMinor, c)}). Not an expense: it's still your money, recorded as owed back to you.")
                        TxnType.SAFE_RETURNED -> Field("Effect", "Back in your ${state.accountName(t.accountId) ?: "account"} balance (${Money.formatSigned(t.amountMinor, c)}). Not income: it's your own money returning.")
                        TxnType.TRANSFER -> LedgerMath.cashDelta(t, state.foreignIds).let { d ->
                            if (d == 0L) Field("Effect", "Moves money between your accounts. Your total balance doesn't change.")
                            else Field("Effect", "Currency exchange: your ${c.code} total changes by ${Money.formatSigned(d, c)}. It isn't income or an expense.")
                        }
                        else -> if (t.accountId in state.foreignIds) Field("Effect on available balance", "None: this account is in ${state.currencyFor(t).code}, kept separate from your ${c.code} total.")
                            else Field("Effect on available balance", Money.formatSigned(LedgerMath.cashDelta(t, state.foreignIds), c))
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

    if (confirmDelete && t.type.isSafekeeping && !pinOk) {
        PinGate(repo.settings, "Deleting safekeeping records needs your PIN.", onOk = { pinOk = true }, onCancel = { confirmDelete = false })
    } else if (confirmDelete) {
        val after = state.available - LedgerMath.cashDelta(t, state.foreignIds)
        val warn = if (after < 0 && LedgerMath.cashDelta(t, state.foreignIds) > 0) "\n\nYour available balance will become ${Money.format(after, c)}." else ""
        ConfirmDialog(
            title = "Delete this ${t.type.label.lowercase()}?",
            message = "${txnTitle(t, cat, t.personId?.let { state.holderById[it]?.name })} · ${Money.format(t.amountMinor, c)}. This can't be undone.$warn",
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
