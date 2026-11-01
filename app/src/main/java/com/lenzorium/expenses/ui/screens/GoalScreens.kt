package com.lenzorium.expenses.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.rounded.Check
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.foundation.layout.FlowRow
import androidx.navigation.NavController
import com.lenzorium.expenses.data.Goal
import com.lenzorium.expenses.data.Repository
import com.lenzorium.expenses.data.SaveResult
import com.lenzorium.expenses.data.Template
import com.lenzorium.expenses.data.TxnType
import com.lenzorium.expenses.domain.GoalProgress
import com.lenzorium.expenses.domain.Money
import com.lenzorium.expenses.ui.LedgerState
import com.lenzorium.expenses.ui.LocalMessenger
import com.lenzorium.expenses.ui.components.AccountSelector
import com.lenzorium.expenses.ui.components.AmountField
import com.lenzorium.expenses.ui.components.CategoryIcons
import com.lenzorium.expenses.ui.components.ConfirmDialog
import com.lenzorium.expenses.ui.components.DatePickDialog
import com.lenzorium.expenses.ui.components.Dates
import com.lenzorium.expenses.ui.components.EmptyState
import com.lenzorium.expenses.ui.components.ErrorBanner
import com.lenzorium.expenses.ui.components.IconBadge
import com.lenzorium.expenses.ui.components.ProgressBar
import com.lenzorium.expenses.ui.components.ProgressRing
import com.lenzorium.expenses.ui.components.ScreenScaffold
import com.lenzorium.expenses.ui.components.SectionCard
import com.lenzorium.expenses.ui.components.SectionTitle
import com.lenzorium.expenses.ui.components.TextInput
import com.lenzorium.expenses.ui.theme.Brand
import com.lenzorium.expenses.ui.theme.LocalMoneyColors
import kotlinx.coroutines.launch
import java.time.LocalDate

private val goalColors = listOf(0xFF4A9EDBL, 0xFF1E9185L, 0xFFCCA34CL, 0xFF7353BAL, 0xFFD64FA1L, 0xFFE4572EL)

