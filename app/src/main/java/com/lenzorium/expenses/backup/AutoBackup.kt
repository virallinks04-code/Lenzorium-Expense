package com.lenzorium.expenses.backup

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.documentfile.provider.DocumentFile
import com.lenzorium.expenses.data.Repository
import java.security.KeyStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The automatic-backup password, kept encrypted with a key that lives in the phone's secure hardware
 * (Android Keystore). The password itself is never stored in plain text and never leaves the phone.
 */
object BackupSecrets {
    private const val ALIAS = "lenzorium_auto_backup"
    private const val PREFS = "lenzorium_secrets"
    private const val K_PW = "auto_backup_pw"

    private fun keystoreKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    fun save(ctx: Context, password: String?) {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (password == null) { prefs.edit().remove(K_PW).commit(); return }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, keystoreKey())
        val blob = c.iv + c.doFinal(password.toByteArray(Charsets.UTF_8))
        prefs.edit().putString(K_PW, Base64.encodeToString(blob, Base64.NO_WRAP)).commit()
    }

    fun hasPassword(ctx: Context): Boolean = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains(K_PW)

    /** Null when no password is set; throws if it can't be unlocked (e.g. settings copied to a new phone). */
    fun load(ctx: Context): String? {
        val b64 = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(K_PW, null) ?: return null
        val blob = Base64.decode(b64, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, keystoreKey(), GCMParameterSpec(128, blob.copyOfRange(0, 12)))
        return String(c.doFinal(blob.copyOfRange(12, blob.size)), Charsets.UTF_8)
    }
}

/**
 * Saves a full backup into the folder the user picked (it can be a Google Drive or other cloud folder
 * through Android's file picker; the app itself never connects to the internet). Keeps the newest 5.
 */
object AutoBackup {
    const val PREFIX = "lenzorium-auto-"
    private const val KEEP = 5

    fun isDue(repo: Repository, now: Long = System.currentTimeMillis()): Boolean {
        val s = repo.settings.current
        return s.setupDone && s.autoBackupUri != null && now - s.autoBackupLast >= s.autoBackupDays * 24L * 60 * 60 * 1000 - 60 * 60 * 1000
    }

    suspend fun run(ctx: Context, repo: Repository): Result<String> = runCatching {
        val tree = repo.settings.current.autoBackupUri ?: error("Automatic backup is off.")
        val dir = DocumentFile.fromTreeUri(ctx, Uri.parse(tree)) ?: error("The backup folder isn't available.")
        if (!dir.canWrite()) error("The app can no longer write to the backup folder. Choose it again in More › Backup.")
        val password = try { BackupSecrets.load(ctx) } catch (e: Exception) { error("The backup password couldn't be unlocked on this phone. Set it again in More › Backup.") }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
        val name = "$PREFIX$stamp" + if (password != null) ".lzbackup" else ".zip"
        val file = dir.createFile("application/octet-stream", name) ?: error("Couldn't create a file in the backup folder.")
        try {
            val out = ctx.contentResolver.openOutputStream(file.uri, "wt") ?: error("Couldn't write the backup file.")
            BackupManager(ctx, repo).write(out, password)
        } catch (e: Exception) {
            file.delete(); throw e
        }
        repo.settings.update { it.copy(autoBackupLast = System.currentTimeMillis()) }
        dir.listFiles().filter { it.name?.startsWith(PREFIX) == true }
            .sortedByDescending { it.name }
            .drop(KEEP)
            .forEach { it.delete() }
        file.name ?: name
    }

    /** Readable folder name for Settings, e.g. "Drive › Backups". */
    fun folderLabel(ctx: Context, uri: String?): String? = uri?.let {
        runCatching { DocumentFile.fromTreeUri(ctx, Uri.parse(it))?.name }.getOrNull() ?: "Chosen folder"
    }
}
