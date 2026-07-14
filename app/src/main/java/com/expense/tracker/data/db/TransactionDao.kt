package com.expense.tracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class BankSummary(
    val bank: String,
    val totalIn: Double,
    val totalOut: Double,
    val txnCount: Int
)

data class CounterpartySummary(
    val counterparty: String,
    /** Comma-separated distinct banks this counterparty appeared on. */
    val banks: String,
    val total: Double,
    val txnCount: Int
)

@Dao
interface TransactionDao {

    // REPLACE so that parser improvements retroactively fix already-stored rows
    // on the next sync (the message ID key keeps this duplicate-safe).
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transactions: List<TransactionEntity>)

    @Query("DELETE FROM transactions WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)

    @Query("SELECT * FROM transactions WHERE timestamp BETWEEN :start AND :end ORDER BY timestamp DESC")
    fun transactionsInRange(start: Long, end: Long): Flow<List<TransactionEntity>>

    @Query(
        """
        SELECT bank,
               SUM(CASE WHEN type = 'CREDIT' THEN amount ELSE 0 END) AS totalIn,
               SUM(CASE WHEN type = 'DEBIT' THEN amount ELSE 0 END) AS totalOut,
               COUNT(*) AS txnCount
        FROM transactions
        WHERE timestamp BETWEEN :start AND :end
        GROUP BY bank
        ORDER BY totalOut DESC
        """
    )
    fun bankSummaries(start: Long, end: Long): Flow<List<BankSummary>>

    @Query(
        """
        SELECT counterparty, GROUP_CONCAT(DISTINCT bank) AS banks,
               SUM(amount) AS total, COUNT(*) AS txnCount
        FROM transactions
        WHERE type = :type AND counterparty IS NOT NULL AND timestamp BETWEEN :start AND :end
        GROUP BY counterparty
        ORDER BY total DESC
        LIMIT :limit
        """
    )
    fun topCounterparties(type: TxnType, start: Long, end: Long, limit: Int = 10): Flow<List<CounterpartySummary>>

    @Query(
        """
        SELECT counterparty, GROUP_CONCAT(DISTINCT bank) AS banks,
               SUM(amount) AS total, COUNT(*) AS txnCount
        FROM transactions
        WHERE type = :type AND counterparty IS NOT NULL AND timestamp BETWEEN :start AND :end
        GROUP BY counterparty
        ORDER BY txnCount DESC
        LIMIT :limit
        """
    )
    fun mostFrequentCounterparties(type: TxnType, start: Long, end: Long, limit: Int = 10): Flow<List<CounterpartySummary>>

    @Query(
        """
        SELECT * FROM transactions
        WHERE timestamp BETWEEN :start AND :end
        ORDER BY amount DESC
        LIMIT :limit
        """
    )
    fun topTransactions(start: Long, end: Long, limit: Int = 10): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    fun observeTransaction(id: String): Flow<TransactionEntity?>

    @Query("SELECT COUNT(*) FROM transactions")
    fun count(): Flow<Int>
}
