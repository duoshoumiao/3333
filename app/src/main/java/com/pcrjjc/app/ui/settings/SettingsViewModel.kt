package com.pcrjjc.app.ui.settings  
  
import android.content.Context  
import android.content.Intent  
import android.os.Build  
import android.util.Log  
import androidx.lifecycle.ViewModel  
import androidx.lifecycle.viewModelScope  
import coil.imageLoader  
import coil.request.CachePolicy  
import coil.request.ImageRequest  
import com.pcrjjc.app.BuildConfig  
import com.pcrjjc.app.data.local.SettingsDataStore  
import com.pcrjjc.app.data.local.dao.BindDao  
import com.pcrjjc.app.data.local.entity.PcrBind  
import com.pcrjjc.app.domain.UpdateChecker  
import com.pcrjjc.app.domain.UpdateInfo  
import com.pcrjjc.app.service.RankMonitorService  
import com.pcrjjc.app.util.CharaIconUtil  
import dagger.hilt.android.lifecycle.HiltViewModel  
import dagger.hilt.android.qualifiers.ApplicationContext  
import kotlinx.coroutines.Dispatchers  
import kotlinx.coroutines.flow.MutableStateFlow  
import kotlinx.coroutines.flow.StateFlow  
import kotlinx.coroutines.launch  
import kotlinx.coroutines.suspendCancellableCoroutine  
import kotlinx.coroutines.withContext  
import javax.inject.Inject  
import kotlin.coroutines.resume  
  
data class SettingsUiState(  
    val binds: List<PcrBind> = emptyList(),  
    val pollingInterval: Long = 1L,  
    val pollingIntervalInput: String = "1",  
    val intervalSaved: Boolean = false,  
    val isCheckingUpdate: Boolean = false,  
    val isDownloading: Boolean = false,  
    val downloadProgress: Float = 0f,  
    val updateMessage: String? = null,  
    val updateInfo: UpdateInfo? = null,  
    // 头像下载状态  
    val isDownloadingAvatars: Boolean = false,  
    val avatarDownloadProgress: Float = 0f,  
    val avatarDownloadMessage: String? = null  
)  
  
@HiltViewModel  
class SettingsViewModel @Inject constructor(  
    @ApplicationContext private val context: Context,  
    private val bindDao: BindDao,  
    private val settingsDataStore: SettingsDataStore  
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
        viewModelScope.launch {  
            settingsDataStore.pollingIntervalFlow.collect { interval ->  
                _uiState.value = _uiState.value.copy(  
                    pollingInterval = interval,  
                    pollingIntervalInput = interval.toString()  
                )  
            }  
        }  
    }  
  
    fun onIntervalInputChanged(input: String) {  
        _uiState.value = _uiState.value.copy(  
            pollingIntervalInput = input,  
            intervalSaved = false  
        )  
    }  
  
    fun savePollingInterval() {  
        val input = _uiState.value.pollingIntervalInput  
        val seconds = input.toLongOrNull()  
        if (seconds == null || seconds < 1) {  
            _uiState.value = _uiState.value.copy(  
                intervalSaved = false  
            )  
            return  
        }  
        viewModelScope.launch {  
            settingsDataStore.setPollingInterval(seconds)  
            _uiState.value = _uiState.value.copy(  
                pollingInterval = seconds,  
                intervalSaved = true  
            )  
            // 重启服务以应用新间隔  
            restartMonitorService(seconds)  
        }  
    }  
  
    private fun restartMonitorService(intervalSeconds: Long) {  
        val intent = Intent(context, RankMonitorService::class.java)  
        intent.putExtra(RankMonitorService.EXTRA_INTERVAL_SECONDS, intervalSeconds)  
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {  
            context.startForegroundService(intent)  
        } else {  
            context.startService(intent)  
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
  
    /**  
     * 批量下载所有 PCR 角色头像到本地 Coil 磁盘缓存  
     * baseId 范围 1001..1999，每个 baseId 下载 61/31/11 三个星级的头像  
     */  
    fun downloadAllAvatars() {  
        if (_uiState.value.isDownloadingAvatars) return  
  
        viewModelScope.launch {  
            _uiState.value = _uiState.value.copy(  
                isDownloadingAvatars = true,  
                avatarDownloadProgress = 0f,  
                avatarDownloadMessage = "准备下载..."  
            )  
  
            val imageLoader = context.imageLoader  
            // PCR 角色 baseId 范围大约 1001~1999  
            val baseIds = (1001..1999).toList()  
            // 对每个 baseId 生成一个虚拟 unitId（baseId * 100 + 1）来调用 getIconUrls  
            val allUrls = baseIds.flatMap { baseId ->  
                CharaIconUtil.getIconUrls(baseId * 100 + 1)  
            }  
            val totalCount = allUrls.size  
            var completed = 0  
            var successCount = 0  
  
            withContext(Dispatchers.IO) {  
                for (url in allUrls) {  
                    try {  
                        val success = suspendCancellableCoroutine { cont ->  
                            val request = ImageRequest.Builder(context)  
                                .data(url)  
                                .diskCachePolicy(CachePolicy.ENABLED)  
                                .memoryCachePolicy(CachePolicy.DISABLED)  
                                .build()  
                            val disposable = imageLoader.enqueue(  
                                request.newBuilder()  
                                    .listener(  
                                        onSuccess = { _, _ -> cont.resume(true) },  
                                        onError = { _, _ -> cont.resume(false) },  
                                        onCancel = { _ -> cont.resume(false) }  
                                    )  
                                    .build()  
                            )  
                            cont.invokeOnCancellation { disposable.dispose() }  
                        }  
                        if (success) successCount++  
                    } catch (_: Exception) {  
                        // 404 或网络错误，跳过  
                    }  
                    completed++  
                    if (completed % 30 == 0 || completed == totalCount) {  
                        _uiState.value = _uiState.value.copy(  
                            avatarDownloadProgress = completed.toFloat() / totalCount,  
                            avatarDownloadMessage = "下载中 $completed/$totalCount (成功 $successCount)"  
                        )  
                    }  
                }  
            }  
  
            _uiState.value = _uiState.value.copy(  
                isDownloadingAvatars = false,  
                avatarDownloadProgress = 1f,  
                avatarDownloadMessage = "下载完成，成功缓存 $successCount 个头像"  
            )  
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