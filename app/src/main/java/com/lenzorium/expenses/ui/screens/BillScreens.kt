package com.lenzorium.expenses.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lenzorium.expenses.data.CategoryKind
import com.lenzorium.expenses.data.Frequency
import com.lenzorium.expenses.data.Recurring
import com.lenzorium.expenses.data.Repository
import com.lenzorium.expenses.data.SaveResult
import com.lenzorium.expenses.data.TxnType
import com.lenzorium.expenses.domain.Money
import com.lenzorium.expenses.domain.PlanMath
import com.lenzorium.expenses.ui.LedgerState
import com.lenzorium.expenses.ui.LocalMessenger
import com.lenzorium.expenses.ui.components.AccountSelector
import com.lenzorium.expenses.ui.components.AmountField
import com.lenzorium.expenses.ui.components.CategoryGrid
import com.lenzorium.expenses.ui.components.CategoryIcons
import com.lenzorium.expenses.ui.components.ConfirmDialog
import com.lenzorium.expenses.ui.components.DatePickDialog
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
import com.lenzorium.expenses.ui.components.rememberDiscardGuard
import com.lenzorium.expenses.ui.components.rememberNotificationAsk
import com.lenzorium.expenses.ui.theme.LocalMoneyColors
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** "Due today", "Due tomorrow", "Due in 5 days", "3 days overdue". */
fun dueText(epochDay: Long, today: Long): String {
    val d = epochDay - today
    return when {
        d < -1 -> "${-d} days overdue"
        d == -1L -> "1 day overdue"
        d == 0L -> "Due today"
        d == 1L -> "Due tomorrow"
        d < 7 -> "Due in $d days"
        else -> "Due ${Dates.epochDay(epochDay)}"
    }
}

fun scheduleText(r: Recurring): String =
    if (r.intervalCount == 1) r.frequency.label else "Every ${r.intervalCount} ${r.frequency.unit}s"

@Composable
fun BillsScreen(state: LedgerState, repo: Repository, nav: NavController) {
    val c = state.currency
    val today = state.today
    var paying by remember { mutableStateOf<Recurring?>(null) }
    val scope = rememberCoroutineScope()
    val messenger = LocalMessenger.current
    val due = state.dueNow
    val upcoming = state.upcoming.filter { it.epochDay > today }
    val all = state.recurring

    ScreenScaffold(
        "Bills & income", nav,
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { nav.navigate(PlanRoutes.recurring()) }, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("Add bill") })
        },
    ) { padding ->
        if (all.isEmpty()) {
            Column(Modifier.padding(padding).padding(16.dp)) {
                SectionCard {
                    EmptyState(
                        Icons.Rounded.EventRepeat,
                        "Never miss a bill",
                        "Add rent, electricity, school fees, subscriptions or your salary. The app reminds you when each is due; you confirm it's paid and it's recorded for you. Nothing is ever deducted automatically.",
                    ) { TextButton(onClick = { nav.navigate(PlanRoutes.recurring()) }) { Text("Add your first bill") } }
                }
            }
            return@ScreenScaffold
        }
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (due.isNotEmpty()) {
                item { SectionTitle("Due now") }
                items(due, key = { "due-${it.id}" }) { r ->
                    SectionCard {
                        Column(Modifier.padding(16.dp)) {
                            BillHeader(state, r, today)
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { paying = r }, modifier = Modifier.weight(1f)) { Text(if (r.type == TxnType.INCOME) "Mark received" else "Mark paid") }
                                OutlinedButton(onClick = {
                                    scope.launch {
                                        repo.skipRecurring(r.id)
                                        messenger.show("${r.name} skipped. Next: ${PlanMath.nextAfter(r, r.nextDueEpochDay)?.let(Dates::epochDay) ?: "none"}")
                                    }
                                }, modifier = Modifier.weight(1f)) { Text("Skip this time") }
                            }
                        }
                    }
                }
            }
            val outlook = PlanMath.expectedNet(state.recurring, today + 30)
            item {
                SectionTitle("Next 30 days")
                SectionCard {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            if (outlook < 0) "${Money.format(-outlook, c)} expected to go out" else "${Money.format(outlook, c)} expected to come in",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            "From your bills and regular income, including anything due now.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (upcoming.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            upcoming.take(8).forEach { o ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(Dates.short(LocalDate.ofEpochDay(o.epochDay)), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(64.dp))
                                    Text(o.rule.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        (if (o.rule.type == TxnType.INCOME) "+" else "−") + Money.format(o.rule.amountMinor, c),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (o.rule.type == TxnType.INCOME) LocalMoneyColors.current.income else MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item { SectionTitle("All bills and regular income") }
            item {
                SectionCard {
                    Column {
                        all.forEachIndexed { i, r ->
                            if (i > 0) HorizontalDivider()
                            Column(Modifier.fillMaxWidth().clickable { nav.navigate(PlanRoutes.recurring(r.id)) }.padding(16.dp)) {
                                BillHeader(state, r, today)
                            }
                        }
                    }
                }
            }
        }
    }

    paying?.let { r -> PayBillDialog(state, repo, r) { paying = null } }
}

