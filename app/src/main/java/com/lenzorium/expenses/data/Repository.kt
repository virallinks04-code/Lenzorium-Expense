package com.lenzorium.expenses.data

import android.content.Context
import androidx.room.withTransaction
import com.lenzorium.expenses.domain.LedgerMath
import com.lenzorium.expenses.domain.Money
import com.lenzorium.expenses.domain.PlanMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

sealed class SaveResult {
    data class Ok(val id: Long) : SaveResult()
    data class Error(val message: String) : SaveResult()
}

/** Existing loan entered during first-time setup. */
data class OpeningLoanInput(
    val lender: String,
    val title: String,
    val amountMinor: Long,
    val dueEpochDay: Long?,
    val notes: String,
)

class Repository(
    private val context: Context,
    val db: AppDatabase,
    val settings: SettingsStore,
) {
    private val dao = db.dao()

    val txns = dao.observeTxns()
    val loans = dao.observeLoans()
    val categories = dao.observeCategories()
    val accounts = dao.observeAccounts()
    val budgets = dao.observeBudgets()
    val recurring = dao.observeRecurring()
    val templates = dao.observeTemplates()
    val goals = dao.observeGoals()
    val goalEntries = dao.observeGoalEntries()

    val receiptsDir: File get() = File(context.filesDir, "receipts").apply { mkdirs() }
    fun receiptFile(name: String): File = File(receiptsDir, name)

    private fun fmt(minor: Long) = Money.format(minor, settings.current.currency)

    private fun startMillis(): Long =
        LocalDate.ofEpochDay(settings.current.trackingStartEpochDay).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun endOfTodayMillis(): Long =
        LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun checkDate(ts: Long): String? = when {
        ts < startMillis() -> "The date is before your tracking start date (${LocalDate.ofEpochDay(settings.current.trackingStartEpochDay)}). Your opening balance already covers that period."
        ts >= endOfTodayMillis() -> "The date can't be in the future."
        else -> null
    }

    suspend fun ensureDefaultCategories() = withContext(Dispatchers.IO) {
        if (dao.categoryCount() == 0) dao.insertCategories(DefaultCategories.all)
    }

    /**
     * Makes sure Cash and Bank exist. For people upgrading from the version without accounts, the old single
     * opening balance is placed in Cash once, and Home asks them to check the split.
     */
    suspend fun ensureAccounts() = withContext(Dispatchers.IO) {
        db.withTransaction { if (dao.allAccounts().isEmpty()) dao.insertAccounts(DefaultAccounts.all) }
        val s = settings.current
        if (s.setupDone && !s.accountsMigrated) {
            val accs = dao.allAccounts()
            val notice = s.openingBalanceMinor != 0L && accs.all { it.openingMinor == 0L }
            if (notice) {
                val cash = accs.firstOrNull { it.id == AppDatabase.CASH_ID } ?: accs.first()
                dao.updateAccount(cash.copy(openingMinor = s.openingBalanceMinor))
            }
            val hasEntries = dao.allTxns().isNotEmpty()
            settings.update { it.copy(accountsMigrated = true, accountsNotice = notice || hasEntries) }
        }
    }

    private suspend fun checkAccount(id: Long?, what: String): String? {
        if (id == null) return "Choose $what."
        val a = dao.account(id) ?: return "That account no longer exists."
        return if (a.archived) "\"${a.name}\" is archived. Choose another account." else null
    }

    suspend fun completeSetup(
        newSettings: AppSettings,
        pin: String?,
        openingLoans: List<OpeningLoanInput>,
        cashOpening: Long = 0L,
        bankOpening: Long = 0L,
    ) =
        withContext(Dispatchers.IO) {
            val startTs = LocalDate.ofEpochDay(newSettings.trackingStartEpochDay)
                .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            db.withTransaction {
                if (dao.categoryCount() == 0) dao.insertCategories(DefaultCategories.all)
                if (dao.allAccounts().isEmpty()) dao.insertAccounts(DefaultAccounts.all)
                dao.account(AppDatabase.CASH_ID)?.let { dao.updateAccount(it.copy(openingMinor = cashOpening)) }
                dao.account(AppDatabase.BANK_ID)?.let { dao.updateAccount(it.copy(openingMinor = bankOpening)) }
                val now = System.currentTimeMillis()
                dao.insertLoans(openingLoans.map {
                    Loan(
                        lender = it.lender.trim(),
                        title = it.title.trim(),
                        principalMinor = it.amountMinor,
                        receivedAt = startTs,
                        dueEpochDay = it.dueEpochDay,
                        notes = it.notes.trim(),
                        isOpening = true,
                        requestId = java.util.UUID.randomUUID().toString(),
                        createdAt = now,
                    )
                })
            }
            settings.update {
                newSettings.copy(
                    setupDone = true, pinHash = it.pinHash, pinSalt = it.pinSalt,
                    openingBalanceMinor = cashOpening + bankOpening, accountsMigrated = true,
                )
            }
            if (pin != null) settings.setPin(pin)
            settings.update { it.copy(biometricEnabled = newSettings.biometricEnabled && pin != null) }
        }

    /** Saves an expense or income. Inserts when txn.id == 0, otherwise updates. */
    suspend fun saveEntry(txn: Txn, oldReceiptToDelete: String?): SaveResult = withContext(Dispatchers.IO) {
        require(txn.type == TxnType.EXPENSE || txn.type == TxnType.INCOME)
        if (txn.amountMinor <= 0) return@withContext SaveResult.Error("Enter an amount greater than zero.")
        if (txn.categoryId == null) return@withContext SaveResult.Error("Choose a category.")
        checkDate(txn.timestamp)?.let { return@withContext SaveResult.Error(it) }
        val original = if (txn.id != 0L) dao.txn(txn.id) else null
        if (original?.accountId != txn.accountId || original == null) {
            checkAccount(txn.accountId, "an account")?.let { return@withContext SaveResult.Error(it) }
        }
        try {
            val id = db.withTransaction {
                if (txn.id == 0L) {
                    // A repeated tap re-sends the same requestId: return the record already saved.
                    dao.txnByRequest(txn.requestId)?.id ?: dao.insertTxn(txn)
                } else {
                    dao.updateTxn(txn); txn.id
                }
            }
            oldReceiptToDelete?.let { receiptFile(it).delete() }
            SaveResult.Ok(id)
        } catch (e: Exception) {
            SaveResult.Error("Couldn't save: ${e.message ?: "unknown error"}. Your entry is still here; try again.")
        }
    }

    /**
     * Creates or updates a loan. A new (non-opening) loan also creates its linked
     * "loan received" transaction in the same database transaction.
     */
    suspend fun saveLoan(loan: Loan, intoAccount: Long?): SaveResult = withContext(Dispatchers.IO) {
        if (loan.lender.isBlank()) return@withContext SaveResult.Error("Enter the lender's name.")
        if (loan.principalMinor <= 0) return@withContext SaveResult.Error("Enter an amount greater than zero.")
        if (!loan.isOpening) {
            checkDate(loan.receivedAt)?.let { return@withContext SaveResult.Error(it) }
            val current = if (loan.id != 0L) dao.txnsForLoan(loan.id).firstOrNull { it.type == TxnType.LOAN_RECEIVED }?.accountId else null
            if (loan.id == 0L || current != intoAccount) checkAccount(intoAccount, "the account the money went into")?.let { return@withContext SaveResult.Error(it) }
        }
        try {
            db.withTransaction {
                if (loan.id == 0L) {
                    dao.loanByRequest(loan.requestId)?.let { return@withTransaction SaveResult.Ok(it.id) }
                    val id = dao.insertLoan(loan)
                    if (!loan.isOpening) {
                        dao.insertTxn(
                            Txn(
                                type = TxnType.LOAN_RECEIVED,
                                amountMinor = loan.principalMinor,
                                timestamp = loan.receivedAt,
                                party = loan.lender,
                                notes = loan.title,
                                loanId = id,
                                accountId = intoAccount,
                                requestId = loan.requestId + "-received",
                                createdAt = System.currentTimeMillis(),
                            )
                        )
                    }
                    SaveResult.Ok(id)
                } else {
                    val existing = dao.loan(loan.id) ?: return@withTransaction SaveResult.Error("This loan no longer exists.")
                    val fixed = loan.copy(isOpening = existing.isOpening, requestId = existing.requestId, createdAt = existing.createdAt)
                    val history = dao.txnsForLoan(loan.id)
                    LedgerMath.validateLoanTimeline(fixed, history, ::fmt)?.let {
                        return@withTransaction SaveResult.Error("Can't change the loan this way. $it")
                    }
                    dao.updateLoan(fixed)
                    history.firstOrNull { it.type == TxnType.LOAN_RECEIVED }?.let { r ->
                        dao.updateTxn(
                            r.copy(
                                amountMinor = fixed.principalMinor,
                                timestamp = fixed.receivedAt,
                                party = fixed.lender,
                                accountId = intoAccount ?: r.accountId,
                                notes = fixed.title,
                            )
                        )
                    }
                    SaveResult.Ok(loan.id)
                }
            }
        } catch (e: Exception) {
            SaveResult.Error("Couldn't save: ${e.message ?: "unknown error"}. Your entry is still here; try again.")
        }
    }

    /** Saves a repayment or a loan charge, rejecting anything that would over-repay the loan at any point in time. */
    suspend fun saveLoanEvent(txn: Txn): SaveResult = withContext(Dispatchers.IO) {
        require(txn.type == TxnType.LOAN_REPAYMENT || txn.type == TxnType.LOAN_CHARGE)
        if (txn.amountMinor <= 0) return@withContext SaveResult.Error("Enter an amount greater than zero.")
        val loanId = txn.loanId ?: return@withContext SaveResult.Error("Choose a loan.")
        checkDate(txn.timestamp)?.let { return@withContext SaveResult.Error(it) }
        if (txn.type == TxnType.LOAN_REPAYMENT) {
            val before = if (txn.id != 0L) dao.txn(txn.id)?.accountId else null
            if (txn.id == 0L || before != txn.accountId) checkAccount(txn.accountId, "the account you paid from")?.let { return@withContext SaveResult.Error(it) }
        }
        try {
            db.withTransaction {
                val loan = dao.loan(loanId) ?: return@withTransaction SaveResult.Error("This loan no longer exists.")
                if (txn.id == 0L) dao.txnByRequest(txn.requestId)?.let { return@withTransaction SaveResult.Ok(it.id) }
                val others = dao.txnsForLoan(loanId).filter { it.id != txn.id }
                val candidate = txn.copy(party = loan.lender)
                LedgerMath.validateLoanTimeline(loan, others + candidate.copy(id = if (txn.id == 0L) Long.MAX_VALUE else txn.id), ::fmt)
                    ?.let { return@withTransaction SaveResult.Error(it) }
                if (txn.id == 0L) SaveResult.Ok(dao.insertTxn(candidate))
                else { dao.updateTxn(candidate); SaveResult.Ok(txn.id) }
            }
        } catch (e: Exception) {
            SaveResult.Error("Couldn't save: ${e.message ?: "unknown error"}. Your entry is still here; try again.")
        }
    }

    /** Deletes a single transaction. The "loan received" entry can only go with its loan. */
    suspend fun deleteTxn(id: Long): SaveResult = withContext(Dispatchers.IO) {
        try {
            db.withTransaction {
                val t = dao.txn(id) ?: return@withTransaction SaveResult.Ok(id)
                if (t.type == TxnType.LOAN_RECEIVED) {
                    return@withTransaction SaveResult.Error("This entry is the money received for a loan. To remove it, delete the loan from the Loans screen, which also removes its repayments.")
                }
                if (t.type == TxnType.LOAN_CHARGE && t.loanId != null) {
                    val loan = dao.loan(t.loanId)
                    if (loan != null) {
                        val rest = dao.txnsForLoan(t.loanId).filter { it.id != id }
                        LedgerMath.validateLoanTimeline(loan, rest, ::fmt)?.let {
                            return@withTransaction SaveResult.Error("Can't delete this charge: later repayments depend on it. $it")
                        }
                    }
                }
                dao.deleteTxn(t)
                SaveResult.Ok(id)
            }
        } catch (e: Exception) {
            SaveResult.Error("Couldn't delete: ${e.message ?: "unknown error"}")
        }
    }

    /** Deletes a loan together with its full linked history, atomically. */
    suspend fun deleteLoan(id: Long): SaveResult = withContext(Dispatchers.IO) {
        try {
            db.withTransaction {
                val loan = dao.loan(id) ?: return@withTransaction SaveResult.Ok(id)
                dao.deleteTxnsForLoan(id)
                dao.deleteLoan(loan)
                SaveResult.Ok(id)
            }
        } catch (e: Exception) {
            SaveResult.Error("Couldn't delete: ${e.message ?: "unknown error"}")
        }
    }

    /** Undo for [deleteTxn]: puts the exact record back (same id and receipt). */
    suspend fun restoreTxn(t: Txn) = withContext(Dispatchers.IO) {
        db.withTransaction { if (dao.txn(t.id) == null) dao.insertTxn(t) }
    }

    /** Everything needed to undo a loan deletion. */
    suspend fun loanSnapshot(id: Long): Pair<Loan, List<Txn>>? = withContext(Dispatchers.IO) {
        dao.loan(id)?.let { it to dao.txnsForLoan(id) }
    }

    suspend fun restoreLoan(loan: Loan, history: List<Txn>) = withContext(Dispatchers.IO) {
        db.withTransaction {
            if (dao.loan(loan.id) == null) {
                dao.insertLoan(loan)
                dao.insertTxns(history.filter { dao.txn(it.id) == null })
            }
        }
    }

    /** Removes receipt photos no record points to (abandoned forms, deletions older than a day). */
    suspend fun cleanOrphanReceipts() = withContext(Dispatchers.IO) {
        runCatching {
            val used = dao.allTxns().mapNotNull { it.receiptFile }.toSet()
            val cutoff = System.currentTimeMillis() - 24L * 60 * 60 * 1000
            receiptsDir.listFiles()?.forEach { f -> if (f.name !in used && f.lastModified() < cutoff) f.delete() }
        }
    }

    /** Moves money between two accounts. Total balance is unchanged. */
    suspend fun saveTransfer(txn: Txn): SaveResult = withContext(Dispatchers.IO) {
        require(txn.type == TxnType.TRANSFER)
        if (txn.amountMinor <= 0) return@withContext SaveResult.Error("Enter an amount greater than zero.")
        if (txn.accountId == null || txn.toAccountId == null) return@withContext SaveResult.Error("Choose both accounts.")
        if (txn.accountId == txn.toAccountId) return@withContext SaveResult.Error("Choose two different accounts.")
        checkDate(txn.timestamp)?.let { return@withContext SaveResult.Error(it) }
        val before = if (txn.id != 0L) dao.txn(txn.id) else null
        if (before?.accountId != txn.accountId) checkAccount(txn.accountId, "the account to move money from")?.let { return@withContext SaveResult.Error(it) }
        if (before?.toAccountId != txn.toAccountId) checkAccount(txn.toAccountId, "the account to move money to")?.let { return@withContext SaveResult.Error(it) }
        try {
            val id = db.withTransaction {
                if (txn.id == 0L) dao.txnByRequest(txn.requestId)?.id ?: dao.insertTxn(txn)
                else { dao.updateTxn(txn); txn.id }
            }
            SaveResult.Ok(id)
        } catch (e: Exception) {
            SaveResult.Error("Couldn't save: ${e.message ?: "unknown error"}. Your entry is still here; try again.")
        }
    }

    suspend fun addAccount(name: String, kind: AccountKind, openingMinor: Long): SaveResult = withContext(Dispatchers.IO) {
        val n = name.trim()
        if (n.isEmpty()) return@withContext SaveResult.Error("Enter a name.")
        val all = dao.allAccounts()
        if (all.any { it.name.equals(n, ignoreCase = true) }) return@withContext SaveResult.Error("An account with this name already exists.")
        SaveResult.Ok(dao.insertAccount(Account(name = n, kind = kind, openingMinor = openingMinor, sortOrder = (all.maxOfOrNull { it.sortOrder } ?: 0) + 1)))
    }

    suspend fun updateAccount(a: Account): SaveResult = withContext(Dispatchers.IO) {
        val n = a.name.trim()
        if (n.isEmpty()) return@withContext SaveResult.Error("Enter a name.")
        if (dao.allAccounts().any { it.id != a.id && it.name.equals(n, ignoreCase = true) }) return@withContext SaveResult.Error("An account with this name already exists.")
        if (a.archived && dao.allAccounts().count { !it.archived && it.id != a.id } == 0) return@withContext SaveResult.Error("Keep at least one active account.")
        dao.updateAccount(a.copy(name = n)); SaveResult.Ok(a.id)
    }

    /** Deletes an account only if nothing uses it; otherwise it should be archived. */
    suspend fun deleteAccount(a: Account): SaveResult = withContext(Dispatchers.IO) {
        if (dao.accountUseCount(a.id) > 0) return@withContext SaveResult.Error("This account has entries, so it can't be deleted. Archive it instead to hide it.")
        if (dao.allAccounts().count { !it.archived && it.id != a.id } == 0) return@withContext SaveResult.Error("Keep at least one active account.")
        dao.deleteAccount(a); SaveResult.Ok(a.id)
    }

    suspend fun txn(id: Long): Txn? = withContext(Dispatchers.IO) { dao.txn(id) }
    suspend fun loan(id: Long): Loan? = withContext(Dispatchers.IO) { dao.loan(id) }

    // Categories
    suspend fun addCategory(name: String, kind: CategoryKind, iconKey: String, color: Long): SaveResult = withContext(Dispatchers.IO) {
        val n = name.trim()
        if (n.isEmpty()) return@withContext SaveResult.Error("Enter a name.")
        val existing = dao.allCategories()
        if (existing.any { it.kind == kind && it.name.equals(n, ignoreCase = true) }) return@withContext SaveResult.Error("A category with this name already exists.")
        SaveResult.Ok(dao.insertCategory(Category(name = n, kind = kind, iconKey = iconKey, colorArgb = color, sortOrder = 100)))
    }

    suspend fun updateCategory(c: Category): SaveResult = withContext(Dispatchers.IO) {
        if (c.name.isBlank()) return@withContext SaveResult.Error("Enter a name.")
        val clash = dao.allCategories().any { it.id != c.id && it.kind == c.kind && it.name.equals(c.name.trim(), ignoreCase = true) }
        if (clash) return@withContext SaveResult.Error("A category with this name already exists.")
        dao.updateCategory(c.copy(name = c.name.trim())); SaveResult.Ok(c.id)
    }

    /** Deletes a category and moves its transactions into that kind's "Other" category. */
    suspend fun deleteCategory(c: Category): SaveResult = withContext(Dispatchers.IO) {
        if (c.isFallback) return@withContext SaveResult.Error("This category is the fallback for its type and can't be deleted.")
        db.withTransaction {
            val fallback = dao.allCategories().firstOrNull { it.kind == c.kind && it.isFallback }
                ?: return@withTransaction SaveResult.Error("No fallback category found.")
            dao.moveCategory(c.id, fallback.id)
            dao.moveRecurringCategory(c.id, fallback.id)
            dao.moveTemplateCategory(c.id, fallback.id)
            dao.deleteBudgetsForCategory(c.id)
            dao.deleteCategory(c)
            SaveResult.Ok(c.id)
        }
    }

    /** Changing the tracking start is only allowed if no entries are dated before it. */
    suspend fun canMoveTrackingStart(newStartEpochDay: Long): Boolean = withContext(Dispatchers.IO) {
        val ms = LocalDate.ofEpochDay(newStartEpochDay).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        dao.allTxns().none { it.timestamp < ms }
    }

    // ---------- Budgets ----------

    suspend fun saveBudget(b: Budget): SaveResult = withContext(Dispatchers.IO) {
        if (b.limitMinor <= 0) return@withContext SaveResult.Error("Enter a monthly limit greater than zero.")
        val all = dao.allBudgets()
        if (all.any { it.id != b.id && it.categoryId == b.categoryId })
            return@withContext SaveResult.Error(if (b.categoryId == null) "You already have an overall budget. Edit it instead." else "This category already has a budget. Edit it instead.")
        if (b.categoryId != null && dao.allCategories().none { it.id == b.categoryId && it.kind == CategoryKind.EXPENSE })
            return@withContext SaveResult.Error("Choose an expense category.")
        if (b.id == 0L) SaveResult.Ok(dao.insertBudget(b)) else { dao.updateBudget(b); SaveResult.Ok(b.id) }
    }

    suspend fun deleteBudget(id: Long) = withContext(Dispatchers.IO) { dao.deleteBudget(id) }

    // ---------- Bills, subscriptions and regular income ----------

    suspend fun saveRecurring(r: Recurring): SaveResult = withContext(Dispatchers.IO) {
        if (r.name.isBlank()) return@withContext SaveResult.Error("Enter a name, e.g. Electricity or Netflix.")
        if (r.amountMinor <= 0) return@withContext SaveResult.Error("Enter an amount greater than zero.")
        if (r.type != TxnType.EXPENSE && r.type != TxnType.INCOME) return@withContext SaveResult.Error("Choose bill or income.")
        if (r.categoryId == null) return@withContext SaveResult.Error("Choose a category.")
        if (r.intervalCount !in 1..365) return@withContext SaveResult.Error("Repeat every 1 to 365.")
        if (r.endEpochDay != null && r.endEpochDay < r.startEpochDay) return@withContext SaveResult.Error("The end date is before the first due date.")
        checkAccount(r.accountId, "an account")?.let { return@withContext SaveResult.Error(it) }
        val fixed = r.copy(name = r.name.trim(), party = r.party.trim().ifEmpty { r.name.trim() })
        if (fixed.id == 0L) SaveResult.Ok(dao.insertRecurring(fixed.copy(nextDueEpochDay = fixed.startEpochDay)))
        else {
            val old = dao.recurring(fixed.id) ?: return@withContext SaveResult.Error("This item no longer exists.")
            // A changed schedule restarts from the first due date that hasn't been handled yet.
            val scheduleChanged = old.startEpochDay != fixed.startEpochDay || old.frequency != fixed.frequency || old.intervalCount != fixed.intervalCount
            val next = if (scheduleChanged) {
                val handledUpTo = old.nextDueEpochDay - 1
                if (fixed.startEpochDay > handledUpTo) fixed.startEpochDay else PlanMath.nextAfter(fixed, handledUpTo) ?: fixed.startEpochDay
            } else old.nextDueEpochDay
            dao.updateRecurring(fixed.copy(nextDueEpochDay = next, createdAt = old.createdAt)); SaveResult.Ok(fixed.id)
        }
    }

    suspend fun deleteRecurring(id: Long) = withContext(Dispatchers.IO) { dao.deleteRecurring(id) }

    /**
     * Records the due payment as a normal expense/income entry and moves the item to its next date, in one
     * database transaction. The entry's request id is tied to the due date, so a repeated tap never doubles it.
     */
    suspend fun markRecurringPaid(id: Long, dueEpochDay: Long, amountMinor: Long, accountId: Long?, paidAt: Long): SaveResult =
        withContext(Dispatchers.IO) {
            val r = dao.recurring(id) ?: return@withContext SaveResult.Error("This item no longer exists.")
            if (amountMinor <= 0) return@withContext SaveResult.Error("Enter an amount greater than zero.")
            checkDate(paidAt)?.let { return@withContext SaveResult.Error(it) }
            checkAccount(accountId, "an account")?.let { return@withContext SaveResult.Error(it) }
            try {
                db.withTransaction {
                    val req = "rec-$id-$dueEpochDay"
                    val existing = dao.txnByRequest(req)
                    val txnId = existing?.id ?: dao.insertTxn(
                        Txn(
                            type = r.type, amountMinor = amountMinor, categoryId = r.categoryId, timestamp = paidAt,
                            party = r.party.ifBlank { r.name }, notes = r.name, accountId = accountId,
                            recurringId = r.id, requestId = req, createdAt = System.currentTimeMillis(),
                        )
                    )
                    if (r.nextDueEpochDay == dueEpochDay) advance(r)
                    SaveResult.Ok(txnId)
                }
            } catch (e: Exception) {
                SaveResult.Error("Couldn't save: ${e.message ?: "unknown error"}")
            }
        }

    /** Skips the current due date without recording anything. */
    suspend fun skipRecurring(id: Long): SaveResult = withContext(Dispatchers.IO) {
        db.withTransaction {
            val r = dao.recurring(id) ?: return@withTransaction SaveResult.Error("This item no longer exists.")
            advance(r); SaveResult.Ok(id)
        }
    }

    private suspend fun advance(r: Recurring) {
        val next = PlanMath.nextAfter(r, r.nextDueEpochDay)
        dao.updateRecurring(if (next == null) r.copy(active = false) else r.copy(nextDueEpochDay = next))
    }

    suspend fun recurringItem(id: Long): Recurring? = withContext(Dispatchers.IO) { dao.recurring(id) }

    // ---------- Quick-add templates ----------

    suspend fun saveTemplate(t: Template): SaveResult = withContext(Dispatchers.IO) {
        if (t.name.isBlank()) return@withContext SaveResult.Error("Enter a name for the shortcut.")
        if (t.amountMinor < 0) return@withContext SaveResult.Error("The amount can't be negative.")
        if (t.type != TxnType.EXPENSE && t.type != TxnType.INCOME) return@withContext SaveResult.Error("Shortcuts are for expenses or income.")
        val all = dao.allTemplates()
        if (all.any { it.id != t.id && it.name.equals(t.name.trim(), ignoreCase = true) }) return@withContext SaveResult.Error("A shortcut with this name already exists.")
        val fixed = t.copy(name = t.name.trim(), party = t.party.trim())
        if (fixed.id == 0L) SaveResult.Ok(dao.insertTemplate(fixed.copy(sortOrder = (all.maxOfOrNull { it.sortOrder } ?: 0) + 1)))
        else { dao.updateTemplate(fixed); SaveResult.Ok(fixed.id) }
    }

    suspend fun deleteTemplate(id: Long) = withContext(Dispatchers.IO) { dao.deleteTemplate(id) }
    suspend fun template(id: Long): Template? = withContext(Dispatchers.IO) { dao.template(id) }

    // ---------- Savings goals (earmarks only: no balance changes) ----------

    suspend fun saveGoal(g: Goal): SaveResult = withContext(Dispatchers.IO) {
        if (g.name.isBlank()) return@withContext SaveResult.Error("Enter a name for the goal.")
        if (g.targetMinor <= 0) return@withContext SaveResult.Error("Enter a target greater than zero.")
        val fixed = g.copy(name = g.name.trim())
        if (fixed.id == 0L) SaveResult.Ok(dao.insertGoal(fixed)) else { dao.updateGoal(fixed); SaveResult.Ok(fixed.id) }
    }

    suspend fun deleteGoal(id: Long) = withContext(Dispatchers.IO) {
        db.withTransaction { dao.deleteGoalEntries(id); dao.deleteGoal(id) }
    }

    /** Sets money aside (positive) or takes it back (negative). The goal's total can't drop below zero. */
    suspend fun addGoalEntry(goalId: Long, amountMinor: Long, note: String): SaveResult = withContext(Dispatchers.IO) {
        if (amountMinor == 0L) return@withContext SaveResult.Error("Enter an amount.")
        db.withTransaction {
            dao.goal(goalId) ?: return@withTransaction SaveResult.Error("This goal no longer exists.")
            val saved = dao.allGoalEntries().filter { it.goalId == goalId }.sumOf { it.amountMinor }
            if (saved + amountMinor < 0) return@withTransaction SaveResult.Error("You can take back at most ${fmt(saved)}.")
            SaveResult.Ok(dao.insertGoalEntry(GoalEntry(goalId = goalId, amountMinor = amountMinor, epochDay = LocalDate.now().toEpochDay(), note = note.trim(), createdAt = System.currentTimeMillis())))
        }
    }

    suspend fun deleteGoalEntry(id: Long) = withContext(Dispatchers.IO) { dao.deleteGoalEntry(id) }
}

