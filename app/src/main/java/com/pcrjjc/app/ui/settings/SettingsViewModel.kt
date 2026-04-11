// app/src/main/java/com/pcrjjc/app/ui/settings/SettingsViewModel.kt  
  
package com.pcrjjc.app.ui.settings  
  
import android.content.Context  
import android.content.Intent  
import android.os.Build  
import android.util.Log  
import androidx.lifecycle.ViewModel  
import androidx.lifecycle.viewModelScope  
import com.pcrjjc.app.BuildConfig  
import com.pcrjjc.app.data.local.SettingsDataStore  
import com.pcrjjc.app.data.local.dao.BindDao  
import com.pcrjjc.app.data.local.entity.PcrBind  
import com.pcrjjc.app.domain.UpdateChecker  
import com.pcrjjc.app.domain.UpdateInfo  
import com.pcrjjc.app.service.RankMonitorService  
import com.pcrjjc.app.util.AssetDownloader  
import com.pcrjjc.app.util.IconStorage  
import com.pcrjjc.app.util.SerializedFileParser  
import com.pcrjjc.app.util.TextureDecoder  
import com.pcrjjc.app.util.UnityBundleParser  
import dagger.hilt.android.lifecycle.HiltViewModel  
import dagger.hilt.android.qualifiers.ApplicationContext  
import kotlinx.coroutines.Dispatchers  
import kotlinx.coroutines.flow.MutableStateFlow  
import kotlinx.coroutines.flow.StateFlow  
import kotlinx.coroutines.launch  
import kotlinx.coroutines.withContext  
import javax.inject.Inject  
  
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
     * 从游戏 CDN 下载所有角色头像（Unity asset bundle 解包）  
     * 已下载过的自动跳过  
     */  
    fun downloadAllAvatars() {  
        if (_uiState.value.isDownloadingAvatars) return  
  
        viewModelScope.launch {  
            _uiState.value = _uiState.value.copy(  
                isDownloadingAvatars = true,  
                avatarDownloadProgress = 0f,  
                avatarDownloadMessage = "获取资源版本..."  
            )  
  
            try {  
                withContext(Dispatchers.IO) {  
                    val downloader = AssetDownloader()  
  
                    // 1. 获取 manifest_ver  
                    val manifestVer = downloader.getManifestVer()  
                    _uiState.value = _uiState.value.copy(  
                        avatarDownloadMessage = "下载资源清单..."  
                    )  
  
                    // 2. 下载并解析 manifest  
                    val manifest = downloader.downloadManifest(manifestVer)  
  
                    // 3. 筛选角色图标 bundle，过滤已下载的  
                    val icons = downloader.getAvailableUnitIcons(manifest)  
                    val toDownload = icons.filter { (baseId, star, _) ->  
                        !IconStorage.hasIcon(context, baseId, star)  
                    }  
  
                    if (toDownload.isEmpty()) {  
                        _uiState.value = _uiState.value.copy(  
                            isDownloadingAvatars = false,  
                            avatarDownloadProgress = 1f,  
                            avatarDownloadMessage = "所有头像已是最新 (共 ${icons.size} 个)"  
                        )  
                        return@withContext  
                    }  
  
                    _uiState.value = _uiState.value.copy(  
                        avatarDownloadMessage = "需要下载 ${toDownload.size} 个图标..."  
                    )  
  
                    var completed = 0  
                    var success = 0  
  
                    // 4. 逐个下载 bundle 并解包提取 Texture2D  
                    for ((baseId, star, hash) in toDownload) {  
                        try {  
                            val bundleData = downloader.downloadBundle(manifestVer, hash)  
                            val files = UnityBundleParser.parse(bundleData)  
                            for (file in files) {  
                                val texture = SerializedFileParser.extractTexture2D(file.data)  
                                if (texture != null) {  
                                    val bitmap = TextureDecoder.decode(texture)  
                                    if (bitmap != null) {  
                                        IconStorage.saveBitmap(context, baseId, star, bitmap)  
                                        bitmap.recycle()  
                                        success++  
                                    }  
                                }  
                            }  
                        } catch (e: Exception) {  
                            Log.w("SettingsVM", "Failed: ${baseId}_$star: ${e.message}")  
                        }  
                        completed++  
                        if (completed % 5 == 0 || completed == toDownload.size) {  
                            _uiState.value = _uiState.value.copy(  
                                avatarDownloadProgress = completed.toFloat() / toDownload.size,  
                                avatarDownloadMessage = "下载中 $completed/${toDownload.size} (成功 $success)"  
                            )  
                        }  
                    }  
  
                    _uiState.value = _uiState.value.copy(  
                        isDownloadingAvatars = false,  
                        avatarDownloadProgress = 1f,  
                        avatarDownloadMessage = "完成，成功下载 $success 个图标"  
                    )  
                }  
            } catch (e: Exception) {  
                Log.e("SettingsVM", "Avatar download failed", e)  
                _uiState.value = _uiState.value.copy(  
                    isDownloadingAvatars = false,  
                    avatarDownloadMessage = "下载失败: ${e.message}"  
                )  
            }  
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