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
import com.expense.tracker.data.db.SplitEntity
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

enum class RangePreset(val label: String, val days: Int) {
    TODAY("Today", 0),
    WEEK("7d", 7),
    MONTH("30d", 30),
    ALL("60d", 60),
    CUSTOM("Custom", -1)
}

data class DailySpending(
    val date: String,
    val dayLabel: String,
    val totalOut: Double,
    val totalIn: Double,
    val txnCount: Int
)

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

    private val _customStart = MutableStateFlow<Long?>(null)
    private val _customEnd = MutableStateFlow<Long?>(null)

    val customStart: StateFlow<Long?> = _customStart
    val customEnd: StateFlow<Long?> = _customEnd

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState

    init {
        viewModelScope.launch {
            val saved = settings.getCustomRange()
            if (saved != null) {
                _customStart.value = saved.first
                _customEnd.value = saved.second
            }
        }

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

    private fun computeRangeStart(preset: RangePreset): Long = when (preset) {
        RangePreset.TODAY -> Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        RangePreset.CUSTOM -> _customStart.value
            ?: (System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30))
        else -> System.currentTimeMillis() - TimeUnit.DAYS.toMillis(preset.days.toLong())
    }

    private fun computeRangeEnd(preset: RangePreset): Long = when (preset) {
        RangePreset.CUSTOM -> _customEnd.value ?: Long.MAX_VALUE
        else -> Long.MAX_VALUE
    }

    private val _rangeStart = _range.combine(_customStart) { preset, _ ->
        computeRangeStart(preset)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30))

    private val _rangeEnd = _range.combine(_customEnd) { preset, _ ->
        computeRangeEnd(preset)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Long.MAX_VALUE)

    // ─── Dashboard / summary flows (exclude transfers) ─────────────────

    val bankSummaries: StateFlow<List<BankSummary>> = _rangeStart
        .combine(_rangeEnd) { s, e -> s to e }
        .flatMapLatest { (s, e) -> dao.bankSummaries(s, e) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val transactions: StateFlow<List<TransactionEntity>> = _rangeStart
        .combine(_rangeEnd) { s, e -> s to e }
        .flatMapLatest { (s, e) -> dao.transactionsInRange(s, e) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTransactions: StateFlow<List<TransactionEntity>> = _rangeStart
        .combine(_rangeEnd) { s, e -> s to e }
        .flatMapLatest { (s, e) -> dao.allTransactionsInRange(s, e) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val topTransactions: StateFlow<List<TransactionEntity>> = _rangeStart
        .combine(_rangeEnd) { s, e -> s to e }
        .flatMapLatest { (s, e) -> dao.topTransactions(s, e) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val topReceivedFrom: StateFlow<List<CounterpartySummary>> = _rangeStart
        .combine(_rangeEnd) { s, e -> s to e }
        .flatMapLatest { (s, e) -> dao.topCounterparties(TxnType.CREDIT, s, e, limit = 5) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val topPaidTo: StateFlow<List<CounterpartySummary>> = _rangeStart
        .combine(_rangeEnd) { s, e -> s to e }
        .flatMapLatest { (s, e) -> dao.topCounterparties(TxnType.DEBIT, s, e, limit = 5) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val mostFrequentPaidTo: StateFlow<List<CounterpartySummary>> = _rangeStart
        .combine(_rangeEnd) { s, e -> s to e }
        .flatMapLatest { (s, e) -> dao.mostFrequentCounterparties(TxnType.DEBIT, s, e, limit = 5) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<CategoryEntity>> = dao.allCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categorySummaries: StateFlow<List<CategorySummary>> = _rangeStart
        .combine(_rangeEnd) { s, e -> s to e }
        .flatMapLatest { (s, e) -> dao.categorySummaries(s, e) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ─── Analytics: daily spending ─────────────────────────────────────

    val dailySpend: StateFlow<List<DailySpending>> = _rangeStart
        .combine(_rangeEnd) { s, e -> s to e }
        .flatMapLatest { (start, end) ->
            dao.allTransactionsInRange(start, end)
        }
        .combine(_range) { txns, _ ->
            val sdf = SimpleDateFormat("dd MMM", Locale.getDefault())
            val labelFormat = SimpleDateFormat("EEE", Locale.getDefault())
            txns
                .groupBy { sdf.format(Date(it.timestamp)) }
                .map { (date, dayTxns) ->
                    DailySpending(
                        date = date,
                        dayLabel = labelFormat.format(Date(dayTxns.first().timestamp)),
                        totalOut = dayTxns.filter { it.type == TxnType.DEBIT && !it.isTransfer }.sumOf { it.amount },
                        totalIn = dayTxns.filter { it.type == TxnType.CREDIT && !it.isTransfer }.sumOf { it.amount },
                        txnCount = dayTxns.size
                    )
                }
                .sortedBy { it.date }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ─── Analytics: category breakdown for pie/donut ───────────────────

    val categoryBreakdown: StateFlow<List<CategorySummary>> = categorySummaries

    // ─── Analytics: income vs expense totals ───────────────────────────

    val totalIncome: StateFlow<Double> = bankSummaries
        .map { summaries -> summaries.sumOf { it.totalIn } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val totalExpense: StateFlow<Double> = bankSummaries
        .map { summaries -> summaries.sumOf { it.totalOut } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // ─── Transaction detail helpers ────────────────────────────────────

    fun observeTransaction(id: String) = dao.observeTransaction(id)

    fun cachedTransaction(id: String): TransactionEntity? =
        transactions.value.find { it.id == id }
            ?: topTransactions.value.find { it.id == id }
            ?: allTransactions.value.find { it.id == id }

    fun setCategory(txnId: String, categoryId: Long?) {
        viewModelScope.launch { dao.setCategory(txnId, categoryId) }
    }

    // ─── Notes ─────────────────────────────────────────────────────────

    fun setNote(txnId: String, note: String?) {
        viewModelScope.launch { dao.setNote(txnId, note) }
    }

    // ─── Transfer toggle ───────────────────────────────────────────────

    fun setTransfer(txnId: String, isTransfer: Boolean) {
        viewModelScope.launch { dao.setTransfer(txnId, isTransfer) }
    }

    // ─── Manual transactions ───────────────────────────────────────────

    fun addManualTransaction(
        bank: String,
        counterparty: String?,
        amount: Double,
        type: TxnType,
        note: String?,
        categoryId: Long?
    ) {
        viewModelScope.launch {
            val txn = TransactionEntity(
                id = "manual-${UUID.randomUUID()}",
                bank = bank,
                accountLast4 = null,
                amount = amount,
                type = type,
                counterparty = counterparty?.ifBlank { null },
                timestamp = System.currentTimeMillis(),
                subject = "Manual entry",
                categoryId = categoryId,
                note = note?.ifBlank { null },
                isManual = true
            )
            dao.insertTransaction(txn)
        }
    }

    fun deleteManualTransaction(txnId: String) {
        viewModelScope.launch { dao.deleteManualTransaction(txnId) }
    }

    // ─── Splits ────────────────────────────────────────────────────────

    fun splitsForTransaction(txnId: String): Flow<List<SplitEntity>> =
        dao.splitsForTransaction(txnId)

    fun addSplit(txnId: String, amount: Double, categoryId: Long?, note: String?) {
        viewModelScope.launch {
            dao.insertSplit(
                SplitEntity(
                    parentTxnId = txnId,
                    amount = amount,
                    categoryId = categoryId,
                    note = note?.ifBlank { null }
                )
            )
        }
    }

    fun updateSplit(splitId: Long, amount: Double, categoryId: Long?, note: String?) {
        viewModelScope.launch { dao.updateSplit(splitId, amount, categoryId, note?.ifBlank { null }) }
    }

    fun deleteSplit(splitId: Long) {
        viewModelScope.launch { dao.deleteSplit(splitId) }
    }

    // ─── Category management ───────────────────────────────────────────

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
        _rangeStart.combine(_rangeEnd) { s, e -> s to e }
            .flatMapLatest { (s, e) -> dao.transactionsForCategory(categoryId, s, e) }

    // ─── Range / custom dates ──────────────────────────────────────────

    fun setRange(preset: RangePreset) {
        _range.value = preset
    }

    fun setCustomRange(start: Long, end: Long) {
        _customStart.value = start
        _customEnd.value = end
        _range.value = RangePreset.CUSTOM
        viewModelScope.launch { settings.setCustomRange(start, end) }
    }

    // ─── Sync / auth ───────────────────────────────────────────────────

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
            SyncEngine.invalidate()
            SyncScheduler.cancel(getApplication())
            settings.clear()
            dao.deleteOlderThan(Long.MAX_VALUE)
            _syncState.value = SyncState.Idle
        }
    }

    fun syncNow() {
        if (_syncState.value is SyncState.Syncing) return
        SyncScheduler.enqueueManual(getApplication(), forceFull = false)
    }
}
