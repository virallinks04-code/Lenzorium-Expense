package com.lenzorium.expenses.backup

import com.lenzorium.expenses.data.Account
import com.lenzorium.expenses.data.AccountKind
import com.lenzorium.expenses.data.AppDatabase
import com.lenzorium.expenses.data.AppSettings
import com.lenzorium.expenses.data.DefaultAccounts
import com.lenzorium.expenses.data.Budget
import com.lenzorium.expenses.data.Category
import com.lenzorium.expenses.data.Frequency
import com.lenzorium.expenses.data.Goal
import com.lenzorium.expenses.data.GoalEntry
import com.lenzorium.expenses.data.Recurring
import com.lenzorium.expenses.data.Template
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
    val accounts: List<Account> = DefaultAccounts.all,
    val budgets: List<Budget> = emptyList(),
    val recurring: List<Recurring> = emptyList(),
    val templates: List<Template> = emptyList(),
    val goals: List<Goal> = emptyList(),
    val goalEntries: List<GoalEntry> = emptyList(),
)

class BackupFormatException(message: String) : Exception(message)

/** JSON encoding of a full backup. Pure Kotlin + org.json so it can be unit-tested. */
object BackupCodec {
    const val FORMAT = "lenzorium-expenses-backup"
    const val VERSION = 3

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
            put("remindersEnabled", s.remindersEnabled)
            put("budgetAlerts", s.budgetAlerts)
        })
        root.put("categories", JSONArray().apply {
            d.categories.forEach { c ->
                put(JSONObject().apply {
                    put("id", c.id); put("name", c.name); put("kind", c.kind.name); put("iconKey", c.iconKey)
                    put("colorArgb", c.colorArgb); put("isFallback", c.isFallback); put("sortOrder", c.sortOrder)
                })
            }
        })
        root.put("accounts", JSONArray().apply {
            d.accounts.forEach { a ->
                put(JSONObject().apply {
                    put("id", a.id); put("name", a.name); put("kind", a.kind.name); put("openingMinor", a.openingMinor)
                    put("archived", a.archived); put("sortOrder", a.sortOrder); put("currency", a.currency ?: JSONObject.NULL)
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
                    put("accountId", t.accountId ?: JSONObject.NULL); put("toAccountId", t.toAccountId ?: JSONObject.NULL)
                    put("tags", t.tags); put("recurringId", t.recurringId ?: JSONObject.NULL)
                })
            }
        })
        root.put("budgets", JSONArray().apply {
            d.budgets.forEach { b ->
                put(JSONObject().apply {
                    put("id", b.id); put("categoryId", b.categoryId ?: JSONObject.NULL); put("limitMinor", b.limitMinor)
                    put("rollover", b.rollover); put("startMonthEpochDay", b.startMonthEpochDay); put("createdAt", b.createdAt)
                })
            }
        })
        root.put("recurring", JSONArray().apply {
            d.recurring.forEach { r ->
                put(JSONObject().apply {
                    put("id", r.id); put("name", r.name); put("type", r.type.name); put("amountMinor", r.amountMinor)
                    put("categoryId", r.categoryId ?: JSONObject.NULL); put("accountId", r.accountId ?: JSONObject.NULL); put("party", r.party)
                    put("frequency", r.frequency.name); put("intervalCount", r.intervalCount); put("startEpochDay", r.startEpochDay)
                    put("endEpochDay", r.endEpochDay ?: JSONObject.NULL); put("nextDueEpochDay", r.nextDueEpochDay); put("remind", r.remind)
                    put("remindDaysBefore", r.remindDaysBefore); put("active", r.active); put("notes", r.notes); put("createdAt", r.createdAt)
                })
            }
        })
        root.put("templates", JSONArray().apply {
            d.templates.forEach { t ->
                put(JSONObject().apply {
                    put("id", t.id); put("name", t.name); put("type", t.type.name); put("amountMinor", t.amountMinor)
                    put("categoryId", t.categoryId ?: JSONObject.NULL); put("accountId", t.accountId ?: JSONObject.NULL)
                    put("party", t.party); put("tags", t.tags); put("sortOrder", t.sortOrder)
                })
            }
        })
        root.put("goals", JSONArray().apply {
            d.goals.forEach { g ->
                put(JSONObject().apply {
                    put("id", g.id); put("name", g.name); put("targetMinor", g.targetMinor); put("targetEpochDay", g.targetEpochDay ?: JSONObject.NULL)
                    put("accountId", g.accountId ?: JSONObject.NULL); put("iconKey", g.iconKey); put("colorArgb", g.colorArgb)
                    put("archived", g.archived); put("createdAt", g.createdAt)
                })
            }
        })
        root.put("goalEntries", JSONArray().apply {
            d.goalEntries.forEach { e ->
                put(JSONObject().apply {
                    put("id", e.id); put("goalId", e.goalId); put("amountMinor", e.amountMinor); put("epochDay", e.epochDay)
                    put("note", e.note); put("createdAt", e.createdAt)
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
                remindersEnabled = s.optBoolean("remindersEnabled", true),
                budgetAlerts = s.optBoolean("budgetAlerts", true),
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
                        accountId = o.optLongOrNull("accountId"), toAccountId = o.optLongOrNull("toAccountId"),
                        tags = o.optString("tags", ""), recurringId = o.optLongOrNull("recurringId"),
                    )
                }
            }
            val accounts: List<Account>
            val finalTxns: List<Txn>
            if (version >= 2) {
                accounts = root.getJSONArray("accounts").let { a ->
                    (0 until a.length()).map { i ->
                        val o = a.getJSONObject(i)
                        Account(
                            id = o.getLong("id"), name = o.getString("name"), kind = AccountKind.valueOf(o.getString("kind")),
                            openingMinor = o.getLong("openingMinor"), archived = o.optBoolean("archived", false), sortOrder = o.optInt("sortOrder", 0),
                            currency = o.optStringOrNull("currency"),
                        )
                    }
                }
                finalTxns = txns
            } else {
                // Backups from before accounts existed: same rules as the database upgrade.
                accounts = DefaultAccounts.all.map { if (it.id == AppDatabase.CASH_ID) it.copy(openingMinor = settings.openingBalanceMinor) else it }
                finalTxns = txns.map { t ->
                    t.copy(
                        accountId = when {
                            t.type == TxnType.LOAN_CHARGE -> null
                            t.paymentMethod == PayMethod.BANK || t.paymentMethod == PayMethod.CARD -> AppDatabase.BANK_ID
                            else -> AppDatabase.CASH_ID
                        }
                    )
                }
            }
            fun arr(k: String) = root.optJSONArray(k) ?: JSONArray()
            fun <T> JSONArray.each(f: (JSONObject) -> T): List<T> = (0 until length()).map { f(getJSONObject(it)) }
            val budgets = arr("budgets").each { o ->
                Budget(o.getLong("id"), o.optLongOrNull("categoryId"), o.getLong("limitMinor"), o.optBoolean("rollover", false), o.getLong("startMonthEpochDay"), o.optLong("createdAt", 0L))
            }
            val recurring = arr("recurring").each { o ->
                Recurring(
                    id = o.getLong("id"), name = o.getString("name"), type = TxnType.valueOf(o.getString("type")), amountMinor = o.getLong("amountMinor"),
                    categoryId = o.optLongOrNull("categoryId"), accountId = o.optLongOrNull("accountId"), party = o.optString("party", ""),
                    frequency = Frequency.valueOf(o.getString("frequency")), intervalCount = o.optInt("intervalCount", 1),
                    startEpochDay = o.getLong("startEpochDay"), endEpochDay = o.optLongOrNull("endEpochDay"), nextDueEpochDay = o.getLong("nextDueEpochDay"),
                    remind = o.optBoolean("remind", true), remindDaysBefore = o.optInt("remindDaysBefore", 1), active = o.optBoolean("active", true),
                    notes = o.optString("notes", ""), createdAt = o.optLong("createdAt", 0L),
                )
            }
            val templates = arr("templates").each { o ->
                Template(
                    id = o.getLong("id"), name = o.getString("name"), type = TxnType.valueOf(o.getString("type")), amountMinor = o.optLong("amountMinor", 0L),
                    categoryId = o.optLongOrNull("categoryId"), accountId = o.optLongOrNull("accountId"), party = o.optString("party", ""),
                    tags = o.optString("tags", ""), sortOrder = o.optInt("sortOrder", 0),
                )
            }
            val goals = arr("goals").each { o ->
                Goal(
                    id = o.getLong("id"), name = o.getString("name"), targetMinor = o.getLong("targetMinor"), targetEpochDay = o.optLongOrNull("targetEpochDay"),
                    accountId = o.optLongOrNull("accountId"), iconKey = o.optString("iconKey", "goal"), colorArgb = o.optLong("colorArgb", 0xFF4A9EDBL),
                    archived = o.optBoolean("archived", false), createdAt = o.optLong("createdAt", 0L),
                )
            }
            val goalEntries = arr("goalEntries").each { o ->
                GoalEntry(o.getLong("id"), o.getLong("goalId"), o.getLong("amountMinor"), o.getLong("epochDay"), o.optString("note", ""), o.optLong("createdAt", 0L))
            }
            validate(cats, loans, finalTxns, accounts)
            validatePlans(cats, accounts, budgets, recurring, templates, goals, goalEntries)
            return BackupData(
                settings.copy(accountsMigrated = true), cats, loans, finalTxns, root.optLong("createdAt", 0L), accounts,
                budgets, recurring, templates, goals, goalEntries,
            )
        } catch (e: BackupFormatException) {
            throw e
        } catch (e: Exception) {
            throw BackupFormatException("The backup file is damaged or incomplete (${e.message}).")
        }
    }

    private fun validate(cats: List<Category>, loans: List<Loan>, txns: List<Txn>, accounts: List<Account>) {
        val accIds = accounts.map { it.id }.toSet()
        if (accounts.isEmpty() || accIds.size != accounts.size) throw BackupFormatException("The backup's accounts are missing or duplicated.")
        if (txns.any { (it.accountId != null && it.accountId !in accIds) || (it.toAccountId != null && it.toAccountId !in accIds) })
            throw BackupFormatException("A transaction refers to a missing account.")
        if (txns.any { it.type == TxnType.TRANSFER && (it.accountId == null || it.toAccountId == null) })
            throw BackupFormatException("A transfer is missing its accounts.")
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

    private fun validatePlans(
        cats: List<Category>, accounts: List<Account>, budgets: List<Budget>, recurring: List<Recurring>,
        templates: List<Template>, goals: List<Goal>, entries: List<GoalEntry>,
    ) {
        val catIds = cats.map { it.id }.toSet()
        val accIds = accounts.map { it.id }.toSet()
        val goalIds = goals.map { it.id }.toSet()
        fun dupes(ids: List<Long>) = ids.toSet().size != ids.size
        if (dupes(budgets.map { it.id }) || dupes(recurring.map { it.id }) || dupes(templates.map { it.id }) || dupes(goals.map { it.id }) || dupes(entries.map { it.id }))
            throw BackupFormatException("The backup contains duplicate records.")
        if (budgets.any { it.limitMinor <= 0 || (it.categoryId != null && it.categoryId !in catIds) }) throw BackupFormatException("A budget in the backup is invalid.")
        if (recurring.any { it.amountMinor <= 0 || (it.categoryId != null && it.categoryId !in catIds) || (it.accountId != null && it.accountId !in accIds) })
            throw BackupFormatException("A bill in the backup is invalid.")
        if (templates.any { it.amountMinor < 0 || (it.categoryId != null && it.categoryId !in catIds) || (it.accountId != null && it.accountId !in accIds) })
            throw BackupFormatException("A shortcut in the backup is invalid.")
        if (goals.any { it.targetMinor <= 0 } || entries.any { it.goalId !in goalIds }) throw BackupFormatException("A savings goal in the backup is invalid.")
    }

    fun isSafeName(name: String): Boolean = name.isNotEmpty() && name.length < 200 && name.matches(Regex("^[A-Za-z0-9._-]+$")) && name != "." && name != ".."
}
