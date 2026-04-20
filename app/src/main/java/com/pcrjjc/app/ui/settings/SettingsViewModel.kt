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
import com.pcrjjc.app.util.CharaRoster    
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
    val serverIpInput: String = "",
    val serverPortInput: String = "",
    val serverSaved: Boolean = false,
    val dailyServerIpInput: String = "",       // ← 新增
    val dailyServerPortInput: String = "",     // ← 新增
    val dailyServerSaved: Boolean = false,
    val roomServerIpInput: String = "",        // 新增：房间服务器IP
    val roomServerPortInput: String = "",      // 新增：房间服务器端口
    val roomServerSaved: Boolean = false,      // 新增
    val isCheckingUpdate: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val cachedAvatarCount: Int = 0,
    val rosterCount: Int = 0,
    val isUpdatingRoster: Boolean = false,
    val rosterMessage: String? = null,
    val updateMessage: String? = null,
    val updateInfo: UpdateInfo? = null,
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
        viewModelScope.launch(Dispatchers.IO) {    
            val count = IconStorage.getCachedCount(context)    
            val rosterCount = CharaRoster.getCachedRosterCount(context)    
            _uiState.value = _uiState.value.copy(    
                cachedAvatarCount = count,    
                rosterCount = rosterCount    
            )    
        }    
        viewModelScope.launch {    
            settingsDataStore.pollingIntervalFlow.collect { interval ->    
                _uiState.value = _uiState.value.copy(    
                    pollingInterval = interval,    
                    pollingIntervalInput = interval.toString()    
                )    
            }    
        }    
        viewModelScope.launch {    
            settingsDataStore.serverIpFlow.collect { ip ->    
                _uiState.value = _uiState.value.copy(serverIpInput = ip)    
            }    
        }    
        viewModelScope.launch {    
            settingsDataStore.serverPortFlow.collect { port ->    
                _uiState.value = _uiState.value.copy(serverPortInput = port)    
            }    
        }    
        // ← 新增：收集清日常服务器地址  
        viewModelScope.launch {    
            settingsDataStore.dailyServerIpFlow.collect { ip ->    
                _uiState.value = _uiState.value.copy(dailyServerIpInput = ip)    
            }    
        }    
