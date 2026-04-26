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
import org.json.JSONArray    
import org.json.JSONObject    
  
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")    
  
class SettingsDataStore(private val context: Context) {    
  
    companion object {  
        private val KEY_POLLING_INTERVAL = longPreferencesKey("polling_interval_seconds")  
        private val KEY_MONITORING_ENABLED = booleanPreferencesKey("is_monitoring_enabled")  
        private val KEY_SERVER_IP = stringPreferencesKey("server_ip")  
        private val KEY_SERVER_PORT = stringPreferencesKey("server_port")  
        private val KEY_DAILY_SAVED_ACCOUNTS = stringPreferencesKey("daily_saved_accounts")  
        private val KEY_DAILY_SERVER_IP = stringPreferencesKey("daily_server_ip")  
        private val KEY_DAILY_SERVER_PORT = stringPreferencesKey("daily_server_port")  
        private val KEY_ROOM_SERVER_IP = stringPreferencesKey("room_server_ip")  
        private val KEY_ROOM_SERVER_PORT = stringPreferencesKey("room_server_port")  
        private val KEY_LAST_UPDATE_CHECK_TIME = longPreferencesKey("last_update_check_time")  
        private val KEY_NOTIFIED_VERSION = stringPreferencesKey("notified_version")  
        private val KEY_USER_QQ = stringPreferencesKey("user_qq")  // 用户QQ号
        private val KEY_USER_NAME = stringPreferencesKey("user_name")  // 用户昵称
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
  
    val roomServerIpFlow: Flow<String> = context.dataStore.data.map { prefs ->  
        prefs[KEY_ROOM_SERVER_IP] ?: ""  
    }  
  
    val roomServerPortFlow: Flow<String> = context.dataStore.data.map { prefs ->  
        prefs[KEY_ROOM_SERVER_PORT] ?: ""  
    }  
  
    val userQqFlow: Flow<String> = context.dataStore.data.map { prefs ->  
        prefs[KEY_USER_QQ] ?: ""  
    }  

    val userNameFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_USER_NAME] ?: ""
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
  
    suspend fun setRoomServerIp(ip: String) {  
        context.dataStore.edit { it[KEY_ROOM_SERVER_IP] = ip }  
    }  
    suspend fun setRoomServerPort(port: String) {  
        context.dataStore.edit { it[KEY_ROOM_SERVER_PORT] = port }  
    }  
  
    suspend fun setUserQq(qq: String) {  
        context.dataStore.edit { it[KEY_USER_QQ] = qq }  
    }  

    suspend fun setUserName(name: String) {
        context.dataStore.edit { it[KEY_USER_NAME] = name }
    }

  
    suspend fun getPollingIntervalSync(): Long {    
        return context.dataStore.data.first()[KEY_POLLING_INTERVAL] ?: 1L    
    }    
  
    suspend fun isMonitoringEnabledSync(): Boolean {    
        return context.dataStore.data.first()[KEY_MONITORING_ENABLED] ?: true    
    }    
  
    suspend fun getUserQq(): String {  
        return context.dataStore.data.first()[KEY_USER_QQ] ?: ""  
    }  

    suspend fun getUserName(): String {
        return context.dataStore.data.first()[KEY_USER_NAME] ?: ""
    }

  
    suspend fun getServerUrl(): String {  
		return "http://43.139.69.254:8040:8077"  
	}  
	  
	suspend fun getDailyServerUrl(): String {  
		return "http://43.139.69.254:8040:8040"  
	}  
	  
	suspend fun getRoomServerUrl(): String {  
		return "http://43.139.69.254:8040:8066"  
	}  
  
    // ==================== 清日常账号保存 ====================    
  
    /**    
     * 获取已保存的清日常账号列表。    
     * 存储格式：JSON 数组 [{"qq":"123","password":"abc"}, ...]    
     */    
    suspend fun getDailySavedAccounts(): List<Pair<String, String>> {    
        val prefs = context.dataStore.data.first()    
        val json = prefs[KEY_DAILY_SAVED_ACCOUNTS] ?: return emptyList()    
        return try {    
            val arr = JSONArray(json)    
            (0 until arr.length()).map { i ->    
                val obj = arr.getJSONObject(i)    
                Pair(obj.getString("qq"), obj.getString("password"))    
            }    
        } catch (e: Exception) {    
            emptyList()    
        }    
    }    
  
    /**    
     * 保存一个清日常账号。如果 QQ 已存在则更新密码，否则追加。    
     */    
    suspend fun saveDailyAccount(qq: String, password: String) {    
        val accounts = getDailySavedAccounts().toMutableList()    
        val existingIndex = accounts.indexOfFirst { it.first == qq }    
        if (existingIndex >= 0) {    
            accounts[existingIndex] = Pair(qq, password)    
        } else {    
            accounts.add(Pair(qq, password))    
        }    
        writeDailyAccounts(accounts)    
    }    
  
    /**    
     * 删除一个已保存的清日常账号。    
     */    
    suspend fun deleteDailyAccount(qq: String) {    
        val accounts = getDailySavedAccounts().toMutableList()    
        accounts.removeAll { it.first == qq }    
        writeDailyAccounts(accounts)    
    }    
  
    private suspend fun writeDailyAccounts(accounts: List<Pair<String, String>>) {  
        val arr = JSONArray()  
        accounts.forEach { (qq, password) ->  
            arr.put(JSONObject().apply {  
                put("qq", qq)  
                put("password", password)  
            })  
        }  
        context.dataStore.edit { prefs ->  
            prefs[KEY_DAILY_SAVED_ACCOUNTS] = arr.toString()  
        }  
    }  
  
    // ==================== 更新检查相关 ====================  
  
    suspend fun getLastUpdateCheckTimeSync(): Long {  
        return context.dataStore.data.first()[KEY_LAST_UPDATE_CHECK_TIME] ?: 0L  
    }  
  
    suspend fun setLastUpdateCheckTime(time: Long) {  
        context.dataStore.edit { prefs ->  
            prefs[KEY_LAST_UPDATE_CHECK_TIME] = time  
        }  
    }  
  
    suspend fun getNotifiedVersionSync(): String? {  
        return context.dataStore.data.first()[KEY_NOTIFIED_VERSION]  
    }  
  
    suspend fun setNotifiedVersion(version: String) {  
        context.dataStore.edit { prefs ->  
            prefs[KEY_NOTIFIED_VERSION] = version  
        }  
    }  
}
