package com.lenzorium.expenses.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AssignmentReturned
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.lenzorium.expenses.data.Holder
import com.lenzorium.expenses.data.Repository
import com.lenzorium.expenses.data.SaveResult
import com.lenzorium.expenses.data.SettingsStore
import com.lenzorium.expenses.data.Txn
import com.lenzorium.expenses.data.TxnType
import com.lenzorium.expenses.domain.HolderSummary
import com.lenzorium.expenses.domain.Money
import com.lenzorium.expenses.domain.SafeMath
import com.lenzorium.expenses.ui.LedgerState
import com.lenzorium.expenses.ui.LocalMessenger
import com.lenzorium.expenses.ui.ReceiptImport
import com.lenzorium.expenses.ui.Routes
import com.lenzorium.expenses.ui.components.AccountSelector
import com.lenzorium.expenses.ui.components.ConfirmDialog
import com.lenzorium.expenses.ui.components.DatePickDialog
import com.lenzorium.expenses.ui.components.DateTimeField
import com.lenzorium.expenses.ui.components.Dates
import com.lenzorium.expenses.ui.components.EmptyState
import com.lenzorium.expenses.ui.components.ErrorBanner
import com.lenzorium.expenses.ui.components.FormSaveBar
import com.lenzorium.expenses.ui.components.FormSection
import com.lenzorium.expenses.ui.components.HeroAmountInput
import com.lenzorium.expenses.ui.components.IconBadge
import com.lenzorium.expenses.ui.components.MessageDialog
import com.lenzorium.expenses.ui.components.ProgressBar
import com.lenzorium.expenses.ui.components.ScreenScaffold
import com.lenzorium.expenses.ui.components.SectionCard
import com.lenzorium.expenses.ui.components.SectionTitle
import com.lenzorium.expenses.ui.components.TextInput
import com.lenzorium.expenses.ui.components.TxnRow
import com.lenzorium.expenses.ui.components.rememberDiscardGuard
import com.lenzorium.expenses.ui.negativeWarning
import com.lenzorium.expenses.ui.theme.Brand
import com.lenzorium.expenses.ui.theme.LocalMoneyColors
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

/** Routes for money given for safekeeping. */
object SafeRoutes {
    const val HOME = "safe"
    fun person(id: Long) = "safe/person/$id"
    fun give(personId: Long = 0, id: Long = 0) = "safe/give?person=$personId&id=$id"
    fun receive(personId: Long = 0, id: Long = 0) = "safe/receive?person=$personId&id=$id"
}

private enum class SafeFilter(val label: String) { ALL("All"), OUTSTANDING("Outstanding"), OVERDUE("Overdue"), SETTLED("Settled") }
private enum class SafePeriod(val label: String) { ALL("All time"), MONTH("This month"), QUARTER("Last 3 months"), YEAR("This year") }

private fun expectedText(s: HolderSummary, today: Long): String? {
    val d = s.expectedBy ?: return null
    return when {
        s.remaining <= 0 -> null
        d < today -> "Overdue since ${Dates.epochDay(d)}"
        d == today -> "Expected back today"
        else -> "Expected back by ${Dates.epochDay(d)}"
    }
}

// ============================ Dashboard ============================

