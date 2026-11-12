package com.lenzorium.expenses.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A person holding some of our money for safekeeping. */
@Entity(tableName = "safe_people")
data class Holder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String = "",
    val notes: String = "",
    val createdAt: Long,
)

/**
 * Audit trail for safekeeping records: every create, edit, delete and undo, with a readable summary.
 * Kept even after the entry itself is deleted.
 */
@Entity(tableName = "audit_log", indices = [Index("personId")])
data class AuditEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val txnId: Long? = null,
    val personId: Long? = null,
    /** CREATED, EDITED, DELETED, RESTORED, PERSON_EDITED, PERSON_DELETED */
    val action: String,
    val details: String,
    val at: Long,
)