@Composable
private fun BillHeader(state: LedgerState, r: Recurring, today: Long) {
    val cat = r.categoryId?.let { state.categoryById[it] }
    val m = LocalMoneyColors.current
    val overdue = r.active && r.nextDueEpochDay < today
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconBadge(
            if (overdue) Icons.Rounded.Warning else cat?.let { CategoryIcons.of(it.iconKey) } ?: Icons.Rounded.EventRepeat,
            if (overdue) m.expense else cat?.let { Color(it.colorArgb) } ?: MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(r.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (!r.active) "Ended · ${scheduleText(r)}" else "${dueText(r.nextDueEpochDay, today)} · ${scheduleText(r)}",
                style = MaterialTheme.typography.bodySmall,
                color = if (overdue) m.expense else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (overdue) FontWeight.SemiBold else null,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                (if (r.type == TxnType.INCOME) "+" else "") + Money.format(r.amountMinor, state.currency),
                style = MaterialTheme.typography.titleSmall,
                color = if (r.type == TxnType.INCOME) m.income else MaterialTheme.colorScheme.onSurface,
            )
            state.accountName(r.accountId)?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

/** Confirms the actual amount and account, then records the entry. */
@Composable
fun PayBillDialog(state: LedgerState, repo: Repository, r: Recurring, onDone: () -> Unit) {
    val c = state.currency
    val scope = rememberCoroutineScope()
    val messenger = LocalMessenger.current
    var amount by remember { mutableStateOf(Money.toInput(r.amountMinor, c.decimals)) }
    var accountId by remember { mutableStateOf(r.accountId?.takeIf { id -> state.accountById[id]?.archived == false } ?: state.activeAccounts.firstOrNull()?.id) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val income = r.type == TxnType.INCOME
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(if (income) "Record ${r.name}" else "Pay ${r.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "This records ${if (income) "income" else "an expense"} dated ${if (r.nextDueEpochDay < state.today) Dates.epochDay(r.nextDueEpochDay) else "today"}. Change the amount if this time was different.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AmountField(amount, { amount = it }, c)
                AccountSelector(state.activeAccounts, accountId, { accountId = it }, state.accountBalances, c, if (income) "Received into" else "Paid from")
                ErrorBanner(error)
            }
        },
        confirmButton = {
            TextButton(enabled = !saving, onClick = {
                val minor = Money.parse(amount, c.decimals)
                if (minor == null || minor <= 0) { error = "Enter an amount greater than zero."; return@TextButton }
                saving = true
                val zone = ZoneId.systemDefault()
                val start = LocalDate.ofEpochDay(state.settings.trackingStartEpochDay)
                val dueDate = LocalDate.ofEpochDay(r.nextDueEpochDay)
                // Overdue items are dated on their due day (noon); items paid early are dated now.
                val paidAt = if (dueDate.isBefore(LocalDate.now()) && !dueDate.isBefore(start))
                    dueDate.atTime(LocalTime.NOON).atZone(zone).toInstant().toEpochMilli() else System.currentTimeMillis()
                scope.launch {
                    when (val res = repo.markRecurringPaid(r.id, r.nextDueEpochDay, minor, accountId, paidAt)) {
                        is SaveResult.Ok -> { messenger.show("${r.name} recorded: ${Money.format(minor, c)}"); onDone() }
                        is SaveResult.Error -> { error = res.message; saving = false }
                    }
                }
            }) { Text(if (income) "Record income" else "Record payment") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Cancel") } },
    )
}

@Composable
fun RecurringFormScreen(state: LedgerState, repo: Repository, editId: Long, nav: NavController) {
    val c = state.currency
    val scope = rememberCoroutineScope()
    val messenger = LocalMessenger.current
    val askNotify = rememberNotificationAsk()
    var loaded by remember { mutableStateOf(editId == 0L) }
    var original by remember { mutableStateOf<Recurring?>(null) }
    var type by remember { mutableStateOf(TxnType.EXPENSE) }
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var categoryId by remember { mutableStateOf<Long?>(null) }
    var accountId by remember { mutableStateOf(state.activeAccounts.firstOrNull()?.id) }
    var frequency by remember { mutableStateOf(Frequency.MONTHLY) }
    var interval by remember { mutableStateOf("1") }
    var start by remember { mutableStateOf(LocalDate.now()) }
    var end by remember { mutableStateOf<LocalDate?>(null) }
    var remind by remember { mutableStateOf(true) }
    var daysBefore by remember { mutableStateOf(1) }
    var notes by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var datePick by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var initial by remember { mutableStateOf<String?>(null) }
    fun snap() = listOf(type, name, amount, categoryId, accountId, frequency, interval, start, end, remind, daysBefore, notes).toString()

    LaunchedEffect(editId) {
        if (editId != 0L) {
            repo.recurringItem(editId)?.let { r ->
                original = r; type = r.type; name = r.name; amount = Money.toInput(r.amountMinor, c.decimals)
                categoryId = r.categoryId; accountId = r.accountId; frequency = r.frequency; interval = r.intervalCount.toString()
                start = LocalDate.ofEpochDay(r.startEpochDay); end = r.endEpochDay?.let(LocalDate::ofEpochDay)
                remind = r.remind; daysBefore = r.remindDaysBefore; notes = r.notes
            }
            loaded = true
        }
        initial = snap()
    }
    val back = rememberDiscardGuard(loaded && initial != null && snap() != initial && !saving) { nav.popBackStack() }
    val income = type == TxnType.INCOME

    ScreenScaffold(
        if (editId == 0L) "New bill or income" else "Edit", nav, onBack = back,
        actions = { if (editId != 0L) TextButton(onClick = { confirmDelete = true }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
        bottomBar = {
            if (loaded) FormSaveBar(if (editId == 0L) "Save" else "Save changes", saving) {
                val minor = Money.parse(amount, c.decimals)
                val n = interval.toIntOrNull()
                when {
                    name.isBlank() -> { error = "Enter a name, e.g. Electricity or Netflix."; return@FormSaveBar }
                    minor == null || minor <= 0 -> { error = "Enter an amount greater than zero."; return@FormSaveBar }
                    n == null || n !in 1..365 -> { error = "Repeat every 1 to 365."; return@FormSaveBar }
                }
                saving = true
                val r = Recurring(
                    id = original?.id ?: 0L, name = name, type = type, amountMinor = minor!!, categoryId = categoryId, accountId = accountId,
                    party = original?.party ?: "", frequency = frequency, intervalCount = n!!, startEpochDay = start.toEpochDay(),
                    endEpochDay = end?.toEpochDay(), nextDueEpochDay = original?.nextDueEpochDay ?: start.toEpochDay(),
                    remind = remind, remindDaysBefore = daysBefore, active = original?.active ?: true, notes = notes.trim(),
                    createdAt = original?.createdAt ?: System.currentTimeMillis(),
                )
                scope.launch {
                    when (val res = repo.saveRecurring(if (original != null && end == null && !original!!.active) r.copy(active = true) else r)) {
                        is SaveResult.Ok -> { if (remind) askNotify(); messenger.show(if (editId == 0L) "$name added" else "Changes saved"); nav.popBackStack() }
                        is SaveResult.Error -> { error = res.message; saving = false }
                    }
                }
            }
        },
    ) { padding ->
        if (!loaded) return@ScreenScaffold
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(TxnType.EXPENSE to "Bill / subscription", TxnType.INCOME to "Regular income").forEachIndexed { i, (t, label) ->
                    SegmentedButton(type == t, { if (type != t) { type = t; categoryId = null } }, SegmentedButtonDefaults.itemShape(i, 2)) { Text(label) }
                }
            }
            HeroAmountInput(amount, { amount = it }, c, if (income) LocalMoneyColors.current.income else MaterialTheme.colorScheme.primary, label = "Usual amount")
            FormSection {
                TextInput(name, { name = it }, "Name", placeholder = if (income) "e.g. Salary" else "e.g. Electricity, Netflix, school fees")
            }
            FormSection {
                CategoryGrid(state.categories.filter { it.kind == if (income) CategoryKind.INCOME else CategoryKind.EXPENSE }, categoryId, { categoryId = it })
            }
            FormSection {
                AccountSelector(state.activeAccounts, accountId, { accountId = it }, state.accountBalances, c, if (income) "Usually received into" else "Usually paid from")
            }
            FormSection {
                Text("Repeats", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Frequency.entries.forEach { f -> FilterChip(frequency == f, { frequency = f }, { Text(f.label) }) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Every", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.width(12.dp))
                    TextInput(interval, { v -> interval = v.filter(Char::isDigit).take(3) }, "Number", modifier = Modifier.width(110.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(frequency.unit + if (interval != "1") "s" else "", style = MaterialTheme.typography.bodyLarge)
                }
                DateRow("First due date", Dates.date(start)) { datePick = "start" }
                DateRow("Ends", end?.let(Dates::date) ?: "Never") { datePick = "end" }
                if (end != null) TextButton(onClick = { end = null }) { Text("Remove end date") }
            }
            FormSection {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.NotificationsActive, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Remind me", style = MaterialTheme.typography.titleSmall)
                        Text("A notification without amounts, around 9am", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(remind, { remind = it })
                }
                if (remind) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to "On the day", 1 to "1 day before", 3 to "3 days before", 7 to "A week before").forEach { (d, l) ->
                        FilterChip(daysBefore == d, { daysBefore = d }, { Text(l) })
                    }
                }
                TextInput(notes, { notes = it }, "Notes", singleLine = false)
            }
            ErrorBanner(error)
            Text(
                "Nothing is deducted automatically. When it's due you choose \"Mark paid\" (it's recorded as a normal entry) or \"Skip\".",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
        }
    }

    when (datePick) {
        "start" -> DatePickDialog(start, { start = it; datePick = null }, { datePick = null })
        "end" -> DatePickDialog(end ?: start.plusYears(1), { end = it; datePick = null }, { datePick = null }, minDate = start)
    }
    if (confirmDelete) {
        ConfirmDialog(
            "Delete \"$name\"?", "Entries already recorded for it stay in your history.", "Delete",
            onConfirm = { scope.launch { repo.deleteRecurring(editId); messenger.show("$name deleted"); nav.popBackStack() } },
            onDismiss = { confirmDelete = false }, destructive = true,
        )
    }
}

@Composable
private fun DateRow(label: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    }
}
