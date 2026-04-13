package com.pcrjjc.app.data.local    
  
import android.content.Context    
import androidx.datastore.core.DataStore    
import androidx.datastore.preferences.core.Preferences    
import androidx.datastore.preferences.core.booleanPreferencesKey    
import androidx.datastore.preferences.core.edit    
import androidx.datastore.preferences.core.longPreferencesKey    
import androidx.datastore.preferences.core.stringPreferencesKey    
import androidx.datastore.preferences.preferencesDataStore    
import kotlinx.coroutines.flow.Flow    
import kotlinx.coroutines.flow.first    
import kotlinx.coroutines.flow.map    
  
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")    
  
class SettingsDataStore(private val context: Context) {    
  
    companion object {    
        private val KEY_POLLING_INTERVAL = longPreferencesKey("polling_interval_seconds")    
        private val KEY_MONITORING_ENABLED = booleanPreferencesKey("is_monitoring_enabled")    
        private val KEY_SERVER_IP = stringPreferencesKey("server_ip")    
        private val KEY_SERVER_PORT = stringPreferencesKey("server_port")    
        private val KEY_DAILY_SERVER_IP = stringPreferencesKey("daily_server_ip")    
        private val KEY_DAILY_SERVER_PORT = stringPreferencesKey("daily_server_port")    
    }    
  
    val pollingIntervalFlow: Flow<Long> = context.dataStore.data.map { prefs ->    
        prefs[KEY_POLLING_INTERVAL] ?: 1L    
    }    
  
    val isMonitoringEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->    
        prefs[KEY_MONITORING_ENABLED] ?: true    
    }    
  
    val serverIpFlow: Flow<String> = context.dataStore.data.map { prefs ->    
        prefs[KEY_SERVER_IP] ?: ""    
    }    
  
    val serverPortFlow: Flow<String> = context.dataStore.data.map { prefs ->    
        prefs[KEY_SERVER_PORT] ?: ""    
    }    
  
    val dailyServerIpFlow: Flow<String> = context.dataStore.data.map { prefs ->    
        prefs[KEY_DAILY_SERVER_IP] ?: ""    
    }    
  
    val dailyServerPortFlow: Flow<String> = context.dataStore.data.map { prefs ->    
        prefs[KEY_DAILY_SERVER_PORT] ?: ""    
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
  
    suspend fun setServerIp(ip: String) {    
        context.dataStore.edit { it[KEY_SERVER_IP] = ip }    
    }    
  
    suspend fun setServerPort(port: String) {    
        context.dataStore.edit { it[KEY_SERVER_PORT] = port }    
    }    
  
    suspend fun setDailyServerIp(ip: String) {    
        context.dataStore.edit { it[KEY_DAILY_SERVER_IP] = ip }    
    }    
  
    suspend fun setDailyServerPort(port: String) {    
        context.dataStore.edit { it[KEY_DAILY_SERVER_PORT] = port }    
    }    
  
    suspend fun getPollingIntervalSync(): Long {    
        return context.dataStore.data.first()[KEY_POLLING_INTERVAL] ?: 1L    
    }    
  
    suspend fun isMonitoringEnabledSync(): Boolean {    
        return context.dataStore.data.first()[KEY_MONITORING_ENABLED] ?: true    
    }    
  
    /**    
     * 获取用户自定义的服务器 URL（截图拆队用）。    
     * 如果 IP 为空则返回 null，调用方应回退到默认 API。    
     */    
    suspend fun getServerUrl(): String? {    
        val prefs = context.dataStore.data.first()    
        val ip = (prefs[KEY_SERVER_IP] ?: "").trim()    
        val port = (prefs[KEY_SERVER_PORT] ?: "").trim()    
        if (ip.isBlank()) return null    
        return if (port.isBlank()) "http://$ip" else "http://$ip:$port"    
    }    
  
    /**    
     * 获取清日常服务器 URL。    
     * 如果 IP 为空则返回 null。    
     */    
    suspend fun getDailyServerUrl(): String? {    
        val prefs = context.dataStore.data.first()    
        val ip = (prefs[KEY_DAILY_SERVER_IP] ?: "").trim()    
        val port = (prefs[KEY_DAILY_SERVER_PORT] ?: "").trim()    
        if (ip.isBlank()) return null    
        return if (port.isBlank()) "http://$ip" else "http://$ip:$port"    
    }    
}