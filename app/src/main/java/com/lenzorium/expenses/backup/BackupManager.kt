package com.lenzorium.expenses.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.lenzorium.expenses.data.Repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Full backup: a .zip with backup.json (transactions, loans, categories, settings)
 * plus every receipt photo under receipts/.
 */
class BackupManager(private val context: Context, private val repo: Repository) {
    private val dao = repo.db.dao()

    suspend fun create(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val data = BackupData(
                settings = repo.settings.current,
                categories = dao.allCategories(),
                loans = dao.allLoans(),
                txns = dao.allTxns(),
                createdAt = System.currentTimeMillis(),
                accounts = dao.allAccounts(),
            )
            val json = BackupCodec.encode(data)
            val out = context.contentResolver.openOutputStream(uri, "wt") ?: error("Couldn't open the file for writing.")
            var receipts = 0
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
                        receipts++
                    }
                }
            }
            repo.settings.update { it.copy(lastBackupAt = System.currentTimeMillis()) }
            data.txns.size
        }
    }

    /**
     * Restores a backup. Nothing existing is touched until the whole file has been read and
     * validated; the database swap happens in one transaction, so a failure leaves current data intact.
     */
    suspend fun restore(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        val staging = File(context.cacheDir, "restore_staging").apply { deleteRecursively(); mkdirs() }
        try {
            var json: String? = null
            val input = context.contentResolver.openInputStream(uri) ?: return@withContext Result.failure(Exception("Couldn't open the file."))
            ZipInputStream(input.buffered()).use { zip ->
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
                            if (total > 2L * 1024 * 1024 * 1024) throw BackupFormatException("The backup is too large.")
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            val text = json ?: throw BackupFormatException("The file isn't a Lenzorium backup (backup.json not found).")
            val data = BackupCodec.decode(text)

            repo.db.withTransaction {
                dao.clearTxns()
                dao.clearLoans()
                dao.clearCategories()
                dao.clearAccounts()
                dao.insertAccounts(data.accounts)
                dao.insertCategories(data.categories)
                dao.insertLoans(data.loans)
                dao.insertTxns(data.txns)
            }

            // Swap receipt folders only after the database restore succeeded.
            val live = File(context.filesDir, "receipts")
            val old = File(context.filesDir, "receipts_old").apply { deleteRecursively() }
            if (live.exists()) live.renameTo(old)
            if (!staging.renameTo(live)) {
                live.mkdirs()
                staging.listFiles()?.forEach { it.copyTo(File(live, it.name), overwrite = true) }
            }
            old.deleteRecursively()

            repo.settings.update { data.settings.copy(setupDone = true, lastBackupAt = System.currentTimeMillis()) }
            Result.success(data.txns.size)
        } catch (e: BackupFormatException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(Exception("Restore failed: ${e.message ?: "unknown error"}. Your existing data was not changed."))
        } finally {
            staging.deleteRecursively()
        }
    }

    companion object {
        const val JSON_NAME = "backup.json"
    }
}
