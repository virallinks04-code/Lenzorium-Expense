package com.lenzorium.expenses.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lenzorium.expenses.backup.BackupSecrets
import com.lenzorium.expenses.cloud.DriveBackup
import com.lenzorium.expenses.cloud.GoogleDrive
import com.lenzorium.expenses.data.Repository
import com.lenzorium.expenses.ui.LedgerState
import com.lenzorium.expenses.ui.LocalMessenger
import com.lenzorium.expenses.ui.components.Dates
import com.lenzorium.expenses.ui.components.ErrorBanner
import com.lenzorium.expenses.ui.components.TextInput
import kotlinx.coroutines.launch

enum class ResetKind { ENTRIES, EVERYTHING }

/**
 * Confirms and performs "Clear all entries" or "Reset app". The user must type DELETE, and is reminded to
 * back up first. For a full reset, they can also delete their Google Drive backups and sign out.
 */
@Composable
fun ResetDialog(state: LedgerState, repo: Repository, kind: ResetKind, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val messenger = LocalMessenger.current
    val s = state.settings
    val full = kind == ResetKind.EVERYTHING
    var typed by remember { mutableStateOf("") }
    var alsoDrive by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun wipe() {
        if (full) {
            // Forget phone-only secrets and the automatic-backup folder too.
            BackupSecrets.save(ctx, null)
            s.autoBackupUri?.let { u ->
                runCatching { ctx.contentResolver.releasePersistableUriPermission(Uri.parse(u), Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            }
            repo.resetAll()
        } else {
            repo.clearEntries()
            messenger.show("All entries deleted. Your accounts, categories and settings are kept.")
        }
    }

    val auth = rememberGoogleAuth(
        onToken = { token ->
            try {
                DriveBackup.deleteAll(token)
                GoogleDrive.revoke(token)
                wipe(); onDone()
            } catch (e: Exception) {
                error = (e.message ?: "Couldn't reach Google Drive.") + " Nothing was deleted."; busy = false
            }
        },
        onError = { error = "$it Nothing was deleted."; busy = false },
    )

    AlertDialog(
        onDismissRequest = { if (!busy) onDone() },
        title = { Text(if (full) "Reset the app?" else "Clear all entries?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (full) {
                    Text("This permanently deletes everything on this phone:", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "• ${state.txns.size} entries, all loans and receipt photos\n• Accounts, categories, budgets, bills, goals and shortcuts\n• Your profile, PIN and all settings",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text("The app will start again from the welcome screen.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text("This permanently deletes ${state.txns.size} entries, all loans, receipt photos and money set aside for goals.", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Kept: your accounts and their opening balances, categories, budgets, bills, shortcuts, goals, profile and settings.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val last = maxOf(s.lastBackupAt, s.driveLast, s.autoBackupLast)
                Text(
                    if (last > 0) "Last backup: ${Dates.dateTime(last)}. Make a new one first (More › Backup) if you might want this data back."
                    else "You have no backup. Make one first (More › Backup) if you might want this data back.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold,
                )
                if (full && s.googleEmail != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(alsoDrive, { alsoDrive = it })
                        Column {
                            Text("Also delete my Google Drive backups", style = MaterialTheme.typography.titleSmall)
                            Text("Otherwise they stay in ${s.googleEmail} and you can restore them later.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                TextInput(typed, { typed = it }, "Type DELETE to confirm")
                ErrorBanner(error)
                if (busy) Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(10.dp)); Text("Deleting…")
                }
            }
        },
        confirmButton = {
            TextButton(enabled = typed.trim().equals("DELETE", ignoreCase = true) && !busy, onClick = {
                busy = true; error = null
                if (full && alsoDrive && s.googleEmail != null) auth()
                else scope.launch {
                    runCatching { wipe() }.onSuccess { onDone() }.onFailure { error = it.message ?: "Couldn't delete."; busy = false }
                }
            }) { Text(if (full) "Reset app" else "Delete entries", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDone) { Text("Cancel") } },
    )
}