@Composable
fun GoalsScreen(state: LedgerState, repo: Repository, nav: NavController) {
    val c = state.currency
    var adding by remember { mutableStateOf(false) }
    ScreenScaffold(
        "Savings goals", nav,
        floatingActionButton = { ExtendedFloatingActionButton(onClick = { adding = true }, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("New goal") }) },
    ) { padding ->
        if (state.goalProgress.isEmpty()) {
            Column(Modifier.padding(padding).padding(16.dp)) {
                SectionCard {
                    EmptyState(
                        Icons.Rounded.Flag, "Save for something",
                        "A new phone, Eid shopping, school fees, an emergency fund. Set a target and put money aside step by step. The money stays in your account; a goal just keeps track of it.",
                    ) { TextButton(onClick = { adding = true }) { Text("Create a goal") } }
                }
            }
            return@ScreenScaffold
        }
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                val total = state.setAside
                val over = total > state.available.coerceAtLeast(0)
                SectionCard {
                    Column(Modifier.padding(16.dp)) {
                        Text("Set aside in total", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(Money.format(total, c), style = MaterialTheme.typography.headlineSmall)
                        Text(
                            if (over) "More than your available balance (${Money.format(state.available, c)}). Check your goals or balances."
                            else "Free to spend after goals: ${Money.format(state.available - total, c)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (over) LocalMoneyColors.current.expense else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(state.goalProgress, key = { it.goal.id }) { g -> GoalCard(state, g) { nav.navigate(PlanRoutes.goal(g.goal.id)) } }
        }
    }
    if (adding) GoalEditDialog(state, repo, null) { adding = false }
}

@Composable
private fun GoalCard(state: LedgerState, g: GoalProgress, onClick: () -> Unit) {
    val c = state.currency
    val color = Color(g.goal.colorArgb)
    SectionCard(Modifier.clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(g.fraction, if (g.reached) Brand.Gold else color, size = 64.dp, stroke = 7.dp, centerTop = "${(g.fraction * 100).toInt()}%")
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(g.goal.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (g.reached) { Spacer(Modifier.width(6.dp)); Icon(Icons.Rounded.EmojiEvents, "Goal reached", tint = Brand.Gold, modifier = Modifier.padding(0.dp)) }
                }
                Text("${Money.format(g.saved, c)} of ${Money.format(g.goal.targetMinor, c)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val need = g.monthlyNeeded(LocalDate.now())
                Text(
                    when {
                        g.reached -> "Goal reached"
                        need != null && g.goal.targetEpochDay != null -> "${Money.format(need, c)}/month to reach it by ${Dates.epochDay(g.goal.targetEpochDay)}"
                        else -> "${Money.format(g.remaining, c)} to go"
                    },
                    style = MaterialTheme.typography.bodySmall, color = if (g.reached) Brand.Gold else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (g.reached) FontWeight.SemiBold else null,
                )
            }
        }
    }
}

@Composable
fun GoalDetailScreen(state: LedgerState, repo: Repository, id: Long, nav: NavController) {
    val c = state.currency
    val scope = rememberCoroutineScope()
    val messenger = LocalMessenger.current
    val goal = state.goals.firstOrNull { it.id == id }
    var moneyDialog by remember { mutableStateOf<Int>(0) } // 1 = add, -1 = take back
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    if (goal == null) { ScreenScaffold("Goal", nav) { }; return }
    val p = state.goalProgress.firstOrNull { it.goal.id == id } ?: GoalProgress(goal, 0)
    val entries = state.plans.goalEntries.filter { it.goalId == id }

    ScreenScaffold(
        goal.name, nav,
        actions = {
            IconButton(onClick = { editing = true }) { Icon(Icons.Rounded.Edit, "Edit goal") }
            IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, "Delete goal") }
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                SectionCard {
                    Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        ProgressRing(p.fraction, if (p.reached) Brand.Gold else Color(goal.colorArgb), size = 140.dp, stroke = 14.dp, centerTop = "${(p.fraction * 100).toInt()}%", centerBottom = if (p.reached) "reached" else "saved")
                        Spacer(Modifier.height(12.dp))
                        Text(Money.format(p.saved, c), style = MaterialTheme.typography.headlineMedium)
                        Text("of ${Money.format(goal.targetMinor, c)}" + (goal.targetEpochDay?.let { " by ${Dates.epochDay(it)}" } ?: ""), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        p.monthlyNeeded(LocalDate.now())?.takeIf { it > 0 }?.let {
                            Spacer(Modifier.height(6.dp))
                            Text("Put aside about ${Money.format(it, c)} a month to get there on time.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        goal.accountId?.let { a -> state.accountName(a)?.let { Text("Kept in $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { moneyDialog = 1 }, modifier = Modifier.weight(1f)) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(6.dp)); Text("Add money") }
                            OutlinedButton(onClick = { moneyDialog = -1 }, enabled = p.saved > 0, modifier = Modifier.weight(1f)) { Icon(Icons.Rounded.Remove, null); Spacer(Modifier.width(6.dp)); Text("Take back") }
                        }
                    }
                }
            }
            item {
                Text(
                    "Adding money to a goal doesn't change your balances. It marks part of your money as saved for this goal.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            if (entries.isNotEmpty()) {
                item { SectionTitle("History") }
                item {
                    SectionCard {
                        Column {
                            entries.forEachIndexed { i, e ->
                                if (i > 0) HorizontalDivider()
                                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(if (e.amountMinor > 0) "Set aside" else "Taken back", style = MaterialTheme.typography.titleSmall)
                                        Text(Dates.epochDay(e.epochDay) + (e.note.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text(Money.formatSigned(e.amountMinor, c), style = MaterialTheme.typography.titleSmall, color = if (e.amountMinor > 0) LocalMoneyColors.current.income else MaterialTheme.colorScheme.onSurface)
                                    IconButton(onClick = { scope.launch { repo.deleteGoalEntry(e.id); messenger.show("Entry removed") } }) {
                                        Icon(Icons.Rounded.Delete, "Remove entry", tint = MaterialTheme.colorScheme.outline)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (moneyDialog != 0) {
        var amount by remember { mutableStateOf("") }
        var note by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        val adding = moneyDialog > 0
        AlertDialog(
            onDismissRequest = { moneyDialog = 0 },
            title = { Text(if (adding) "Add to ${goal.name}" else "Take back from ${goal.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AmountField(amount, { amount = it }, c)
                    if (adding && p.remaining > 0) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(false, { amount = Money.toInput(p.remaining, c.decimals) }, { Text("Rest: ${Money.format(p.remaining, c)}") })
                        }
                    }
                    TextInput(note, { note = it }, "Note (optional)")
                    ErrorBanner(error)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val minor = Money.parse(amount, c.decimals)
                    if (minor == null || minor <= 0) { error = "Enter an amount greater than zero."; return@TextButton }
                    scope.launch {
                        when (val r = repo.addGoalEntry(id, if (adding) minor else -minor, note)) {
                            is SaveResult.Ok -> { messenger.show(if (adding) "${Money.format(minor, c)} set aside" else "${Money.format(minor, c)} taken back"); moneyDialog = 0 }
                            is SaveResult.Error -> error = r.message
                        }
                    }
                }) { Text(if (adding) "Add" else "Take back") }
            },
            dismissButton = { TextButton(onClick = { moneyDialog = 0 }) { Text("Cancel") } },
        )
    }
    if (editing) GoalEditDialog(state, repo, goal) { editing = false }
    if (confirmDelete) {
        ConfirmDialog(
            "Delete \"${goal.name}\"?", "Your balances don't change; only the goal and its history are removed.", "Delete",
            onConfirm = { scope.launch { repo.deleteGoal(id); messenger.show("Goal deleted"); nav.popBackStack() } },
            onDismiss = { confirmDelete = false }, destructive = true,
        )
    }
}

@Composable
private fun GoalEditDialog(state: LedgerState, repo: Repository, existing: Goal?, onDone: () -> Unit) {
    val c = state.currency
    val scope = rememberCoroutineScope()
    val messenger = LocalMessenger.current
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var amount by remember { mutableStateOf(existing?.let { Money.toInput(it.targetMinor, c.decimals) } ?: "") }
    var date by remember { mutableStateOf(existing?.targetEpochDay?.let(LocalDate::ofEpochDay)) }
    var accountId by remember { mutableStateOf(existing?.accountId) }
    var color by remember { mutableStateOf(existing?.colorArgb ?: goalColors[state.goals.size % goalColors.size]) }
    var pick by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(if (existing == null) "New savings goal" else "Edit goal") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextInput(name, { name = it }, "Name", placeholder = "e.g. New phone, Emergency fund")
                AmountField(amount, { amount = it }, c, label = "Target")
                Row(Modifier.fillMaxWidth().clickable { pick = true }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Target date", modifier = Modifier.weight(1f))
                    Text(date?.let(Dates::date) ?: "None", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall)
                }
                if (date != null) TextButton(onClick = { date = null }) { Text("Remove date") }
                AccountSelector(state.activeAccounts, accountId, { accountId = if (accountId == it) null else it }, state.accountBalances, c, "Kept in (optional)")
                Text("Colour", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    goalColors.forEach { col ->
                        Box(
                            Modifier.size(34.dp).clip(CircleShape).background(Color(col)).clickable { color = col },
                            contentAlignment = Alignment.Center,
                        ) { if (col == color) Icon(Icons.Rounded.Check, "Selected", tint = Color.White, modifier = Modifier.size(18.dp)) }
                    }
                }
                ErrorBanner(error)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val minor = Money.parse(amount, c.decimals)
                if (minor == null || minor <= 0) { error = "Enter a target greater than zero."; return@TextButton }
                val g = Goal(existing?.id ?: 0, name, minor, date?.toEpochDay(), accountId, existing?.iconKey ?: "goal", color, existing?.archived ?: false, existing?.createdAt ?: System.currentTimeMillis())
                scope.launch {
                    when (val r = repo.saveGoal(g)) {
                        is SaveResult.Ok -> { messenger.show(if (existing == null) "Goal created" else "Goal updated"); onDone() }
                        is SaveResult.Error -> error = r.message
                    }
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Cancel") } },
    )
    if (pick) DatePickDialog(date ?: LocalDate.now().plusMonths(6), { date = it; pick = false }, { pick = false }, minDate = LocalDate.now())
}

// ---------- Shortcuts (quick-add templates) ----------

@Composable
fun ShortcutsScreen(state: LedgerState, repo: Repository, nav: NavController) {
    val c = state.currency
    val scope = rememberCoroutineScope()
    val messenger = LocalMessenger.current
    var editing by remember { mutableStateOf<Template?>(null) }
    ScreenScaffold("Shortcuts", nav) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text(
                    "Shortcuts fill in an entry for you: tap one under Add and just press Save. To make one, fill in an expense or income and tap the bookmark at the top.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            if (state.templates.isEmpty()) {
                item {
                    SectionCard {
                        EmptyState(Icons.Rounded.Bolt, "No shortcuts yet", "Great for things you buy often: tea, petrol, a bus ride, your phone package.") {
                            TextButton(onClick = { nav.navigate(com.lenzorium.expenses.ui.Routes.entry(TxnType.EXPENSE)) }) { Text("Add an expense") }
                        }
                    }
                }
            } else item {
                SectionCard {
                    Column {
                        state.templates.forEachIndexed { i, t ->
                            if (i > 0) HorizontalDivider()
                            val cat = t.categoryId?.let { state.categoryById[it] }
                            Row(Modifier.fillMaxWidth().clickable { nav.navigate(PlanRoutes.template(t.id, t.type)) }.padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                IconBadge(cat?.let { CategoryIcons.of(it.iconKey) } ?: Icons.Rounded.Bolt, cat?.let { Color(it.colorArgb) } ?: MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(t.name, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        listOfNotNull(if (t.amountMinor > 0) Money.format(t.amountMinor, c) else "Amount each time", cat?.name, state.accountName(t.accountId)).joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = { editing = t }) { Icon(Icons.Rounded.Edit, "Edit ${t.name}") }
                                IconButton(onClick = {
                                    scope.launch {
                                        repo.deleteTemplate(t.id)
                                        messenger.show("Shortcut deleted", "Undo") { repo.saveTemplate(t.copy(id = 0)) }
                                    }
                                }) { Icon(Icons.Rounded.Delete, "Delete ${t.name}") }
                            }
                        }
                    }
                }
            }
        }
    }
    editing?.let { t ->
        var name by remember(t) { mutableStateOf(t.name) }
        var amount by remember(t) { mutableStateOf(if (t.amountMinor > 0) Money.toInput(t.amountMinor, c.decimals) else "") }
        var error by remember(t) { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Edit shortcut") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextInput(name, { name = it }, "Name")
                    AmountField(amount, { amount = it }, c, label = "Amount (leave empty to ask each time)")
                    ErrorBanner(error)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val minor = if (amount.isBlank()) 0L else Money.parse(amount, c.decimals)
                    if (minor == null) { error = "Enter a valid amount."; return@TextButton }
                    scope.launch {
                        when (val r = repo.saveTemplate(t.copy(name = name, amountMinor = minor))) {
                            is SaveResult.Ok -> editing = null
                            is SaveResult.Error -> error = r.message
                        }
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
        )
    }
}
