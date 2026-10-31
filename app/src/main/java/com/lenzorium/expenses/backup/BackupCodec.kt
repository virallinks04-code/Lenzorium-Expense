package com.lenzorium.expenses.backup

import com.lenzorium.expenses.data.AppSettings
import com.lenzorium.expenses.data.Category
import com.lenzorium.expenses.data.CategoryKind
import com.lenzorium.expenses.data.Loan
import com.lenzorium.expenses.data.PayMethod
import com.lenzorium.expenses.data.ThemeMode
import com.lenzorium.expenses.data.Txn
import com.lenzorium.expenses.data.TxnType
import com.lenzorium.expenses.domain.Currencies
import org.json.JSONArray
import org.json.JSONObject

data class BackupData(
    val settings: AppSettings,
    val categories: List<Category>,
    val loans: List<Loan>,
    val txns: List<Txn>,
    val createdAt: Long,
)

class BackupFormatException(message: String) : Exception(message)

/** JSON encoding of a full backup. Pure Kotlin + org.json so it can be unit-tested. */
object BackupCodec {
    const val FORMAT = "lenzorium-expenses-backup"
    const val VERSION = 1

    fun encode(d: BackupData): String {
        val root = JSONObject()
        root.put("format", FORMAT)
        root.put("version", VERSION)
        root.put("createdAt", d.createdAt)
        root.put("settings", JSONObject().apply {
            val s = d.settings
            put("currencyCode", s.currencyCode)
            put("openingBalanceMinor", s.openingBalanceMinor)
            put("trackingStartEpochDay", s.trackingStartEpochDay)
            put("themeMode", s.themeMode.name)
            put("showAfterDebt", s.showAfterDebt)
            put("pinHash", s.pinHash ?: JSONObject.NULL)
            put("pinSalt", s.pinSalt ?: JSONObject.NULL)
            put("biometricEnabled", s.biometricEnabled)
            put("hideAmounts", s.hideAmounts)
        })
        root.put("categories", JSONArray().apply {
            d.categories.forEach { c ->
                put(JSONObject().apply {
                    put("id", c.id); put("name", c.name); put("kind", c.kind.name); put("iconKey", c.iconKey)
                    put("colorArgb", c.colorArgb); put("isFallback", c.isFallback); put("sortOrder", c.sortOrder)
                })
            }
        })
        root.put("loans", JSONArray().apply {
            d.loans.forEach { l ->
                put(JSONObject().apply {
                    put("id", l.id); put("lender", l.lender); put("title", l.title); put("principalMinor", l.principalMinor)
                    put("receivedAt", l.receivedAt); put("dueEpochDay", l.dueEpochDay ?: JSONObject.NULL); put("notes", l.notes)
                    put("isOpening", l.isOpening); put("requestId", l.requestId); put("createdAt", l.createdAt)
                })
            }
        })
        root.put("transactions", JSONArray().apply {
            d.txns.forEach { t ->
                put(JSONObject().apply {
                    put("id", t.id); put("type", t.type.name); put("amountMinor", t.amountMinor)
                    put("categoryId", t.categoryId ?: JSONObject.NULL); put("timestamp", t.timestamp); put("party", t.party)
                    put("paymentMethod", t.paymentMethod?.name ?: JSONObject.NULL); put("notes", t.notes)
                    put("receiptFile", t.receiptFile ?: JSONObject.NULL); put("loanId", t.loanId ?: JSONObject.NULL)
                    put("requestId", t.requestId); put("createdAt", t.createdAt)
                })
            }
        })
        return root.toString()
    }

    private fun JSONObject.optLongOrNull(k: String): Long? = if (!has(k) || isNull(k)) null else getLong(k)
    private fun JSONObject.optStringOrNull(k: String): String? = if (!has(k) || isNull(k)) null else getString(k)

