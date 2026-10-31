package com.lenzorium.expenses.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.lenzorium.expenses.data.PayMethod
import com.lenzorium.expenses.data.Txn
import com.lenzorium.expenses.data.TxnType
import com.lenzorium.expenses.domain.LedgerMath
import com.lenzorium.expenses.domain.Money
import com.lenzorium.expenses.ui.LedgerState
import com.lenzorium.expenses.ui.Routes
import com.lenzorium.expenses.ui.components.DatePickDialog
import com.lenzorium.expenses.ui.components.Dates
import com.lenzorium.expenses.ui.components.EmptyState
import com.lenzorium.expenses.ui.components.ScreenScaffold
import com.lenzorium.expenses.ui.components.TxnRow
import com.lenzorium.expenses.ui.theme.LocalMoneyColors
import java.time.LocalDate

/** Filter state lives in a ViewModel so it survives navigating to details and back. */
class TxnFilterViewModel : ViewModel() {
    var query by mutableStateOf("")
    var types by mutableStateOf(emptySet<TxnType>())
    var categories by mutableStateOf(emptySet<Long>())
    var methods by mutableStateOf(emptySet<PayMethod>())
    var from by mutableStateOf<LocalDate?>(null)
    var to by mutableStateOf<LocalDate?>(null)

    val active: Boolean get() = types.isNotEmpty() || categories.isNotEmpty() || methods.isNotEmpty() || from != null || to != null

    fun clear() {
        types = emptySet(); categories = emptySet(); methods = emptySet(); from = null; to = null
    }

    fun applyTo(state: LedgerState): List<Txn> {
        val q = query.trim()
        val fromMs = from?.let { Dates.startOf(it) }
        val toMs = to?.let { Dates.startOf(it.plusDays(1)) }
        return state.txns.filter { t ->
            (types.isEmpty() || t.type in types) &&
                (categories.isEmpty() || t.categoryId in categories) &&
                (methods.isEmpty() || t.paymentMethod in methods) &&
                (fromMs == null || t.timestamp >= fromMs) &&
                (toMs == null || t.timestamp < toMs) &&
                (q.isEmpty() || matches(t, q, state))
        }
    }

    private fun matches(t: Txn, q: String, state: LedgerState): Boolean {
        val loan = t.loanId?.let { state.loanById[it] }
        val cat = t.categoryId?.let { state.categoryById[it] }
        return listOfNotNull(t.party, t.notes, cat?.name, loan?.lender, loan?.title).any { it.contains(q, ignoreCase = true) }
    }
}