@Composable
fun SafekeepingScreen(state: LedgerState, nav: NavController) {
    val c = state.currency
    val t = state.safeTotals
    val today = state.today
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(SafeFilter.ALL) }
    var period by remember { mutableStateOf(SafePeriod.ALL) }
    val m = LocalMoneyColors.current

    val people = state.safeSummaries.filter { it.given > 0 || it.entries.isEmpty() }
        .filter { s ->
            val q = query.trim()
            (q.isEmpty() || s.holder.name.contains(q, true) || s.holder.phone.contains(q) || s.entries.any { it.party.contains(q, true) || it.notes.contains(q, true) }) &&
                when (filter) {
                    SafeFilter.ALL -> true
                    SafeFilter.OUTSTANDING -> s.remaining > 0
                    SafeFilter.OVERDUE -> s.overdue(today)
                    SafeFilter.SETTLED -> s.settled
                }
        }
        .sortedWith(compareByDescending<HolderSummary> { it.overdue(today) }.thenByDescending { it.remaining > 0 }.thenBy { it.expectedBy ?: Long.MAX_VALUE }.thenByDescending { it.remaining })
    val startMs = when (period) {
        SafePeriod.ALL -> Long.MIN_VALUE
        SafePeriod.MONTH -> Dates.startOf(YearMonth.now().atDay(1))
        SafePeriod.QUARTER -> Dates.startOf(LocalDate.now().minusMonths(3))
        SafePeriod.YEAR -> Dates.startOf(LocalDate.now().withDayOfYear(1))
    }
    val recent = state.txns.filter { it.type.isSafekeeping && it.timestamp >= startMs }
        .filter { tx -> query.isBlank() || (tx.personId?.let { state.holderById[it]?.name } ?: "").contains(query.trim(), true) || tx.party.contains(query.trim(), true) }
    val periodGiven = recent.sumOf { if (it.type == TxnType.SAFE_GIVEN) it.amountMinor else 0L }
    val periodBack = recent.sumOf { if (it.type == TxnType.SAFE_RETURNED) it.amountMinor else 0L }

    ScreenScaffold("Money with others", nav) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                SectionCard {
                    Column(Modifier.fillMaxWidth().background(HeroBrush.hero()).padding(20.dp)) {
                        Text("Still with others", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.85f))
                        Text(Money.format(t.outstanding, c), style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
                        Text(
                            if (t.peopleHolding == 0) "Nobody is holding your money" else "${t.peopleHolding} ${if (t.peopleHolding == 1) "person is" else "people are"} holding your money",
                            style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f),
                        )
                        Spacer(Modifier.height(14.dp))
                        Row {
                            HeroStat("Total given", Money.format(t.given, c), Modifier.weight(1f))
                            HeroStat("Recovered", Money.format(t.returned, c), Modifier.weight(1f))
                        }
                        if (t.given > 0) {
                            Spacer(Modifier.height(12.dp))
                            ProgressBar(t.returned.toFloat() / t.given, Brand.Gold)
                            Spacer(Modifier.height(4.dp))
                            Text("${(t.returned * 100 / t.given)}% recovered", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.85f))
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { nav.navigate(SafeRoutes.give()) }, modifier = Modifier.weight(1f).height(52.dp), shape = MaterialTheme.shapes.medium) {
                        Icon(Icons.Rounded.Handshake, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Give Money", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = { nav.navigate(SafeRoutes.receive()) }, enabled = t.outstanding > 0,
                        modifier = Modifier.weight(1f).height(52.dp), shape = MaterialTheme.shapes.medium,
                    ) { Icon(Icons.Rounded.AssignmentReturned, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Receive Back", maxLines = 1) }
                }
            }
            item {
                Text(
                    "Money you give someone to keep is still yours: it leaves your Cash or Bank balance but is never counted as an expense, and money received back is never counted as income.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            if (state.safeSummaries.isEmpty()) {
                item {
                    SectionCard {
                        EmptyState(Icons.Rounded.Handshake, "Nothing given yet", "When you hand money to a friend, family member or employee to keep for you, record it here so you always know who has how much.") {
                            TextButton(onClick = { nav.navigate(SafeRoutes.give()) }) { Text("Give money for safekeeping") }
                        }
                    }
                }
                return@LazyColumn
            }
            item {
                OutlinedTextField(
                    query, { query = it }, placeholder = { Text("Search name, phone or purpose") }, singleLine = true,
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Clear search") } },
                    modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
                )
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SafeFilter.entries.forEach { f ->
                        val n = when (f) {
                            SafeFilter.ALL -> null
                            SafeFilter.OUTSTANDING -> state.safeSummaries.count { it.remaining > 0 }
                            SafeFilter.OVERDUE -> state.safeSummaries.count { it.overdue(today) }
                            SafeFilter.SETTLED -> state.safeSummaries.count { it.settled }
                        }
                        FilterChip(filter == f, { filter = f }, { Text(f.label + (n?.let { " ($it)" } ?: "")) })
                    }
                }
            }
            item { SectionTitle(if (filter == SafeFilter.ALL) "People" else filter.label) }
            if (people.isEmpty()) item {
                Text("No one matches.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(4.dp))
            }
            items(people, key = { "p" + it.holder.id }) { s -> PersonCard(state, s) { nav.navigate(SafeRoutes.person(s.holder.id)) } }

            item {
                SectionTitle("Transactions")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SafePeriod.entries.forEach { p -> FilterChip(period == p, { period = p }, { Text(p.label) }) }
                }
                if (recent.isNotEmpty()) Text(
                    "Given ${Money.format(periodGiven, c)} · Received back ${Money.format(periodBack, c)}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                )
            }
            item {
                SectionCard {
                    Column {
                        if (recent.isEmpty()) Text("No entries in this period.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
                        recent.take(40).forEachIndexed { i, tx ->
                            if (i > 0) HorizontalDivider()
                            TxnRow(tx, null, c, onClick = { nav.navigate(Routes.txn(tx.id)) }, showDate = true, account = state.accountName(tx.accountId), person = tx.personId?.let { state.holderById[it]?.name })
                        }
                    }
                }
            }
        }
    }
}