    /** Parses and validates a backup. Throws [BackupFormatException] with a readable message on any problem. */
    fun decode(json: String): BackupData {
        val root = try { JSONObject(json) } catch (e: Exception) { throw BackupFormatException("The file isn't a Lenzorium backup.") }
        if (root.optString("format") != FORMAT) throw BackupFormatException("The file isn't a Lenzorium backup.")
        val version = root.optInt("version", -1)
        if (version < 1 || version > VERSION) throw BackupFormatException("This backup was made by a newer version of the app. Update the app and try again.")
        try {
            val s = root.getJSONObject("settings")
            val code = s.getString("currencyCode")
            if (Currencies.all.none { it.code == code }) throw BackupFormatException("Unknown currency in backup: $code")
            val settings = AppSettings(
                setupDone = true,
                currencyCode = code,
                openingBalanceMinor = s.getLong("openingBalanceMinor"),
                trackingStartEpochDay = s.getLong("trackingStartEpochDay"),
                themeMode = runCatching { ThemeMode.valueOf(s.getString("themeMode")) }.getOrDefault(ThemeMode.SYSTEM),
                showAfterDebt = s.optBoolean("showAfterDebt", true),
                pinHash = s.optStringOrNull("pinHash"),
                pinSalt = s.optStringOrNull("pinSalt"),
                biometricEnabled = s.optBoolean("biometricEnabled", false),
                hideAmounts = s.optBoolean("hideAmounts", false),
            )
            val cats = root.getJSONArray("categories").let { a ->
                (0 until a.length()).map { i ->
                    val o = a.getJSONObject(i)
                    Category(
                        id = o.getLong("id"), name = o.getString("name"), kind = CategoryKind.valueOf(o.getString("kind")),
                        iconKey = o.getString("iconKey"), colorArgb = o.getLong("colorArgb"),
                        isFallback = o.optBoolean("isFallback", false), sortOrder = o.optInt("sortOrder", 0),
                    )
                }
            }
            val loans = root.getJSONArray("loans").let { a ->
                (0 until a.length()).map { i ->
                    val o = a.getJSONObject(i)
                    Loan(
                        id = o.getLong("id"), lender = o.getString("lender"), title = o.optString("title", ""),
                        principalMinor = o.getLong("principalMinor"), receivedAt = o.getLong("receivedAt"),
                        dueEpochDay = o.optLongOrNull("dueEpochDay"), notes = o.optString("notes", ""),
                        isOpening = o.getBoolean("isOpening"), requestId = o.getString("requestId"), createdAt = o.getLong("createdAt"),
                    )
                }
            }
            val txns = root.getJSONArray("transactions").let { a ->
                (0 until a.length()).map { i ->
                    val o = a.getJSONObject(i)
                    Txn(
                        id = o.getLong("id"), type = TxnType.valueOf(o.getString("type")), amountMinor = o.getLong("amountMinor"),
                        categoryId = o.optLongOrNull("categoryId"), timestamp = o.getLong("timestamp"), party = o.optString("party", ""),
                        paymentMethod = o.optStringOrNull("paymentMethod")?.let { PayMethod.valueOf(it) }, notes = o.optString("notes", ""),
                        receiptFile = o.optStringOrNull("receiptFile"), loanId = o.optLongOrNull("loanId"),
                        requestId = o.getString("requestId"), createdAt = o.getLong("createdAt"),
                    )
                }
            }
            validate(cats, loans, txns)
            return BackupData(settings, cats, loans, txns, root.optLong("createdAt", 0L))
        } catch (e: BackupFormatException) {
            throw e
        } catch (e: Exception) {
            throw BackupFormatException("The backup file is damaged or incomplete (${e.message}).")
        }
    }

    private fun validate(cats: List<Category>, loans: List<Loan>, txns: List<Txn>) {
        val catIds = cats.map { it.id }.toSet()
        val loanIds = loans.map { it.id }.toSet()
        if (catIds.size != cats.size || loanIds.size != loans.size || txns.map { it.id }.toSet().size != txns.size)
            throw BackupFormatException("The backup contains duplicate records.")
        if (loans.any { it.principalMinor <= 0 }) throw BackupFormatException("The backup contains an invalid loan amount.")
        for (t in txns) {
            if (t.amountMinor <= 0) throw BackupFormatException("The backup contains an invalid amount.")
            if (t.categoryId != null && t.categoryId !in catIds) throw BackupFormatException("A transaction refers to a missing category.")
            if (t.type.isLoanRelated && (t.loanId == null || t.loanId !in loanIds)) throw BackupFormatException("A loan entry refers to a missing loan.")
            t.receiptFile?.let { if (!isSafeName(it)) throw BackupFormatException("The backup contains an invalid file name.") }
        }
    }

    fun isSafeName(name: String): Boolean = name.isNotEmpty() && name.length < 200 && name.matches(Regex("^[A-Za-z0-9._-]+$")) && name != "." && name != ".."
}
