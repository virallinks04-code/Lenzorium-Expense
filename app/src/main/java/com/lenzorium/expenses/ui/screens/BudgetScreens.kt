package com.lenzorium.expenses.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.DonutLarge
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.FlowRow
import androidx.navigation.NavController
import com.lenzorium.expenses.data.Budget
import com.lenzorium.expenses.data.CategoryKind
import com.lenzorium.expenses.data.Repository
import com.lenzorium.expenses.data.SaveResult
import com.lenzorium.expenses.domain.BudgetLevel
import com.lenzorium.expenses.domain.BudgetStatus
import com.lenzorium.expenses.domain.Money
import com.lenzorium.expenses.ui.LedgerState
import com.lenzorium.expenses.ui.LocalMessenger
import com.lenzorium.expenses.ui.components.AmountField
import com.lenzorium.expenses.ui.components.CategoryIcons
import com.lenzorium.expenses.ui.components.ConfirmDialog
import com.lenzorium.expenses.ui.components.EmptyState
import com.lenzorium.expenses.ui.components.ErrorBanner
import com.lenzorium.expenses.ui.components.IconBadge
import com.lenzorium.expenses.ui.components.ProgressBar
import com.lenzorium.expenses.ui.components.ProgressRing
import com.lenzorium.expenses.ui.components.ScreenScaffold
import com.lenzorium.expenses.ui.components.SectionCard
import com.lenzorium.expenses.ui.components.SectionTitle
import com.lenzorium.expenses.ui.components.budgetColor
import com.lenzorium.expenses.ui.components.rememberNotificationAsk
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

fun budgetLevelText(s: BudgetStatus): String = when (s.level) {
    BudgetLevel.OVER -> "Over budget"
    BudgetLevel.NEAR -> "Nearly used"
    BudgetLevel.OK -> "On track"
}