@Composable
fun TransactionsScreen(state: LedgerState, nav: NavController) {
    val f: TxnFilterViewModel = viewModel()
    val c = state.currency
    val m = LocalMoneyColors.current
    val results = remember(state.txns, f.query, f.types, f.categories, f.methods, f.from, f.to) { f.applyTo(state) }
    var dialog by remember { mutableStateOf<String?>(null) }

    ScreenScaffold(
        "Transactions",
        nav = null,
        actions = {
            TextButton(onClick = { nav.navigate(Routes.LOANS) }) {
                Icon(Icons.Rounded.AccountBalance, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Loans")
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TextField(
                value = f.query,
                onValueChange = { f.query = it },
                placeholder = { Text("Search merchant, source, lender, notes", maxLines = 1) },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = { if (f.query.isNotEmpty()) IconButton(onClick = { f.query = "" }) { Icon(Icons.Rounded.Close, "Clear search") } },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DropChip("Type", f.types.size) { dialog = "type" }
                DropChip("Category", f.categories.size) { dialog = "category" }
                DropChip("Method", f.methods.size) { dialog = "method" }
                DropChip(if (f.from != null || f.to != null) dateLabel(f.from, f.to) else "Dates", if (f.from != null || f.to != null) 1 else 0) { dialog = "dates" }
                if (f.active) FilterChip(selected = false, onClick = { f.clear() }, label = { Text("Clear filters") })
            }

            if (results.isNotEmpty()) {
                val inSum = results.sumOf { maxOf(0L, LedgerMath.cashDelta(it)) }
                val outSum = results.sumOf { maxOf(0L, -LedgerMath.cashDelta(it)) }
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
                    Text("${results.size} entries", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Text("In ${Money.format(inSum, c)}", style = MaterialTheme.typography.bodySmall, color = m.income)
                    Spacer(Modifier.width(12.dp))
                    Text("Out ${Money.format(outSum, c)}", style = MaterialTheme.typography.bodySmall, color = m.expense)
                }
            }

            when {
                state.txns.isEmpty() -> EmptyState(
                    Icons.AutoMirrored.Rounded.ReceiptLong,
                    "Nothing recorded yet",
                    "Your expenses, income, loans and repayments will all appear here in date order. Use the Add button below to record your first one.",
                )
                results.isEmpty() -> EmptyState(Icons.Rounded.SearchOff, "No matches", "Try a different search or clear the filters.") {
                    OutlinedButton(onClick = { f.clear(); f.query = "" }) { Text("Clear all") }
                }
                else -> {
                    val grouped = results.groupBy { Dates.localDate(it.timestamp) }
                    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                        grouped.forEach { (day, list) ->
                            item(key = "h$day") {
                                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 2.dp)) {
                                    Text(Dates.dayHeader(day), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                                    val net = list.sumOf { LedgerMath.cashDelta(it) }
                                    Text(Money.formatSigned(net, c), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            items(list, key = { it.id }) { t ->
                                TxnRow(t, t.categoryId?.let { state.categoryById[it] }, c, onClick = { nav.navigate(Routes.txn(t.id)) })
                            }
                        }
                    }
                }
            }
        }
    }

    when (dialog) {
        "type" -> MultiSelectDialog(
            "Transaction type",
            TxnType.entries.map { it to it.label },
            f.types,
            onDone = { f.types = it; dialog = null },
            onDismiss = { dialog = null },
        )
        "category" -> MultiSelectDialog(
            "Category",
            state.categories.map { it.id to "${it.name} (${if (it.kind.name == "EXPENSE") "expense" else "income"})" },
            f.categories,
            onDone = { f.categories = it; dialog = null },
            onDismiss = { dialog = null },
        )
        "method" -> MultiSelectDialog(
            "Payment method",
            PayMethod.entries.map { it to it.label },
            f.methods,
            onDone = { f.methods = it; dialog = null },
            onDismiss = { dialog = null },
        )
        "dates" -> DateRangeDialog(f.from, f.to, onDone = { a, b -> f.from = a; f.to = b; dialog = null }, onDismiss = { dialog = null })
    }
}

private fun dateLabel(from: LocalDate?, to: LocalDate?): String = when {
    from != null && to != null -> "${Dates.short(from)} – ${Dates.short(to)}"
    from != null -> "From ${Dates.short(from)}"
    to != null -> "Until ${Dates.short(to)}"
    else -> "Dates"
}

@Composable
private fun DropChip(label: String, count: Int, onClick: () -> Unit) {
    FilterChip(
        selected = count > 0,
        onClick = onClick,
        label = { Text(if (count > 1) "$label · $count" else label) },
        trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, null, Modifier.size(18.dp)) },
    )
}

@Composable
fun <T> MultiSelectDialog(title: String, options: List<Pair<T, String>>, initial: Set<T>, onDone: (Set<T>) -> Unit, onDismiss: () -> Unit) {
    var sel by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { (v, label) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { sel = if (v in sel) sel - v else sel + v },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = v in sel, onCheckedChange = { sel = if (it) sel + v else sel - v })
                        Text(label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDone(sel) }) { Text("Apply") } },
        dismissButton = { TextButton(onClick = { onDone(emptySet()) }) { Text("Any") } },
    )
}

@Composable
fun DateRangeDialog(from: LocalDate?, to: LocalDate?, onDone: (LocalDate?, LocalDate?) -> Unit, onDismiss: () -> Unit) {
    var a by remember { mutableStateOf(from) }
    var b by remember { mutableStateOf(to) }
    var picking by remember { mutableStateOf<Int?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Date range") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { picking = 0 }, modifier = Modifier.fillMaxWidth()) { Text("From: " + (a?.let { Dates.date(it) } ?: "any")) }
                OutlinedButton(onClick = { picking = 1 }, modifier = Modifier.fillMaxWidth()) { Text("To: " + (b?.let { Dates.date(it) } ?: "any")) }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { val t = LocalDate.now(); a = t.withDayOfMonth(1); b = t }) { Text("This month") }
                    TextButton(onClick = { val t = LocalDate.now(); a = t.minusDays(6); b = t }) { Text("Last 7 days") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val x = a
                val y = b
                if (x != null && y != null && y.isBefore(x)) onDone(y, x) else onDone(x, y)
            }) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = { onDone(null, null) }) { Text("Any date") } },
    )
    picking?.let { which ->
        DatePickDialog(
            initial = (if (which == 0) a else b) ?: LocalDate.now(),
            onPick = { if (which == 0) a = it else b = it },
            onDismiss = { picking = null },
        )
    }
}
