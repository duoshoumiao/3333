package com.pcrjjc.app.ui.settings  
  
import android.content.Context  
import android.util.Log  
import androidx.lifecycle.ViewModel  
import androidx.lifecycle.viewModelScope  
import com.pcrjjc.app.BuildConfig  
import com.pcrjjc.app.data.local.dao.BindDao  
import com.pcrjjc.app.data.local.entity.PcrBind  
import com.pcrjjc.app.domain.UpdateChecker  
import com.pcrjjc.app.domain.UpdateInfo  
import dagger.hilt.android.lifecycle.HiltViewModel  
import dagger.hilt.android.qualifiers.ApplicationContext  
import kotlinx.coroutines.flow.MutableStateFlow  
import kotlinx.coroutines.flow.StateFlow  
import kotlinx.coroutines.launch  
import javax.inject.Inject  
  
data class SettingsUiState(  
    val binds: List<PcrBind> = emptyList(),  
    val isCheckingUpdate: Boolean = false,  
    val isDownloading: Boolean = false,  
    val downloadProgress: Float = 0f,  
    val updateMessage: String? = null,  
    val updateInfo: UpdateInfo? = null  
)  
  
@HiltViewModel  
class SettingsViewModel @Inject constructor(  
    @ApplicationContext private val context: Context,  
    private val bindDao: BindDao  
) : ViewModel() {  
  
    private val _uiState = MutableStateFlow(SettingsUiState())  
    val uiState: StateFlow<SettingsUiState> = _uiState  
  
    private val updateChecker = UpdateChecker(context)  
  
    init {  
        viewModelScope.launch {  
            bindDao.getAllBinds().collect { binds ->  
                _uiState.value = _uiState.value.copy(binds = binds)  
            }  
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
  
    fun checkForUpdate() {  
        viewModelScope.launch {  
            _uiState.value = _uiState.value.copy(  
                isCheckingUpdate = true,  
                updateMessage = null,  
                updateInfo = null  
            )  
            try {  
                val info = updateChecker.checkForUpdate(BuildConfig.VERSION_NAME)  
                if (info != null) {  
                    _uiState.value = _uiState.value.copy(  
                        isCheckingUpdate = false,  
                        updateMessage = "发现新版本: v${info.versionName}",  
                        updateInfo = info  
                    )  
                } else {  
                    _uiState.value = _uiState.value.copy(  
                        isCheckingUpdate = false,  
                        updateMessage = "已是最新版本 (${BuildConfig.VERSION_NAME})"  
                    )  
                }  
            } catch (e: Exception) {  
                Log.e("SettingsVM", "Update check failed", e)  
                _uiState.value = _uiState.value.copy(  
                    isCheckingUpdate = false,  
                    updateMessage = "检查更新失败: ${e.message}"  
                )  
            }  
        }  
    }  
  
    fun downloadAndInstall() {  
        val info = _uiState.value.updateInfo ?: return  
        viewModelScope.launch {  
            _uiState.value = _uiState.value.copy(  
                isDownloading = true,  
                downloadProgress = 0f,  
                updateMessage = "正在下载..."  
            )  
            try {  
                val file = updateChecker.downloadApk(info.downloadUrl) { progress ->  
                    _uiState.value = _uiState.value.copy(  
                        downloadProgress = progress,  
                        updateMessage = "正在下载... ${(progress * 100).toInt()}%"  
                    )  
                }  
                if (file != null) {  
                    _uiState.value = _uiState.value.copy(  
                        isDownloading = false,  
                        updateMessage = "下载完成，正在安装..."  
                    )  
                    updateChecker.installApk(file)  
                } else {  
                    _uiState.value = _uiState.value.copy(  
                        isDownloading = false,  
                        updateMessage = "下载失败，请重试"  
                    )  
                }  
            } catch (e: Exception) {  
                Log.e("SettingsVM", "Download failed", e)  
                _uiState.value = _uiState.value.copy(  
                    isDownloading = false,  
                    updateMessage = "下载失败: ${e.message}"  
                )  
            }  
        }  
    }  
}