@Composable
fun BudgetsScreen(state: LedgerState, repo: Repository, nav: NavController) {
    val c = state.currency
    var editing by remember { mutableStateOf<Budget?>(null) }
    var adding by remember { mutableStateOf(false) }
    val monthName = state.thisMonth.atDay(1).format(DateTimeFormatter.ofPattern("MMMM yyyy"))

    ScreenScaffold(
        "Budgets", nav,
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { adding = true }, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("Add budget") })
        },
    ) { padding ->
        if (state.budgets.isEmpty()) {
            Column(Modifier.padding(padding).padding(16.dp)) {
                SectionCard {
                    EmptyState(
                        Icons.Rounded.DonutLarge,
                        "Set a monthly budget",
                        "Choose how much you want to spend each month, overall or per category. You'll see what's left and get a gentle warning at 80%. Budgets never stop you adding an expense.",
                    ) { TextButton(onClick = { adding = true }) { Text("Create your first budget") } }
                }
            }
            return@ScreenScaffold
        }
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text(monthName, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp)) }
            state.overallBudget?.let { s ->
                item {
                    SectionCard(Modifier.clickable { editing = s.budget }) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            ProgressRing(s.fraction, budgetColor(s.level), centerTop = "${(s.fraction * 100).toInt().coerceAtMost(999)}%", centerBottom = "used")
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text("All spending", style = MaterialTheme.typography.titleMedium)
                                Text("${Money.format(s.spent, c)} of ${Money.format(s.available, c)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    if (s.remaining >= 0) "${Money.format(s.remaining, c)} left" else "${Money.format(-s.remaining, c)} over",
                                    style = MaterialTheme.typography.titleSmall, color = budgetColor(s.level), fontWeight = FontWeight.SemiBold,
                                )
                                if (s.carried != 0L) Text(
                                    (if (s.carried > 0) "Includes ${Money.format(s.carried, c)} carried from last month" else "Includes ${Money.format(-s.carried, c)} overspent last month"),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            if (state.categoryBudgets.isNotEmpty()) {
                item { SectionTitle("By category") }
                items(state.categoryBudgets, key = { it.budget.id }) { s -> BudgetRow(state, s) { editing = s.budget } }
            }
            item {
                Text(
                    "Only expenses count. Transfers between your accounts and loan repayments are never counted as spending.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                )
            }
        }
    }

    if (adding || editing != null) {
        BudgetEditDialog(state, repo, editing) { adding = false; editing = null }
    }
}

@Composable
fun BudgetRow(state: LedgerState, s: BudgetStatus, onClick: () -> Unit) {
    val c = state.currency
    val cat = s.budget.categoryId?.let { state.categoryById[it] }
    val color = budgetColor(s.level)
    SectionCard(Modifier.clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(cat?.let { CategoryIcons.of(it.iconKey) } ?: Icons.Rounded.Savings, cat?.let { Color(it.colorArgb) } ?: MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(cat?.name ?: "All spending", style = MaterialTheme.typography.titleSmall)
                    Text("${Money.format(s.spent, c)} of ${Money.format(s.available, c)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        if (s.remaining >= 0) Money.format(s.remaining, c) else "−" + Money.format(-s.remaining, c),
                        style = MaterialTheme.typography.titleSmall, color = color, fontWeight = FontWeight.SemiBold,
                    )
                    Text(if (s.remaining >= 0) "left" else budgetLevelText(s).lowercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(10.dp))
            ProgressBar(s.fraction, color)
        }
    }
}

@Composable
private fun BudgetEditDialog(state: LedgerState, repo: Repository, existing: Budget?, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    val messenger = LocalMessenger.current
    val c = state.currency
    val used = state.budgets.filter { it.id != existing?.id }.map { it.categoryId }.toSet()
    var categoryId by remember { mutableStateOf(existing?.categoryId ?: if (null in used) state.categories.firstOrNull { it.kind == CategoryKind.EXPENSE && it.id !in used }?.id else null) }
    var amount by remember { mutableStateOf(existing?.let { Money.toInput(it.limitMinor, c.decimals) } ?: "") }
    var rollover by remember { mutableStateOf(existing?.rollover ?: false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val askNotify = rememberNotificationAsk()

    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(if (existing == null) "New budget" else "Edit budget") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("For", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (null !in used) FilterChip(categoryId == null, { categoryId = null }, { Text("All spending") })
                    state.categories.filter { it.kind == CategoryKind.EXPENSE && it.id !in used }.forEach { cat ->
                        FilterChip(categoryId == cat.id, { categoryId = cat.id }, { Text(cat.name) })
                    }
                }
                AmountField(amount, { amount = it }, c, label = "Monthly limit")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Roll over", style = MaterialTheme.typography.titleSmall)
                        Text("Add last month's unused amount (or subtract overspending)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(rollover, { rollover = it })
                }
                ErrorBanner(error)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val minor = Money.parse(amount, c.decimals)
                if (minor == null || minor <= 0) { error = "Enter a monthly limit greater than zero."; return@TextButton }
                val monthStart = (existing?.startMonthEpochDay ?: LocalDate.now().withDayOfMonth(1).toEpochDay())
                val b = Budget(existing?.id ?: 0, categoryId, minor, rollover, monthStart, existing?.createdAt ?: System.currentTimeMillis())
                scope.launch {
                    when (val r = repo.saveBudget(b)) {
                        is SaveResult.Ok -> { if (existing == null) askNotify(); messenger.show(if (existing == null) "Budget created" else "Budget updated"); onDone() }
                        is SaveResult.Error -> error = r.message
                    }
                }
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (existing != null) TextButton(onClick = { confirmDelete = true }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDone) { Text("Cancel") }
            }
        },
    )
    if (confirmDelete && existing != null) {
        ConfirmDialog(
            "Delete this budget?", "Your expenses are not affected.", "Delete",
            onConfirm = { scope.launch { repo.deleteBudget(existing.id); messenger.show("Budget deleted"); onDone() } },
            onDismiss = { confirmDelete = false }, destructive = true,
        )
    }
}
