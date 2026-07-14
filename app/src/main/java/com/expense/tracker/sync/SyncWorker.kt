package com.expense.tracker.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.expense.tracker.data.SettingsStore
import com.expense.tracker.data.db.AppDatabase
import com.expense.tracker.data.mail.ImapClient
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Periodic background sync:
 * 1. Fetches the last 60 days of bank alert emails from Gmail over IMAP (read-only).
 * 2. Inserts new transactions (duplicates ignored via the message ID key).
 * 3. Deletes local rows older than 60 days. Gmail itself is never touched.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = SettingsStore(applicationContext)
        val email = settings.accountName.first() ?: return Result.failure()
        val password = settings.appPassword.first() ?: return Result.failure()
        val dao = AppDatabase.get(applicationContext).transactionDao()

        return try {
            val transactions = ImapClient(email, password).fetchTransactions(RETENTION_DAYS)
            dao.insertAll(transactions)
            dao.deleteOlderThan(retentionCutoff())
            settings.setLastSync(System.currentTimeMillis())
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val RETENTION_DAYS = 60

        fun retentionCutoff(): Long =
            System.currentTimeMillis() - TimeUnit.DAYS.toMillis(RETENTION_DAYS.toLong())
    }
}
