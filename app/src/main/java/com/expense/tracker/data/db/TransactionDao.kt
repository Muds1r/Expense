package com.expense.tracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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

data class CategorySummary(
    val categoryId: Long?,
    val categoryName: String,
    val totalIn: Double,
    val totalOut: Double,
    val txnCount: Int,
    val budgetAmount: Double?
)

@Dao
interface TransactionDao {

    // ─── Synced transactions ───────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(transactions: List<TransactionEntity>)

    @Query(
        """
        UPDATE transactions SET bank = :bank, accountLast4 = :accountLast4, amount = :amount,
               type = :type, counterparty = :counterparty, timestamp = :timestamp, subject = :subject
        WHERE id = :id
        """
    )
    suspend fun updateParsedFields(
        id: String,
        bank: String,
        accountLast4: String?,
        amount: Double,
        type: TxnType,
        counterparty: String?,
        timestamp: Long,
        subject: String
    )

    @Transaction
    suspend fun upsertParsed(transactions: List<TransactionEntity>) {
        insertIgnore(transactions)
        transactions.forEach { t ->
            updateParsedFields(
                id = t.id,
                bank = t.bank,
                accountLast4 = t.accountLast4,
                amount = t.amount,
                type = t.type,
                counterparty = t.counterparty,
                timestamp = t.timestamp,
                subject = t.subject
            )
        }
    }

    // ─── Category assignment ───────────────────────────────────────────

    @Query("UPDATE transactions SET categoryId = :categoryId WHERE id = :id")
    suspend fun setCategory(id: String, categoryId: Long?)

    @Query("DELETE FROM transactions WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)

    // ─── Note ──────────────────────────────────────────────────────────

    @Query("UPDATE transactions SET note = :note WHERE id = :id")
    suspend fun setNote(id: String, note: String?)

    // ─── Transfer toggle ───────────────────────────────────────────────

    @Query("UPDATE transactions SET isTransfer = :isTransfer WHERE id = :id")
    suspend fun setTransfer(id: String, isTransfer: Boolean)

    // ─── Manual transactions ───────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(txn: TransactionEntity): Long

    @Query("DELETE FROM transactions WHERE id = :id AND isManual = 1")
    suspend fun deleteManualTransaction(id: String)

    // ─── Splits ────────────────────────────────────────────────────────

    @Insert
    suspend fun insertSplit(split: SplitEntity): Long

    @Query("DELETE FROM splits WHERE id = :splitId")
    suspend fun deleteSplit(splitId: Long)

    @Query("DELETE FROM splits WHERE parentTxnId = :txnId")
    suspend fun deleteSplitsForTransaction(txnId: String)

    @Query("SELECT * FROM splits WHERE parentTxnId = :txnId ORDER BY id ASC")
    fun splitsForTransaction(txnId: String): Flow<List<SplitEntity>>

    @Query("SELECT * FROM splits WHERE parentTxnId = :txnId ORDER BY id ASC")
    suspend fun getSplitsForTransaction(txnId: String): List<SplitEntity>

    @Query("SELECT * FROM splits WHERE id = :id")
    suspend fun getSplit(id: Long): SplitEntity?

    @Query("UPDATE splits SET amount = :amount, categoryId = :categoryId, note = :note WHERE id = :id")
    suspend fun updateSplit(id: Long, amount: Double, categoryId: Long?, note: String?)

    // ─── Queries: in-range transactions (exclude transfers in summaries) ──

    @Query(
        """
        SELECT * FROM transactions
        WHERE timestamp BETWEEN :start AND :end AND isTransfer = 0
        ORDER BY timestamp DESC
        """
    )
    fun transactionsInRange(start: Long, end: Long): Flow<List<TransactionEntity>>

    /** All transactions including transfers (for the transactions tab list). */
    @Query(
        """
        SELECT * FROM transactions
        WHERE timestamp BETWEEN :start AND :end
        ORDER BY timestamp DESC
        """
    )
    fun allTransactionsInRange(start: Long, end: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    fun observeTransaction(id: String): Flow<TransactionEntity?>

    @Query(
        """
        SELECT bank,
               SUM(CASE WHEN type = 'CREDIT' THEN amount ELSE 0 END) AS totalIn,
               SUM(CASE WHEN type = 'DEBIT' THEN amount ELSE 0 END) AS totalOut,
               COUNT(*) AS txnCount
        FROM transactions
        WHERE timestamp BETWEEN :start AND :end AND isTransfer = 0
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
          AND isTransfer = 0
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
          AND isTransfer = 0
        GROUP BY counterparty
        ORDER BY txnCount DESC
        LIMIT :limit
        """
    )
    fun mostFrequentCounterparties(type: TxnType, start: Long, end: Long, limit: Int = 10): Flow<List<CounterpartySummary>>

    @Query(
        """
        SELECT * FROM transactions
        WHERE timestamp BETWEEN :start AND :end AND isTransfer = 0
        ORDER BY amount DESC
        LIMIT :limit
        """
    )
    fun topTransactions(start: Long, end: Long, limit: Int = 10): Flow<List<TransactionEntity>>

    @Query(
        """
        SELECT t.categoryId AS categoryId,
               COALESCE(c.name, 'Uncategorized') AS categoryName,
               SUM(CASE WHEN t.type = 'CREDIT' THEN t.amount ELSE 0 END) AS totalIn,
               SUM(CASE WHEN t.type = 'DEBIT' THEN t.amount ELSE 0 END) AS totalOut,
               COUNT(*) AS txnCount,
               c.budgetAmount AS budgetAmount
        FROM transactions t
        LEFT JOIN categories c ON c.id = t.categoryId
        WHERE t.timestamp BETWEEN :start AND :end AND t.isTransfer = 0
        GROUP BY t.categoryId, categoryName, c.budgetAmount
        ORDER BY totalOut DESC, totalIn DESC
        """
    )
    fun categorySummaries(start: Long, end: Long): Flow<List<CategorySummary>>

    @Query(
        """
        SELECT * FROM transactions
        WHERE ((:categoryId IS NULL AND categoryId IS NULL) OR categoryId = :categoryId)
          AND timestamp BETWEEN :start AND :end
        ORDER BY timestamp DESC
        """
    )
    fun transactionsForCategory(categoryId: Long?, start: Long, end: Long): Flow<List<TransactionEntity>>

    // ─── Category management ───────────────────────────────────────────

    @Query("SELECT * FROM categories WHERE id = :id")
    fun observeCategory(id: Long): Flow<CategoryEntity?>

    @Query("UPDATE categories SET budgetAmount = :budgetAmount WHERE id = :id")
    suspend fun setCategoryBudget(id: Long, budgetAmount: Double?)

    @Query("SELECT COUNT(*) FROM transactions")
    fun count(): Flow<Int>

    @Insert
    suspend fun insertCategory(category: CategoryEntity): Long

    @Query("SELECT * FROM categories ORDER BY name ASC")
    fun allCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getCategory(id: Long): CategoryEntity?

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteCategory(id: Long)

    @Query("UPDATE transactions SET categoryId = NULL WHERE categoryId = :id")
    suspend fun clearCategoryFromTransactions(id: Long)

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun categoryCount(): Int
}
