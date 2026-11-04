package com.lenzorium.expenses.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.lenzorium.expenses.backup.AutoBackup
import com.lenzorium.expenses.backup.BackupManager
import com.lenzorium.expenses.backup.BackupSecrets
import com.lenzorium.expenses.backup.PasswordNeededException
import com.lenzorium.expenses.backup.StagedRestore
import com.lenzorium.expenses.backup.WrongPasswordException
import com.lenzorium.expenses.data.Repository
import com.lenzorium.expenses.ui.LedgerState
import com.lenzorium.expenses.ui.components.Dates
import com.lenzorium.expenses.ui.components.ErrorBanner
import com.lenzorium.expenses.ui.components.MessageDialog
import com.lenzorium.expenses.ui.components.SectionCard
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Backup card on the More screen: manual backup (optionally password-protected), restore with preview, automatic backup. */
@Composable
fun BackupCard(state: LedgerState, repo: Repository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val s = state.settings
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<Pair<String, String>?>(null) }
    var askPasswordForCreate by remember { mutableStateOf(false) }
    var createPassword by remember { mutableStateOf<String?>(null) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var restorePasswordError by remember { mutableStateOf<String?>(null) }
    var askRestorePassword by remember { mutableStateOf(false) }
    var staged by remember { mutableStateOf<StagedRestore?>(null) }
    var autoDialog by remember { mutableStateOf(false) }
    val manager = remember(repo) { BackupManager(context, repo) }

    val createLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val pw = createPassword
        createPassword = null
        if (uri != null) scope.launch {
            busy = true
            message = manager.create(uri, pw).fold(
                {
                    "Backup saved" to "Your full backup ($it entries plus accounts, loans, budgets, bills, goals, settings and receipt photos) was saved." +
                        (if (pw != null) " It's protected with your password: keep the password safe, because without it the file can't be opened." else "") +
                        " Keep a copy off this phone, for example in Google Drive."
                },
                { "Backup failed" to (it.message ?: "Unknown error") },
            )
            busy = false
        }
    }

    fun inspect(uri: Uri, pw: String?) {
        scope.launch {
            busy = true
            manager.inspect(uri, pw).fold(
                { staged = it; askRestorePassword = false; restoreUri = null },
                { e ->
                    when (e) {
                        is PasswordNeededException -> { restoreUri = uri; restorePasswordError = null; askRestorePassword = true }
                        is WrongPasswordException -> { restoreUri = uri; restorePasswordError = e.message; askRestorePassword = true }
                        else -> message = "Can't restore this file" to (e.message ?: "Unknown error")
                    }
                },
            )
            busy = false
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) inspect(uri, null) }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            repo.settings.update { it.copy(autoBackupUri = uri.toString(), autoBackupLast = 0L) }
            autoDialog = true
        }
    }

    SectionCard {
        Column {
            Row2(Icons.Rounded.Backup, "Create full backup", "Everything, including receipt photos, in one file. Optional password.") { if (!busy) askPasswordForCreate = true }
            HorizontalDivider()
            Row2(Icons.Rounded.Restore, "Restore from backup", "See what's in the file before anything is replaced") {
                if (!busy) restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
            }
            HorizontalDivider()
            Row2(
                Icons.Rounded.CloudSync, "Automatic backup",
                if (s.autoBackupUri == null) "Off. Save a backup to a folder (e.g. Google Drive) every day or week"
                else (if (s.autoBackupDays == 1) "Daily" else "Weekly") + " to ${AutoBackup.folderLabel(context, s.autoBackupUri)}" +
                    (if (s.autoBackupLast > 0) " · last ${Dates.date(s.autoBackupLast)}" else " · not run yet") +
                    (if (BackupSecrets.hasPassword(context)) " · password-protected" else ""),
            ) { autoDialog = true }
            if (busy) Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(10.dp)); Text("Working…")
            }
        }
    }

    if (askPasswordForCreate) {
        PasswordChoiceDialog(
            title = "Protect this backup?",
            explain = "With a password, nobody can open the file without it, even if it's shared or your cloud account is accessed. If you forget the password, the backup can't be recovered.",
            allowNone = true,
            onDone = { pw ->
                askPasswordForCreate = false
                createPassword = pw
                val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))
                createLauncher.launch("lenzorium-backup-$stamp" + if (pw != null) ".lzbackup" else ".zip")
            },
            onDismiss = { askPasswordForCreate = false },
        )
    }

    if (askRestorePassword) {
        var pw by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { askRestorePassword = false; restoreUri = null },
            title = { Text("Backup password") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("This backup is protected. Enter the password used when it was made.", style = MaterialTheme.typography.bodyMedium)
                    PasswordField(pw, { pw = it }, "Password")
                    ErrorBanner(restorePasswordError)
                }
            },
            confirmButton = { TextButton(enabled = pw.isNotEmpty() && !busy, onClick = { restoreUri?.let { inspect(it, pw) } }) { Text("Open") } },
            dismissButton = { TextButton(onClick = { askRestorePassword = false; restoreUri = null }) { Text("Cancel") } },
        )
    }

    staged?.let { st ->
        val p = st.preview
        AlertDialog(
            onDismissRequest = { manager.discard(st); staged = null },
            title = { Text("Restore this backup?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Made ${if (p.createdAt > 0) Dates.dateTime(p.createdAt) else "on an unknown date"}" + if (p.encrypted) " · password-protected" else "", style = MaterialTheme.typography.titleSmall)
                    Text(
                        listOf(
                            "${p.entries} entries", "${p.accounts} accounts", "${p.loans} loans", "${p.budgets} budgets",
                            "${p.bills} bills", "${p.goals} goals", "${p.receipts} receipt photos",
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    HorizontalDivider()
                    Text(
                        "Everything currently in the app (${state.txns.size} entries) will be replaced. Tip: create a backup of your current data first.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    staged = null
                    scope.launch {
                        busy = true
                        message = manager.apply(st).fold(
                            { "Restore complete" to "Restored $it entries and everything else in the backup." },
                            { "Restore failed" to (it.message ?: "Unknown error") },
                        )
                        busy = false
                    }
                }) { Text("Replace and restore", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { manager.discard(st); staged = null }) { Text("Cancel") } },
        )
    }

    if (autoDialog) {
        var setPassword by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { autoDialog = false },
            title = { Text("Automatic backup") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "The app saves a full backup to a folder you choose. Pick a Google Drive folder (or another cloud app's folder) in the file picker and Android keeps it in your cloud; this app itself never connects to the internet. The newest 5 backups are kept.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(Modifier.fillMaxWidth().clickable { folderLauncher.launch(null) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Folder", style = MaterialTheme.typography.titleSmall)
                            Text(AutoBackup.folderLabel(context, s.autoBackupUri) ?: "Not chosen", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(if (s.autoBackupUri == null) "Choose" else "Change", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall)
                    }
                    if (s.autoBackupUri != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(s.autoBackupDays == 1, { repo.settings.update { it.copy(autoBackupDays = 1) } }, { Text("Daily") })
                            FilterChip(s.autoBackupDays == 7, { repo.settings.update { it.copy(autoBackupDays = 7) } }, { Text("Weekly") })
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Password protection", style = MaterialTheme.typography.titleSmall)
                                Text("Strongly recommended for cloud folders", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(BackupSecrets.hasPassword(context), { on -> if (on) setPassword = true else { BackupSecrets.save(context, null); repo.settings.update { it.copy() } } })
                        }
                        TextButton(enabled = !busy, onClick = {
                            scope.launch {
                                busy = true
                                message = AutoBackup.run(context, repo).fold({ "Backup saved" to "Saved as $it." }, { "Backup failed" to (it.message ?: "Unknown error") })
                                busy = false
                            }
                        }) { Text("Back up now") }
                        TextButton(onClick = {
                            s.autoBackupUri?.let { u -> runCatching { context.contentResolver.releasePersistableUriPermission(Uri.parse(u), Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } }
                            repo.settings.update { it.copy(autoBackupUri = null) }
                            autoDialog = false
                        }) { Text("Turn off automatic backup", color = MaterialTheme.colorScheme.error) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { autoDialog = false }) { Text("Done") } },
        )
        if (setPassword) {
            PasswordChoiceDialog(
                title = "Password for automatic backups",
                explain = "Every automatic backup will need this password to restore. It's stored on this phone in Android's secure key storage. If you forget it, those backups can't be opened.",
                allowNone = false,
                onDone = { pw -> pw?.let { BackupSecrets.save(context, it) }; repo.settings.update { it.copy() }; setPassword = false },
                onDismiss = { setPassword = false },
            )
        }
    }

    message?.let { (t, m) -> MessageDialog(t, m) { message = null } }
}

@Composable
private fun Row2(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun PasswordField(value: String, onChange: (String) -> Unit, label: String) {
    var show by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = { IconButton(onClick = { show = !show }) { Icon(if (show) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (show) "Hide password" else "Show password") } },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Asks for a new password twice. With [allowNone], "No password" returns null. */
@Composable
internal fun PasswordChoiceDialog(title: String, explain: String, allowNone: Boolean, onDone: (String?) -> Unit, onDismiss: () -> Unit) {
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(explain, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                PasswordField(a, { a = it; err = null }, "Password (at least 8 characters)")
                PasswordField(b, { b = it; err = null }, "Repeat password")
                ErrorBanner(err)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    a.length < 8 -> err = "Use at least 8 characters."
                    a != b -> err = "The passwords don't match."
                    else -> onDone(a)
                }
            }) { Text("Use password") }
        },
        dismissButton = {
            Row {
                if (allowNone) TextButton(onClick = { onDone(null) }) { Text("No password") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
