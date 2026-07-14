package com.expense.tracker.sync

import android.content.Context
import com.expense.tracker.data.SettingsStore
import com.expense.tracker.data.db.AppDatabase
import com.expense.tracker.data.mail.ImapClient
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Shared sync logic used by both the manual sync button and the background worker.
 *
 * Syncs are incremental: after the first full 60-day fetch, only mail newer than
 * the last sync (minus a 1-day overlap for safety) is downloaded, which makes
 * routine syncs take seconds instead of re-reading the whole window.
 * [SYNC_LOGIC_VERSION] forces one full re-fetch whenever search/parsing logic
 * changes in an update, so fixes apply to already-stored history.
 */
object SyncEngine {

    const val RETENTION_DAYS = 60
    const val SYNC_LOGIC_VERSION = 2
    private val OVERLAP_MS = TimeUnit.DAYS.toMillis(1)

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
     * Runs a sync with stored credentials and applies the 60-day retention.
     * @return the number of fetched transactions, or null if not signed in.
     */
    suspend fun sync(context: Context): Int? {
        val settings = SettingsStore(context)
        val email = settings.accountName.first() ?: return null
        val password = settings.appPassword.first() ?: return null
        val dao = AppDatabase.get(context).transactionDao()

        val transactions = ImapClient(email, password).fetchTransactions(sinceFor(settings))
        dao.insertAll(transactions)
        dao.deleteOlderThan(retentionCutoff())
        settings.setLastSync(System.currentTimeMillis())
        settings.setSyncMarker(SYNC_LOGIC_VERSION)
        return transactions.size
    }
}
