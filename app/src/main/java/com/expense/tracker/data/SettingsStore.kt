package com.expense.tracker.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {

    private val accountKey = stringPreferencesKey("account_name")
    private val lastSyncKey = longPreferencesKey("last_sync")

    val accountName: Flow<String?> = context.dataStore.data.map { it[accountKey] }
    val lastSync: Flow<Long?> = context.dataStore.data.map { it[lastSyncKey] }

    suspend fun setAccountName(name: String) {
        context.dataStore.edit { it[accountKey] = name }
    }

    suspend fun setLastSync(time: Long) {
        context.dataStore.edit { it[lastSyncKey] = time }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}
