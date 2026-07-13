package com.expense.tracker.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class TxnType { DEBIT, CREDIT }

@Entity(
    tableName = "transactions",
    indices = [Index("timestamp"), Index("bank"), Index("counterparty")]
)
data class TransactionEntity(
    /** Gmail message ID — guarantees we never store the same email twice. */
    @PrimaryKey val id: String,
    val bank: String,
    val accountLast4: String?,
    val amount: Double,
    val type: TxnType,
    /** UPI ID, merchant, or person name extracted from the email. */
    val counterparty: String?,
    /** Epoch millis of the email's internal date. */
    val timestamp: Long,
    /** Email subject, kept for debugging/verification in the detail view. */
    val subject: String
)
