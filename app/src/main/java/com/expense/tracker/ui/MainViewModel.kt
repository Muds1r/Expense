package com.expense.tracker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.expense.tracker.data.SettingsStore
import com.expense.tracker.data.db.AppDatabase
import com.expense.tracker.data.db.BankSummary
import com.expense.tracker.data.db.CategoryEntity
import com.expense.tracker.data.db.CategorySummary
import com.expense.tracker.data.db.CounterpartySummary
import com.expense.tracker.data.db.TransactionEntity
import com.expense.tracker.data.db.TxnType
import com.expense.tracker.data.mail.ImapClient
import com.expense.tracker.sync.SyncEngine
import com.expense.tracker.sync.SyncScheduler
import com.expense.tracker.sync.SyncWorker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.TimeUnit

enum class RangePreset(val label: String, val days: Int) {
    TODAY("Today", 0),
    WEEK("7d", 7),
    MONTH("30d", 30),
    ALL("60d", 60)
}

sealed interface SyncState {
    data object Idle : SyncState
    data object Syncing : SyncState
    data class Success(val count: Int, val at: Long) : SyncState
    data class Error(val message: String) : SyncState
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

    init {
        viewModelScope.launch {
            SyncScheduler.manualSyncFlow(getApplication()).collect { infos ->
                val info = infos.firstOrNull() ?: return@collect
                when (info.state) {
                    WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED ->
                        _syncState.value = SyncState.Syncing
                    WorkInfo.State.SUCCEEDED -> {
                        val count = info.outputData.getInt(SyncWorker.KEY_COUNT, 0)
                        val at = info.outputData.getLong(SyncWorker.KEY_AT, System.currentTimeMillis())
                        _syncState.value = SyncState.Success(count, at)
                    }
                    WorkInfo.State.FAILED -> {
                        val err = info.outputData.getString(SyncWorker.KEY_ERROR) ?: "Sync failed"
                        // First connect saves credentials before sync finishes — roll back on failure.
                        if (settings.lastSync.first() == null) {
                            settings.clear()
                            SyncScheduler.cancel(getApplication())
                        }
                        _syncState.value = SyncState.Error(err)
                    }
                    WorkInfo.State.CANCELLED -> _syncState.value = SyncState.Idle
                }
            }
        }
    }

    private fun rangeStart(preset: RangePreset): Long = when (preset) {
        RangePreset.TODAY -> Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        else -> System.currentTimeMillis() - TimeUnit.DAYS.toMillis(preset.days.toLong())
    }

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
        .flatMapLatest { dao.topCounterparties(TxnType.CREDIT, rangeStart(it), Long.MAX_VALUE, limit = 5) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val topPaidTo: StateFlow<List<CounterpartySummary>> = _range
        .flatMapLatest { dao.topCounterparties(TxnType.DEBIT, rangeStart(it), Long.MAX_VALUE, limit = 5) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val mostFrequentPaidTo: StateFlow<List<CounterpartySummary>> = _range
        .flatMapLatest { dao.mostFrequentCounterparties(TxnType.DEBIT, rangeStart(it), Long.MAX_VALUE, limit = 5) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<CategoryEntity>> = dao.allCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categorySummaries: StateFlow<List<CategorySummary>> = _range
        .flatMapLatest { dao.categorySummaries(rangeStart(it), Long.MAX_VALUE) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Stable Room Flow — do not wrap in stateIn here (that caused detail-screen flicker). */
    fun observeTransaction(id: String) = dao.observeTransaction(id)

    /** Instant lookup from lists already on screen — avoids a blank/not-found flash. */
    fun cachedTransaction(id: String): TransactionEntity? =
        transactions.value.find { it.id == id }
            ?: topTransactions.value.find { it.id == id }

    fun setCategory(txnId: String, categoryId: Long?) {
        viewModelScope.launch { dao.setCategory(txnId, categoryId) }
    }

    fun addCategory(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            if (categories.value.any { it.name.equals(trimmed, ignoreCase = true) }) return@launch
            dao.insertCategory(CategoryEntity(name = trimmed))
        }
    }

    fun deleteCategory(id: Long) {
        viewModelScope.launch {
            dao.clearCategoryFromTransactions(id)
            dao.deleteCategory(id)
        }
    }

    fun setCategoryBudget(id: Long, budgetAmount: Double?) {
        viewModelScope.launch { dao.setCategoryBudget(id, budgetAmount) }
    }

    fun observeCategory(id: Long) = dao.observeCategory(id)

    fun transactionsForCategory(categoryId: Long?): Flow<List<TransactionEntity>> =
        _range.flatMapLatest { dao.transactionsForCategory(categoryId, rangeStart(it), Long.MAX_VALUE) }

    fun setRange(preset: RangePreset) {
        _range.value = preset
    }

    /**
     * First-time setup: save credentials, then run a full sync through WorkManager
     * so the long initial fetch survives a locked screen.
     */
    fun connect(email: String, appPassword: String) {
        if (_syncState.value is SyncState.Syncing) return
        val cleanEmail = email.trim()
        val cleanPassword = ImapClient.cleanAppPassword(appPassword)
        if (cleanEmail.isBlank() || cleanPassword.isBlank()) return

        viewModelScope.launch {
            settings.setCredentials(cleanEmail, cleanPassword)
            SyncScheduler.schedulePeriodic(getApplication())
            SyncScheduler.enqueueManual(getApplication(), forceFull = true)
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

    /** Enqueues an expedited WorkManager job — keeps running with screen locked. */
    fun syncNow() {
        if (_syncState.value is SyncState.Syncing) return
        SyncScheduler.enqueueManual(getApplication(), forceFull = false)
    }
}
