package com.pcrjjc.app.ui.settings  
  
import android.content.Context  
import android.content.Intent  
import android.graphics.BitmapFactory  
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
import com.pcrjjc.app.util.IconStorage  
import dagger.hilt.android.lifecycle.HiltViewModel  
import dagger.hilt.android.qualifiers.ApplicationContext  
import kotlinx.coroutines.Dispatchers  
import kotlinx.coroutines.async  
import kotlinx.coroutines.awaitAll  
import kotlinx.coroutines.coroutineScope  
import kotlinx.coroutines.flow.MutableStateFlow  
import kotlinx.coroutines.flow.StateFlow  
import kotlinx.coroutines.launch  
import kotlinx.coroutines.sync.Semaphore  
import kotlinx.coroutines.sync.withPermit  
import kotlinx.coroutines.withContext  
import okhttp3.OkHttpClient  
import okhttp3.Request  
import java.util.concurrent.TimeUnit  
import java.util.concurrent.atomic.AtomicInteger  
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
     * 从 redive.estertion.win 下载所有角色头像（webp → PNG 本地保存）  
     * 已下载过的自动跳过  
     */  
    fun downloadAllAvatars() {  
        if (_uiState.value.isDownloadingAvatars) return  
  
        viewModelScope.launch {  
            _uiState.value = _uiState.value.copy(  
                isDownloadingAvatars = true,  
                avatarDownloadProgress = 0f,  
                avatarDownloadMessage = "准备下载..."  
            )  
  
            try {  
                withContext(Dispatchers.IO) {  
                    val client = OkHttpClient.Builder()  
                        .connectTimeout(10, TimeUnit.SECONDS)  
                        .readTimeout(10, TimeUnit.SECONDS)  
                        .build()  
  
                    val baseUrl = "https://redive.estertion.win/icon/unit/"  
                    val stars = listOf(6, 3, 1)  
  
                    // 构建待下载列表，跳过已缓存的  
                    val toDownload = mutableListOf<Pair<Int, Int>>() // (baseId, star)  
                    for (baseId in 1001..1899) {  
                        for (star in stars) {  
                            if (!IconStorage.hasIcon(context, baseId, star)) {  
                                toDownload.add(baseId to star)  
                            }  
                        }  
                    }  
  
                    if (toDownload.isEmpty()) {  
                        _uiState.value = _uiState.value.copy(  
                            isDownloadingAvatars = false,  
                            avatarDownloadProgress = 1f,  
                            avatarDownloadMessage = "所有头像已是最新"  
                        )  
                        return@withContext  
                    }  
  
                    _uiState.value = _uiState.value.copy(  
                        avatarDownloadMessage = "需要下载 ${toDownload.size} 个图标..."  
                    )  
  
                    val completed = AtomicInteger(0)  
                    val success = AtomicInteger(0)  
                    val total = toDownload.size  
                    val semaphore = Semaphore(5)  
  
                    coroutineScope {  
                        val jobs = toDownload.map { (baseId, star) ->  
                            async(Dispatchers.IO) {  
                                semaphore.withPermit {  
                                    try {  
                                        val url = "${baseUrl}${baseId}${star}1.webp"  
                                        val request = Request.Builder().url(url).build()  
                                        val response = client.newCall(request).execute()  
                                        response.use { resp ->  
                                            if (resp.isSuccessful) {  
                                                val bytes = resp.body?.bytes()  
                                                if (bytes != null) {  
                                                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)  
                                                    if (bitmap != null) {  
                                                        IconStorage.saveBitmap(context, baseId, star, bitmap)  
                                                        bitmap.recycle()  
                                                        success.incrementAndGet()  
                                                    }  
                                                }  
                                            }  
                                        }  
                                    } catch (e: Exception) {  
                                        Log.w("SettingsVM", "Failed: ${baseId}_$star: ${e.message}")  
                                    }  
  
                                    val done = completed.incrementAndGet()  
                                    if (done % 20 == 0 || done == total) {  
                                        _uiState.value = _uiState.value.copy(  
                                            avatarDownloadProgress = done.toFloat() / total,  
                                            avatarDownloadMessage = "下载中 $done/$total (成功 ${success.get()})"  
                                        )  
                                    }  
                                }  
                            }  
                        }  
                        jobs.awaitAll()  
                    }  
  
                    _uiState.value = _uiState.value.copy(  
                        isDownloadingAvatars = false,  
                        avatarDownloadProgress = 1f,  
                        avatarDownloadMessage = "完成，成功下载 ${success.get()} 个图标"  
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