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
import com.pcrjjc.app.util.EmailSender 
import com.pcrjjc.app.util.TopBarBgStorage
import com.pcrjjc.app.util.IconStorage    
import com.pcrjjc.app.util.ExIconStorage
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
    val cachedAvatarCount: Int = 0,
    val rosterCount: Int = 0,
    val isUpdatingRoster: Boolean = false,
    val rosterMessage: String? = null,
    val updateMessage: String? = null,
    val updateInfo: UpdateInfo? = null,
    val isDownloadingAvatars: Boolean = false,
    val avatarDownloadProgress: Float = 0f,
    val avatarDownloadMessage: String? = null,  
    val serverIpInput: String = "114.514.1.1",   // ← 新增  
    val ipSaved: Boolean = false,                      // ← 新增  
	val emailAddress: String = "",                     // 邮箱地址  
    val emailAuthCode: String = "",                    // 邮箱授权码  
    val emailPushEnabled: Boolean = false,             // 邮箱推送开关  
    val emailSaved: Boolean = false,                   // 邮箱配置已保存  
    val isTestingEmail: Boolean = false,               // 正在测试邮件  
    val emailTestMessage: String? = null,               // 测试结果消息
	val cachedExIconCount: Int = 0,  
    val isDownloadingExIcons: Boolean = false,  
    val exIconDownloadProgress: Float = 0f,  
    val exIconDownloadMessage: String? = null
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
        _uiState.value = _uiState.value.copy(  
                cachedAvatarCount = count,  
                rosterCount = rosterCount,  
                cachedExIconCount = ExIconStorage.getCachedCount(context)  
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
            val ip = settingsDataStore.getServerIp()  
            _uiState.value = _uiState.value.copy(serverIpInput = ip)  
        }
	    viewModelScope.launch {  
            val email = settingsDataStore.getEmailAddress()  
            val authCode = settingsDataStore.getEmailAuthCode()  
            val enabled = settingsDataStore.isEmailPushEnabled()  
            _uiState.value = _uiState.value.copy(  
                emailAddress = email,  
                emailAuthCode = authCode,  
                emailPushEnabled = enabled  
            )  
        }
	}
  
    fun onIntervalInputChanged(input: String) {    
        _uiState.value = _uiState.value.copy(    
            pollingIntervalInput = input,    
            intervalSaved = false    
        )    
    }    
  
    fun onIpInputChanged(input: String) {  
        _uiState.value = _uiState.value.copy(  
            serverIpInput = input,  
            ipSaved = false  
        )  
    }  
  
    fun saveServerIp() {  
        val ip = _uiState.value.serverIpInput.trim()  
        if (ip.isBlank()) return  
        viewModelScope.launch {  
            settingsDataStore.setServerIp(ip)  
            _uiState.value = _uiState.value.copy(ipSaved = true)  
        }  
    }
	
	fun onEmailAddressChanged(input: String) {  
        _uiState.value = _uiState.value.copy(emailAddress = input, emailSaved = false)  
    }  
  
    fun onEmailAuthCodeChanged(input: String) {  
        _uiState.value = _uiState.value.copy(emailAuthCode = input, emailSaved = false)  
    }  
  
    fun onEmailPushEnabledChanged(enabled: Boolean) {  
        viewModelScope.launch {  
            settingsDataStore.setEmailPushEnabled(enabled)  
            _uiState.value = _uiState.value.copy(emailPushEnabled = enabled)  
        }  
    }  
  
    fun saveEmailSettings() {  
        val email = _uiState.value.emailAddress.trim()  
        val authCode = _uiState.value.emailAuthCode.trim()  
        if (email.isBlank() || authCode.isBlank()) return  
        viewModelScope.launch {  
            settingsDataStore.setEmailAddress(email)  
            settingsDataStore.setEmailAuthCode(authCode)  
            _uiState.value = _uiState.value.copy(emailSaved = true)  
        }  
    }  
  
    fun testEmailPush() {  
        val email = _uiState.value.emailAddress.trim()  
        val authCode = _uiState.value.emailAuthCode.trim()  
        if (email.isBlank() || authCode.isBlank()) return  
        _uiState.value = _uiState.value.copy(isTestingEmail = true, emailTestMessage = null)  
        viewModelScope.launch {  
            val result = EmailSender.sendEmail(  
                email = email,  
                authCode = authCode,  
                subject = "竞技场推送测试",  
                body = "这是一封测试邮件，说明邮箱推送配置正确。"  
            )  
            _uiState.value = if (result.isSuccess) {  
                _uiState.value.copy(isTestingEmail = false, emailTestMessage = "测试邮件发送成功！")  
            } else {  
                _uiState.value.copy(isTestingEmail = false, emailTestMessage = "发送失败: ${result.exceptionOrNull()?.message}")  
            }  
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
  
                    val roster = CharaRoster.fetchRoster(client, context)      
                    if (roster != null && roster.isNotEmpty()) {      
                        _uiState.value = _uiState.value.copy(      
                            avatarDownloadMessage = "花名册包含 ${roster.size} 个角色，正在获取服务端头像列表...",      
                            rosterCount = roster.size      
                        )      
                    } else {      
                        _uiState.value = _uiState.value.copy(      
                            avatarDownloadMessage = "花名册获取失败，继续获取服务端头像列表..."      
                        )      
                    }   
  
                    // 获取服务端实际存在的头像文件名列表（目录索引页），避免请求不存在的头像  
                    _uiState.value = _uiState.value.copy(  
                        avatarDownloadMessage = "正在获取服务端头像列表..."  
                    )  
                    val iconIds = try {  
                        client.newCall(  
                            Request.Builder().url(baseUrl).build()  
                        ).execute().use { resp ->  
                            if (!resp.isSuccessful) emptySet<String>()  
                            else {  
                                val html = resp.body?.string() ?: ""  
                                Regex("""(\d{4}[136]1)\.webp""").findAll(html)  
                                    .map { it.groupValues[1] }  
                                    .toSet()  
                            }  
                        }  
                    } catch (e: Exception) {  
                        emptySet()  
                    }  
  
                    if (iconIds.isEmpty()) {  
                        _uiState.value = _uiState.value.copy(  
                            isDownloadingAvatars = false,  
                            avatarDownloadMessage = "服务端头像列表获取失败"  
                        )  
                        return@withContext  
                    }  
  
                    _uiState.value = _uiState.value.copy(  
                        avatarDownloadMessage = "服务端共 ${iconIds.size} 个头像，正在对比本地缺失..."  
                    )  
  
                    // 头像ID为6位：前4位角色ID + 第5位星级 + 第6位固定"1"  
                    // 只下载服务端列表里存在、且本地缺失的头像  
                    val toDownload = mutableListOf<Pair<Int, Int>>()  
                    for (iconId in iconIds) {  
                        val baseId = iconId.substring(0, 4).toInt()  
                        val star = iconId[4].digitToInt()  
                        if (!IconStorage.hasIcon(context, baseId, star)) {  
                            toDownload.add(baseId to star)  
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
  
    fun downloadAllExIcons() {  
        if (_uiState.value.isDownloadingExIcons) return  
  
        viewModelScope.launch {  
            _uiState.value = _uiState.value.copy(  
                isDownloadingExIcons = true,  
                exIconDownloadProgress = 0f,  
                exIconDownloadMessage = "正在获取服务端EX图标列表..."  
            )  
  
            try {  
                withContext(Dispatchers.IO) {  
                    val client = OkHttpClient.Builder()  
                        .connectTimeout(10, TimeUnit.SECONDS)  
                        .readTimeout(10, TimeUnit.SECONDS)  
                        .build()  
  
                    // redive 图床EX装备图标目录（与头像同站的 equipment 目录）  
                    val baseUrl = "https://redive.estertion.win/icon/equipment/"  
  
                    val iconIds = try {  
                        client.newCall(  
                            Request.Builder().url(baseUrl).build()  
                        ).execute().use { resp ->  
                            if (!resp.isSuccessful) emptySet<Int>()  
                            else {  
                                val html = resp.body?.string() ?: ""  
                                // 文件名形如 icon_equipment_500001.webp，EX装备ID为6位  
                                Regex("""(\d{6})\.webp""").findAll(html)  
                                    .map { it.groupValues[1].toInt() }  
                                    .toSet()  
                            }  
                        }  
                    } catch (e: Exception) {  
                        emptySet()  
                    }  
  
                    if (iconIds.isEmpty()) {  
                        _uiState.value = _uiState.value.copy(  
                            isDownloadingExIcons = false,  
                            exIconDownloadMessage = "服务端EX图标列表获取失败"  
                        )  
                        return@withContext  
                    }  
  
                    val toDownload = iconIds.filter { !ExIconStorage.hasIcon(context, it) }  
                    if (toDownload.isEmpty()) {  
                        _uiState.value = _uiState.value.copy(  
                            isDownloadingExIcons = false,  
                            exIconDownloadProgress = 1f,  
                            exIconDownloadMessage = "所有EX图标已是最新（共 ${iconIds.size} 个）"  
                        )  
                        return@withContext  
                    }  
  
                    _uiState.value = _uiState.value.copy(  
                        exIconDownloadMessage = "需要下载 ${toDownload.size} 个EX图标..."  
                    )  
  
                    val completed = AtomicInteger(0)  
                    val success = AtomicInteger(0)  
                    val total = toDownload.size  
                    val semaphore = Semaphore(5)  
  
                    coroutineScope {  
                        val jobs = toDownload.map { exId ->  
                            async(Dispatchers.IO) {  
                                semaphore.withPermit {  
                                    try {  
                                        val url = "${baseUrl}${exId}.webp"  
                                        val request = Request.Builder().url(url).build()  
                                        client.newCall(request).execute().use { resp ->  
                                            if (resp.isSuccessful) {  
                                                val bytes = resp.body?.bytes()  
                                                if (bytes != null) {  
                                                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)  
                                                    if (bitmap != null) {  
                                                        ExIconStorage.saveBitmap(context, exId, bitmap)  
                                                        bitmap.recycle()  
                                                        success.incrementAndGet()  
                                                    }  
                                                }  
                                            }  
                                        }  
                                    } catch (e: Exception) {  
                                        Log.w("SettingsVM", "EX icon failed: $exId: ${e.message}")  
                                    }  
                                    val done = completed.incrementAndGet()  
                                    if (done % 10 == 0 || done == total) {  
                                        _uiState.value = _uiState.value.copy(  
                                            exIconDownloadProgress = done.toFloat() / total,  
                                            exIconDownloadMessage = "下载中 $done/$total (成功 ${success.get()})"  
                                        )  
                                    }  
                                }  
                            }  
                        }  
                        jobs.awaitAll()  
                    }  
  
                    _uiState.value = _uiState.value.copy(  
                        isDownloadingExIcons = false,  
                        exIconDownloadProgress = 1f,  
                        exIconDownloadMessage = "完成，成功下载 ${success.get()} 个EX图标",  
                        cachedExIconCount = ExIconStorage.getCachedCount(context)  
                    )  
                }  
            } catch (e: Exception) {  
                Log.e("SettingsVM", "EX icon download failed", e)  
                _uiState.value = _uiState.value.copy(  
                    isDownloadingExIcons = false,  
                    exIconDownloadMessage = "下载失败: ${e.message}"  
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
  
    // ========== 新增：清除自定义顶栏背景，恢复默认 ==========  
    fun clearTopBarBg() {  
        TopBarBgStorage.clear(context)  
    }  
}