package com.lenzorium.expenses.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.lenzorium.expenses.data.Repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** What a backup file contains, shown before anything is replaced. */
data class BackupPreview(
    val createdAt: Long,
    val entries: Int,
    val accounts: Int,
    val loans: Int,
    val budgets: Int,
    val bills: Int,
    val goals: Int,
    val receipts: Int,
    val encrypted: Boolean,
)

/** A backup that has been read and checked, waiting for the user to confirm the restore. */
class StagedRestore internal constructor(val preview: BackupPreview, internal val data: BackupData, internal val staging: File)

class PasswordNeededException : Exception("This backup is protected with a password.")
class WrongPasswordException : Exception("Wrong password, or the file is damaged.")

/**
 * Full backup: a .zip with backup.json (all records and settings) plus every receipt photo under receipts/.
 * Optionally password-protected (AES-256-GCM, see [BackupCrypto]).
 */
class BackupManager(private val context: Context, private val repo: Repository) {
    private val dao = repo.db.dao()

    private suspend fun snapshot() = BackupData(
        settings = repo.settings.current,
        categories = dao.allCategories(),
        loans = dao.allLoans(),
        txns = dao.allTxns(),
        createdAt = System.currentTimeMillis(),
        accounts = dao.allAccounts(),
        budgets = dao.allBudgets(),
        recurring = dao.allRecurring(),
        templates = dao.allTemplates(),
        goals = dao.allGoals(),
        goalEntries = dao.allGoalEntries(),
    )

    /** Writes a complete backup to [raw] and closes it. Returns the number of entries. */
    suspend fun write(raw: OutputStream, password: String?): Int = withContext(Dispatchers.IO) {
        val data = snapshot()
        val json = BackupCodec.encode(data)
        val out = if (password != null) BackupCrypto.encrypt(raw, password) else raw
        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(JSON_NAME))
            zip.write(json.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            data.txns.mapNotNull { it.receiptFile }.distinct().forEach { name ->
                val f = repo.receiptFile(name)
                if (f.exists()) {
                    zip.putNextEntry(ZipEntry("receipts/$name"))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
        repo.settings.update { it.copy(lastBackupAt = System.currentTimeMillis()) }
        data.txns.size
    }

    suspend fun create(uri: Uri, password: String? = null): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val out = context.contentResolver.openOutputStream(uri, "wt") ?: error("Couldn't open the file for writing.")
            write(out, password)
        }
    }

