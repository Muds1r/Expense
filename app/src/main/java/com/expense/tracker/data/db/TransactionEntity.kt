package com.expense.tracker.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class TxnType { DEBIT, CREDIT }

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Optional spending limit for this category (PKR). Null = no budget set. */
    val budgetAmount: Double? = null
)

@Entity(
    tableName = "transactions",
    indices = [Index("timestamp"), Index("bank"), Index("counterparty"), Index("categoryId")]
)
data class TransactionEntity(
    /** Gmail message ID — guarantees we never store the same email twice. */
    @PrimaryKey val id: String,
    val bank: String,
    val accountLast4: String?,
    val amount: Double,
    val type: TxnType,
    /** Person / merchant / beneficiary extracted from the email. */
    val counterparty: String?,
    /** Epoch millis of the email's internal date. */
    val timestamp: Long,
    /** Email subject, kept for verification in the detail view. */
    val subject: String,
    /** User-assigned budget category; preserved across syncs. */
    val categoryId: Long? = null,
    /** User note attached to this transaction. */
    val note: String? = null,
    /** True if this transaction was manually added by the user (not synced from email). */
    val isManual: Boolean = false,
    /** True if this is a pass-through / transfer (money in then out — not real income or expense). */
    val isTransfer: Boolean = false
)

@Entity(
    tableName = "transaction_splits",
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentTxnId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("parentTxnId")]
)
data class SplitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** The transaction this split belongs to. */
    val parentTxnId: String,
    /** Amount of this split (must be > 0). */
    val amount: Double,
    /** Optional category for this split. */
    val categoryId: Long? = null,
    /** Optional note explaining this split portion. */
    val note: String? = null
)
