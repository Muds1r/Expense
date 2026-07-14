package com.expense.tracker.sync

import android.content.Context
import com.expense.tracker.data.SettingsStore
import com.expense.tracker.data.db.AppDatabase
import com.expense.tracker.data.mail.ImapClient
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Shared sync logic used by both the manual sync button and the background worker.
 *
 * Syncs are incremental: after the first full 60-day fetch, only mail newer than
 * the last sync (minus a 1-day overlap for safety) is downloaded.
 * [invalidate] bumps a generation so an in-flight sync cannot write after sign-out.
 */
object SyncEngine {

    const val RETENTION_DAYS = 60
    const val SYNC_LOGIC_VERSION = 4
    private val OVERLAP_MS = TimeUnit.DAYS.toMillis(1)
    private val generation = AtomicLong(0)

    /** Call on sign-out so any running sync abandons before writing. */
    fun invalidate() {
        generation.incrementAndGet()
    }

    fun retentionCutoff(): Long =
        System.currentTimeMillis() - TimeUnit.DAYS.toMillis(RETENTION_DAYS.toLong())

    private suspend fun sinceFor(settings: SettingsStore): Long {
        val full = retentionCutoff()
        val marker = settings.syncMarker.first()
        val last = settings.lastSync.first()
        return if (marker != SYNC_LOGIC_VERSION || last == null) full
        else maxOf(full, last - OVERLAP_MS)
    }

    /**
     * @return fetched count, or null if not signed in / cancelled mid-flight.
     */
    suspend fun sync(context: Context, forceFull: Boolean = false): Int? {
        val gen = generation.get()
        val settings = SettingsStore(context)
        val email = settings.accountName.first() ?: return null
        val password = settings.appPassword.first() ?: return null
        val dao = AppDatabase.get(context).transactionDao()

        val since = if (forceFull) retentionCutoff() else sinceFor(settings)
        val transactions = ImapClient(email, password).fetchTransactions(since)

        // Signed out (or another invalidate) while we were downloading.
        if (generation.get() != gen || settings.accountName.first() != email) {
            throw SyncCancelledException()
        }

        dao.upsertParsed(transactions)
        dao.deleteOlderThan(retentionCutoff())

        if (generation.get() != gen || settings.accountName.first() != email) {
            throw SyncCancelledException()
        }

        settings.setLastSync(System.currentTimeMillis())
        settings.setSyncMarker(SYNC_LOGIC_VERSION)

        BudgetAlerts.checkAndNotify(context)
        return transactions.size
    }
}

/** Thrown when sign-out (or invalidate) happens mid-sync — not a real failure. */
class SyncCancelledException : Exception("Sync cancelled")

