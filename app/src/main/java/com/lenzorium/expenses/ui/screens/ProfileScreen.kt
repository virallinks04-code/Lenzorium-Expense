package com.lenzorium.expenses.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.lenzorium.expenses.backup.BackupManager
import com.lenzorium.expenses.backup.BackupSecrets
import com.lenzorium.expenses.backup.PasswordNeededException
import com.lenzorium.expenses.backup.StagedRestore
import com.lenzorium.expenses.backup.WrongPasswordException
import com.lenzorium.expenses.cloud.DriveBackup
import com.lenzorium.expenses.cloud.DriveFile
import com.lenzorium.expenses.cloud.GoogleDrive
import com.lenzorium.expenses.cloud.NeedsUserApproval
import com.lenzorium.expenses.data.Repository
import com.lenzorium.expenses.ui.LedgerState
import com.lenzorium.expenses.ui.LocalMessenger
import com.lenzorium.expenses.ui.components.ConfirmDialog
import com.lenzorium.expenses.ui.components.Dates
import com.lenzorium.expenses.ui.components.ErrorBanner
import com.lenzorium.expenses.ui.components.MessageDialog
import com.lenzorium.expenses.ui.components.ScreenScaffold
import com.lenzorium.expenses.ui.components.SectionCard
import com.lenzorium.expenses.ui.components.SectionTitle
import com.lenzorium.expenses.ui.components.TextInput
import com.lenzorium.expenses.ui.theme.Brand
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Runs [onToken] with a Google access token, showing Google's account picker / consent when needed. */
@Composable
fun rememberGoogleAuth(onToken: suspend (String) -> Unit, onError: (String) -> Unit): () -> Unit {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            scope.launch {
                runCatching { GoogleDrive.tokenFromResult(ctx, res.data) }
                    .onSuccess { onToken(it) }
                    .onFailure { onError(it.message ?: "Sign-in failed.") }
            }
        } else onError("Sign-in was cancelled.")
    }
    return {
        scope.launch {
            try {
                onToken(GoogleDrive.token(ctx))
            } catch (e: NeedsUserApproval) {
                launcher.launch(IntentSenderRequest.Builder(e.pendingIntent).build())
            } catch (e: Exception) {
                onError(e.message ?: "Couldn't connect to Google.")
            }
        }
    }
}

/** Round avatar: Google photo, or initials on brand blue. */
@Composable
fun Avatar(name: String, photo: String?, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(CircleShape).background(Brand.BlueDeep), contentAlignment = Alignment.Center) {
        val initials = name.split(' ', '.', '_').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
        if (initials.isNotEmpty()) Text(initials, color = Color.White, style = if (size >= 64.dp) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleSmall)
        else Icon(Icons.Rounded.AccountCircle, null, tint = Color.White, modifier = Modifier.size(size * 0.7f))
        if (photo != null) AsyncImage(photo, "Profile photo", contentScale = ContentScale.Crop, modifier = Modifier.size(size).clip(CircleShape))
    }
}

