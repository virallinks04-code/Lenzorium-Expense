package com.lenzorium.expenses.cloud

import android.content.Context
import android.net.Uri
import com.lenzorium.expenses.backup.BackupManager
import com.lenzorium.expenses.backup.BackupSecrets
import com.lenzorium.expenses.backup.StagedRestore
import com.lenzorium.expenses.data.Repository
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Full backups to the signed-in user's Google Drive (hidden app folder). Keeps the newest [KEEP]. */
object DriveBackup {
    private const val PREFIX = "lenzorium-"
    private const val KEEP = 10

    fun isDue(repo: Repository, now: Long = System.currentTimeMillis()): Boolean {
        val s = repo.settings.current
        return s.setupDone && s.googleEmail != null && s.driveBackup &&
            now - s.driveLast >= s.autoBackupDays * 24L * 60 * 60 * 1000 - 60 * 60 * 1000
    }

    /** Creates a backup and uploads it. Uses the automatic-backup password if one is set. */
    suspend fun backupNow(ctx: Context, repo: Repository, token: String): DriveFile {
        val password = try { BackupSecrets.load(ctx) } catch (e: Exception) {
            throw IllegalStateException("Your backup password couldn't be unlocked on this phone. Set it again in Profile.")
        }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
        val name = PREFIX + stamp + if (password != null) ".lzbackup" else ".zip"
        val tmp = File(ctx.cacheDir, "drive-upload.tmp")
        try {
            tmp.outputStream().use { BackupManager(ctx, repo).write(it, password) }
            val uploaded = GoogleDrive.upload(token, tmp, name)
            repo.settings.update { it.copy(driveLast = System.currentTimeMillis()) }
            runCatching {
                GoogleDrive.list(token).filter { it.name.startsWith(PREFIX) }.drop(KEEP).forEach { GoogleDrive.delete(token, it.id) }
            }
            return uploaded
        } finally {
            tmp.delete()
        }
    }

    suspend fun backups(token: String): List<DriveFile> = GoogleDrive.list(token).filter { it.name.startsWith(PREFIX) }

    /** Downloads a backup and checks it; nothing is replaced until [BackupManager.apply]. */
    suspend fun fetch(ctx: Context, repo: Repository, token: String, file: DriveFile, password: String?): Result<StagedRestore> {
        val tmp = File(ctx.cacheDir, "drive-${file.id}.tmp")
        return try {
            if (!tmp.exists() || tmp.length() != file.size) GoogleDrive.download(token, file.id, tmp)
            val r = BackupManager(ctx, repo).inspect(Uri.fromFile(tmp), password)
            // Keep the download only while the user is still typing the password.
            val e = r.exceptionOrNull()
            if (e !is com.lenzorium.expenses.backup.PasswordNeededException && e !is com.lenzorium.expenses.backup.WrongPasswordException) tmp.delete()
            r
        } catch (e: Exception) {
            tmp.delete()
            Result.failure(e)
        }
    }

    /** Removes every backup this app stored in the user's Google Drive. */
    suspend fun deleteAll(token: String): Int {
        val all = GoogleDrive.list(token)
        all.forEach { GoogleDrive.delete(token, it.id) }
        return all.size
    }
}
