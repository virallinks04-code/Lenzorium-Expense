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
            .commit()
        _state.value = s
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
