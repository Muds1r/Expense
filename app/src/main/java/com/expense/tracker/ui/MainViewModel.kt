package com.expense.tracker.ui

import android.accounts.Account
import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.expense.tracker.data.SettingsStore
import com.expense.tracker.data.db.AppDatabase
import com.expense.tracker.data.db.BankSummary
import com.expense.tracker.data.db.CounterpartySummary
import com.expense.tracker.data.db.TransactionEntity
import com.expense.tracker.data.db.TxnType
import com.expense.tracker.data.gmail.GmailClient
import com.expense.tracker.sync.SyncScheduler
import com.expense.tracker.sync.SyncWorker
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

enum class RangePreset(val label: String, val days: Int) {
    WEEK("7 days", 7),
    MONTH("30 days", 30),
    ALL("60 days", 60)
}

sealed interface SyncState {
    data object Idle : SyncState
    data object Syncing : SyncState
    data class Success(val count: Int, val at: Long) : SyncState
    data class Error(val message: String) : SyncState
    /** Gmail needs a one-time consent screen; launch [intent] from the Activity. */
    data class NeedsConsent(val intent: Intent) : SyncState
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = AppDatabase.get(app).transactionDao()
    private val settings = SettingsStore(app)

    val accountName: StateFlow<String?> = settings.accountName
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val lastSync: StateFlow<Long?> = settings.lastSync
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _range = MutableStateFlow(RangePreset.MONTH)
    val range: StateFlow<RangePreset> = _range

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState

    private fun rangeStart(preset: RangePreset): Long =
        System.currentTimeMillis() - TimeUnit.DAYS.toMillis(preset.days.toLong())

    val bankSummaries: StateFlow<List<BankSummary>> = _range
        .flatMapLatest { dao.bankSummaries(rangeStart(it), Long.MAX_VALUE) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val transactions: StateFlow<List<TransactionEntity>> = _range
        .flatMapLatest { dao.transactionsInRange(rangeStart(it), Long.MAX_VALUE) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val topTransactions: StateFlow<List<TransactionEntity>> = _range
        .flatMapLatest { dao.topTransactions(rangeStart(it), Long.MAX_VALUE) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val topReceivedFrom: StateFlow<List<CounterpartySummary>> = _range
        .flatMapLatest { dao.topCounterparties(TxnType.CREDIT, rangeStart(it), Long.MAX_VALUE) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val topPaidTo: StateFlow<List<CounterpartySummary>> = _range
        .flatMapLatest { dao.topCounterparties(TxnType.DEBIT, rangeStart(it), Long.MAX_VALUE) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val mostFrequentPaidTo: StateFlow<List<CounterpartySummary>> = _range
        .flatMapLatest { dao.mostFrequentCounterparties(TxnType.DEBIT, rangeStart(it), Long.MAX_VALUE) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setRange(preset: RangePreset) {
        _range.value = preset
    }

    fun onSignedIn(name: String) {
        viewModelScope.launch {
            settings.setAccountName(name)
            SyncScheduler.schedulePeriodic(getApplication(), name)
            syncNow()
        }
    }

    fun signOut() {
        viewModelScope.launch {
            SyncScheduler.cancel(getApplication())
            settings.clear()
            dao.deleteOlderThan(Long.MAX_VALUE)
            _syncState.value = SyncState.Idle
        }
    }

    fun syncNow() {
        val name = accountName.value ?: return
        if (_syncState.value is SyncState.Syncing) return

        viewModelScope.launch {
            _syncState.value = SyncState.Syncing
            try {
                val client = GmailClient(getApplication(), Account(name, "com.google"))
                val txns = client.fetchTransactions(days = SyncWorker.RETENTION_DAYS)
                dao.insertAll(txns)
                dao.deleteOlderThan(SyncWorker.retentionCutoff())
                val now = System.currentTimeMillis()
                settings.setLastSync(now)
                _syncState.value = SyncState.Success(txns.size, now)
            } catch (e: UserRecoverableAuthIOException) {
                _syncState.value = SyncState.NeedsConsent(e.intent)
            } catch (e: Exception) {
                _syncState.value = SyncState.Error(e.message ?: "Sync failed")
            }
        }
    }
}
