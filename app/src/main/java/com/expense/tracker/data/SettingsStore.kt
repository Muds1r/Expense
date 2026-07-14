package com.expense.tracker.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.Flow

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {

    private val accountKey = stringPreferencesKey("account_name")
    private val appPasswordKey = stringPreferencesKey("app_password")
    private val lastSyncKey = longPreferencesKey("last_sync")
    private val syncMarkerKey = intPreferencesKey("sync_logic_version")

    val accountName: Flow<String?> = context.dataStore.data.map { it[accountKey] }
    val appPassword: Flow<String?> = context.dataStore.data.map { it[appPasswordKey] }
    val lastSync: Flow<Long?> = context.dataStore.data.map { it[lastSyncKey] }
    val syncMarker: Flow<Int?> = context.dataStore.data.map { it[syncMarkerKey] }

    suspend fun setCredentials(email: String, appPassword: String) {
        context.dataStore.edit {
            it[accountKey] = email
            it[appPasswordKey] = appPassword
        }
    }

    suspend fun setLastSync(time: Long) {
        context.dataStore.edit { it[lastSyncKey] = time }
    }

    suspend fun setSyncMarker(version: Int) {
        context.dataStore.edit { it[syncMarkerKey] = version }
    }

    suspend fun budgetAlertLevel(categoryId: Long, periodKey: String): Int {
        val key = intPreferencesKey("budget_alert_${categoryId}_$periodKey")
        return context.dataStore.data.map { it[key] ?: 0 }.first()
    }

    suspend fun setBudgetAlertLevel(categoryId: Long, periodKey: String, level: Int) {
        val key = intPreferencesKey("budget_alert_${categoryId}_$periodKey")
        context.dataStore.edit { it[key] = level }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}
