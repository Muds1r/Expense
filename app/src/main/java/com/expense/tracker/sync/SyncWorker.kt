package com.expense.tracker.sync

import android.accounts.Account
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.expense.tracker.data.db.AppDatabase
import com.expense.tracker.data.gmail.GmailClient
import java.util.concurrent.TimeUnit

/**
 * Periodic background sync:
 * 1. Fetches the last 60 days of bank alert emails from Gmail (read-only).
 * 2. Inserts new transactions (duplicates ignored via the Gmail message ID key).
 * 3. Deletes local rows older than 60 days. Gmail itself is never touched.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val accountName = inputData.getString(KEY_ACCOUNT_NAME) ?: return Result.failure()
        val dao = AppDatabase.get(applicationContext).transactionDao()

        return try {
            val client = GmailClient(applicationContext, Account(accountName, "com.google"))
            val transactions = client.fetchTransactions(days = RETENTION_DAYS)
            dao.insertAll(transactions)
            dao.deleteOlderThan(retentionCutoff())
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val KEY_ACCOUNT_NAME = "account_name"
        const val RETENTION_DAYS = 60

        fun retentionCutoff(): Long =
            System.currentTimeMillis() - TimeUnit.DAYS.toMillis(RETENTION_DAYS.toLong())
    }
}