    /**
     * Reads and fully checks a backup without changing anything. Fails with [PasswordNeededException]
     * for a protected file when no password is given, and [WrongPasswordException] for a wrong one.
     */
    suspend fun inspect(uri: Uri, password: String?): Result<StagedRestore> = withContext(Dispatchers.IO) {
        val staging = File(context.cacheDir, "restore_staging").apply { deleteRecursively(); mkdirs() }
        try {
            val input = BufferedInputStream(context.contentResolver.openInputStream(uri) ?: throw Exception("Couldn't open the file."))
            input.mark(16)
            val head = ByteArray(BackupCrypto.headerSize)
            val n = input.read(head)
            val encrypted = n == head.size && BackupCrypto.isEncrypted(head)
            if (!encrypted) input.reset()
            val stream: InputStream = when {
                !encrypted -> input
                password == null -> { input.close(); throw PasswordNeededException() }
                else -> BackupCrypto.decrypt(input, password)
            }
            var json: String? = null
            var receipts = 0
            try {
                ZipInputStream(stream).use { zip ->
                    var entry = zip.nextEntry
                    var total = 0L
                    while (entry != null) {
                        val name = entry.name
                        when {
                            name == JSON_NAME -> json = zip.readBytes().toString(Charsets.UTF_8)
                            name.startsWith("receipts/") && !entry.isDirectory -> {
                                val file = name.removePrefix("receipts/")
                                if (!BackupCodec.isSafeName(file)) throw BackupFormatException("The backup contains an invalid file name.")
                                File(staging, file).outputStream().use { o -> total += zip.copyTo(o) }
                                receipts++
                                if (total > 2L * 1024 * 1024 * 1024) throw BackupFormatException("The backup is too large.")
                            }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            } catch (e: BackupFormatException) {
                throw e
            } catch (e: Exception) {
                if (encrypted) throw WrongPasswordException()
                throw e
            }
            val text = json ?: throw if (encrypted) WrongPasswordException() else BackupFormatException("The file isn't a Lenzorium backup (backup.json not found).")
            val data = BackupCodec.decode(text)
            Result.success(
                StagedRestore(
                    BackupPreview(
                        data.createdAt, data.txns.size, data.accounts.size, data.loans.size, data.budgets.size,
                        data.recurring.size, data.goals.size, receipts, encrypted,
                    ),
                    data, staging,
                )
            )
        } catch (e: PasswordNeededException) {
            staging.deleteRecursively(); Result.failure(e)
        } catch (e: WrongPasswordException) {
            staging.deleteRecursively(); Result.failure(e)
        } catch (e: BackupFormatException) {
            staging.deleteRecursively(); Result.failure(e)
        } catch (e: Exception) {
            staging.deleteRecursively()
            Result.failure(Exception("Couldn't read the backup: ${e.message ?: "unknown error"}. Nothing was changed."))
        }
    }

    /** Replaces all data with a staged backup in one database transaction; a failure leaves current data intact. */
    suspend fun apply(staged: StagedRestore): Result<Int> = withContext(Dispatchers.IO) {
        val data = staged.data
        try {
            repo.db.withTransaction {
                dao.clearTxns()
                dao.clearLoans()
                dao.clearCategories()
                dao.clearAccounts()
                dao.clearBudgets(); dao.clearRecurring(); dao.clearTemplates(); dao.clearGoalEntries(); dao.clearGoals()
                dao.insertAccounts(data.accounts)
                dao.insertCategories(data.categories)
                dao.insertLoans(data.loans)
                dao.insertTxns(data.txns)
                dao.insertBudgets(data.budgets)
                dao.insertRecurringList(data.recurring)
                dao.insertTemplates(data.templates)
                dao.insertGoals(data.goals)
                dao.insertGoalEntries(data.goalEntries)
            }
            // Swap receipt folders only after the database restore succeeded.
            val live = File(context.filesDir, "receipts")
            val old = File(context.filesDir, "receipts_old").apply { deleteRecursively() }
            if (live.exists()) live.renameTo(old)
            if (!staged.staging.renameTo(live)) {
                live.mkdirs()
                staged.staging.listFiles()?.forEach { it.copyTo(File(live, it.name), overwrite = true) }
            }
            old.deleteRecursively()
            val keep = repo.settings.current
            repo.settings.update {
                data.settings.copy(
                    setupDone = true, lastBackupAt = System.currentTimeMillis(),
                    // Automatic-backup settings belong to this phone, not to the file.
                    autoBackupUri = keep.autoBackupUri, autoBackupDays = keep.autoBackupDays, autoBackupLast = keep.autoBackupLast,
                    googleEmail = keep.googleEmail, googlePhoto = keep.googlePhoto, driveBackup = keep.driveBackup, driveLast = keep.driveLast,
                    profileName = keep.profileName.ifBlank { data.settings.profileName },
                )
            }
            Result.success(data.txns.size)
        } catch (e: Exception) {
            Result.failure(Exception("Restore failed: ${e.message ?: "unknown error"}. Your existing data was not changed."))
        } finally {
            staged.staging.deleteRecursively()
        }
    }

    fun discard(staged: StagedRestore) { staged.staging.deleteRecursively() }

    /** One-step restore (used by first-time setup). */
    suspend fun restore(uri: Uri, password: String? = null): Result<Int> =
        inspect(uri, password).fold({ apply(it) }, { Result.failure(it) })

    companion object {
        const val JSON_NAME = "backup.json"
    }
}