@Composable
fun ProfileScreen(state: LedgerState, repo: Repository, nav: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val messenger = LocalMessenger.current
    val s = state.settings
    var busy by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var editName by remember { mutableStateOf(false) }
    var restoreOpen by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }
    var confirmDeleteCloud by remember { mutableStateOf(false) }
    var setPassword by remember { mutableStateOf(false) }
    var offerRestore by remember { mutableStateOf<List<DriveFile>?>(null) }
    var pendingAction by remember { mutableStateOf<String?>(null) }

    val auth = rememberGoogleAuth(
        onToken = { token ->
            val action = pendingAction ?: "signin"
            pendingAction = null
            try {
                when (action) {
                    "signin" -> {
                        busy = "Signing in…"
                        val p = GoogleDrive.profile(token)
                        repo.settings.update { it.copy(googleEmail = p.email, googlePhoto = p.photoUrl, profileName = it.profileName.ifBlank { p.name }) }
                        val existing = DriveBackup.backups(token)
                        if (existing.isNotEmpty() && state.txns.size < 3) offerRestore = existing
                        else {
                            busy = "Backing up to Google Drive…"
                            DriveBackup.backupNow(ctx, repo, token)
                            messenger.show("Signed in. Your data is backed up to Google Drive.")
                        }
                    }
                    "backup" -> { busy = "Backing up to Google Drive…"; DriveBackup.backupNow(ctx, repo, token); messenger.show("Backed up to Google Drive") }
                    "restore" -> restoreOpen = true
                    "deleteCloud" -> { busy = "Deleting…"; val n = DriveBackup.deleteAll(token); messenger.show("Deleted $n backups from Google Drive") }
                    "signout" -> {
                        GoogleDrive.revoke(token)
                        repo.settings.update { it.copy(googleEmail = null, googlePhoto = null) }
                        messenger.show("Signed out. Your data stays on this phone.")
                    }
                }
            } catch (e: Exception) {
                error = e.message ?: "Something went wrong."
            }
            busy = null
        },
        onError = { msg ->
            if (pendingAction == "signout") { repo.settings.update { it.copy(googleEmail = null, googlePhoto = null) }; messenger.show("Signed out.") }
            else error = msg
            pendingAction = null; busy = null
        },
    )
    fun run(action: String) { error = null; pendingAction = action; auth() }

    val start = LocalDate.ofEpochDay(s.trackingStartEpochDay)
    val days = ChronoUnit.DAYS.between(start, LocalDate.now()) + 1
    val name = s.profileName.ifBlank { "Your profile" }

    ScreenScaffold("Profile", nav) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                SectionCard {
                    Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Avatar(s.profileName, s.googlePhoto, 88.dp)
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(name, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            IconButton(onClick = { editName = true }) { Icon(Icons.Rounded.Edit, "Edit name", Modifier.size(20.dp)) }
                        }
                        Text(s.googleEmail ?: "Not signed in · data on this phone only", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(16.dp))
                        Row(Modifier.fillMaxWidth()) {
                            Stat("${state.txns.size}", "entries", Modifier.weight(1f))
                            Stat("${state.activeAccounts.size}", "accounts", Modifier.weight(1f))
                            Stat("$days", if (days == 1L) "day tracked" else "days tracked", Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Tracking since ${Dates.date(start)} · ${state.currency.code}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            item { SectionTitle("Google account") }
            item {
                SectionCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (s.googleEmail == null) {
                            Text("Sign in to keep your data safe", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Your records are backed up to a private folder in your own Google Drive that only this app can open. Lose or change your phone? Sign in and everything comes back. Lenzorium has no servers and never sees your data.",
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Button(onClick = { run("signin") }, enabled = busy == null, modifier = Modifier.fillMaxWidth().height(52.dp), shape = MaterialTheme.shapes.medium) {
                                Icon(Icons.Rounded.AccountCircle, null); Spacer(Modifier.width(8.dp)); Text("Sign in with Google", style = MaterialTheme.typography.titleMedium)
                            }
                            Text("Signing in is optional. The app works fully without it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.CloudDone, null, tint = Brand.Positive)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Google Drive backup", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        if (s.driveLast > 0) "Last backup ${Dates.dateTime(s.driveLast)}" else "No backup yet",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(s.driveBackup, { v -> repo.settings.update { it.copy(driveBackup = v) } })
                            }
                            if (s.driveBackup) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(s.autoBackupDays == 1, { repo.settings.update { it.copy(autoBackupDays = 1) } }, { Text("Daily") })
                                FilterChip(s.autoBackupDays == 7, { repo.settings.update { it.copy(autoBackupDays = 7) } }, { Text("Weekly") })
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { run("backup") }, enabled = busy == null, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Rounded.CloudUpload, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Back up now", maxLines = 1)
                                }
                                OutlinedButton(onClick = { run("restore") }, enabled = busy == null, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Rounded.CloudDownload, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Restore", maxLines = 1)
                                }
                            }
                            HorizontalDivider()
                            Row(Modifier.fillMaxWidth().clickable { if (BackupSecrets.hasPassword(ctx)) { BackupSecrets.save(ctx, null); repo.settings.update { it.copy() } } else setPassword = true }, verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Backup password", style = MaterialTheme.typography.titleSmall)
                                    Text("Extra protection: even with access to your Google account, backups can't be opened without it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(BackupSecrets.hasPassword(ctx), { on -> if (on) setPassword = true else { BackupSecrets.save(ctx, null); repo.settings.update { it.copy() } } })
                            }
                        }
                        busy?.let {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(10.dp)); Text(it)
                            }
                        }
                        ErrorBanner(error)
                    }
                }
            }

            item { SectionTitle("Privacy and security") }
            item {
                SectionCard {
                    Column {
                        ProfileRow(Icons.Rounded.Lock, "App lock", if (s.lockEnabled) "PIN" + (if (s.biometricEnabled) " and fingerprint / face" else "") + " is on" else "Off. Turn on in More › Security") { nav.navigate(com.lenzorium.expenses.ui.Routes.SETTINGS) }
                        HorizontalDivider()
                        ProfileRow(Icons.Rounded.Shield, "Where your data is", if (s.googleEmail != null) "On this phone, and backed up to your own Google Drive" else "Only on this phone (plus your own backup files)") { }
                        if (s.googleEmail != null) {
                            HorizontalDivider()
                            ProfileRow(Icons.Rounded.DeleteForever, "Delete my Google Drive backups", "Removes every backup this app saved to your Drive", destructive = true) { confirmDeleteCloud = true }
                            HorizontalDivider()
                            ProfileRow(Icons.Rounded.Logout, "Sign out", "Stops Google Drive backups. Your data stays on this phone.") { confirmSignOut = true }
                        }
                    }
                }
            }
        }
    }

    if (editName) {
        var v by remember { mutableStateOf(s.profileName) }
        AlertDialog(
            onDismissRequest = { editName = false },
            title = { Text("Your name") },
            text = { TextInput(v, { v = it.take(40) }, "Name", placeholder = "Shown on Home") },
            confirmButton = { TextButton(onClick = { repo.settings.update { it.copy(profileName = v.trim()) }; editName = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { editName = false }) { Text("Cancel") } },
        )
    }
    if (setPassword) {
        PasswordChoiceDialog(
            title = "Backup password",
            explain = "Every new backup (Google Drive and automatic folder backups) will need this password to restore. It's kept in this phone's secure key storage. If you forget it, those backups can't be opened by anyone.",
            allowNone = false,
            onDone = { pw -> pw?.let { BackupSecrets.save(ctx, it) }; repo.settings.update { it.copy() }; setPassword = false },
            onDismiss = { setPassword = false },
        )
    }
    if (confirmSignOut) ConfirmDialog(
        "Sign out of Google?", "Backups to Google Drive stop. Everything stays on this phone, and backups already in your Drive stay there until you delete them.", "Sign out",
        onConfirm = { confirmSignOut = false; run("signout") }, onDismiss = { confirmSignOut = false },
    )
    if (confirmDeleteCloud) ConfirmDialog(
        "Delete all Google Drive backups?", "Every backup this app saved in your Google Drive will be permanently deleted. Data on this phone isn't affected.", "Delete", destructive = true,
        onConfirm = { confirmDeleteCloud = false; run("deleteCloud") }, onDismiss = { confirmDeleteCloud = false },
    )
    if (restoreOpen) DriveRestoreDialog(state, repo) { restoreOpen = false }
    offerRestore?.let { files ->
        ConfirmDialog(
            "Backups found in your Google Drive",
            "The newest is from ${Dates.dateTime(files.first().createdMillis)}. Restore it to this phone?", "Choose backup",
            onConfirm = { offerRestore = null; restoreOpen = true },
            onDismiss = { offerRestore = null },
        )
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ProfileRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, destructive: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Lists the backups in the user's Google Drive, then downloads, checks and previews the chosen one before
 * replacing anything. Used from Profile and from the first-time setup screen.
 */
@Composable
fun DriveRestoreDialog(state: LedgerState?, repo: Repository, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var token by remember { mutableStateOf<String?>(null) }
    var files by remember { mutableStateOf<List<DriveFile>?>(null) }
    var chosen by remember { mutableStateOf<DriveFile?>(null) }
    var needPassword by remember { mutableStateOf(false) }
    var pw by remember { mutableStateOf("") }
    var staged by remember { mutableStateOf<StagedRestore?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(true) }
    var done by remember { mutableStateOf<String?>(null) }

    val auth = rememberGoogleAuth(
        onToken = { t ->
            token = t
            runCatching {
                if (repo.settings.current.googleEmail == null) {
                    val p = GoogleDrive.profile(t)
                    repo.settings.update { it.copy(googleEmail = p.email, googlePhoto = p.photoUrl, profileName = it.profileName.ifBlank { p.name }) }
                }
                DriveBackup.backups(t)
            }.onSuccess { files = it }.onFailure { error = it.message }
            busy = false
        },
        onError = { error = it; busy = false },
    )
    androidx.compose.runtime.LaunchedEffect(Unit) { auth() }

    fun open(f: DriveFile, password: String?) {
        val t = token ?: return
        busy = true; error = null
        scope.launch {
            DriveBackup.fetch(ctx, repo, t, f, password).fold(
                { staged = it; needPassword = false },
                { e ->
                    when (e) {
                        is PasswordNeededException -> needPassword = true
                        is WrongPasswordException -> { needPassword = true; error = e.message }
                        else -> error = e.message
                    }
                },
            )
            busy = false
        }
    }

    val st = staged
    AlertDialog(
        onDismissRequest = { st?.let { BackupManager(ctx, repo).discard(it) }; onDone() },
        title = { Text(if (st != null) "Restore this backup?" else "Restore from Google Drive") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    done != null -> Text(done!!)
                    st != null -> {
                        val p = st.preview
                        Text("Made ${Dates.dateTime(p.createdAt)}" + if (p.encrypted) " · password-protected" else "", style = MaterialTheme.typography.titleSmall)
                        Text(listOf("${p.entries} entries", "${p.accounts} accounts", "${p.loans} loans", "${p.budgets} budgets", "${p.bills} bills", "${p.goals} goals", "${p.receipts} receipt photos").joinToString(" · "))
                        if (state != null && state.txns.isNotEmpty()) Text("The ${state.txns.size} entries now on this phone will be replaced.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    needPassword -> {
                        Text("This backup is protected. Enter its password.")
                        PasswordField(pw, { pw = it }, "Password")
                    }
                    busy -> Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); Spacer(Modifier.width(10.dp)); Text("Connecting to Google Drive…") }
                    files?.isEmpty() == true -> Text("No backups found in this Google account yet.")
                    files != null -> {
                        Text("Choose a backup:", style = MaterialTheme.typography.bodyMedium)
                        files!!.take(10).forEach { f ->
                            Row(
                                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable { chosen = f; open(f, null) }
                                    .background(if (chosen?.id == f.id) MaterialTheme.colorScheme.primaryContainer else Color.Transparent).padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(Dates.dateTime(f.createdMillis), style = MaterialTheme.typography.titleSmall)
                                    Text("%.1f MB".format(f.size / 1_000_000.0) + if (f.name.endsWith(".lzbackup")) " · password-protected" else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (busy && chosen?.id == f.id) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            }
                        }
                    }
                }
                ErrorBanner(error)
            }
        },
        confirmButton = {
            when {
                done != null -> TextButton(onClick = onDone) { Text("OK") }
                st != null -> TextButton(onClick = {
                    staged = null; busy = true
                    scope.launch {
                        done = BackupManager(ctx, repo).apply(st).fold({ "Restored $it entries and everything else in the backup." }, { it.message ?: "Restore failed." })
                        busy = false
                    }
                }) { Text("Replace and restore", color = MaterialTheme.colorScheme.error) }
                needPassword -> TextButton(enabled = pw.isNotEmpty() && !busy, onClick = { chosen?.let { open(it, pw) } }) { Text("Open") }
                else -> {}
            }
        },
        dismissButton = { if (done == null) TextButton(onClick = { st?.let { BackupManager(ctx, repo).discard(it) }; onDone() }) { Text("Cancel") } },
    )
}
