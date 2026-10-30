package com.lenzorium.expenses.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lenzorium.expenses.data.Repository
import com.lenzorium.expenses.domain.Money
import com.lenzorium.expenses.domain.Period
import com.lenzorium.expenses.domain.PeriodKind
import com.lenzorium.expenses.domain.ReportBuilder
import com.lenzorium.expenses.export.ReportExporter
import com.lenzorium.expenses.ui.LedgerState
import com.lenzorium.expenses.ui.components.DonutWithLegend
import com.lenzorium.expenses.ui.components.EmptyState
import com.lenzorium.expenses.ui.components.HorizontalBars
import com.lenzorium.expenses.ui.components.IncomeVsExpense
import com.lenzorium.expenses.ui.components.MessageDialog
import com.lenzorium.expenses.ui.components.ScreenScaffold
import com.lenzorium.expenses.ui.components.SectionCard
import com.lenzorium.expenses.ui.components.SectionTitle
import com.lenzorium.expenses.ui.components.Slice
import com.lenzorium.expenses.ui.components.StatTile
import com.lenzorium.expenses.ui.components.TrendBars
import com.lenzorium.expenses.ui.theme.LocalMoneyColors
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun ReportsScreen(state: LedgerState, repo: Repository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = state.currency
    val m = LocalMoneyColors.current
    var kindName by rememberSaveable { mutableStateOf(PeriodKind.MONTH.name) }
    var startDay by rememberSaveable { mutableStateOf(LocalDate.now().withDayOfMonth(1).toEpochDay()) }
    var endDay by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    var customDialog by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val kind = PeriodKind.valueOf(kindName)
    val period = if (kind == PeriodKind.CUSTOM) Period(kind, LocalDate.ofEpochDay(startDay), LocalDate.ofEpochDay(endDay))
    else Period.of(kind, LocalDate.ofEpochDay(startDay))

    val report = remember(period, state.txns, state.loans, state.categories, state.settings.openingBalanceMinor) {
        ReportBuilder.build(period, state.settings.openingBalanceMinor, state.txns, state.loans, state.categories)
    }
    fun setPeriod(p: Period) { kindName = p.kind.name; startDay = p.start.toEpochDay(); endDay = p.endInclusive.toEpochDay() }

    val baseName = "lenzorium-report-${period.start}-to-${period.endInclusive}"
    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) scope.launch {
            message = ReportExporter.writePdf(context, uri, report, c, state.categories, state.loans)
                .fold({ "PDF report saved." }, { "Couldn't save the PDF: ${it.message}" })
        }
    }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            message = ReportExporter.writeCsv(context, uri, report, c, state.categories, state.loans)
                .fold({ "CSV report saved." }, { "Couldn't save the CSV: ${it.message}" })
        }
    }

    ScreenScaffold("Reports", nav = null) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    PeriodKind.entries.forEachIndexed { i, k ->
                        SegmentedButton(
                            selected = k == kind,
                            onClick = {
                                if (k == PeriodKind.CUSTOM) customDialog = true
                                else {
                                    val today = LocalDate.now()
                                    val containsToday = !today.isBefore(period.start) && !today.isAfter(period.endInclusive)
                                    setPeriod(Period.of(k, if (containsToday || kind == PeriodKind.CUSTOM) today else period.start.coerceAtMostToday()))
                                }
                            },
                            shape = SegmentedButtonDefaults.itemShape(i, PeriodKind.entries.size),
                        ) { Text(k.label, maxLines = 1) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    IconButton(onClick = { setPeriod(period.shift(-1)) }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Previous period") }
                    TextButton(onClick = { if (kind == PeriodKind.CUSTOM) customDialog = true }, modifier = Modifier.weight(1f)) {
                        Text(period.label, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                    }
                    IconButton(onClick = { setPeriod(period.shift(1)) }, enabled = period.endInclusive.isBefore(LocalDate.now())) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Next period")
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("Total income", Money.format(report.totals.income, c), Modifier.weight(1f), valueColor = m.income)
                    StatTile("Total expenses", Money.format(report.totals.expenses, c), Modifier.weight(1f), valueColor = m.expense)
                }
            }
            item {
                SectionCard {
                    Column(Modifier.padding(16.dp)) {
                        Text("Available balance", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        KV("Opening (start of ${com.lenzorium.expenses.ui.components.Dates.short(period.start)})", Money.format(report.openingBalance, c))
                        KV("+ Income", Money.format(report.totals.income, c))
                        KV("+ Money borrowed", Money.format(report.totals.borrowed, c))
                        KV("− Expenses", Money.format(report.totals.expenses, c))
                        KV("− Loan repayments", Money.format(report.totals.repayments, c))
                        HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        KV("Closing (end of ${com.lenzorium.expenses.ui.components.Dates.short(period.endInclusive)})", Money.format(report.closingBalance, c), bold = true)
                    }
                }
            }

            item {
                SectionTitle("Spending by category")
                SectionCard {
                    Column(Modifier.padding(16.dp)) {
                        if (report.expenseByCategory.isEmpty()) {
                            Text("No expenses in this period.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            val slices = report.expenseByCategory.map { Slice(it.name, it.amount, Color(it.colorArgb)) }
                            DonutWithLegend(slices, c, "Spent", Money.number(report.totals.expenses, c.decimals))
                            Spacer(Modifier.height(16.dp))
                            HorizontalBars(slices, c)
                        }
                    }
                }
            }

            item {
                SectionTitle("Income vs expenses")
                SectionCard { Column(Modifier.padding(16.dp)) { IncomeVsExpense(report.totals.income, report.totals.expenses, m.income, m.expense, c) } }
            }

            item {
                SectionTitle("Spending trend · ${report.trendUnit}")
                SectionCard {
                    Column(Modifier.padding(16.dp)) {
                        if (report.trendValues.all { it == 0L }) Text("No spending to chart.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else TrendBars(report.trendValues, report.trendLabels, m.expense)
                    }
                }
            }

            if (report.incomeByCategory.isNotEmpty()) {
                item {
                    SectionTitle("Income by category")
                    SectionCard { Column(Modifier.padding(16.dp)) { HorizontalBars(report.incomeByCategory.map { Slice(it.name, it.amount, Color(it.colorArgb)) }, c) } }
                }
            }

            item {
                SectionTitle("Borrowing and repayments")
                SectionCard {
                    Column(Modifier.padding(16.dp)) {
                        Text("Kept separate: borrowing isn't income and repayments aren't expenses.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        KV("Money borrowed", Money.format(report.totals.borrowed, c))
                        KV("Loan repayments", Money.format(report.totals.repayments, c))
                        if (report.totals.charges > 0) KV("Fees / interest added to debt", Money.format(report.totals.charges, c))
                        HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        KV("Debt as of ${com.lenzorium.expenses.ui.components.Dates.date(period.endInclusive)}", Money.format(report.debtAtEnd, c), bold = true)
                        KV("Debt now (current)", Money.format(report.debtNow, c))
                    }
                }
            }

            item {
                SectionTitle("Debt by lender")
                SectionCard {
                    Column(Modifier.padding(16.dp)) {
                        if (report.lenders.isEmpty()) {
                            Text("No outstanding loans.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Row {
                                Text("Lender", Modifier.weight(1.3f), style = MaterialTheme.typography.labelMedium)
                                Text("As of period end", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.End)
                                Text("Current", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.End)
                            }
                            report.lenders.forEach { l ->
                                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                                Row {
                                    Text(l.lender + if (l.loans > 1) " (${l.loans})" else "", Modifier.weight(1.3f), style = MaterialTheme.typography.bodyMedium)
                                    Text(Money.number(l.atPeriodEnd, c.decimals), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End)
                                    Text(Money.number(l.current, c.decimals), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text("Amounts in ${c.code}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            item {
                SectionTitle("Export this report")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { pdfLauncher.launch("$baseName.pdf") }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.PictureAsPdf, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("PDF")
                    }
                    OutlinedButton(onClick = { csvLauncher.launch("$baseName.csv") }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Description, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("CSV")
                    }
                }
                Text(
                    "Exports are reports for reading or sharing. They are not backups — use Settings › Backup to save everything.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            if (state.txns.isEmpty()) {
                item { EmptyState(Icons.Rounded.BarChart, "Reports fill in as you go", "Add expenses and income to see where your money goes.") }
            }
        }
    }

    if (customDialog) {
        DateRangeDialog(
            LocalDate.ofEpochDay(startDay), LocalDate.ofEpochDay(endDay),
            onDone = { a, b ->
                customDialog = false
                val s = a ?: b ?: LocalDate.now()
                val e = b ?: a ?: LocalDate.now()
                setPeriod(Period(PeriodKind.CUSTOM, s, e))
            },
            onDismiss = { customDialog = false },
        )
    }
    message?.let { MessageDialog("Export", it) { message = null } }
}

private fun LocalDate.coerceAtMostToday(): LocalDate = if (isAfter(LocalDate.now())) LocalDate.now() else this

@Composable
private fun KV(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = if (bold) FontWeight.SemiBold else null)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = if (bold) FontWeight.SemiBold else null)
    }
}
