package com.lenzorium.expenses.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lenzorium.expenses.data.TxnType
import com.lenzorium.expenses.domain.LedgerMath
import com.lenzorium.expenses.domain.Money
import com.lenzorium.expenses.ui.LedgerState
import com.lenzorium.expenses.ui.Routes
import com.lenzorium.expenses.ui.components.Dates
import com.lenzorium.expenses.ui.components.DonutWithLegend
import com.lenzorium.expenses.ui.components.EmptyState
import com.lenzorium.expenses.ui.components.IconBadge
import com.lenzorium.expenses.ui.components.SectionCard
import com.lenzorium.expenses.ui.components.SectionTitle
import com.lenzorium.expenses.ui.components.Slice
import com.lenzorium.expenses.ui.components.StatTile
import com.lenzorium.expenses.ui.components.TxnRow
import com.lenzorium.expenses.ui.goTab
import com.lenzorium.expenses.ui.theme.LocalMoneyColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(state: LedgerState, nav: NavController) {
    val c = state.currency
    val m = LocalMoneyColors.current
    val today = LocalDate.now()
    val dayStart = Dates.startOf(today)
    val dayEnd = Dates.startOf(today.plusDays(1))
    val monthStart = Dates.startOf(today.withDayOfMonth(1))
    val monthEnd = Dates.startOf(today.withDayOfMonth(1).plusMonths(1))
    val todayTotals = remember(state.txns) { LedgerMath.periodTotals(state.txns, dayStart, dayEnd) }
    val month = remember(state.txns) { LedgerMath.periodTotals(state.txns, monthStart, monthEnd) }
    val slices = remember(month, state.categories) {
        month.expensesByCategory.entries.sortedByDescending { it.value }.map { (id, v) ->
            val cat = id?.let { state.categoryById[it] }
            Slice(cat?.name ?: "Uncategorised", v, cat?.let { Color(it.colorArgb) } ?: Color.Gray)
        }
    }
    val upcoming = state.loanSummaries.filter { it.outstanding > 0 && it.loan.dueEpochDay != null }.sortedBy { it.loan.dueEpochDay }.take(3)
    val recent = state.txns.take(6)

    LazyColumn(modifier = Modifier.statusBarsPadding(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Lenzorium", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                    Text(today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { nav.navigate(Routes.LOANS) }) {
                    Icon(Icons.Rounded.AccountBalance, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Loans")
                }
            }
        }

        // Current balances
        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.linearGradient(listOf(m.heroStart, m.heroEnd)), MaterialTheme.shapes.large)
                    .padding(20.dp)
            ) {
                Text("AVAILABLE BALANCE · NOW", style = MaterialTheme.typography.labelMedium, color = m.onHero.copy(alpha = 0.8f))
                Spacer(Modifier.height(4.dp))
                Text(Money.format(state.available, c), style = MaterialTheme.typography.displaySmall, color = m.onHero)
                if (state.available < 0) {
                    Text("Your balance is negative", style = MaterialTheme.typography.bodySmall, color = Color(0xFFFFD180))
                }
                Spacer(Modifier.height(16.dp))
                Row {
                    Column(Modifier.weight(1f).clickable { nav.navigate(Routes.LOANS) }) {
                        Text("Outstanding debt", style = MaterialTheme.typography.labelMedium, color = m.onHero.copy(alpha = 0.8f))
                        Text(Money.format(state.debt, c), style = MaterialTheme.typography.titleMedium, color = m.onHero)
                    }
                    if (state.settings.showAfterDebt) {
                        Column(Modifier.weight(1f)) {
                            Text("After clearing debt", style = MaterialTheme.typography.labelMedium, color = m.onHero.copy(alpha = 0.8f))
                            Text(Money.format(state.afterDebt, c), style = MaterialTheme.typography.titleMedium, color = m.onHero)
                        }
                    }
                }
            }
        }

        item {
            Button(
                onClick = { nav.navigate(Routes.entry(TxnType.EXPENSE)) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = MaterialTheme.shapes.medium,
            ) {
                Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(8.dp))
                Text("Add Expense", style = MaterialTheme.typography.titleMedium)
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                QuickAction(Icons.Rounded.TrendingUp, m.income, "Income") { nav.navigate(Routes.entry(TxnType.INCOME)) }
                QuickAction(Icons.Rounded.AccountBalance, m.loan, "Take loan") { nav.navigate(Routes.loanForm()) }
                QuickAction(Icons.Rounded.Payments, m.loan, "Repay") { nav.navigate(Routes.repay()) }
                QuickAction(Icons.AutoMirrored.Rounded.ReceiptLong, MaterialTheme.colorScheme.primary, "History") { nav.goTab(Routes.TXNS) }
            }
        }

        // Period totals
        item {
            SectionTitle("This month · ${today.format(DateTimeFormatter.ofPattern("MMMM yyyy"))}")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Today's expenses", Money.format(todayTotals.expenses, c), Modifier.weight(1f), valueColor = m.expense)
                StatTile("Month's expenses", Money.format(month.expenses, c), Modifier.weight(1f), valueColor = m.expense)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Month's income", Money.format(month.income, c), Modifier.weight(1f), valueColor = m.income)
                StatTile(
                    "Loan repayments",
                    Money.format(month.repayments, c),
                    Modifier.weight(1f),
                    valueColor = m.loan,
                    caption = "Not counted as expenses",
                )
            }
        }

        item {
            SectionTitle("Spending by category")
            SectionCard {
                Column(Modifier.padding(16.dp)) {
                    if (slices.isEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(Icons.Rounded.PieChart, MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Text("No expenses this month yet. Your breakdown appears here as you add them.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        DonutWithLegend(slices, c, "This month", Money.number(month.expenses, c.decimals))
                    }
                }
            }
        }

        if (upcoming.isNotEmpty()) {
            item {
                SectionTitle("Upcoming loan due dates")
                SectionCard {
                    Column {
                        upcoming.forEachIndexed { i, s ->
                            if (i > 0) HorizontalDivider()
                            Row(
                                Modifier.fillMaxWidth().clickable { nav.navigate(Routes.loan(s.loan.id)) }.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                IconBadge(if (s.overdue) Icons.Rounded.Warning else Icons.Rounded.Event, if (s.overdue) m.expense else m.loan)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(s.loan.lender, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        (if (s.overdue) "Overdue · was due " else "Due ") + Dates.epochDay(s.loan.dueEpochDay!!),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (s.overdue) m.expense else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = if (s.overdue) FontWeight.SemiBold else null,
                                    )
                                }
                                Text(Money.format(s.outstanding, c), style = MaterialTheme.typography.titleSmall)
                            }
                        }
                    }
                }
            }
        }

        item {
            SectionTitle("Recent transactions") {
                if (recent.isNotEmpty()) TextButton(onClick = { nav.goTab(Routes.TXNS) }) { Text("See all") }
            }
        }
        if (recent.isEmpty()) {
            item {
                SectionCard {
                    EmptyState(
                        Icons.AutoMirrored.Rounded.ReceiptLong,
                        "No transactions yet",
                        "Tap \"Add Expense\" when you spend money, or use Add to record income or a loan.",
                    )
                }
            }
        } else {
            item {
                SectionCard {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        recent.forEach { t ->
                            TxnRow(t, t.categoryId?.let { state.categoryById[it] }, c, onClick = { nav.navigate(Routes.txn(t.id)) }, showDate = true)
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun QuickAction(icon: ImageVector, tint: Color, label: String, onClick: () -> Unit) {
    Column(
        Modifier.clickable(onClick = onClick).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconBadge(icon, tint, size = 48)
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}
