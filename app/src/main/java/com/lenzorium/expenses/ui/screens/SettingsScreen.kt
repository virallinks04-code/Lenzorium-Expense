package com.lenzorium.expenses.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.DonutLarge
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material.icons.rounded.Shield
import com.lenzorium.expenses.ui.PrivacyText
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lenzorium.expenses.BuildConfig
import com.lenzorium.expenses.backup.BackupManager
import com.lenzorium.expenses.data.Repository
import com.lenzorium.expenses.data.ThemeMode
import com.lenzorium.expenses.domain.Currencies
import com.lenzorium.expenses.domain.Money
import com.lenzorium.expenses.ui.Biometrics
import com.lenzorium.expenses.ui.LedgerState
import com.lenzorium.expenses.ui.Routes
import com.lenzorium.expenses.ui.components.AmountField
import com.lenzorium.expenses.ui.components.ConfirmDialog
import com.lenzorium.expenses.ui.components.DatePickDialog
import com.lenzorium.expenses.ui.components.Dates
import com.lenzorium.expenses.ui.components.ErrorBanner
import com.lenzorium.expenses.ui.components.MessageDialog
import com.lenzorium.expenses.ui.components.ScreenScaffold
import com.lenzorium.expenses.ui.components.SectionCard
import com.lenzorium.expenses.ui.components.SectionTitle
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun SettingsScreen(state: LedgerState, repo: Repository, nav: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val s = state.settings
    val c = state.currency
    var dialog by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<Pair<String, String>?>(null) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var busy by remember { mutableStateOf(false) }
    val askNotify = com.lenzorium.expenses.ui.components.rememberNotificationAsk()

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            busy = true
            message = BackupManager(context, repo).create(uri).fold(
                { "Backup saved" to "Your full backup ($it transactions, loans, categories, settings and receipt photos) was saved. Keep it somewhere safe, such as Google Drive." },
                { "Backup failed" to (it.message ?: "Unknown error") },
            )
            busy = false
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> restoreUri = uri }

    ScreenScaffold("More", nav = null) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { SectionTitle("Plan") }
            item {
                SectionCard {
                    Column {
                        val ob = state.overallBudget
                        SettingRow(
                            Icons.Rounded.DonutLarge, "Budgets",
                            when {
                                ob != null -> "${Money.format(ob.remaining.coerceAtLeast(0), c)} left this month"
                                state.budgets.isNotEmpty() -> "${state.budgets.size} category budget${if (state.budgets.size > 1) "s" else ""}"
                                else -> "Set monthly spending limits"
                            },
                        ) { nav.navigate(PlanRoutes.BUDGETS) }
                        HorizontalDivider()
                        SettingRow(
                            Icons.Rounded.EventRepeat, "Bills & regular income",
                            when {
                                state.dueNow.isNotEmpty() -> "${state.dueNow.size} due now"
                                state.recurring.isNotEmpty() -> "${state.recurring.count { it.active }} active"
                                else -> "Reminders for rent, utilities, subscriptions"
                            },
                        ) { nav.navigate(PlanRoutes.BILLS) }
                        HorizontalDivider()
                        SettingRow(Icons.Rounded.Flag, "Savings goals", if (state.goalProgress.isEmpty()) "Save step by step for something" else "${Money.format(state.setAside, c)} set aside") { nav.navigate(PlanRoutes.GOALS) }
                        HorizontalDivider()
                        SettingRow(Icons.Rounded.AccountBalance, "Loans", state.debt.takeIf { it > 0 }?.let { "${Money.format(it, c)} outstanding" } ?: "Money you borrowed") { nav.navigate(Routes.LOANS) }
                        HorizontalDivider()
                        SettingRow(Icons.Rounded.Bolt, "Shortcuts", if (state.templates.isEmpty()) "One-tap entries for things you buy often" else "${state.templates.size} saved") { nav.navigate(PlanRoutes.SHORTCUTS) }
                    }
                }
            }
            item { SectionTitle("Ledger") }
            item {
                SectionCard {
                    Column {
                        SettingRow(Icons.Rounded.Payments, "Currency", "${c.code} · ${c.name}") { dialog = "currency" }
                        HorizontalDivider()
                        SettingRow(
                            Icons.Rounded.AccountBalanceWallet, "Accounts",
                            state.activeAccounts.joinToString(" · ") { "${it.name} ${Money.format(state.balanceOf(it.id), c)}" }.ifBlank { "Cash, bank and wallets" },
                        ) { nav.navigate(com.lenzorium.expenses.ui.Routes.ACCOUNTS) }
                        HorizontalDivider()
                        SettingRow(Icons.Rounded.CalendarToday, "Tracking start date", Dates.epochDay(s.trackingStartEpochDay)) { dialog = "start" }
                        HorizontalDivider()
                        SettingRow(Icons.Rounded.Category, "Categories", "Add, rename or remove categories") { nav.navigate(Routes.CATEGORIES) }
                    }
                }
            }
            item { SectionTitle("Display") }
            item {
                SectionCard {
                    Column {
                        SettingRow(Icons.Rounded.DarkMode, "Theme", when (s.themeMode) { ThemeMode.SYSTEM -> "Follow system"; ThemeMode.LIGHT -> "Light"; ThemeMode.DARK -> "Dark" }) { dialog = "theme" }
                        HorizontalDivider()
                        SwitchRow(Icons.Rounded.Visibility, "Show \"Balance after clearing debt\"", "Available balance − outstanding debt, on Home", s.showAfterDebt) { v -> repo.settings.update { it.copy(showAfterDebt = v) } }
                    }
                }
            }
            item { SectionTitle("Notifications") }
            item {
                SectionCard {
                    Column {
                        SwitchRow(Icons.Rounded.NotificationsActive, "Bill reminders", "Around 9am when a bill is due. No amounts are shown.", s.remindersEnabled) { v ->
                            if (v) askNotify()
                            repo.settings.update { it.copy(remindersEnabled = v) }
                        }
                        HorizontalDivider()
                        SwitchRow(Icons.Rounded.DonutLarge, "Budget warnings", "When a budget reaches 80% and when it's used up", s.budgetAlerts) { v ->
                            if (v) askNotify()
                            repo.settings.update { it.copy(budgetAlerts = v) }
                        }
                    }
                }
            }
            item { SectionTitle("Security") }
            item {
                SectionCard {
                    Column {
                        SwitchRow(Icons.Rounded.Lock, "PIN lock", if (s.lockEnabled) "Asks for your PIN when the app opens" else "Off", s.lockEnabled) { on ->
                            if (on) dialog = "pin" else dialog = "pinOff"
                        }
                        if (s.lockEnabled) {
                            HorizontalDivider()
                            SettingRow(Icons.Rounded.Lock, "Change PIN", null) { dialog = "pin" }
                            if (Biometrics.isAvailable(context)) {
                                HorizontalDivider()
                                SwitchRow(Icons.Rounded.Fingerprint, "Fingerprint / face unlock", "Your PIN still works as a backup", s.biometricEnabled) { on ->
                                    if (on) Biometrics.prompt(context, "Confirm to enable", onSuccess = { repo.settings.update { it.copy(biometricEnabled = true) } })
                                    else repo.settings.update { it.copy(biometricEnabled = false) }
                                }
                            }
                        }
                    }
                }
            }
            item { SectionTitle("Backup") }
            item {
                SectionCard {
                    Column {
                        SettingRow(Icons.Rounded.Backup, "Create full backup", "Transactions, loans, categories, settings and receipt photos in one file") {
                            if (!busy) backupLauncher.launch("lenzorium-backup-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")) + ".zip")
                        }
                        HorizontalDivider()
                        SettingRow(Icons.Rounded.Restore, "Restore from backup", "Replaces the data on this phone with a backup file") {
                            if (!busy) restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                        }
                    }
                }
                Text(
                    "Report exports (PDF/CSV) on the Reports screen are not backups and can't be restored.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                )
            }
            item { SectionTitle("Your data") }
            item {
                SectionCard {
                    Column {
                        SettingRow(Icons.Rounded.Shield, "How your data is kept safe", "Stored on this phone · Google backup · backup files") { dialog = "data" }
                        HorizontalDivider()
                        SettingRow(Icons.Rounded.Policy, "Privacy policy", "No account, no ads, no tracking") { dialog = "privacy" }
                    }
                }
            }
            item {
                Column(
                    Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    com.lenzorium.expenses.ui.components.BrandMark(48)
                    Spacer(Modifier.padding(4.dp))
                    Text("Lenzorium Expenses", style = MaterialTheme.typography.titleSmall)
                    Text("Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.padding(4.dp))
                    Text(
                        "Cash, bank and card are labels for how you paid, not separate account balances. Credit-card limits and card debt aren't tracked.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
            }
        }
    }

    when (dialog) {
        "data" -> InfoDialog(
            "How your data is kept safe",
            listOf(
                "Saved on this phone" to "Every entry is written to the app's private database the moment you tap Save. It stays after you close the app, restart the phone or install an update.",
                "Google backup" to "If Android backup is on (Settings › Google › Backup), your records are included in your phone's encrypted Google backup and come back automatically when you sign in on a new phone and reinstall the app.",
                "Backup file" to "Create full backup saves everything, including receipt photos, to one file you control. Keep a copy somewhere other than this phone, such as Google Drive.",
                "When data is lost" to "Uninstalling the app or using \"Clear storage\" deletes the records on this phone. Restore them from Google backup or from your backup file.",
            ),
        ) { dialog = null }
        "privacy" -> InfoDialog("Privacy policy", PrivacyText.sections, footer = "Last updated ${PrivacyText.UPDATED}") { dialog = null }
        "currency" -> CurrencyDialog(state, repo, onDone = { dialog = null })
        "start" -> DatePickDialog(
            LocalDate.ofEpochDay(s.trackingStartEpochDay),
            onPick = { d ->
                scope.launch {
                    if (repo.canMoveTrackingStart(d.toEpochDay())) repo.settings.update { it.copy(trackingStartEpochDay = d.toEpochDay()) }
                    else message = "Can't change start date" to "Some entries are dated before ${Dates.date(d)}. Edit or delete them first, or choose an earlier date."
                }
            },
            onDismiss = { dialog = null },
            maxDate = LocalDate.now(),
        )
        "theme" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Theme") },
            text = {
                Column {
                    listOf(ThemeMode.SYSTEM to "Follow system", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark").forEach { (mode, label) ->
                        Row(
                            Modifier.fillMaxWidth().clickable { repo.settings.update { it.copy(themeMode = mode) }; dialog = null },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = s.themeMode == mode, onClick = { repo.settings.update { it.copy(themeMode = mode) }; dialog = null })
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text("Close") } },
        )
        "pin" -> SetPinDialog(onSet = { pin -> repo.settings.setPin(pin); dialog = null }, onDismiss = { dialog = null })
        "pinOff" -> ConfirmDialog(
            "Turn off PIN lock?", "Anyone with your phone will be able to open the app.", "Turn off",
            onConfirm = { repo.settings.clearPin(); dialog = null }, onDismiss = { dialog = null },
        )
    }
    restoreUri?.let { uri ->
        ConfirmDialog(
            title = "Restore from backup?",
            message = "Everything currently in the app will be replaced by the backup's transactions, loans, categories, settings and receipts. " +
                "If the backup can't be read, nothing is changed.\n\nTip: create a backup of your current data first.",
            confirmLabel = "Replace and restore",
            destructive = true,
            onConfirm = {
                restoreUri = null
                scope.launch {
                    busy = true
                    message = BackupManager(context, repo).restore(uri).fold(
                        { "Restore complete" to "Restored $it transactions." },
                        { "Restore failed" to (it.message ?: "Unknown error") },
                    )
                    busy = false
                }
            },
            onDismiss = { restoreUri = null },
        )
    }
    message?.let { (t, m) -> MessageDialog(t, m) { message = null } }
}

