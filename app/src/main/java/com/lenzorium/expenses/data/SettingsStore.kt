package com.lenzorium.expenses.data

import android.content.Context
import android.content.SharedPreferences
import com.lenzorium.expenses.domain.Currencies
import com.lenzorium.expenses.domain.CurrencyInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDate

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val setupDone: Boolean = false,
    val currencyCode: String = "PKR",
    val openingBalanceMinor: Long = 0L,
    val trackingStartEpochDay: Long = LocalDate.now().toEpochDay(),
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val showAfterDebt: Boolean = true,
    val pinHash: String? = null,
    val pinSalt: String? = null,
    val biometricEnabled: Boolean = false,
    /** Hide balance figures on Home until tapped. */
    val hideAmounts: Boolean = false,
    /** Epoch millis of the last full backup the user saved, or 0. */
    val lastBackupAt: Long = 0L,
    /** True once the old single opening balance has been moved into accounts. */
    val accountsMigrated: Boolean = false,
    /** Show a one-time note on Home asking the user to check their Cash/Bank split. */
    val accountsNotice: Boolean = false,
    /** Daily notification for bills that are due. */
    val remindersEnabled: Boolean = true,
    /** Notify when a budget reaches 80% and 100%. */
    val budgetAlerts: Boolean = true,
    /** Home shows a monthly budget card (hidden until the user creates one). */
    val homeShowInsights: Boolean = true,
    /** Folder chosen for automatic backups (a document-tree URI), or null when off. */
    val autoBackupUri: String? = null,
    /** Days between automatic backups (1 = daily, 7 = weekly). */
    val autoBackupDays: Int = 7,
    val autoBackupLast: Long = 0L,
    /** Name shown on Home and in Profile (from Google or typed by the user). */
    val profileName: String = "",
    /** Signed-in Google account, or null when using the app without signing in. */
    val googleEmail: String? = null,
    val googlePhoto: String? = null,
    /** Back up to Google Drive automatically (uses [autoBackupDays]). */
    val driveBackup: Boolean = true,
    val driveLast: Long = 0L,
) {
    val currency: CurrencyInfo get() = Currencies.find(currencyCode)
    val lockEnabled: Boolean get() = pinHash != null
}

class SettingsStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("lenzorium_settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<AppSettings> = _state.asStateFlow()
    val current: AppSettings get() = _state.value

    private fun read(): AppSettings = AppSettings(
        setupDone = prefs.getBoolean(K_SETUP, false),
        currencyCode = prefs.getString(K_CURRENCY, "PKR") ?: "PKR",
        openingBalanceMinor = prefs.getLong(K_OPENING, 0L),
        trackingStartEpochDay = prefs.getLong(K_START, LocalDate.now().toEpochDay()),
        themeMode = runCatching { ThemeMode.valueOf(prefs.getString(K_THEME, "SYSTEM")!!) }.getOrDefault(ThemeMode.SYSTEM),
        showAfterDebt = prefs.getBoolean(K_AFTER_DEBT, true),
        pinHash = prefs.getString(K_PIN_HASH, null),
        pinSalt = prefs.getString(K_PIN_SALT, null),
        biometricEnabled = prefs.getBoolean(K_BIO, false),
        hideAmounts = prefs.getBoolean(K_HIDE, false),
        lastBackupAt = prefs.getLong(K_LAST_BACKUP, 0L),
        accountsMigrated = prefs.getBoolean(K_ACC_MIGRATED, false),
        accountsNotice = prefs.getBoolean(K_ACC_NOTICE, false),
        remindersEnabled = prefs.getBoolean(K_REMINDERS, true),
        budgetAlerts = prefs.getBoolean(K_BUDGET_ALERTS, true),
        homeShowInsights = prefs.getBoolean(K_HOME_INSIGHTS, true),
        autoBackupUri = prefs.getString(K_AUTO_URI, null),
        autoBackupDays = prefs.getInt(K_AUTO_DAYS, 7),
        autoBackupLast = prefs.getLong(K_AUTO_LAST, 0L),
        profileName = prefs.getString(K_PROFILE_NAME, "") ?: "",
        googleEmail = prefs.getString(K_G_EMAIL, null),
        googlePhoto = prefs.getString(K_G_PHOTO, null),
        driveBackup = prefs.getBoolean(K_DRIVE, true),
        driveLast = prefs.getLong(K_DRIVE_LAST, 0L),
    )

    @Synchronized
    fun update(transform: (AppSettings) -> AppSettings) {
        val s = transform(_state.value)
        // commit() so settings survive an immediate process death.
        prefs.edit()
            .putBoolean(K_SETUP, s.setupDone)
            .putString(K_CURRENCY, s.currencyCode)
            .putLong(K_OPENING, s.openingBalanceMinor)
            .putLong(K_START, s.trackingStartEpochDay)
            .putString(K_THEME, s.themeMode.name)
            .putBoolean(K_AFTER_DEBT, s.showAfterDebt)
            .putString(K_PIN_HASH, s.pinHash)
            .putString(K_PIN_SALT, s.pinSalt)
            .putBoolean(K_BIO, s.biometricEnabled)
            .putBoolean(K_HIDE, s.hideAmounts)
            .putLong(K_LAST_BACKUP, s.lastBackupAt)
            .putBoolean(K_ACC_MIGRATED, s.accountsMigrated)
            .putBoolean(K_ACC_NOTICE, s.accountsNotice)
            .putBoolean(K_REMINDERS, s.remindersEnabled)
            .putBoolean(K_BUDGET_ALERTS, s.budgetAlerts)
            .putBoolean(K_HOME_INSIGHTS, s.homeShowInsights)
            .putString(K_AUTO_URI, s.autoBackupUri)
            .putInt(K_AUTO_DAYS, s.autoBackupDays)
            .putLong(K_AUTO_LAST, s.autoBackupLast)
            .putString(K_PROFILE_NAME, s.profileName)
            .putString(K_G_EMAIL, s.googleEmail)
            .putString(K_G_PHOTO, s.googlePhoto)
            .putBoolean(K_DRIVE, s.driveBackup)
            .putLong(K_DRIVE_LAST, s.driveLast)
            .commit()
        _state.value = s
    }

    /** Remembers which budget warnings were already shown, e.g. "2026-11:5:80". */
    fun alreadyAlerted(key: String): Boolean = prefs.getStringSet(K_ALERTED, emptySet())!!.contains(key)
    fun markAlerted(key: String) {
        val month = key.substringBefore(':')
        val kept = prefs.getStringSet(K_ALERTED, emptySet())!!.filter { it.startsWith(month) }.toSet()
        prefs.edit().putStringSet(K_ALERTED, kept + key).apply()
    }

    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }.toHex()
        update { it.copy(pinHash = hash(pin, salt), pinSalt = salt) }
    }

    fun clearPin() = update { it.copy(pinHash = null, pinSalt = null, biometricEnabled = false) }

    fun checkPin(pin: String): Boolean {
        val s = current
        val salt = s.pinSalt ?: return false
        return MessageDigest.isEqual(hash(pin, salt).toByteArray(), (s.pinHash ?: "").toByteArray())
    }

    companion object {
        private const val K_SETUP = "setup_done"
        private const val K_CURRENCY = "currency"
        private const val K_OPENING = "opening_balance"
        private const val K_START = "tracking_start"
        private const val K_THEME = "theme"
        private const val K_AFTER_DEBT = "show_after_debt"
        private const val K_PIN_HASH = "pin_hash"
        private const val K_PIN_SALT = "pin_salt"
        private const val K_BIO = "biometric"
        private const val K_HIDE = "hide_amounts"
        private const val K_LAST_BACKUP = "last_backup"
        private const val K_ACC_MIGRATED = "accounts_migrated"
        private const val K_ACC_NOTICE = "accounts_notice"
        private const val K_REMINDERS = "reminders"
        private const val K_BUDGET_ALERTS = "budget_alerts"
        private const val K_HOME_INSIGHTS = "home_insights"
        private const val K_AUTO_URI = "auto_backup_uri"
        private const val K_AUTO_DAYS = "auto_backup_days"
        private const val K_AUTO_LAST = "auto_backup_last"
        private const val K_PROFILE_NAME = "profile_name"
        private const val K_G_EMAIL = "google_email"
        private const val K_G_PHOTO = "google_photo"
        private const val K_DRIVE = "drive_backup"
        private const val K_DRIVE_LAST = "drive_last"

        /** Small notification bookkeeping kept out of [AppSettings]. */
        const val K_ALERTED = "budget_alerted"

        fun hash(pin: String, salt: String): String {
            // Iterated SHA-256; PIN never stored in plain text.
            var bytes = (salt + pin).toByteArray()
            val md = MessageDigest.getInstance("SHA-256")
            repeat(20_000) { bytes = md.digest(bytes) }
            return bytes.toHex()
        }

        private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
    }
}
