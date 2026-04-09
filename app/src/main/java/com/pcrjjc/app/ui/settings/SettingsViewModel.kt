// app/src/main/java/com/pcrjjc/app/ui/settings/SettingsViewModel.kt  
package com.pcrjjc.app.ui.settings  
  
import android.content.Context  
import android.content.Intent  
import android.content.SharedPreferences  
import android.os.Build  
import androidx.lifecycle.ViewModel  
import androidx.lifecycle.viewModelScope  
import com.pcrjjc.app.data.local.dao.BindDao  
import com.pcrjjc.app.data.local.entity.PcrBind  
import com.pcrjjc.app.service.RankMonitorService  
import dagger.hilt.android.lifecycle.HiltViewModel  
import dagger.hilt.android.qualifiers.ApplicationContext  
import kotlinx.coroutines.flow.MutableStateFlow  
import kotlinx.coroutines.flow.StateFlow  
import kotlinx.coroutines.launch  
import javax.inject.Inject  
  
data class SettingsUiState(  
    val pollingIntervalSeconds: Long = 30,  
    val isMonitoringEnabled: Boolean = false,  
    val binds: List<PcrBind> = emptyList()  
)  
  
@HiltViewModel  
class SettingsViewModel @Inject constructor(  
    @ApplicationContext private val context: Context,  
    private val bindDao: BindDao  
) : ViewModel() {  
  
    companion object {  
        private const val PREFS_NAME = "pcrjjc_settings"  
        private const val KEY_POLLING_INTERVAL = "polling_interval_seconds"  
        private const val KEY_MONITORING_ENABLED = "monitoring_enabled"  
    }  
  
    private val prefs: SharedPreferences =  
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)  
  
    private val _uiState = MutableStateFlow(  
        SettingsUiState(  
            pollingIntervalSeconds = prefs.getLong(KEY_POLLING_INTERVAL, 30),  
            isMonitoringEnabled = prefs.getBoolean(KEY_MONITORING_ENABLED, false)  
        )  
    )  
    val uiState: StateFlow<SettingsUiState> = _uiState  
  
    init {  
        viewModelScope.launch {  
            bindDao.getAllBinds().collect { binds ->  
                _uiState.value = _uiState.value.copy(binds = binds)  
            }  
        }  
        // 如果上次退出时监控是开启的，自动恢复监控  
        if (_uiState.value.isMonitoringEnabled) {  
            startMonitoring()  
        }  
    }  
  
    fun setPollingInterval(seconds: Long) {  
        _uiState.value = _uiState.value.copy(pollingIntervalSeconds = seconds)  
        prefs.edit().putLong(KEY_POLLING_INTERVAL, seconds).apply()  
        if (_uiState.value.isMonitoringEnabled) {  
            startMonitoring()  
        }  
    }  
  
    fun toggleMonitoring(enabled: Boolean) {  
        _uiState.value = _uiState.value.copy(isMonitoringEnabled = enabled)  
        prefs.edit().putBoolean(KEY_MONITORING_ENABLED, enabled).apply()  
        if (enabled) {  
            startMonitoring()  
        } else {  
            stopMonitoring()  
        }  
    }  
  
    fun updateBindNotice(  
        bind: PcrBind,  
        jjcNotice: Boolean? = null,  
        pjjcNotice: Boolean? = null,  
        upNotice: Boolean? = null,  
        onlineNotice: Int? = null  
    ) {  
        viewModelScope.launch {  
            val updated = bind.copy(  
                jjcNotice = jjcNotice ?: bind.jjcNotice,  
                pjjcNotice = pjjcNotice ?: bind.pjjcNotice,  
                upNotice = upNotice ?: bind.upNotice,  
                onlineNotice = onlineNotice ?: bind.onlineNotice  
            )  
            bindDao.update(updated)  
        }  
    }  
  
    private fun startMonitoring() {  
        val interval = _uiState.value.pollingIntervalSeconds  
        val intent = Intent(context, RankMonitorService::class.java)  
        intent.putExtra(RankMonitorService.EXTRA_INTERVAL_SECONDS, interval)  
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {  
            context.startForegroundService(intent)  
        } else {  
            context.startService(intent)  
        }  
    }  
  
    private fun stopMonitoring() {  
        context.stopService(Intent(context, RankMonitorService::class.java))  
    }  
}