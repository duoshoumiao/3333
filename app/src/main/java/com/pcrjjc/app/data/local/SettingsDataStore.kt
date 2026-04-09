package com.pcrjjc.app.data.local  
  
import android.content.Context  
import androidx.datastore.core.DataStore  
import androidx.datastore.preferences.core.Preferences  
import androidx.datastore.preferences.core.booleanPreferencesKey  
import androidx.datastore.preferences.core.edit  
import androidx.datastore.preferences.core.longPreferencesKey  
import androidx.datastore.preferences.preferencesDataStore  
import kotlinx.coroutines.flow.Flow  
import kotlinx.coroutines.flow.first  
import kotlinx.coroutines.flow.map  
  
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")  
  
class SettingsDataStore(private val context: Context) {  
  
    companion object {  
        private val KEY_POLLING_INTERVAL = longPreferencesKey("polling_interval_seconds")  
        private val KEY_MONITORING_ENABLED = booleanPreferencesKey("is_monitoring_enabled")  
    }  
  
    val pollingIntervalFlow: Flow<Long> = context.dataStore.data.map { prefs ->  
        prefs[KEY_POLLING_INTERVAL] ?: 30L  
    }  
  
    val isMonitoringEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->  
        prefs[KEY_MONITORING_ENABLED] ?: false  
    }  
  
    suspend fun setPollingInterval(seconds: Long) {  
        context.dataStore.edit { prefs ->  
            prefs[KEY_POLLING_INTERVAL] = seconds  
        }  
    }  
  
    suspend fun setMonitoringEnabled(enabled: Boolean) {  
        context.dataStore.edit { prefs ->  
            prefs[KEY_MONITORING_ENABLED] = enabled  
        }  
    }  
  
    suspend fun getPollingIntervalSync(): Long {  
        return context.dataStore.data.first()[KEY_POLLING_INTERVAL] ?: 30L  
    }  
  
    suspend fun isMonitoringEnabledSync(): Boolean {  
        return context.dataStore.data.first()[KEY_MONITORING_ENABLED] ?: false  