viewModelScope.launch {
            settingsDataStore.dailyServerPortFlow.collect { port ->
                _uiState.value = _uiState.value.copy(dailyServerPortInput = port)
            }
        }
        // 新增：收集房间服务器地址
        viewModelScope.launch {
            settingsDataStore.roomServerIpFlow.collect { ip ->
                _uiState.value = _uiState.value.copy(roomServerIpInput = ip)
            }
        }
        viewModelScope.launch {
            settingsDataStore.roomServerPortFlow.collect { port ->
                _uiState.value = _uiState.value.copy(roomServerPortInput = port)
            }
        }
    }
  
    fun onIntervalInputChanged(input: String) {    
        _uiState.value = _uiState.value.copy(    
            pollingIntervalInput = input,    
            intervalSaved = false    
        )    
    }    
  
    fun onServerIpInputChanged(input: String) {    
        _uiState.value = _uiState.value.copy(serverIpInput = input, serverSaved = false)    
    }    
  
    fun onServerPortInputChanged(input: String) {    
        _uiState.value = _uiState.value.copy(serverPortInput = input, serverSaved = false)    
    }    
  
    fun saveServerAddress() {    
        val ip = _uiState.value.serverIpInput.trim()    
        val port = _uiState.value.serverPortInput.trim()    
        if (port.isNotEmpty()) {    
            val portInt = port.toIntOrNull()    
            if (portInt == null || portInt < 1 || portInt > 65535) return    
        }    
        viewModelScope.launch {    
            settingsDataStore.setServerIp(ip)    
            settingsDataStore.setServerPort(port)    
            _uiState.value = _uiState.value.copy(serverSaved = true)    
        }    
    }    
  
    // ← 新增：清日常服务器地址  
    fun onDailyServerIpInputChanged(input: String) {    
        _uiState.value = _uiState.value.copy(dailyServerIpInput = input, dailyServerSaved = false)    
    }    
  
    fun onDailyServerPortInputChanged(input: String) {    
        _uiState.value = _uiState.value.copy(dailyServerPortInput = input, dailyServerSaved = false)    
    }    
  
    fun saveDailyServerAddress() {
        val ip = _uiState.value.dailyServerIpInput.trim()
        val port = _uiState.value.dailyServerPortInput.trim()
        if (port.isNotEmpty()) {
            val portInt = port.toIntOrNull()
            if (portInt == null || portInt < 1 || portInt > 65535) return
        }
        viewModelScope.launch {
            settingsDataStore.setDailyServerIp(ip)
            settingsDataStore.setDailyServerPort(port)
            _uiState.value = _uiState.value.copy(dailyServerSaved = true)
        }
    }

    // 新增：房间服务器地址
    fun onRoomServerIpInputChanged(input: String) {
        _uiState.value = _uiState.value.copy(roomServerIpInput = input, roomServerSaved = false)
    }

    fun onRoomServerPortInputChanged(input: String) {
        _uiState.value = _uiState.value.copy(roomServerPortInput = input, roomServerSaved = false)
    }

    fun saveRoomServerAddress() {
        val ip = _uiState.value.roomServerIpInput.trim()
        val port = _uiState.value.roomServerPortInput.trim()
        if (port.isNotEmpty()) {
            val portInt = port.toIntOrNull()
            if (portInt == null || portInt < 1 || portInt > 65535) return
        }
        viewModelScope.launch {
            settingsDataStore.setRoomServerIp(ip)
            settingsDataStore.setRoomServerPort(port)
            _uiState.value = _uiState.value.copy(roomServerSaved = true)
        }
    }    
  
    fun savePollingInterval() {    
        val input = _uiState.value.pollingIntervalInput    
        val seconds = input.toLongOrNull()    
        if (seconds == null || seconds < 1) {    
            _uiState.value = _uiState.value.copy(intervalSaved = false)    
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
  
    fun updateRoster() {    
        if (_uiState.value.isUpdatingRoster) return    
  
        viewModelScope.launch {    
            _uiState.value = _uiState.value.copy(    
                isUpdatingRoster = true,    
                rosterMessage = "正在获取花名册..."    
            )    
            try {    
                withContext(Dispatchers.IO) {    
                    val client = OkHttpClient.Builder()    
                        .connectTimeout(15, TimeUnit.SECONDS)    
                        .readTimeout(15, TimeUnit.SECONDS)    
                        .build()    
  
                    val roster = CharaRoster.fetchRoster(client, context, forceRefresh = true)    
                    if (roster != null && roster.isNotEmpty()) {    
                        _uiState.value = _uiState.value.copy(    
                            isUpdatingRoster = false,    
                            rosterCount = roster.size,    
                            rosterMessage = "花名册更新成功，共 ${roster.size} 个角色"    
                        )    
                    } else {    
                        _uiState.value = _uiState.value.copy(    
                            isUpdatingRoster = false,    
                            rosterMessage = "花名册更新失败，请检查网络"    
                        )    
                    }    
                }    
            } catch (e: Exception) {    
                Log.e("SettingsVM", "Roster update failed", e)    
                _uiState.value = _uiState.value.copy(    
                    isUpdatingRoster = false,    
                    rosterMessage = "花名册更新失败: ${e.message}"    
                )    
            }    
        }    
    }    
  
    fun downloadAllAvatars() {    
        if (_uiState.value.isDownloadingAvatars) return    
  
        viewModelScope.launch {    
            _uiState.value = _uiState.value.copy(    
                isDownloadingAvatars = true,    
                avatarDownloadProgress = 0f,    
                avatarDownloadMessage = "正在获取花名册..."    
            )    
  
            try {    
                withContext(Dispatchers.IO) {    
                    val client = OkHttpClient.Builder()    
                        .connectTimeout(10, TimeUnit.SECONDS)    
                        .readTimeout(10, TimeUnit.SECONDS)    
                        .build()    
  
                    val baseUrl = "https://redive.estertion.win/icon/unit/"    
                    val stars = listOf(6, 3)    
  
                    val roster = CharaRoster.fetchRoster(client, context)    
                    val charaIds: List<Int> = if (roster != null && roster.isNotEmpty()) {    
                        _uiState.value = _uiState.value.copy(    
                            avatarDownloadMessage = "花名册包含 ${roster.size} 个角色，正在检查缺失头像...",    
                            rosterCount = roster.size    
                        )    
                        roster    
                    } else {    
                        _uiState.value = _uiState.value.copy(    
                            avatarDownloadMessage = "花名册获取失败，使用默认范围..."    
                        )    
                        (1001..1899).toList()    
                    }    
  
                    val toDownload = mutableListOf<Pair<Int, Int>>()    
                    for (baseId in charaIds) {    
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
  
                    val newCount = IconStorage.getCachedCount(context)    
                    _uiState.value = _uiState.value.copy(    
                        isDownloadingAvatars = false,    
                        avatarDownloadProgress = 1f,    
                        avatarDownloadMessage = "完成，成功下载 ${success.get()} 个图标",    
                        cachedAvatarCount = newCount    
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