@Composable
private fun InfoDialog(title: String, sections: List<Pair<String, String>>, footer: String? = null, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                sections.forEach { (h, b) ->
                    Column {
                        Text(h, style = MaterialTheme.typography.titleSmall)
                        Text(b, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                footer?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SwitchRow(icon: ImageVector, title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun CurrencyDialog(state: LedgerState, repo: Repository, onDone: () -> Unit) {
    val hasData = state.txns.isNotEmpty() || state.loans.isNotEmpty() || state.totalOpening != 0L
    val current = state.currency
    val options = if (hasData) Currencies.all.filter { it.decimals == current.decimals } else Currencies.all
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Currency") },
        text = {
            Column {
                if (hasData) Text(
                    "Amounts are not converted — only the currency label changes. Currencies with a different number of decimal places aren't offered once you have data.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(options, key = { it.code }) { cur ->
                        Row(
                            Modifier.fillMaxWidth().clickable { repo.settings.update { it.copy(currencyCode = cur.code) }; onDone() }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = cur.code == current.code, onClick = { repo.settings.update { it.copy(currencyCode = cur.code) }; onDone() })
                            Text("${cur.code} · ${cur.name}")
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text("Close") } },
    )
}

@Composable
private fun SetPinDialog(onSet: (String) -> Unit, onDismiss: () -> Unit) {
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set PIN") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PinField(a, { a = it }, "New PIN (4–8 digits)")
                PinField(b, { b = it }, "Repeat PIN")
                ErrorBanner(err)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    a.length !in 4..8 -> err = "PIN must be 4 to 8 digits."
                    a != b -> err = "The two PINs don't match."
                    else -> onSet(a)
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
