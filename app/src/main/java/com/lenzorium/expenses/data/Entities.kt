package com.lenzorium.expenses.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

enum class TxnType {
    EXPENSE,
    INCOME,
    LOAN_RECEIVED,
    LOAN_REPAYMENT,
    LOAN_CHARGE,
    /** Money moved between two of the user's accounts (e.g. ATM withdrawal). Total balance is unchanged. */
    TRANSFER;

    val isLoanRelated: Boolean get() = this == LOAN_RECEIVED || this == LOAN_REPAYMENT || this == LOAN_CHARGE

    val label: String
        get() = when (this) {
            EXPENSE -> "Expense"
            INCOME -> "Income"
            LOAN_RECEIVED -> "Loan received"
            LOAN_REPAYMENT -> "Loan repayment"
            LOAN_CHARGE -> "Loan charge"
            TRANSFER -> "Transfer"
        }
}

enum class PayMethod {
    CASH, BANK, CARD;

    val label: String
        get() = when (this) {
            CASH -> "Cash"
            BANK -> "Bank"
            CARD -> "Card"
        }
}

enum class CategoryKind { EXPENSE, INCOME }

enum class AccountKind(val label: String) {
    CASH("Cash"),
    BANK("Bank account"),
    WALLET("Mobile wallet"),
    OTHER("Other"),
}

/** A place where money is kept: cash in hand, a bank account, a mobile wallet. */
@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: AccountKind,
    /** Balance on the tracking start date. */
    val openingMinor: Long = 0,
    /** Hidden from pickers but kept so old entries still show where the money went. */
    val archived: Boolean = false,
    val sortOrder: Int = 0,
)

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: CategoryKind,
    val iconKey: String,
    val colorArgb: Long,
    /** "Other" fallback categories cannot be deleted; transactions are moved into them. */
    val isFallback: Boolean = false,
    val sortOrder: Int = 0,
)

@Entity(
    tableName = "loans",
    indices = [Index(value = ["requestId"], unique = true)],
)
data class Loan(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val lender: String,
    val title: String,
    /** Amount received (or, for an existing loan entered at setup, the amount owed when tracking began). */
    val principalMinor: Long,
    /** Epoch millis when the money was received. */
    val receivedAt: Long,
    /** Optional due date as LocalDate.toEpochDay(). */
    val dueEpochDay: Long? = null,
    val notes: String = "",
    /** True for a loan that already existed before tracking began: it adds debt but never cash. */
    val isOpening: Boolean = false,
    val requestId: String,
    val createdAt: Long,
)

@Entity(
    tableName = "transactions",
    indices = [
        Index("loanId"),
        Index("timestamp"),
        Index(value = ["requestId"], unique = true),
    ],
)
data class Txn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: TxnType,
    /** Always positive, in minor currency units (e.g. cents). */
    val amountMinor: Long,
    val categoryId: Long? = null,
    /** Epoch millis. */
    val timestamp: Long,
    /** Merchant for expenses, source for income, lender for loan entries. */
    val party: String = "",
    val paymentMethod: PayMethod? = null,
    val notes: String = "",
    /** File name inside filesDir/receipts, or null. */
    val receiptFile: String? = null,
    val loanId: Long? = null,
    val requestId: String,
    val createdAt: Long,
    /** Account the money left (expense, repayment, transfer source) or arrived in (income, loan received). */
    val accountId: Long? = null,
    /** Destination account of a transfer. */
    val toAccountId: Long? = null,
)

class Converters {
    @TypeConverter fun txnTypeToString(v: TxnType): String = v.name
    @TypeConverter fun stringToTxnType(v: String): TxnType = TxnType.valueOf(v)
    @TypeConverter fun payToString(v: PayMethod?): String? = v?.name
    @TypeConverter fun stringToPay(v: String?): PayMethod? = v?.let { PayMethod.valueOf(it) }
    @TypeConverter fun kindToString(v: CategoryKind): String = v.name
    @TypeConverter fun stringToKind(v: String): CategoryKind = CategoryKind.valueOf(v)
    @TypeConverter fun accountKindToString(v: AccountKind): String = v.name
    @TypeConverter fun stringToAccountKind(v: String): AccountKind = AccountKind.valueOf(v)
}