private object HeroBrush {
    @Composable
    fun hero(): androidx.compose.ui.graphics.Brush {
        val m = LocalMoneyColors.current
        return androidx.compose.ui.graphics.Brush.linearGradient(listOf(m.heroStart, m.heroEnd))
    }
}

@Composable
private fun HeroStat(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
        Text(value, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Initials(name: String, size: Int = 44) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiaryContainer), contentAlignment = Alignment.Center) {
        Text(
            name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() },
            style = if (size > 50) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}

@Composable
private fun PersonCard(state: LedgerState, s: HolderSummary, onClick: () -> Unit) {
    val c = state.currency
    val m = LocalMoneyColors.current
    val overdue = s.overdue(state.today)
    SectionCard(Modifier.clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Initials(s.holder.name)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.holder.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        expectedText(s, state.today) ?: if (s.settled) "Fully recovered" else if (s.given == 0L) "No entries yet" else "Given ${Money.format(s.given, c)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (overdue) m.expense else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (overdue) FontWeight.SemiBold else null,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        if (s.settled) "Settled" else Money.format(s.remaining, c),
                        style = MaterialTheme.typography.titleSmall,
                        color = if (s.settled) m.income else MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (!s.settled && s.given > 0) Text("to recover", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (s.given > 0) {
                Spacer(Modifier.height(10.dp))
                ProgressBar(s.fraction, if (s.settled) m.income else if (overdue) m.expense else MaterialTheme.colorScheme.tertiary, height = 6.dp)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${Money.format(s.returned, c)} of ${Money.format(s.given, c)} recovered",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ============================ Person history ============================

@Composable
fun SafePersonScreen(state: LedgerState, repo: Repository, id: Long, nav: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val messenger = LocalMessenger.current
    val c = state.currency
    val m = LocalMoneyColors.current
    val s = state.safeSummaries.firstOrNull { it.holder.id == id }
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showAudit by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    if (s == null) { ScreenScaffold("Person", nav) { }; return }
    val h = s.holder
    // Oldest first, with the balance still outstanding after each entry.
    val running = remember(s.entries) {
        var bal = 0L
        s.entries.sortedWith(compareBy<Txn> { it.timestamp }.thenBy { it.id }).map { t ->
            bal += if (t.type == TxnType.SAFE_GIVEN) t.amountMinor else -t.amountMinor
            t to bal
        }.reversed()
    }
    val audit = state.plans.audit.filter { it.personId == id }

    ScreenScaffold(
        h.name, nav,
        actions = {
            IconButton(onClick = { editing = true }) { Icon(Icons.Rounded.Edit, "Edit person") }
            if (s.entries.isEmpty()) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, "Remove person") }
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                SectionCard {
                    Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Initials(h.name, 64)
                        Spacer(Modifier.height(8.dp))
                        Text(h.name, style = MaterialTheme.typography.titleLarge)
                        if (h.phone.isNotBlank()) Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(h.phone, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            IconButton(onClick = { runCatching { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + h.phone.filter { it.isDigit() || it == '+' }))) } }) {
                                Icon(Icons.Rounded.Call, "Call ${h.name}", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        if (h.notes.isNotBlank()) Text(h.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        Text("Remaining with ${h.name.substringBefore(' ')}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(Money.format(s.remaining, c), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = if (s.settled) m.income else MaterialTheme.colorScheme.tertiary)
                        expectedText(s, state.today)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = if (s.overdue(state.today)) m.expense else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold) }
                        Spacer(Modifier.height(12.dp))
                        ProgressBar(s.fraction, if (s.settled) m.income else MaterialTheme.colorScheme.tertiary)
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth()) {
                            MiniStat("Total given", Money.format(s.given, c), Modifier.weight(1f))
                            MiniStat("Total returned", Money.format(s.returned, c), Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { nav.navigate(SafeRoutes.give(id)) }, modifier = Modifier.weight(1f)) { Text("Give more", maxLines = 1) }
                            OutlinedButton(onClick = { nav.navigate(SafeRoutes.receive(id)) }, enabled = s.remaining > 0, modifier = Modifier.weight(1f)) { Text("Receive back", maxLines = 1) }
                        }
                    }
                }
            }
            item { SectionTitle("History") }
            item {
                SectionCard {
                    Column {
                        if (running.isEmpty()) Text("No entries yet.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        running.forEachIndexed { i, (t, bal) ->
                            if (i > 0) HorizontalDivider()
                            Row(Modifier.fillMaxWidth().clickable { nav.navigate(Routes.txn(t.id)) }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                IconBadge(if (t.type == TxnType.SAFE_GIVEN) Icons.Rounded.Handshake else Icons.Rounded.AssignmentReturned, if (t.type == TxnType.SAFE_GIVEN) MaterialTheme.colorScheme.tertiary else m.income)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(if (t.type == TxnType.SAFE_GIVEN) "Given" else "Received back", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        listOfNotNull(Dates.date(t.timestamp), (if (t.type == TxnType.SAFE_GIVEN) "from " else "into ") + (state.accountName(t.accountId) ?: "?"), t.party.ifBlank { null }).joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        (if (t.type == TxnType.SAFE_GIVEN) "" else "+") + Money.format(t.amountMinor, c),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (t.type == TxnType.SAFE_GIVEN) MaterialTheme.colorScheme.onSurface else m.income,
                                    )
                                    Text("left ${Money.format(bal, c)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
            if (audit.isNotEmpty()) {
                item {
                    SectionTitle("Change history") { TextButton(onClick = { showAudit = !showAudit }) { Text(if (showAudit) "Hide" else "Show (${audit.size})") } }
                }
                if (showAudit) item {
                    SectionCard {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            audit.forEach { a ->
                                Row {
                                    Icon(Icons.Rounded.History, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            when (a.action) { "CREATED" -> "Added"; "EDITED" -> "Edited"; "DELETED" -> "Deleted"; "RESTORED" -> "Restored (undo)"; "PERSON_EDITED" -> "Person updated"; else -> a.action.lowercase() } + " · " + Dates.dateTime(a.at),
                                            style = MaterialTheme.typography.labelLarge,
                                        )
                                        Text(a.details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (editing) PersonDialog(repo, h) { editing = false }
    if (confirmDelete) ConfirmDialog(
        "Remove ${h.name}?", "They have no entries, so nothing else changes.", "Remove", destructive = true,
        onConfirm = {
            confirmDelete = false
            scope.launch {
                when (val r = repo.deleteHolder(id)) {
                    is SaveResult.Ok -> { messenger.show("${h.name} removed"); nav.popBackStack() }
                    is SaveResult.Error -> error = r.message
                }
            }
        },
        onDismiss = { confirmDelete = false },
    )
    error?.let { MessageDialog("Can't remove", it) { error = null } }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PersonDialog(repo: Repository, existing: Holder, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(existing.name) }
    var phone by remember { mutableStateOf(existing.phone) }
    var notes by remember { mutableStateOf(existing.notes) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Edit person") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TextInput(name, { name = it }, "Name")
                PhoneInput(phone) { phone = it }
                TextInput(notes, { notes = it }, "Notes", singleLine = false)
                ErrorBanner(error)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    when (val r = repo.saveHolder(existing.copy(name = name, phone = phone, notes = notes))) {
                        is SaveResult.Ok -> onDone()
                        is SaveResult.Error -> error = r.message
                    }
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Cancel") } },
    )
}

@Composable
private fun PhoneInput(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value, { v -> onChange(v.filter { it.isDigit() || it in "+ -()" }.take(20)) },
        label = { Text("Contact number (optional)") }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        modifier = Modifier.fillMaxWidth(),
    )
}

// ============================ PIN check for corrections ============================

/**
 * When an app PIN is set, editing or deleting safekeeping records needs it again ("proper permission").
 * Without a PIN, [onOk] runs straight away.
 */
@Composable
fun PinGate(settings: SettingsStore, reason: String, onOk: () -> Unit, onCancel: () -> Unit) {
    if (settings.current.pinHash == null) { LaunchedEffect(Unit) { onOk() }; return }
    var pin by remember { mutableStateOf("") }
    var tries by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Enter your PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(reason, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    pin, { v -> pin = v.filter(Char::isDigit).take(8); error = null }, label = { Text("PIN") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                )
                ErrorBanner(error)
            }
        },
        confirmButton = {
            TextButton(enabled = pin.length >= 4, onClick = {
                if (settings.checkPin(pin)) onOk()
                else {
                    tries++; pin = ""
                    if (tries >= 5) onCancel() else error = "Wrong PIN. ${5 - tries} tries left."
                }
            }) { Text("Continue") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

// ============================ Give / Receive forms ============================

/**
 * One form for both directions. [type] SAFE_GIVEN = "Give money for safekeeping",
 * SAFE_RETURNED = "Receive money back".
 */
@Composable
fun SafeFormScreen(state: LedgerState, repo: Repository, type: TxnType, personArg: Long, editId: Long, nav: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val messenger = LocalMessenger.current
    val c = state.currency
    val m = LocalMoneyColors.current
    val giving = type == TxnType.SAFE_GIVEN
    val original = remember(editId) { state.txns.firstOrNull { it.id == editId && editId != 0L } }
    var permitted by remember { mutableStateOf(editId == 0L) }

    var personId by remember { mutableStateOf(original?.personId ?: personArg.takeIf { it != 0L }) }
    var newPerson by remember { mutableStateOf(state.plans.holders.isEmpty() && original == null) }
    var newName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf(personId?.let { state.holderById[it]?.phone } ?: "") }
    var amount by remember { mutableStateOf(original?.let { Money.toInput(it.amountMinor, c.decimals) } ?: "") }
    var timestamp by remember { mutableStateOf(original?.timestamp ?: System.currentTimeMillis()) }
    var accountId by remember { mutableStateOf(original?.accountId ?: state.activeLedgerAccounts.firstOrNull()?.id) }
    var purpose by remember { mutableStateOf(original?.party ?: "") }
    var due by remember { mutableStateOf(original?.dueEpochDay?.let(LocalDate::ofEpochDay)) }
    var notes by remember { mutableStateOf(original?.notes ?: "") }
    var receipt by remember { mutableStateOf(original?.receiptFile) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var pickDue by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmNegative by remember { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf(false) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val requestId = remember { original?.requestId ?: UUID.randomUUID().toString() }
    val initial = remember { listOf(personId, amount, timestamp, accountId, purpose, due, notes, receipt, phone).toString() }
    val dirty = listOf(personId, amount, timestamp, accountId, purpose, due, notes, receipt, phone).toString() != initial || newName.isNotBlank()
    val back = rememberDiscardGuard(dirty && !saving) { nav.popBackStack() }

    // Balance for the chosen person, leaving out the entry being edited.
    val others = state.txns.filter { it.id != editId }
    val given = personId?.let { SafeMath.given(others, it) } ?: 0L
    val returned = personId?.let { SafeMath.returned(others, it) } ?: 0L
    val remaining = given - returned
    val minor = Money.parse(amount, c.decimals)
    val tooMuch = !giving && minor != null && personId != null && minor > remaining

    fun importUri(uri: Uri?) {
        if (uri == null) return
        importing = true
        scope.launch {
            val name = ReceiptImport.importImage(ctx, uri, repo.receiptsDir)
            importing = false
            if (name == null) error = "Couldn't read that photo. Try another one." else receipt = name
        }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { importUri(it) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) importUri(cameraUri) }

    fun save(confirmed: Boolean) {
        error = null
        val v = minor
        if (v == null || v <= 0) { error = "Enter an amount greater than zero."; return }
        if (!newPerson && personId == null) { error = "Choose who has the money."; return }
        if (newPerson && newName.isBlank()) { error = "Enter the person's name."; return }
        if (tooMuch) { error = "Only ${Money.format(remaining.coerceAtLeast(0), c)} is outstanding with this person."; return }
        saving = true
        scope.launch {
            // Create the person, or update their contact number, first.
            val pid: Long = if (newPerson) {
                when (val r = repo.saveHolder(Holder(name = newName, phone = phone, createdAt = System.currentTimeMillis()))) {
                    is SaveResult.Ok -> r.id
                    is SaveResult.Error -> { error = r.message; saving = false; return@launch }
                }
            } else {
                val h = state.holderById[personId!!]
                if (h != null && h.phone != phone.trim()) repo.saveHolder(h.copy(phone = phone))
                personId!!
            }
            val txn = Txn(
                id = original?.id ?: 0L, type = type, amountMinor = v, timestamp = timestamp, party = purpose, notes = notes,
                requestId = requestId, createdAt = original?.createdAt ?: System.currentTimeMillis(), accountId = accountId,
                personId = pid, dueEpochDay = if (giving) due?.toEpochDay() else null, receiptFile = receipt,
            )
            if (!confirmed) negativeWarning(state, original, txn)?.let { confirmNegative = it; saving = false; if (newPerson) { newPerson = false; personId = pid }; return@launch }
            val oldReceipt = original?.receiptFile?.takeIf { it != receipt }
            when (val r = repo.saveSafe(txn, oldReceipt)) {
                is SaveResult.Ok -> {
                    val who = state.holderById[pid]?.name ?: newName.trim()
                    messenger.show(
                        if (original != null) "Changes saved"
                        else if (giving) "${Money.format(v, c)} given to $who for safekeeping"
                        else "${Money.format(v, c)} received back from $who"
                    )
                    nav.popBackStack()
                }
                is SaveResult.Error -> { error = r.message; saving = false; if (newPerson) { newPerson = false; personId = pid } }
            }
        }
    }

    if (!permitted) {
        PinGate(repo.settings, "Corrections to safekeeping records need your PIN.", onOk = { permitted = true }, onCancel = { nav.popBackStack() })
        return
    }

    ScreenScaffold(
        when {
            original != null -> if (giving) "Edit money given" else "Edit money received"
            giving -> "Give money for safekeeping"
            else -> "Receive money back"
        },
        nav, onBack = back,
        actions = { if (original != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, "Delete entry") } },
        bottomBar = { FormSaveBar(if (original != null) "Save changes" else if (giving) "Give money" else "Record money received", saving, enabled = !tooMuch) { save(false) } },
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Who
            FormSection {
                Text(if (giving) "Given to" else "Received from", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val choices = state.safeSummaries
                    .filter { giving || it.remaining > 0 || it.holder.id == original?.personId }
                    .sortedByDescending { it.lastActivity }
                if (!giving && choices.isEmpty()) Text("Nobody is holding your money at the moment.", style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    choices.take(16).forEach { s ->
                        FilterChip(!newPerson && personId == s.holder.id, { newPerson = false; personId = s.holder.id; phone = s.holder.phone }, {
                            Text(s.holder.name + if (!giving) " · " + Money.format(s.remaining, c) else "", maxLines = 1)
                        })
                    }
                    if (giving) FilterChip(newPerson, { newPerson = true; personId = null; phone = "" }, { Text("New person") }, leadingIcon = { Icon(Icons.Rounded.PersonAdd, null, Modifier.size(18.dp)) })
                }
                if (newPerson) TextInput(newName, { newName = it }, "Person's name", placeholder = "e.g. Bilal (brother)")
                if (giving && (newPerson || personId != null)) PhoneInput(phone) { phone = it }
            }

            // Balance with this person (receive) or what's already with them (give)
            if (personId != null && !newPerson && (given > 0 || !giving)) {
                SectionCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        BalanceLine("Original amount given", Money.format(given, c))
                        BalanceLine("Previously returned", Money.format(returned, c))
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        BalanceLine("Remaining", Money.format(remaining, c), bold = true, color = MaterialTheme.colorScheme.tertiary)
                        if (!giving && minor != null && minor > 0 && !tooMuch) BalanceLine("Remaining after this", Money.format(remaining - minor, c), color = if (remaining - minor == 0L) m.income else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            HeroAmountInput(amount, { amount = it }, c, if (giving) MaterialTheme.colorScheme.tertiary else m.income, label = if (giving) "Amount given" else "Amount received now", isError = tooMuch)
            if (tooMuch) Text("More than the ${Money.format(remaining.coerceAtLeast(0), c)} outstanding.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            if (!giving && remaining > 0) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(minor == remaining, { amount = Money.toInput(remaining, c.decimals) }, { Text("All: ${Money.format(remaining, c)}") })
                    if (remaining >= 2) FilterChip(minor == remaining / 2, { amount = Money.toInput(remaining / 2, c.decimals) }, { Text("Half") })
                }
            }

            FormSection {
                AccountSelector(state.activeLedgerAccounts, accountId, { accountId = it }, state.accountBalances, c, if (giving) "Paid from" else "Received through")
            }
            FormSection {
                DateTimeField(timestamp, { timestamp = it }, minDate = LocalDate.ofEpochDay(state.settings.trackingStartEpochDay), label = if (giving) "Date given" else "Recovery date")
                if (giving) {
                    TextInput(purpose, { purpose = it }, "Purpose / description", placeholder = "e.g. Keep for Eid shopping")
                    Row(Modifier.fillMaxWidth().clickable { pickDue = true }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Expected return date", style = MaterialTheme.typography.bodyLarge)
                            Text("Optional", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(due?.let(Dates::date) ?: "Not set", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        if (due != null) IconButton(onClick = { due = null }) { Icon(Icons.Rounded.Close, "Clear date") }
                    }
                }
                TextInput(notes, { notes = it }, "Notes", singleLine = false)
            }

            if (giving) FormSection {
                Text("Attachment / receipt (optional)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val r = receipt
                when {
                    r != null -> Box {
                        AsyncImage(repo.receiptFile(r), "Attachment", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp).clip(MaterialTheme.shapes.medium))
                        FilledTonalIconButton(onClick = { receipt = null }, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(32.dp)) { Icon(Icons.Rounded.Close, "Remove attachment", Modifier.size(18.dp)) }
                    }
                    importing -> CircularProgressIndicator(Modifier.size(32.dp))
                    else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            val u = ReceiptImport.newCameraUri(ctx); cameraUri = u
                            runCatching { camera.launch(u) }.onFailure { error = "No camera app is available." }
                        }, modifier = Modifier.weight(1f)) { Icon(Icons.Rounded.PhotoCamera, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Camera") }
                        OutlinedButton(onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.PhotoLibrary, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Gallery")
                        }
                    }
                }
            }

            ErrorBanner(error)
            Text(
                if (giving) "This moves money out of the account you choose. It is not an expense: it's recorded as money owed back to you."
                else "This puts the money back into the account you choose. It is not income: it's your own money returning.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
        }
    }

    if (pickDue) DatePickDialog(due ?: LocalDate.now().plusMonths(1), { due = it; pickDue = false }, { pickDue = false }, minDate = Dates.localDateTime(timestamp).toLocalDate())
    confirmNegative?.let { msg ->
        ConfirmDialog("Balance will go negative", msg, "Save anyway", onConfirm = { confirmNegative = null; save(true) }, onDismiss = { confirmNegative = null })
    }
    if (confirmDelete && original != null) {
        ConfirmDialog(
            "Delete this entry?", "${original.type.label} · ${Money.format(original.amountMinor, c)}. Balances are updated and the deletion is kept in the change history.", "Delete",
            destructive = true,
            onConfirm = {
                confirmDelete = false
                scope.launch {
                    when (val r = repo.deleteTxn(original.id)) {
                        is SaveResult.Ok -> { messenger.show("Entry deleted", "Undo") { repo.restoreTxn(original) }; nav.popBackStack() }
                        is SaveResult.Error -> error = r.message
                    }
                }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun BalanceLine(label: String, value: String, bold: Boolean = false, color: Color = MaterialTheme.colorScheme.onSurface) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = if (bold) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium, fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium, color = color)
    }
}