object DefaultAccounts {
    val all: List<Account> = listOf(
        Account(id = AppDatabase.CASH_ID, name = "Cash", kind = AccountKind.CASH, sortOrder = 1),
        Account(id = AppDatabase.BANK_ID, name = "Bank", kind = AccountKind.BANK, sortOrder = 2),
    )
}

object DefaultCategories {
    // ARGB colours as Long to keep Room/backup simple.
    val all: List<Category> = listOf(
        Category(name = "Food", kind = CategoryKind.EXPENSE, iconKey = "food", colorArgb = 0xFFE4572EL, sortOrder = 1),
        Category(name = "Groceries", kind = CategoryKind.EXPENSE, iconKey = "groceries", colorArgb = 0xFF2E9E5BL, sortOrder = 2),
        Category(name = "Transport", kind = CategoryKind.EXPENSE, iconKey = "transport", colorArgb = 0xFF2D7DD2L, sortOrder = 3),
        Category(name = "Shopping", kind = CategoryKind.EXPENSE, iconKey = "shopping", colorArgb = 0xFFD64FA1L, sortOrder = 4),
        Category(name = "Bills", kind = CategoryKind.EXPENSE, iconKey = "bills", colorArgb = 0xFFF2A007L, sortOrder = 5),
        Category(name = "Rent", kind = CategoryKind.EXPENSE, iconKey = "rent", colorArgb = 0xFF7353BAL, sortOrder = 6),
        Category(name = "Health", kind = CategoryKind.EXPENSE, iconKey = "health", colorArgb = 0xFFD7263DL, sortOrder = 7),
        Category(name = "Education", kind = CategoryKind.EXPENSE, iconKey = "education", colorArgb = 0xFF1B998BL, sortOrder = 8),
        Category(name = "Entertainment", kind = CategoryKind.EXPENSE, iconKey = "entertainment", colorArgb = 0xFFFF7F11L, sortOrder = 9),
        Category(name = "Other", kind = CategoryKind.EXPENSE, iconKey = "other", colorArgb = 0xFF6C757DL, isFallback = true, sortOrder = 99),
        Category(name = "Salary", kind = CategoryKind.INCOME, iconKey = "salary", colorArgb = 0xFF0B6E4FL, sortOrder = 1),
        Category(name = "Business", kind = CategoryKind.INCOME, iconKey = "business", colorArgb = 0xFF2D7DD2L, sortOrder = 2),
        Category(name = "Freelance", kind = CategoryKind.INCOME, iconKey = "freelance", colorArgb = 0xFF7353BAL, sortOrder = 3),
        Category(name = "Gift", kind = CategoryKind.INCOME, iconKey = "gift", colorArgb = 0xFFD64FA1L, sortOrder = 4),
        Category(name = "Other Income", kind = CategoryKind.INCOME, iconKey = "other_income", colorArgb = 0xFF6C757DL, isFallback = true, sortOrder = 99),
    )
}
