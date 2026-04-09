package com.pcrjjc.app.ui.settings  
  
import android.content.Context  
import androidx.lifecycle.ViewModel  
import androidx.lifecycle.viewModelScope  
import androidx.work.Constraints  
import androidx.work.ExistingPeriodicWorkPolicy  
import androidx.work.NetworkType  
import androidx.work.PeriodicWorkRequestBuilder  
import androidx.work.WorkManager  
import com.pcrjjc.app.data.local.dao.BindDao  
import com.pcrjjc.app.data.local.entity.PcrBind  
import com.pcrjjc.app.worker.RankCheckWorker  
import dagger.hilt.android.lifecycle.HiltViewModel  
import dagger.hilt.android.qualifiers.ApplicationContext  
import kotlinx.coroutines.flow.MutableStateFlow  
import kotlinx.coroutines.flow.StateFlow  
import kotlinx.coroutines.launch  
import java.util.concurrent.TimeUnit  
import javax.inject.Inject  
  
data class SettingsUiState(  
    val pollingIntervalMinutes: Long = 15,  
    val isMonitoringEnabled: Boolean = false,  
    val binds: List<PcrBind> = emptyList()  
)  
  
@HiltViewModel  
class SettingsViewModel @Inject constructor(  
    @ApplicationContext private val context: Context,  
    private val bindDao: BindDao  
) : ViewModel() {  
  
    private val _uiState = MutableStateFlow(SettingsUiState())  
    val uiState: StateFlow<SettingsUiState> = _uiState  
  
    init {  
        viewModelScope.launch {  
            bindDao.getAllBinds().collect { binds ->  
                _uiState.value = _uiState.value.copy(binds = binds)  
            }  
        }  
    }  
  
    fun setPollingInterval(minutes: Long) {  
        _uiState.value = _uiState.value.copy(pollingIntervalMinutes = minutes)  
        if (_uiState.value.isMonitoringEnabled) {  
            startMonitoring()  
        }  
    }  
  
    fun toggleMonitoring(enabled: Boolean) {  
        _uiState.value = _uiState.value.copy(isMonitoringEnabled = enabled)  
        if (enabled) {  
            startMonitoring()  
        } else {  
            stopMonitoring()  
        }  
    }  
  
    fun updateBindNotice(bind: PcrBind, jjcNotice: Boolean? = null, pjjcNotice: Boolean? = null, upNotice: Boolean? = null, onlineNotice: Int? = null) {  
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
        val interval = _uiState.value.pollingIntervalMinutes  
  
        val constraints = Constraints.Builder()  
            .setRequiredNetworkType(NetworkType.CONNECTED)  
            .build()  
  
        val workRequest = PeriodicWorkRequestBuilder<RankCheckWorker>(  
            interval, TimeUnit.MINUTES  
        )  
            .setConstraints(constraints)  
            .build()  
  
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(  
            RankCheckWorker.WORK_NAME,  
            ExistingPeriodicWorkPolicy.UPDATE,  
            workRequest  
        )  
    }  
  
    private fun stopMonitoring() {  
        WorkManager.getInstance(context).cancelUniqueWork(RankCheckWorker.WORK_NAME)  
    }  
}