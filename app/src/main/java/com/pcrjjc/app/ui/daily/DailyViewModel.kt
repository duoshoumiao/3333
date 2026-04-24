package com.pcrjjc.app.ui.daily  
  
import android.util.Log  
import androidx.lifecycle.ViewModel  
import androidx.lifecycle.viewModelScope  
import com.pcrjjc.app.data.local.SettingsDataStore  
import dagger.hilt.android.lifecycle.HiltViewModel  
import kotlinx.coroutines.Dispatchers  
import kotlinx.coroutines.flow.MutableStateFlow  
import kotlinx.coroutines.flow.StateFlow  
import kotlinx.coroutines.launch  
import kotlinx.coroutines.withContext  
import okhttp3.Cookie  
import okhttp3.CookieJar  
import okhttp3.HttpUrl  
import okhttp3.MediaType.Companion.toMediaType  
import okhttp3.OkHttpClient  
import okhttp3.Request  
import okhttp3.RequestBody.Companion.toRequestBody  
import org.json.JSONArray  
import org.json.JSONObject  
import java.util.concurrent.TimeUnit  
import javax.inject.Inject  
import kotlinx.coroutines.async  
import kotlinx.coroutines.coroutineScope
  
// ==================== 指令数据 ====================  
  
data class CommandItem(  
    val command: String,  
    val description: String  
)  
  
val DAILY_COMMANDS: List<CommandItem> = listOf(  
    CommandItem("#清日常 ", "无昵称则默认账号"),  
    CommandItem("#清日常所有", "清该qq号下所有号的日常"),  
    CommandItem("#日常记录", "查看清日常状态"),  
    CommandItem("#日常报告 [0|1|2|3]", "最近四次清日常报告"),  
    CommandItem("#查角色 [昵称]", "查看角色练度"),  
    CommandItem("#查缺角色", "查看缺少的限定常驻角色"),  
    CommandItem("#查ex装备 [会战]", "查看ex装备库存"),  
    CommandItem("#查探险编队", "根据记忆碎片角色编队战力相当的队伍"),  
    CommandItem("#查兑换角色碎片 [开换]", "查询兑换特别角色的记忆碎片策略"),  
    CommandItem("#查心碎", "查询缺口心碎"),  
    CommandItem("#查纯净碎片", "查询缺口纯净碎片，国服六星+日服二专需求"),  
    CommandItem("#查记忆碎片 [可刷取|大师币]", "查询缺口记忆碎片，可按地图可刷取或大师币商店过滤"),  
    CommandItem("#查装备 [rank] [fav]", "查询缺口装备，rank为数字，fav表示只查询favorite的角色"),  
    CommandItem("#刷图推荐 [rank] [fav]", "查询缺口装备的刷图推荐，格式同上"),  
    CommandItem("#公会支援", "查询公会支援角色配置"), 
    CommandItem("#刷新box", ""),  
    CommandItem("#查缺称号", "查看缺少的称号"),  
    CommandItem("#jjc透视", "查前51名"),  
    CommandItem("#pjjc透视", "查前51名"),  
    CommandItem("#jjc回刺 排名 阵容编号", "比如 #jjc回刺 19 2 就是打19 选择阵容2进攻"),  
    CommandItem("#pjjc回刺 记录编号", "比如 #pjjc回刺 -1 就是打记录里第一条"),  
    CommandItem("#pjjc换防", "将pjjc防守阵容随机错排"),  
    CommandItem("#卡池", "查看当前卡池"),   
    CommandItem("#免费十连 卡池id", "卡池id来自【#卡池】"),  
    CommandItem("#来发十连 卡池id [抽到出] [单抽券|单抽] [编号小优先] [开抽]", "赛博抽卡，谨慎使用"),  
    CommandItem("#智能刷h图", ""),  
    CommandItem("#智能刷外传", ""),  
    CommandItem("#刷专二", ""),  
    CommandItem("#查深域", ""),  
    CommandItem("#强化ex装", ""),  
    CommandItem("#合成ex装", ""),    
    CommandItem("#领小屋体力", ""),  
    CommandItem("#公会点赞", ""),  
    CommandItem("#领每日体力", ""),  
    CommandItem("#领取礼物箱", ""),  
    CommandItem("#查公会深域进度", ""),  
    CommandItem("#收菜", "探险续航"),  
    CommandItem("#一键编队 页 队 队名 星级角色1 ...", "设置多队编队，队伍不足5人结尾"),  
    CommandItem("#兑天井 卡池id 角色名", "如 #兑天井 10283 火电  用 #卡池 获取ID"),  
    CommandItem("#拉角色练度 等级 品级 ub s1 s2 ex 装备星级 专武1 专武2 角色名", "角色名不输入则全选"),  
    CommandItem("#大富翁 [保留骰子数] [搬空商店为止|不止搬空商店] [到达次数]", "运行大富翁游戏"),  
    CommandItem("#商店购买 [上期|当期]", "购买大富翁商店物品，默认购买当期"),  
    CommandItem("#查玩家 uid", ""),  
    CommandItem("#炼金 物贯 物贯 物贯 物贯 1 彩装ID +(看属性/看概率/炼成)", "炼成之前去网站设置参数"),  
    CommandItem("#撤下会战ex装", ""),  
    CommandItem("#撤下普通ex装", ""),  
    CommandItem("#买记忆碎片 角色 星级 专武 开买 界限突破", "分别代表:角色 星级 专武 是否购买 是否突破"),  
    CommandItem("#角色升星 星级 忽略盈余 升至最高 角色名", ""),  
    CommandItem("#角色突破 忽略盈余 角色名", "忽略盈余：碎片不溢出就不突破"),  
    CommandItem("#pjjc自动换防", "字面意思"), 
    CommandItem("#终止换防", "停止自动换防任务"),   // ← 新增   
    CommandItem("#挂地下城支援 [星级]角色", "星级可选(3/4/5)，如：#挂好友支援 3水电"),  
    CommandItem("#挂会战支援 [星级]角色", ""),  
    CommandItem("#挂好友支援 [星级]角色", ""),  
    CommandItem("#一键穿ex +角色名 试穿/数字 1 2 3", "数字0表示不改动"),  
    CommandItem("#添加好友", ""),   
    CommandItem("#保存ex状态", "保存当前所有角色的普通EX装备穿戴状态"),  
    CommandItem("#恢复ex状态", "恢复之前保存的普通EX装备穿戴状态"),  
)  
  
// ==================== 定时任务数据 ====================  
  
/** 后端可用的 cron 槽位索引（cron5/cron6 已注释掉） */  
val CRON_INDICES = listOf(1, 2, 3, 4, 7, 8, 9, 10)  
  
/** 不执行日常模块的可选值 */  
val MODULE_EXCLUDE_CANDIDATES = listOf("体力获取", "体力消耗")  
  
data class CronConfig(  
    val index: Int,                          // 槽位编号，如 1,2,3,4,7,8,9,10  
    val enabled: Boolean = false,            // cronN  
    val time: String = "00:00",              // time_cronN  "HH:mm"  
    val clanbattleRun: Boolean = false,      // clanbattle_run_cronN  
    val moduleExcludeType: List<String> = emptyList()  // module_exclude_type_cronN  
)  

data class SavedDailyAccount(  
    val qq: String,  
    val password: String  
)

// ==================== 日常模块数据 ====================  
  
data class DailyCandidateEntry(  
    val value: Any?,  
    val display: String,  
    val tags: List<String> = emptyList()  
)  
  
data class DailyConfigEntry(  
    val key: String,  
    val desc: String,  
    val configType: String,       // bool / int / single / multi / multi_search / text / time  
    val default: Any? = null,  
    val currentValue: Any? = null,  
    val candidates: List<DailyCandidateEntry> = emptyList()  
)  
  
data class DailyModuleItem(  
    val key: String,  
    val name: String,  
    val description: String,  
    val enabled: Boolean,  
    val implemented: Boolean = true,  
    val staminaRelative: Boolean = false,  
    val tags: List<String> = emptyList(),  
    val runnable: Boolean = true,  
    val configOrder: List<String> = emptyList(),  
    val configs: List<DailyConfigEntry> = emptyList()  
)
  
// ==================== UI 状态 ====================  
  
enum class DailyPhase { LOGIN, ACCOUNTS, COMMANDS }  
  
data class DailyUiState(  
    val phase: DailyPhase = DailyPhase.LOGIN,  
    val isLoading: Boolean = false,
    val isAutoDefRunning: Boolean = false,       // ← 新增  
    val autoDefMessages: List<String> = emptyList(),  // ← 新增   
    val serverUrl: String? = null,  
    val qqInput: String = "",  
    val passwordInput: String = "",  
    val accounts: List<String> = emptyList(),  
    val selectedAccount: String? = null,  
    val errorMessage: String? = null,  
    val isExecuting: Boolean = false,  
    val executionResult: String? = null,  
    val showResultDialog: Boolean = false,  
	val resultImageBase64: String? = null,
    // ---- 定时任务 ----  
    val cronConfigs: List<CronConfig> = emptyList(),  
    val isLoadingCron: Boolean = false,  
    val isSavingCron: Boolean = false,  
    val cronError: String? = null,  
    val showCronSection: Boolean = false,
    val savedAccounts: List<SavedDailyAccount> = emptyList(),  
    // ---- 日常模块 ----  
    val showDailySection: Boolean = false,  
    val isLoadingDaily: Boolean = false,  
    val isSavingDaily: Boolean = false,  
    val dailyModules: List<DailyModuleItem> = emptyList(),  
    val dailyError: String? = null,  
    val expandedModuleKey: String? = null,
	// ---- 工具模块 ----  
    val showToolSection: Boolean = false,  
    val isLoadingTool: Boolean = false,  
    val isSavingTool: Boolean = false,  
    val toolModules: List<DailyModuleItem> = emptyList(),  
    val toolError: String? = null,  
    val expandedToolModuleKey: String? = null,  
    val executingModuleKey: String? = null,  // 正在执行单个模块的key（所有模块共用）  
    // ---- 角色模块 ----  
    val showUnitSection: Boolean = false,  
    val isLoadingUnit: Boolean = false,  
    val isSavingUnit: Boolean = false,  
    val unitModules: List<DailyModuleItem> = emptyList(),  
    val unitError: String? = null,  
    val expandedUnitModuleKey: String? = null,  
    // ---- 规划模块 ----  
    val showPlanningSection: Boolean = false,  
    val isLoadingPlanning: Boolean = false,  
    val isSavingPlanning: Boolean = false,  
    val planningModules: List<DailyModuleItem> = emptyList(),  
    val planningError: String? = null,  
    val expandedPlanningModuleKey: String? = null,  
    // ---- 公会模块 ----  
    val showClanSection: Boolean = false,  
    val isLoadingClan: Boolean = false,  
    val isSavingClan: Boolean = false,  
    val clanModules: List<DailyModuleItem> = emptyList(),  
    val clanError: String? = null,  
    val expandedClanModuleKey: String? = null,  
    // ---- 危险模块 ----  
    val showDangerSection: Boolean = false,  
    val isLoadingDanger: Boolean = false,  
    val isSavingDanger: Boolean = false,  
    val dangerModules: List<DailyModuleItem> = emptyList(),  
    val dangerError: String? = null,  
    val expandedDangerModuleKey: String? = null,  
    // ---- 修改密码 ----  
    val showChangePasswordDialog: Boolean = false,  
    val newPasswordInput: String = "",  
    val isChangingPassword: Boolean = false, 
	// ---- 账号管理 ----  
    val showCreateAccountDialog: Boolean = false,  
    val createAccountAlias: String = "",  
    val createAccountUsername: String = "",  
    val createAccountPassword: String = "",  
    val createAccountChannel: String = "官服",  
    val isCreatingAccount: Boolean = false,  
    val createAccountError: String? = null,  
  
    val showEditAccountDialog: Boolean = false,  
    val editAccountAlias: String = "",  
    val editAccountUsername: String = "",  
    val editAccountPassword: String = "",  
    val editAccountChannel: String = "官服",  
    val isEditingAccount: Boolean = false,  
    val editAccountError: String? = null,  
  
    val showDeleteConfirmDialog: Boolean = false,  
    val deleteTargetAlias: String = "",  
    val isDeletingAccount: Boolean = false
)  
  
// ==================== ViewModel ====================  
  
@HiltViewModel  
class DailyViewModel @Inject constructor(  
    private val settingsDataStore: SettingsDataStore  
) : ViewModel() {  
  
    companion object {  
        private const val TAG = "DailyVM"  
        private const val APP_VERSION = "1.7.0"  
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()  
		val CHANNEL_OPTIONS = listOf("官服", "渠道服", "官服免登录") 
    }  
  
    private val _uiState = MutableStateFlow(DailyUiState())  
    val uiState: StateFlow<DailyUiState> = _uiState  
  
    private val cookieStore = mutableListOf<Cookie>()  
  
    private val httpClient = OkHttpClient.Builder()  
        .connectTimeout(30, TimeUnit.SECONDS)  
        .readTimeout(300, TimeUnit.SECONDS)  // 指令执行可能很久  
        .writeTimeout(30, TimeUnit.SECONDS)  
        .cookieJar(object : CookieJar {  
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {  
                cookieStore.addAll(cookies)  
            }  
            override fun loadForRequest(url: HttpUrl): List<Cookie> {  
                return cookieStore.toList()  
            }  
        })  
        .build()  
  
    init {  
		viewModelScope.launch {  
			val url = settingsDataStore.getDailyServerUrl()  
			_uiState.value = _uiState.value.copy(serverUrl = url)  
			loadSavedAccounts()   // ← 新增  
		}  
	}  
  
    // ==================== 输入 ====================  
  
    fun onQqInputChanged(value: String) {  
        _uiState.value = _uiState.value.copy(qqInput = value)  
    }  
  
    fun onPasswordInputChanged(value: String) {  
        _uiState.value = _uiState.value.copy(passwordInput = value)  
    }  
  
    // ==================== 登录 ====================  
  
    fun login() {  
        val state = _uiState.value  
        val baseUrl = state.serverUrl  
        if (baseUrl.isNullOrBlank()) {  
            _uiState.value = state.copy(errorMessage = "请先在设置中配置清日常服务器地址")  
            return  
        }  
        val qq = state.qqInput.trim()  
        val password = state.passwordInput.trim()  
        if (qq.isBlank() || password.isBlank()) {  
            _uiState.value = state.copy(errorMessage = "请输入QQ和密码")  
            return  
        }  
  
        _uiState.value = state.copy(isLoading = true, errorMessage = null)  
  
        viewModelScope.launch {  
            try {  
                val loginResult = withContext(Dispatchers.IO) {  
                    val json = JSONObject().apply {  
                        put("qq", qq)  
                        put("password", password)  
                    }  
                    val request = Request.Builder()  
                        .url("$baseUrl/daily/api/login/qq")  
                        .addHeader("X-App-Version", APP_VERSION)  
                        .post(json.toString().toRequestBody(JSON_MEDIA_TYPE))  
                        .build()  
                    httpClient.newCall(request).execute().use { resp ->  
                        val text = resp.body?.string() ?: ""  
                        if (!resp.isSuccessful) throw Exception(text.ifBlank { "登录失败 (${resp.code})" })  
                        text  
                    }  
                }  
                Log.d(TAG, "Login success: $loginResult")  
                saveCurrentAccount()   // ← 新增  
				loadAccounts()  
            } catch (e: Exception) {  
                Log.e(TAG, "Login failed: ${e.message}", e)  
                _uiState.value = _uiState.value.copy(  
                    isLoading = false,  
                    errorMessage = "登录失败: ${e.message}"  
                )  
            }  
        }  
    }  
  
    // ==================== 加载账号列表 ====================  
  
    private suspend fun loadAccounts() {  
        val baseUrl = _uiState.value.serverUrl ?: return  
        try {  
            val accounts = withContext(Dispatchers.IO) {  
                val request = Request.Builder()  
                    .url("$baseUrl/daily/api/account")  
                    .addHeader("X-App-Version", APP_VERSION)  
                    .get()  
                    .build()  
                httpClient.newCall(request).execute().use { resp ->  
                    val text = resp.body?.string() ?: ""  
                    if (!resp.isSuccessful) throw Exception(text.ifBlank { "获取账号失败 (${resp.code})" })  
                    val json = JSONObject(text)  
                    val arr = json.optJSONArray("accounts") ?: throw Exception("返回数据格式异常")  
                    val list = mutableListOf<String>()  
                    for (i in 0 until arr.length()) {  
                        val obj = arr.getJSONObject(i)  
                        list.add(obj.optString("name", "账号${i + 1}"))  
                    }  
                    list  
                }  
            }  
            _uiState.value = _uiState.value.copy(  
                isLoading = false,  
                phase = DailyPhase.ACCOUNTS,  
                accounts = accounts,  
                errorMessage = null  
            )  
        } catch (e: Exception) {  
            Log.e(TAG, "Load accounts failed: ${e.message}", e)  
            _uiState.value = _uiState.value.copy(  
                isLoading = false,  
                errorMessage = "获取账号列表失败: ${e.message}"  
            )  
        }  
    }  
  
    // ==================== 账号管理 ====================  
  
    fun showCreateDialog() {  
        _uiState.value = _uiState.value.copy(  
            showCreateAccountDialog = true,  
            createAccountAlias = "",  
            createAccountUsername = "",  
            createAccountPassword = "",  
            createAccountChannel = "官服",  
            createAccountError = null  
        )  
    }  
  
    fun dismissCreateDialog() {  
        _uiState.value = _uiState.value.copy(  
            showCreateAccountDialog = false,  
            createAccountAlias = "",  
            createAccountUsername = "",  
            createAccountPassword = "",  
            createAccountChannel = "官服",  
            isCreatingAccount = false,  
            createAccountError = null  
        )  
    }  
  
    fun onCreateAliasChanged(value: String) {  
        _uiState.value = _uiState.value.copy(createAccountAlias = value)  
    }  
  
    fun onCreateUsernameChanged(value: String) {  
        _uiState.value = _uiState.value.copy(createAccountUsername = value)  
    }  
  
    fun onCreatePasswordChanged(value: String) {  
        _uiState.value = _uiState.value.copy(createAccountPassword = value)  
    }  
  
    fun onCreateChannelChanged(value: String) {  
        _uiState.value = _uiState.value.copy(createAccountChannel = value)  
    }  
  
    fun createAccount() {  
        val state = _uiState.value  
        val baseUrl = state.serverUrl ?: return  
        val alias = state.createAccountAlias.trim()  
        if (alias.isBlank()) {  
            _uiState.value = state.copy(createAccountError = "账号昵称不能为空")  
            return  
        }  
  
        _uiState.value = state.copy(isCreatingAccount = true, createAccountError = null)  
  
        viewModelScope.launch {  
            try {  
                withContext(Dispatchers.IO) {  
                    // 1. 创建账号  
                    val createJson = JSONObject().apply { put("alias", alias) }  
                    val createReq = Request.Builder()  
                        .url("$baseUrl/daily/api/account")  
                        .addHeader("X-App-Version", APP_VERSION)  
                        .post(createJson.toString().toRequestBody(JSON_MEDIA_TYPE))  
                        .build()  
                    httpClient.newCall(createReq).execute().use { resp ->  
                        val text = resp.body?.string() ?: ""  
                        if (!resp.isSuccessful) throw Exception(text.ifBlank { "创建账号失败 (${resp.code})" })  
                    }  
  
                    // 2. 如果填了用户名/密码，更新账号信息  
                    val username = _uiState.value.createAccountUsername.trim()  
                    val password = _uiState.value.createAccountPassword.trim()  
                    val channel = _uiState.value.createAccountChannel  
                    if (username.isNotBlank() || password.isNotBlank()) {  
                        val updateJson = JSONObject().apply {  
                            if (username.isNotBlank()) put("username", username)  
                            if (password.isNotBlank()) put("password", password)  
                            put("channel", channel)  
                        }  
                        val updateReq = Request.Builder()  
                            .url("$baseUrl/daily/api/account/$alias")  
                            .addHeader("X-App-Version", APP_VERSION)  
                            .put(updateJson.toString().toRequestBody(JSON_MEDIA_TYPE))  
                            .build()  
                        httpClient.newCall(updateReq).execute().use { resp ->  
                            val text = resp.body?.string() ?: ""  
                            if (!resp.isSuccessful) throw Exception(text.ifBlank { "保存账号信息失败 (${resp.code})" })  
                        }  
                    }  
                }  
                dismissCreateDialog()  
                loadAccounts()  
            } catch (e: Exception) {  
                Log.e(TAG, "Create account failed: ${e.message}", e)  
                _uiState.value = _uiState.value.copy(  
                    isCreatingAccount = false,  
                    createAccountError = "创建失败: ${e.message}"  
                )  
            }  
        }  
    }  
  
    fun showEditDialog(alias: String) {  
        _uiState.value = _uiState.value.copy(  
            showEditAccountDialog = true,  
            editAccountAlias = alias,  
            editAccountUsername = "",  
            editAccountPassword = "",  
            editAccountChannel = "官服",  
            isEditingAccount = true,  
            editAccountError = null  
        )  
        val baseUrl = _uiState.value.serverUrl ?: return  
        viewModelScope.launch {  
            try {  
                val info = withContext(Dispatchers.IO) {  
                    val req = Request.Builder()  
                        .url("$baseUrl/daily/api/account/$alias")  
                        .addHeader("X-App-Version", APP_VERSION)  
                        .get()  
                        .build()  
                    httpClient.newCall(req).execute().use { resp ->  
                        val text = resp.body?.string() ?: ""  
                        if (!resp.isSuccessful) throw Exception(text.ifBlank { "获取账号信息失败 (${resp.code})" })  
                        JSONObject(text)  
                    }  
                }  
                _uiState.value = _uiState.value.copy(  
                    editAccountUsername = info.optString("username", ""),  
                    editAccountPassword = "",  
                    editAccountChannel = info.optString("channel", "官服"),  
                    isEditingAccount = false  
                )  
            } catch (e: Exception) {  
                Log.e(TAG, "Load account info failed: ${e.message}", e)  
                _uiState.value = _uiState.value.copy(  
                    isEditingAccount = false,  
                    editAccountError = "加载账号信息失败: ${e.message}"  
                )  
            }  
        }  
    }  
  
    fun dismissEditDialog() {  
        _uiState.value = _uiState.value.copy(  
            showEditAccountDialog = false,  
            editAccountAlias = "",  
            editAccountUsername = "",  
            editAccountPassword = "",  
            editAccountChannel = "官服",  
            isEditingAccount = false,  
            editAccountError = null  
        )  
    }  
  
    fun onEditUsernameChanged(value: String) {  
        _uiState.value = _uiState.value.copy(editAccountUsername = value)  
    }  
  
    fun onEditPasswordChanged(value: String) {  
        _uiState.value = _uiState.value.copy(editAccountPassword = value)  
    }  
  
    fun onEditChannelChanged(value: String) {  
        _uiState.value = _uiState.value.copy(editAccountChannel = value)  
    }  
  
    fun saveAccountInfo() {  
        val state = _uiState.value  
        val baseUrl = state.serverUrl ?: return  
        val alias = state.editAccountAlias  
  
        _uiState.value = state.copy(isEditingAccount = true, editAccountError = null)  
  
        viewModelScope.launch {  
            try {  
                withContext(Dispatchers.IO) {  
                    val json = JSONObject().apply {  
                        put("username", state.editAccountUsername.trim())  
                        put("password", state.editAccountPassword.trim())  
                        put("channel", state.editAccountChannel)  
                    }  
                    val req = Request.Builder()  
                        .url("$baseUrl/daily/api/account/$alias")  
                        .addHeader("X-App-Version", APP_VERSION)  
                        .put(json.toString().toRequestBody(JSON_MEDIA_TYPE))  
                        .build()  
                    httpClient.newCall(req).execute().use { resp ->  
                        val text = resp.body?.string() ?: ""  
                        if (!resp.isSuccessful) throw Exception(text.ifBlank { "保存失败 (${resp.code})" })  
                    }  
                }  
                dismissEditDialog()  
            } catch (e: Exception) {  
                Log.e(TAG, "Save account info failed: ${e.message}", e)  
                _uiState.value = _uiState.value.copy(  
                    isEditingAccount = false,  
                    editAccountError = "保存失败: ${e.message}"  
                )  
            }  
        }  
    }  
  
    fun showDeleteConfirm(alias: String) {  
        _uiState.value = _uiState.value.copy(  
            showDeleteConfirmDialog = true,  
            deleteTargetAlias = alias  
        )  
    }  
  
    fun dismissDeleteConfirm() {  
        _uiState.value = _uiState.value.copy(  
            showDeleteConfirmDialog = false,  
            deleteTargetAlias = "",  
            isDeletingAccount = false  
        )  
    }  
  
    fun deleteAccount() {  
        val state = _uiState.value  
        val baseUrl = state.serverUrl ?: return  
        val alias = state.deleteTargetAlias  
        if (alias.isBlank()) return  
  
        _uiState.value = state.copy(isDeletingAccount = true)  
  
        viewModelScope.launch {  
            try {  
                withContext(Dispatchers.IO) {  
                    val req = Request.Builder()  
                        .url("$baseUrl/daily/api/account/$alias")  
                        .addHeader("X-App-Version", APP_VERSION)  
                        .delete()  
                        .build()  
                    httpClient.newCall(req).execute().use { resp ->  
                        val text = resp.body?.string() ?: ""  
                        if (!resp.isSuccessful) throw Exception(text.ifBlank { "删除失败 (${resp.code})" })  
                    }  
                }  
                dismissDeleteConfirm()  
                loadAccounts()  
            } catch (e: Exception) {  
                Log.e(TAG, "Delete account failed: ${e.message}", e)  
                _uiState.value = _uiState.value.copy(  
                    isDeletingAccount = false,  
                    errorMessage = "删除账号失败: ${e.message}"  
                )  
                dismissDeleteConfirm()  
            }  
        }  
    }
	
	// ==================== 选择账号 ====================  
  
    fun selectAccount(alias: String) {    
        _uiState.value = _uiState.value.copy(    
            phase = DailyPhase.COMMANDS,    
            selectedAccount = alias,    
            cronConfigs = emptyList(),    
            showCronSection = false,    
            cronError = null,  
            // 切换账号时重置日常状态  
            dailyModules = emptyList(),  
            showDailySection = false,  
            dailyError = null,  
            expandedModuleKey = null,
            // 切换账号时重置工具模块状态  
            toolModules = emptyList(),  
            showToolSection = false,  
            toolError = null,  
            expandedToolModuleKey = null,  
            executingModuleKey = null,  
            // 切换账号时重置角色模块状态  
            unitModules = emptyList(),  
            showUnitSection = false,  
            unitError = null,  
            expandedUnitModuleKey = null,  
            // 切换账号时重置规划模块状态  
            planningModules = emptyList(),  
            showPlanningSection = false,  
            planningError = null,  
            expandedPlanningModuleKey = null,  
            // 切换账号时重置公会模块状态  
            clanModules = emptyList(),  
            showClanSection = false,  
            clanError = null,  
            expandedClanModuleKey = null,  
            // 切换账号时重置危险模块状态  
            dangerModules = emptyList(),  
            showDangerSection = false,  
            dangerError = null,  
            expandedDangerModuleKey = null			
        )    
    }  
  
    // ==================== 执行指令（核心：通过 command relay） ====================  
  
	fun executeCommand(commandText: String) {  
		val state = _uiState.value  
		val baseUrl = state.serverUrl ?: return  
		val acc = state.selectedAccount ?: return  
	  
		if (commandText.isBlank()) return  
	  
		// 拦截 #日常设置 命令，通过 API 直接处理  
		val settingMatch = Regex("""^#日常设置\s+(\S+)\s+(\S+)\s+(.+)$""").find(commandText.trim())  
		if (settingMatch != null) {  
			val (moduleTarget, optionTarget, valueStr) = settingMatch.destructured  
			handleDailySettingViaApi(baseUrl, acc, moduleTarget, optionTarget, valueStr)  
			return  
		}  
	  
		// 拦截 #清日常所有  
		if (commandText.trim() == "#清日常所有") {  
			executeDailyAll(baseUrl)  
			return  
		}  
  
		// ===== 新增：拦截 #pjjc自动换防 =====  
		if (commandText.trim() == "#pjjc自动换防") {  
			startAutoDefense(baseUrl, acc)  
			return  
		}  
	  
		// ===== 新增：拦截 #终止换防 =====  
		if (commandText.trim() == "#终止换防") {  
			stopAutoDefense(baseUrl)  
			return  
		}  
	  
  
        _uiState.value = state.copy(isExecuting = true, errorMessage = null)  
  
        viewModelScope.launch {  
            try {  
                val resultJson = withContext(Dispatchers.IO) {  
                    val json = JSONObject().apply {  
                        put("command", commandText)  
                    }  
                    val request = Request.Builder()  
                        .url("$baseUrl/daily/api/account/$acc/command")  
                        .addHeader("X-App-Version", APP_VERSION)  
                        .post(json.toString().toRequestBody(JSON_MEDIA_TYPE))  
                        .build()  
                    httpClient.newCall(request).execute().use { resp ->  
                        resp.body?.string() ?: ""  
                    }  
                }  
  
                // 提取后端渲染的图片（如果有）  
                val imageBase64 = try {  
                    val obj = JSONObject(resultJson)  
                    val result = obj.optJSONObject("result")  
                    if (result != null && result.has("image") && !result.isNull("image")) {  
                        result.getString("image")  
                    } else null  
                } catch (e: Exception) { null }  
  
                val displayText = parseRelayResponse(resultJson)  
                _uiState.value = _uiState.value.copy(  
                    isExecuting = false,  
                    executionResult = displayText,  
                    resultImageBase64 = imageBase64,  
                    showResultDialog = true  
                )  
            } catch (e: Exception) {  
                Log.e(TAG, "Execute command failed: ${e.message}", e)  
                _uiState.value = _uiState.value.copy(  
                    isExecuting = false,  
                    executionResult = "执行失败: ${e.message}",  
                    showResultDialog = true  
                )  
            }  
        }  
    }  
	
	private fun executeDailyAll(baseUrl: String) {  
		val accounts = _uiState.value.accounts  
		if (accounts.isEmpty()) {  
			_uiState.value = _uiState.value.copy(  
				executionResult = "没有可用账号",  
				showResultDialog = true  
			)  
			return  
		}  
	  
		_uiState.value = _uiState.value.copy(isExecuting = true, errorMessage = null)  
	  
		viewModelScope.launch {  
			try {  
				val results = coroutineScope {  
					accounts.map { accName ->  
						async(Dispatchers.IO) {  
							try {  
								val request = Request.Builder()  
									.url("$baseUrl/daily/api/account/$accName/do_daily")  
									.addHeader("X-App-Version", APP_VERSION)  
									.post("{}".toRequestBody(JSON_MEDIA_TYPE))  
									.build()  
								httpClient.newCall(request).execute().use { resp ->  
									val text = resp.body?.string() ?: ""  
									if (!resp.isSuccessful) {  
										Pair(accName, "失败: ${text.ifBlank { "HTTP ${resp.code}" }}")  
									} else {  
										Pair(accName, "成功")  
									}  
								}  
							} catch (e: Exception) {  
								Pair(accName, "失败: ${e.message}")  
							}  
						}  
					}.map { it.await() }  
				}  
	  
				// 汇总结果  
				val resultText = buildString {  
					appendLine("清日常所有 执行结果：")  
					appendLine("=".repeat(30))  
					for (pair in results) {  
						appendLine("【${pair.first}】${pair.second}")  
					}  
				}  
	  
				_uiState.value = _uiState.value.copy(  
					isExecuting = false,  
					executionResult = resultText,  
					showResultDialog = true  
				)  
			} catch (e: Exception) {  
				Log.e(TAG, "executeDailyAll failed: ${e.message}", e)  
				_uiState.value = _uiState.value.copy(  
					isExecuting = false,  
					executionResult = "清日常所有执行失败: ${e.message}",  
					showResultDialog = true  
				)  
			}  
		}  
	}
	
	private var autoDefPollJob: kotlinx.coroutines.Job? = null  
  
    private fun startAutoDefense(baseUrl: String, acc: String) {  
        _uiState.value = _uiState.value.copy(isExecuting = true, errorMessage = null)  
  
        viewModelScope.launch {  
            try {  
                val result = withContext(Dispatchers.IO) {  
                    val request = Request.Builder()  
                        .url("$baseUrl/daily/api/account/$acc/pjjc_auto_def/start")  
                        .addHeader("X-App-Version", APP_VERSION)  
                        .post("{}".toRequestBody(JSON_MEDIA_TYPE))  
                        .build()  
                    httpClient.newCall(request).execute().use { resp ->  
                        val text = resp.body?.string() ?: ""  
                        if (!resp.isSuccessful) {  
                            val json = try { JSONObject(text) } catch (e: Exception) { null }  
                            throw Exception(json?.optString("message") ?: text.ifBlank { "启动失败 (${resp.code})" })  
                        }  
                        val json = JSONObject(text)  
                        json.optString("message", "自动换防已启动")  
                    }  
                }  
  
                _uiState.value = _uiState.value.copy(  
                    isExecuting = false,  
                    isAutoDefRunning = true,  
                    executionResult = result,  
                    showResultDialog = true  
                )  
  
                pollAutoDefStatus(baseUrl)  
  
            } catch (e: Exception) {  
                Log.e(TAG, "Start auto defense failed: ${e.message}", e)  
                _uiState.value = _uiState.value.copy(  
                    isExecuting = false,  
                    executionResult = "启动自动换防失败: ${e.message}",  
                    showResultDialog = true  
                )  
            }  
        }  
    }  
  
    private fun stopAutoDefense(baseUrl: String) {  
        _uiState.value = _uiState.value.copy(isExecuting = true, errorMessage = null)  
  
        viewModelScope.launch {  
            try {  
                val result = withContext(Dispatchers.IO) {  
                    val request = Request.Builder()  
                        .url("$baseUrl/daily/api/pjjc_auto_def/stop")  
                        .addHeader("X-App-Version", APP_VERSION)  
                        .post("{}".toRequestBody(JSON_MEDIA_TYPE))  
                        .build()  
                    httpClient.newCall(request).execute().use { resp ->  
                        val text = resp.body?.string() ?: ""  
                        val json = try { JSONObject(text) } catch (e: Exception) { null }  
                        json?.optString("message") ?: text.ifBlank { if (resp.isSuccessful) "已终止" else "终止失败 (${resp.code})" }  
                    }  
                }  
  
                autoDefPollJob?.cancel()  
                _uiState.value = _uiState.value.copy(  
                    isExecuting = false,  
                    isAutoDefRunning = false,  
                    autoDefMessages = emptyList(),  
                    executionResult = result,  
                    showResultDialog = true  
                )  
            } catch (e: Exception) {  
                Log.e(TAG, "Stop auto defense failed: ${e.message}", e)  
                _uiState.value = _uiState.value.copy(  
                    isExecuting = false,  
                    executionResult = "终止自动换防失败: ${e.message}",  
                    showResultDialog = true  
                )  
            }  
        }  
    }  
  
    private fun pollAutoDefStatus(baseUrl: String) {  
        autoDefPollJob?.cancel()  
        autoDefPollJob = viewModelScope.launch {  
            while (_uiState.value.isAutoDefRunning) {  
                try {  
                    val status = withContext(Dispatchers.IO) {  
                        val request = Request.Builder()  
                            .url("$baseUrl/daily/api/pjjc_auto_def/status")  
                            .addHeader("X-App-Version", APP_VERSION)  
                            .get()  
                            .build()  
                        httpClient.newCall(request).execute().use { resp ->  
                            val text = resp.body?.string() ?: "{}"  
                            JSONObject(text)  
                        }  
                    }  
  
                    val running = status.optBoolean("running", false)  
                    val messagesArr = status.optJSONArray("messages")  
                    val messages = if (messagesArr != null) {  
                        (0 until messagesArr.length()).map { messagesArr.optString(it, "") }  
                    } else emptyList()  
  
                    _uiState.value = _uiState.value.copy(  
                        isAutoDefRunning = running,  
                        autoDefMessages = messages  
                    )  
  
                    if (!running) break  
  
                    kotlinx.coroutines.delay(5000)  
                } catch (e: Exception) {  
                    Log.e(TAG, "Poll auto def status failed: ${e.message}", e)  
                    kotlinx.coroutines.delay(10000)  
                }  
            }  
        }  
    }  
	
	/**  
	 * 通过 HTTP API 处理 #日常设置 命令：  
	 * 1. GET /daily/api/account/$acc/daily 获取模块信息（含候选值）  
	 * 2. 根据模块序号和选项序号找到目标配置  
	 * 3. 对 multi/single 类型做候选值匹配（支持 ':' 前缀匹配）  
	 * 4. PUT /daily/api/account/$acc/config 写入匹配后的完整值  
	 */  
	private fun handleDailySettingViaApi(  
		baseUrl: String,  
		acc: String,  
		moduleTarget: String,  
		optionTarget: String,  
		valueStr: String  
	) {  
		_uiState.value = _uiState.value.copy(isExecuting = true, errorMessage = null)  
	  
		viewModelScope.launch {  
			try {  
				val result = withContext(Dispatchers.IO) {  
					// Step 1: 获取模块信息  
					val infoRequest = Request.Builder()  
						.url("$baseUrl/daily/api/account/$acc/daily")  
						.addHeader("X-App-Version", APP_VERSION)  
						.get()  
						.build()  
					val infoJson = httpClient.newCall(infoRequest).execute().use { resp ->  
						if (!resp.isSuccessful) throw Exception("获取模块信息失败 (${resp.code})")  
						JSONObject(resp.body?.string() ?: "{}")  
					}  
	  
					val order = infoJson.getJSONArray("order")  
					val info = infoJson.getJSONObject("info")  
	  
					// Step 2: 匹配模块（按序号或名称）  
					// 构建已实现模块的有序列表（与后端 _get_daily_modules 一致）  
					// order 数组中的每个 key 对应 info 中的模块  
					data class ModuleEntry(val idx: Int, val key: String, val moduleInfo: JSONObject)  
					val modules = mutableListOf<ModuleEntry>()  
					var seqIdx = 1  
					for (i in 0 until order.length()) {  
						val mKey = order.getString(i)  
						if (info.has(mKey)) {  
							val mInfo = info.getJSONObject(mKey)  
							// 只包含已实现的模块（有 config 或 name 字段的）  
							modules.add(ModuleEntry(seqIdx, mKey, mInfo))  
							seqIdx++  
						}  
					}  
	  
					// 按序号或名称匹配模块  
					val targetModule = if (moduleTarget.all { it.isDigit() }) {  
						val idx = moduleTarget.toInt()  
						modules.find { it.idx == idx }  
							?: throw Exception("模块序号 $idx 不存在")  
					} else {  
						modules.find {  
							it.moduleInfo.optString("name") == moduleTarget ||  
							it.key == moduleTarget  
						} ?: throw Exception("未找到模块【$moduleTarget】")  
					}  
	  
					// Step 3: 匹配选项  
					val moduleConfig = targetModule.moduleInfo.optJSONObject("config")  
						?: throw Exception("模块【${targetModule.moduleInfo.optString("name")}】没有子选项")  
	  
					// 将 config 转为有序列表（使用 config_order 保证与后端一致）  
					data class ConfigEntry(val idx: Int, val key: String, val configObj: JSONObject)  
					val configEntries = mutableListOf<ConfigEntry>()  
					val configOrder = targetModule.moduleInfo.optJSONArray("config_order")  
					if (configOrder != null && configOrder.length() > 0) {  
						for (i in 0 until configOrder.length()) {  
							val cKey = configOrder.getString(i)  
							val cObj = moduleConfig.optJSONObject(cKey) ?: continue  
							configEntries.add(ConfigEntry(i + 1, cKey, cObj))  
						}  
					} else {  
						// fallback: 无 config_order 时用 keys()（不保证顺序）  
						val configKeys = moduleConfig.keys()  
						var optIdx = 1  
						while (configKeys.hasNext()) {  
							val cKey = configKeys.next()  
							val cObj = moduleConfig.getJSONObject(cKey)  
							configEntries.add(ConfigEntry(optIdx, cKey, cObj))  
							optIdx++  
						}  
					} 
	  
					val targetConfig = if (optionTarget.all { it.isDigit() }) {  
						val oidx = optionTarget.toInt()  
						configEntries.find { it.idx == oidx }  
							?: throw Exception("选项序号 $oidx 不存在")  
					} else {  
						configEntries.find {  
							it.configObj.optString("desc") == optionTarget ||  
							it.key == optionTarget  
						} ?: throw Exception("未找到选项【$optionTarget】")  
					}  
	  
					val ctype = targetConfig.configObj.optString("config_type", "text")  
					val candidatesArr = targetConfig.configObj.optJSONArray("candidates")  
	  
					// Step 4: 根据类型解析值并匹配候选值  
					val finalValue: Any = when (ctype) {  
						"bool" -> {  
							val trueVals = setOf("开启", "开", "true", "是", "1", "on")  
							val falseVals = setOf("关闭", "关", "false", "否", "0", "off")  
							when (valueStr.lowercase()) {  
								in trueVals -> true  
								in falseVals -> false  
								else -> throw Exception("请输入: 开启 或 关闭")  
							}  
						}  
						"int" -> {  
							valueStr.toIntOrNull() ?: throw Exception("请输入整数")  
						}  
						"text" -> valueStr  
						"time" -> {  
							val parts = valueStr.replace("：", ":").split(":")  
							if (parts.size != 2) throw Exception("时间格式错误，请输入 HH:MM")  
							val h = parts[0].toIntOrNull() ?: throw Exception("时间格式错误")  
							val m = parts[1].toIntOrNull() ?: throw Exception("时间格式错误")  
							if (h !in 0..23 || m !in 0..59) throw Exception("时间范围错误")  
							JSONArray().apply { put(parts[0]); put(parts[1]) }  
						}  
						"multi", "multi_search" -> {  
							// 解析候选值列表  
							val candidates = mutableListOf<Pair<Any, String>>() // (value, display)  
							if (candidatesArr != null) {  
								for (ci in 0 until candidatesArr.length()) {  
									val cand = candidatesArr.getJSONObject(ci)  
									val v = cand.get("value")  
									val d = cand.optString("display", v.toString())  
									candidates.add(Pair(v, d))  
								}  
							}  
	  
							val parts = valueStr.replace("，", ",").split(",").map { it.trim() }  
							val resolved = JSONArray()  
							for (p in parts) {  
								var matched: Any? = null  
								// 1. 按 display 精确匹配  
								for ((v, d) in candidates) {  
									if (d == p) { matched = v; break }  
								}  
								// 2. 按 value 精确匹配  
								if (matched == null) {  
									for ((v, _) in candidates) {  
										if (v.toString() == p) { matched = v; break }  
									}  
								}  
								// 3. 按 ':' 前缀匹配（如 "40013" 匹配 "40013: 卡池名"）  
								if (matched == null) {  
									for ((v, _) in candidates) {  
										val vs = v.toString()  
										if (":" in vs && vs.split(":")[0].trim() == p.trim()) {  
											matched = v; break  
										}  
									}  
								}  
								if (matched != null) {  
									resolved.put(matched)  
								} else {  
									val displays = candidates.take(10).joinToString(", ") { it.second }  
									throw Exception("值【$p】不在候选范围\n可选: $displays")  
								}  
							}  
							resolved  
						}  
						"single" -> {  
							val candidates = mutableListOf<Pair<Any, String>>()  
							if (candidatesArr != null) {  
								for (ci in 0 until candidatesArr.length()) {  
									val cand = candidatesArr.getJSONObject(ci)  
									val v = cand.get("value")  
									val d = cand.optString("display", v.toString())  
									candidates.add(Pair(v, d))  
								}  
							}  
							var matched: Any? = null  
							// 1. display 精确匹配  
							for ((v, d) in candidates) {  
								if (d == valueStr) { matched = v; break }  
							}  
							// 2. value 精确匹配  
							if (matched == null) {  
								for ((v, _) in candidates) {  
									if (v.toString() == valueStr) { matched = v; break }  
								}  
							}  
							// 3. ':' 前缀匹配  
							if (matched == null) {  
								for ((v, _) in candidates) {  
									val vs = v.toString()  
									if (":" in vs && vs.split(":")[0].trim() == valueStr.trim()) {  
										matched = v; break  
									}  
								}  
							}  
							// 4. 类型转换匹配  
							if (matched == null) {  
								val num = valueStr.toIntOrNull()  
								if (num != null) {  
									for ((v, _) in candidates) {  
										if (v is Int && v == num) { matched = v; break }  
										if (v is Number && v.toInt() == num) { matched = v; break }  
									}  
								}  
							}  
							matched ?: run {  
								val displays = candidates.take(10).joinToString(", ") { it.second }  
								throw Exception("值【$valueStr】不在候选范围\n可选: $displays")  
							}  
						}  
						else -> valueStr  
					}  
	  
					// Step 5: PUT /config 写入  
					val configJson = JSONObject().apply {  
						put(targetConfig.key, finalValue)  
					}  
					val putRequest = Request.Builder()  
						.url("$baseUrl/daily/api/account/$acc/config")  
						.addHeader("X-App-Version", APP_VERSION)  
						.put(configJson.toString().toRequestBody(JSON_MEDIA_TYPE))  
						.build()  
					httpClient.newCall(putRequest).execute().use { resp ->  
						if (!resp.isSuccessful) {  
							val body = resp.body?.string() ?: ""  
							throw Exception(body.ifBlank { "保存失败 (${resp.code})" })  
						}  
					}  
	  
					// 构造成功消息  
					val moduleName = targetModule.moduleInfo.optString("name", targetModule.key)  
					val configDesc = targetConfig.configObj.optString("desc", targetConfig.key)  
					val displayVal = if (finalValue is JSONArray) {  
						(0 until finalValue.length()).joinToString(", ") { finalValue.getString(it) }  
					} else {  
						finalValue.toString()  
					}  
					"【$moduleName】$configDesc: $displayVal\n设置成功"  
				}  
	  
				_uiState.value = _uiState.value.copy(  
					isExecuting = false,  
					executionResult = result,  
					showResultDialog = true  
				)  
			} catch (e: Exception) {  
				Log.e(TAG, "handleDailySettingViaApi failed: ${e.message}", e)  
				_uiState.value = _uiState.value.copy(  
					isExecuting = false,  
					executionResult = "设置失败: ${e.message}",  
					showResultDialog = true  
				)  
			}  
		}  
	}
  
    /**  
     * 解析 command relay 返回的 JSON。  
     * 格式：  
     *   成功: {"status":"ok","result":{"name":"...","log":"...","status":"成功"}}  
     *   提前返回: {"status":"finish","message":"..."}  
     *   错误: {"status":"error","message":"..."}  
     */  
    private fun parseRelayResponse(json: String): String {  
        return try {  
            val obj = JSONObject(json)  
            when (obj.optString("status", "error")) {  
                "ok" -> {  
                    val result = obj.optJSONObject("result")  
                    val name = result?.optString("name", "") ?: ""  
                    val log = result?.optString("log", "") ?: ""  
                    val resultStatus = result?.optString("status", "") ?: ""  
                    buildString {  
                        if (name.isNotBlank()) {  
                            append("【$name】")  
                            if (resultStatus.isNotBlank()) append(" $resultStatus")  
                            append("\n\n")  
                        }  
                        append(log.ifBlank { "(无日志输出)" })  
                    }  
                }  
                "finish" -> obj.optString("message", "指令已完成")  
                else -> "错误: " + obj.optString("message", "未知错误")  
            }  
        } catch (e: Exception) {  
            json.ifBlank { "未知错误" }  
        }  
    }  
  
    // ==================== 定时任务管理 ====================  
  
    /** 切换定时设置区域的展开/折叠，首次展开时自动加载 */  
    fun toggleCronSection() {  
        val state = _uiState.value  
        val newShow = !state.showCronSection  
        _uiState.value = state.copy(showCronSection = newShow)  
        if (newShow && state.cronConfigs.isEmpty() && !state.isLoadingCron) {  
            loadCronConfig()  
        }  
    }  
  
    /**  
     * 从后端加载定时任务配置。  
     *  
     * GET /daily/api/account/<acc>/cron 返回：  
     * {  
     *   "config": { "cron1": true, "time_cron1": "05:00", "clanbattle_run_cron1": false,  
     *               "module_exclude_type_cron1": [], ... },  
     *   "order": ["cron1","cron2","cron3","cron4","cron7","cron8","cron9","cron10"],  
     *   "info": { ... }  
     * }  
     */  
    fun loadCronConfig() {  
        val state = _uiState.value  
        val baseUrl = state.serverUrl ?: return  
        val acc = state.selectedAccount ?: return  
  
        _uiState.value = state.copy(isLoadingCron = true, cronError = null)  
  
        viewModelScope.launch {  
            try {  
                val configs = withContext(Dispatchers.IO) {  
                    val request = Request.Builder()  
                        .url("$baseUrl/daily/api/account/$acc/cron")  
                        .addHeader("X-App-Version", APP_VERSION)  
                        .get()  
                        .build()  
                    httpClient.newCall(request).execute().use { resp ->  
                        val text = resp.body?.string() ?: ""  
                        if (!resp.isSuccessful) throw Exception(text.ifBlank { "获取定时配置失败 (${resp.code})" })  
                        parseCronResponse(text)  
                    }  
                }  
                _uiState.value = _uiState.value.copy(  
                    isLoadingCron = false,  
                    cronConfigs = configs,  
                    cronError = null  
                )  
            } catch (e: Exception) {  
                Log.e(TAG, "Load cron config failed: ${e.message}", e)  
                _uiState.value = _uiState.value.copy(  
                    isLoadingCron = false,  
                    cronError = "加载定时配置失败: ${e.message}"  
                )  
            }  
        }  
    }  
  
    /** 解析 /cron 接口返回的 JSON，提取每个槽位的配置 */  
    private fun parseCronResponse(json: String): List<CronConfig> {  
        val root = JSONObject(json)  
        val config = root.optJSONObject("config") ?: JSONObject()  
        val orderArr = root.optJSONArray("order")  
  
        // 优先使用 order 字段确定槽位顺序，回退到硬编码列表  
        val indices: List<Int> = if (orderArr != null && orderArr.length() > 0) {  
            (0 until orderArr.length()).mapNotNull { i ->  
                val key = orderArr.optString(i, "")  
                // key 形如 "cron1", "cron10"  
                key.removePrefix("cron").toIntOrNull()  
            }  
        } else {  
            CRON_INDICES  
        }  
  
        return indices.map { idx ->  
            val suffix = "cron$idx"  
            CronConfig(  
                index = idx,  
                enabled = config.optBoolean(suffix, false),  
                time = normalizeTime(config.optString("time_$suffix", "00:00")),  
                clanbattleRun = config.optBoolean("clanbattle_run_$suffix", false),  
                moduleExcludeType = parseStringList(config.optJSONArray("module_exclude_type_$suffix"))  
            )  
        }  
    }  
  
    /** 将可能带秒的时间 "HH:mm:ss" 截断为 "HH:mm" */  
    private fun normalizeTime(raw: String): String {  
        val parts = raw.split(":")  
        return if (parts.size >= 2) "${parts[0]}:${parts[1]}" else raw  
    }  
  
    /** JSONArray -> List<String> */  
    private fun parseStringList(arr: JSONArray?): List<String> {  
        if (arr == null) return emptyList()  
        return (0 until arr.length()).mapNotNull { arr.optString(it, null) }  
    }  
  
    // ---------- 单项修改 ----------  
  
    /** 开关某个定时任务 */  
    fun toggleCron(index: Int, enabled: Boolean) {  
        saveCronField("cron$index", enabled)  
        updateLocalCron(index) { it.copy(enabled = enabled) }  
    }  
  
    /** 修改执行时间 */  
    fun updateCronTime(index: Int, time: String) {  
        saveCronField("time_cron$index", time)  
        updateLocalCron(index) { it.copy(time = time) }  
    }  
  
    /** 开关会战期间执行 */  
    fun toggleClanbattleRun(index: Int, run: Boolean) {  
        saveCronField("clanbattle_run_cron$index", run)  
        updateLocalCron(index) { it.copy(clanbattleRun = run) }  
    }  
  
    /** 修改不执行日常模块 */  
    fun updateModuleExcludeType(index: Int, types: List<String>) {  
        saveCronFieldList("module_exclude_type_cron$index", types)  
        updateLocalCron(index) { it.copy(moduleExcludeType = types) }  
    }  
  
    /** 先乐观更新本地状态 */  
    private fun updateLocalCron(index: Int, transform: (CronConfig) -> CronConfig) {  
        val state = _uiState.value  
        _uiState.value = state.copy(  
            cronConfigs = state.cronConfigs.map { if (it.index == index) transform(it) else it }  
        )  
    }  
  
    /** PUT 单个配置字段（布尔/字符串） */  
    private fun saveCronField(key: String, value: Any) {  
        val state = _uiState.value  
        val baseUrl = state.serverUrl ?: return  
        val acc = state.selectedAccount ?: return  
  
        _uiState.value = state.copy(isSavingCron = true)  
  
        viewModelScope.launch {  
            try {  
                withContext(Dispatchers.IO) {  
                    val json = JSONObject().apply {  
                        put(key, value)  
                    }  
                    val request = Request.Builder()  
                        .url("$baseUrl/daily/api/account/$acc/config")  
                        .addHeader("X-App-Version", APP_VERSION)  
                        .put(json.toString().toRequestBody(JSON_MEDIA_TYPE))  
                        .build()  
                    httpClient.newCall(request).execute().use { resp ->  
                        if (!resp.isSuccessful) {  
                            val text = resp.body?.string() ?: ""  
                            throw Exception(text.ifBlank { "保存失败 (${resp.code})" })  
                        }  
                    }  
                }  
                _uiState.value = _uiState.value.copy(isSavingCron = false, cronError = null)  
            } catch (e: Exception) {  
                Log.e(TAG, "Save cron field failed: ${e.message}", e)  
                _uiState.value = _uiState.value.copy(  
                    isSavingCron = false,  
                    cronError = "保存失败: ${e.message}"  
                )  
                // 保存失败时重新加载以恢复正确状态  
                loadCronConfig()  
            }  
        }  
    }  
  
    /** PUT 列表类型配置字段 */  
    private fun saveCronFieldList(key: String, values: List<String>) {  
        val state = _uiState.value  
        val baseUrl = state.serverUrl ?: return  
        val acc = state.selectedAccount ?: return  
  
        _uiState.value = state.copy(isSavingCron = true)  
  
        viewModelScope.launch {  
            try {  
                withContext(Dispatchers.IO) {  
                    val json = JSONObject().apply {  
                        put(key, JSONArray(values))  
                    }  
                    val request = Request.Builder()  
                        .url("$baseUrl/daily/api/account/$acc/config")  
                        .addHeader("X-App-Version", APP_VERSION)  
                        .put(json.toString().toRequestBody(JSON_MEDIA_TYPE))  
                        .build()  
                    httpClient.newCall(request).execute().use { resp ->  
                        if (!resp.isSuccessful) {  
                            val text = resp.body?.string() ?: ""  
                            throw Exception(text.ifBlank { "保存失败 (${resp.code})" })  
                        }  
                    }  
                }  
                _uiState.value = _uiState.value.copy(isSavingCron = false, cronError = null)  
            } catch (e: Exception) {  
                Log.e(TAG, "Save cron field list failed: ${e.message}", e)  
                _uiState.value = _uiState.value.copy(  
                    isSavingCron = false,  
                    cronError = "保存失败: ${e.message}"  
                )  
                // 保存失败时重新加载以恢复正确状态  
                loadCronConfig()  
            }  
        }  
    }  
	
	// ==================== 日常模块管理 ====================  
  
    fun toggleDailySection() {  
        val state = _uiState.value  
        val newShow = !state.showDailySection  
        _uiState.value = state.copy(showDailySection = newShow)  
        if (newShow && state.dailyModules.isEmpty() && !state.isLoadingDaily) {  
            loadDailyConfig()  
        }  
    }  
  
    fun loadDailyConfig() {  
        val state = _uiState.value  
        val baseUrl = state.serverUrl ?: return  
        val acc = state.selectedAccount ?: return  
  
        _uiState.value = state.copy(isLoadingDaily = true, dailyError = null)  
  
        viewModelScope.launch {  
            try {  
                val modules = withContext(Dispatchers.IO) {  
                    val request = Request.Builder()  
                        .url("$baseUrl/daily/api/account/$acc/daily")  
                        .addHeader("X-App-Version", APP_VERSION)  
                        .get()  
                        .build()  
                    httpClient.newCall(request).execute().use { resp ->  
                        val text = resp.body?.string() ?: ""  
                        if (!resp.isSuccessful) throw Exception(text.ifBlank { "获取日常配置失败 (${resp.code})" })  
                        parseDailyResponse(text)  
                    }  
                }  
                _uiState.value = _uiState.value.copy(  
                    isLoadingDaily = false,  
                    dailyModules = modules,  
                    dailyError = null  
                )  
            } catch (e: Exception) {  
                Log.e(TAG, "Load daily config failed: ${e.message}", e)  
                _uiState.value = _uiState.value.copy(  
                    isLoadingDaily = false,  
                    dailyError = "加载日常配置失败: ${e.message}"  
                )  
            }  
        }  
    }  
  
    private fun parseDailyResponse(json: String): List<DailyModuleItem> {  
        val root = JSONObject(json)  
        val config = root.optJSONObject("config") ?: JSONObject()  
        val orderArr = root.optJSONArray("order") ?: return emptyList()  
        val info = root.optJSONObject("info") ?: return emptyList()  
  
        val result = mutableListOf<DailyModuleItem>()  
        for (i in 0 until orderArr.length()) {  
            val mKey = orderArr.getString(i)  
            val mInfo = info.optJSONObject(mKey) ?: continue  
  
            val configObj = mInfo.optJSONObject("config") ?: JSONObject()  
            val configOrderArr = mInfo.optJSONArray("config_order")  
            val configKeys = if (configOrderArr != null) {  
                (0 until configOrderArr.length()).map { configOrderArr.getString(it) }  
            } else {  
                configObj.keys().asSequence().toList()  
            }  
  
            val configs = configKeys.mapNotNull { cKey ->  
                val cObj = configObj.optJSONObject(cKey) ?: return@mapNotNull null  
                val candidatesArr = cObj.optJSONArray("candidates")  
                val candidates = if (candidatesArr != null) {  
                    (0 until candidatesArr.length()).map { ci ->  
                        val cand = candidatesArr.getJSONObject(ci)  
                        DailyCandidateEntry(  
                            value = cand.opt("value"),  
                            display = cand.optString("display", ""),  
                            tags = parseStringList(cand.optJSONArray("tags"))  
                        )  
                    }  
                } else emptyList()  
  
                DailyConfigEntry(  
                    key = cObj.optString("key", cKey),  
                    desc = cObj.optString("desc", cKey),  
                    configType = cObj.optString("config_type", "bool"),  
                    default = cObj.opt("default"),  
                    currentValue = config.opt(cKey),  
                    candidates = candidates  
                )  
            }  
  
            val tags = parseStringList(mInfo.optJSONArray("tags"))  
  
            result.add(  
                DailyModuleItem(  
                    key = mKey,  
                    name = mInfo.optString("name", mKey),  
                    description = mInfo.optString("description", ""),  
                    enabled = config.optBoolean(mKey, false),  
                    implemented = mInfo.optBoolean("implemented", true),  
                    staminaRelative = mInfo.optBoolean("stamina_relative", false),  
                    tags = tags,  
                    runnable = mInfo.optBoolean("runnable", true),  
                    configOrder = configKeys,  
                    configs = configs  
                )  
            )  
        }  
        return result  
    }  
  
    fun toggleDailyModule(moduleKey: String, enabled: Boolean) {  
        saveDailyField(moduleKey, enabled)  
        _uiState.value = _uiState.value.copy(  
            dailyModules = _uiState.value.dailyModules.map {  
                if (it.key == moduleKey) it.copy(enabled = enabled) else it  
            }  
        )  
    }  
  
    fun updateDailyConfig(configKey: String, value: Any) {  
        saveDailyField(configKey, value)  
        // 乐观更新本地 currentValue  
        _uiState.value = _uiState.value.copy(  
            dailyModules = _uiState.value.dailyModules.map { module ->  
                module.copy(configs = module.configs.map { cfg ->  
                    if (cfg.key == configKey) cfg.copy(currentValue = value) else cfg  
                })  
            }  
        )  
    }  
  
    fun updateDailyConfigList(configKey: String, values: List<Any?>) {  
        val state = _uiState.value  
        val baseUrl = state.serverUrl ?: return  
        val acc = state.selectedAccount ?: return  
  
        _uiState.value = state.copy(isSavingDaily = true)  
  
        // 乐观更新  
        _uiState.value = _uiState.value.copy(  
            dailyModules = _uiState.value.dailyModules.map { module ->  
                module.copy(configs = module.configs.map { cfg ->  
                    if (cfg.key == configKey) cfg.copy(currentValue = values) else cfg  
                })  
            }  
        )  
  
        viewModelScope.launch {  
            try {  
                withContext(Dispatchers.IO) {  
                    val json = JSONObject().apply {  
                        put(configKey, JSONArray(values))  
                    }  
                    val request = Request.Builder()  
                        .url("$baseUrl/daily/api/account/$acc/config")  
                        .addHeader("X-App-Version", APP_VERSION)  
                        .put(json.toString().toRequestBody(JSON_MEDIA_TYPE))  
                        .build()  
                    httpClient.newCall(request).execute().use { resp ->  
                        if (!resp.isSuccessful) {  
                            val text = resp.body?.string() ?: ""  
                            throw Exception(text.ifBlank { "保存失败 (${resp.code})" })  
                        }  
                    }  
                }  
                _uiState.value = _uiState.value.copy(isSavingDaily = false, dailyError = null)  
            } catch (e: Exception) {  
                Log.e(TAG, "Save daily field list failed: ${e.message}", e)  
                _uiState.value = _uiState.value.copy(  
                    isSavingDaily = false,  
                    dailyError = "保存失败: ${e.message}"  
                )  
                loadDailyConfig()  
            }  
        }  
    }  
  
    fun expandDailyModule(moduleKey: String?) {  
        val current = _uiState.value.expandedModuleKey  
        _uiState.value = _uiState.value.copy(  
            expandedModuleKey = if (current == moduleKey) null else moduleKey  
        )  
    }  
  
    private fun saveDailyField(key: String, value: Any) {  
        val state = _uiState.value  
        val baseUrl = state.serverUrl ?: return  
        val acc = state.selectedAccount ?: return  
  
        _uiState.value = state.copy(isSavingDaily = true)  
  
        viewModelScope.launch {  
            try {  
                withContext(Dispatchers.IO) {  
                    val json = JSONObject().apply {  
                        put(key, value)  
                    }  
                    val request = Request.Builder()  
                        .url("$baseUrl/daily/api/account/$acc/config")  
                        .addHeader("X-App-Version", APP_VERSION)  
                        .put(json.toString().toRequestBody(JSON_MEDIA_TYPE))  
                        .build()  
                    httpClient.newCall(request).execute().use { resp ->  
                        if (!resp.isSuccessful) {  
                            val text = resp.body?.string() ?: ""  
                            throw Exception(text.ifBlank { "保存失败 (${resp.code})" })  
                        }  
                    }  
                }  
                _uiState.value = _uiState.value.copy(isSavingDaily = false, dailyError = null)  
            } catch (e: Exception) {  
                Log.e(TAG, "Save daily field failed: ${e.message}", e)  
                _uiState.value = _uiState.value.copy(  
                    isSavingDaily = false,  
                    dailyError = "保存失败: ${e.message}"  
                )  
                loadDailyConfig()  
            }  
        }  
    }  
  
    fun clearDailyError() {  
        _uiState.value = _uiState.value.copy(dailyError = null)  
    }
	
	// ==================== 通用模块管理（工具/角色/规划/公会/危险） ====================  
  
    /** 获取指定 section 的当前模块列表 */  
    private fun getModulesForSection(sectionKey: String): List<DailyModuleItem> {  
        val s = _uiState.value  
        return when (sectionKey) {  
            "tool" -> s.toolModules  
            "unit" -> s.unitModules  
            "planning" -> s.planningModules  
            "clan" -> s.clanModules  
            "danger" -> s.dangerModules  
            else -> emptyList()  
        }  
    }  
  
    /** 获取指定 section 是否正在加载 */  
    private fun isSectionLoading(sectionKey: String): Boolean {  
        val s = _uiState.value  
        return when (sectionKey) {  
            "tool" -> s.isLoadingTool  
            "unit" -> s.isLoadingUnit  
            "planning" -> s.isLoadingPlanning  
            "clan" -> s.isLoadingClan  
            "danger" -> s.isLoadingDanger  
            else -> false  
        }  
    }  
  
    /** 获取指定 section 是否展开 */  
    private fun isSectionShown(sectionKey: String): Boolean {  
        val s = _uiState.value  
        return when (sectionKey) {  
            "tool" -> s.showToolSection  
            "unit" -> s.showUnitSection  
            "planning" -> s.showPlanningSection  
            "clan" -> s.showClanSection  
            "danger" -> s.showDangerSection  
            else -> false  
        }  
    }  
  
    /** 更新指定 section 的 UI 状态 */  
    private fun updateSectionState(  
        sectionKey: String,  
        show: Boolean? = null,  
        modules: List<DailyModuleItem>? = null,  
        isLoading: Boolean? = null,  
        isSaving: Boolean? = null,  
        error: String? = null,  
        clearError: Boolean = false,  
        expandedKey: String? = null,  
        clearExpandedKey: Boolean = false  
    ) {  
        val s = _uiState.value  
        _uiState.value = when (sectionKey) {  
            "tool" -> s.copy(  
                showToolSection = show ?: s.showToolSection,  
                toolModules = modules ?: s.toolModules,  
                isLoadingTool = isLoading ?: s.isLoadingTool,  
                isSavingTool = isSaving ?: s.isSavingTool,  
                toolError = if (clearError) null else (error ?: s.toolError),  
                expandedToolModuleKey = if (clearExpandedKey) null else (expandedKey ?: s.expandedToolModuleKey)  
            )  
            "unit" -> s.copy(  
                showUnitSection = show ?: s.showUnitSection,  
                unitModules = modules ?: s.unitModules,  
                isLoadingUnit = isLoading ?: s.isLoadingUnit,  
                isSavingUnit = isSaving ?: s.isSavingUnit,  
                unitError = if (clearError) null else (error ?: s.unitError),  
                expandedUnitModuleKey = if (clearExpandedKey) null else (expandedKey ?: s.expandedUnitModuleKey)  
            )  
            "planning" -> s.copy(  
                showPlanningSection = show ?: s.showPlanningSection,  
                planningModules = modules ?: s.planningModules,  
                isLoadingPlanning = isLoading ?: s.isLoadingPlanning,  
                isSavingPlanning = isSaving ?: s.isSavingPlanning,  
                planningError = if (clearError) null else (error ?: s.planningError),  
                expandedPlanningModuleKey = if (clearExpandedKey) null else (expandedKey ?: s.expandedPlanningModuleKey)  
            )  
            "clan" -> s.copy(  
                showClanSection = show ?: s.showClanSection,  
                clanModules = modules ?: s.clanModules,  
                isLoadingClan = isLoading ?: s.isLoadingClan,  
                isSavingClan = isSaving ?: s.isSavingClan,  
                clanError = if (clearError) null else (error ?: s.clanError),  
                expandedClanModuleKey = if (clearExpandedKey) null else (expandedKey ?: s.expandedClanModuleKey)  
            )  
            "danger" -> s.copy(  
                showDangerSection = show ?: s.showDangerSection,  
                dangerModules = modules ?: s.dangerModules,  
                isLoadingDanger = isLoading ?: s.isLoadingDanger,  
                isSavingDanger = isSaving ?: s.isSavingDanger,  
                dangerError = if (clearError) null else (error ?: s.dangerError),  
                expandedDangerModuleKey = if (clearExpandedKey) null else (expandedKey ?: s.expandedDangerModuleKey)  
            )  
            else -> s  
        }  
    }  
  
    /** 展开/折叠模块区域，首次展开时自动加载 */  
    fun toggleSection(sectionKey: String) {  
        val newShow = !isSectionShown(sectionKey)  
        updateSectionState(sectionKey, show = newShow)  
        if (newShow && getModulesForSection(sectionKey).isEmpty() && !isSectionLoading(sectionKey)) {  
            loadSectionConfig(sectionKey)  
        }  
    }  
  
    /** 加载指定模块区域的配置 */  
    fun loadSectionConfig(sectionKey: String) {  
        val state = _uiState.value  
        val baseUrl = state.serverUrl ?: return  
        val acc = state.selectedAccount ?: return  
  
        updateSectionState(sectionKey, isLoading = true, clearError = true)  
  
        viewModelScope.launch {  
            try {  
                val modules = withContext(Dispatchers.IO) {  
                    val request = Request.Builder()  
                        .url("$baseUrl/daily/api/account/$acc/$sectionKey")  
                        .addHeader("X-App-Version", APP_VERSION)  
                        .get()  
                        .build()  
                    httpClient.newCall(request).execute().use { resp ->  
                        val text = resp.body?.string() ?: ""  
                        if (!resp.isSuccessful) throw Exception(text.ifBlank { "获取配置失败 (${resp.code})" })  
                        parseDailyResponse(text)  
                    }  
                }  
                updateSectionState(sectionKey, isLoading = false, modules = modules, clearError = true)  
            } catch (e: Exception) {  
                Log.e(TAG, "Load $sectionKey config failed: ${e.message}", e)  
                updateSectionState(sectionKey, isLoading = false, error = "加载失败: ${e.message}")  
            }  
        }  
    }  
  
    /** 开关模块 */  
    fun toggleSectionModule(sectionKey: String, moduleKey: String, enabled: Boolean) {  
        saveDailyField(moduleKey, enabled)  
        val updated = getModulesForSection(sectionKey).map {  
            if (it.key == moduleKey) it.copy(enabled = enabled) else it  
        }  
        updateSectionState(sectionKey, modules = updated)  
    }  
  
    /** 更新模块配置（单值） */  
    fun updateSectionConfig(sectionKey: String, configKey: String, value: Any) {  
        saveDailyField(configKey, value)  
        val updated = getModulesForSection(sectionKey).map { module ->  
            module.copy(configs = module.configs.map { cfg ->  
                if (cfg.key == configKey) cfg.copy(currentValue = value) else cfg  
            })  
        }  
        updateSectionState(sectionKey, modules = updated)  
    }  
  
    /** 更新模块配置（列表值） */  
    fun updateSectionConfigList(sectionKey: String, configKey: String, values: List<Any?>) {  
        val state = _uiState.value  
        val baseUrl = state.serverUrl ?: return  
        val acc = state.selectedAccount ?: return  
  
        updateSectionState(sectionKey, isSaving = true)  
  
        // 乐观更新  
        val updated = getModulesForSection(sectionKey).map { module ->  
            module.copy(configs = module.configs.map { cfg ->  
                if (cfg.key == configKey) cfg.copy(currentValue = values) else cfg  
            })  
        }  
        updateSectionState(sectionKey, modules = updated)  
  
        viewModelScope.launch {  
            try {  
                withContext(Dispatchers.IO) {  
                    val json = JSONObject().apply {  
                        put(configKey, JSONArray(values))  
                    }  
                    val request = Request.Builder()  
                        .url("$baseUrl/daily/api/account/$acc/config")  
                        .addHeader("X-App-Version", APP_VERSION)  
                        .put(json.toString().toRequestBody(JSON_MEDIA_TYPE))  
                        .build()  
                    httpClient.newCall(request).execute().use { resp ->  
                        if (!resp.isSuccessful) {  
                            val text = resp.body?.string() ?: ""  
                            throw Exception(text.ifBlank { "保存失败 (${resp.code})" })  
                        }  
                    }  
                }  
                updateSectionState(sectionKey, isSaving = false, clearError = true)  
            } catch (e: Exception) {  
                Log.e(TAG, "Save $sectionKey config list failed: ${e.message}", e)  
                updateSectionState(sectionKey, isSaving = false, error = "保存失败: ${e.message}")  
                loadSectionConfig(sectionKey)  
            }  
        }  
    }  
  
    /** 展开/收起模块详情 */  
    fun expandSectionModule(sectionKey: String, moduleKey: String?) {  
        val currentKey = when (sectionKey) {  
            "tool" -> _uiState.value.expandedToolModuleKey  
            "unit" -> _uiState.value.expandedUnitModuleKey  
            "planning" -> _uiState.value.expandedPlanningModuleKey  
            "clan" -> _uiState.value.expandedClanModuleKey  
            "danger" -> _uiState.value.expandedDangerModuleKey  
            else -> null  
        }  
        val newKey = if (currentKey == moduleKey) null else moduleKey  
        updateSectionState(sectionKey, expandedKey = newKey, clearExpandedKey = (newKey == null))  
    }  
  
    /** 清除模块区域错误 */  
    fun clearSectionError(sectionKey: String) {  
        updateSectionState(sectionKey, clearError = true)  
    }  
  
    // ==================== 执行单个模块 ====================  
  
    fun executeModule(moduleKey: String) {  
        val state = _uiState.value  
        val baseUrl = state.serverUrl ?: return  
        val acc = state.selectedAccount ?: return  
  
        _uiState.value = state.copy(executingModuleKey = moduleKey)  
  
        viewModelScope.launch {  
            try {  
                val (imageBytes, fallbackText) = withContext(Dispatchers.IO) {  
                    // Step 1: 执行模块  
                    val doJson = JSONObject().apply {  
                        put("order", moduleKey)  
                    }  
                    val doRequest = Request.Builder()  
                        .url("$baseUrl/daily/api/account/$acc/do_single")  
                        .addHeader("X-App-Version", APP_VERSION)  
                        .post(doJson.toString().toRequestBody(JSON_MEDIA_TYPE))  
                        .build()  
                    val resultList = httpClient.newCall(doRequest).execute().use { resp ->  
                        val text = resp.body?.string() ?: ""  
                        if (!resp.isSuccessful) throw Exception(text.ifBlank { "执行失败 (${resp.code})" })  
                        JSONArray(text)  
                    }  
  
                    if (resultList.length() == 0) {  
                        return@withContext Pair<ByteArray?, String>(null, "执行完成，无返回结果")  
                    }  
  
                    // Step 2: 获取完整结果  
                    val firstResult = resultList.getJSONObject(0)  
                    val resultUrl = firstResult.optString("url", "")  
  
                    if (resultUrl.isBlank()) {  
                        val status = firstResult.optString("status", "unknown")  
                        val alias = firstResult.optString("alias", moduleKey)  
                        return@withContext Pair<ByteArray?, String>(null, "$alias: $status")  
                    }  
  
                    // 优先获取渲染好的结果图片  
                    try {  
                        val imgRequest = Request.Builder()  
                            .url("$baseUrl$resultUrl")  
                            .addHeader("X-App-Version", APP_VERSION)  
                            .get()  
                            .build()  
                        val imgBytes = httpClient.newCall(imgRequest).execute().use { resp ->  
                            if (!resp.isSuccessful) throw Exception("获取结果图片失败 (${resp.code})")  
                            resp.body?.bytes()  
                        }  
                        if (imgBytes != null && imgBytes.isNotEmpty()) {  
                            return@withContext Pair<ByteArray?, String>(imgBytes, "")  
                        }  
                    } catch (imgErr: Exception) {  
                        Log.w(TAG, "Fetch result image failed, fallback to text: ${imgErr.message}")  
                    }  
  
                    // 图片获取失败，回退到文本模式  
                    try {  
                        val textRequest = Request.Builder()  
                            .url("$baseUrl$resultUrl?text=true")  
                            .addHeader("X-App-Version", APP_VERSION)  
                            .get()  
                            .build()  
                        val textJson = httpClient.newCall(textRequest).execute().use { resp ->  
                            resp.body?.string() ?: ""  
                        }  
                        val resultObj = JSONObject(textJson)  
                        val name = resultObj.optString("name", moduleKey)  
                        val log = resultObj.optString("log", "")  
                        val resultStatus = resultObj.optString("status", "")  
                        val displayText = buildString {  
                            if (name.isNotBlank()) {  
                                append("【$name】")  
                                if (resultStatus.isNotBlank()) append(" $resultStatus")  
                                append("\n\n")  
                            }  
                            append(log.ifBlank { "(无日志输出)" })  
                        }  
                        return@withContext Pair<ByteArray?, String>(null, displayText)  
                    } catch (textErr: Exception) {  
                        Log.w(TAG, "Fetch result text also failed: ${textErr.message}")  
                        val status = firstResult.optString("status", "unknown")  
                        return@withContext Pair<ByteArray?, String>(null, "执行完成: $status")  
                    }  
                }  
  
                // 显示结果  
                val imageBase64 = if (imageBytes != null) {  
                    android.util.Base64.encodeToString(imageBytes, android.util.Base64.DEFAULT)  
                } else null  
  
                _uiState.value = _uiState.value.copy(  
                    executingModuleKey = null,  
                    executionResult = fallbackText,  
                    resultImageBase64 = imageBase64,  
                    showResultDialog = true  
                )  
            } catch (e: Exception) {  
                Log.e(TAG, "Execute module $moduleKey failed: ${e.message}", e)  
                _uiState.value = _uiState.value.copy(  
                    executingModuleKey = null,  
                    executionResult = "执行失败: ${e.message}",  
                    resultImageBase64 = null,  
                    showResultDialog = true  
                )  
            }  
        }  
    }
  
    // ==================== 修改登录密码 ====================  
  
    fun showChangePasswordDialog() {  
        _uiState.value = _uiState.value.copy(showChangePasswordDialog = true)  
    }  
  
    fun dismissChangePasswordDialog() {  
        _uiState.value = _uiState.value.copy(  
            showChangePasswordDialog = false,  
            newPasswordInput = "",  
            isChangingPassword = false  
        )  
    }  
  
    fun onNewPasswordInputChanged(value: String) {  
        _uiState.value = _uiState.value.copy(newPasswordInput = value)  
    }  
  
    fun changePassword() {  
        val state = _uiState.value  
        val baseUrl = state.serverUrl ?: return  
        val newPassword = state.newPasswordInput.trim()  
        if (newPassword.isBlank()) {  
            _uiState.value = state.copy(errorMessage = "新密码不能为空")  
            return  
        }  
  
        _uiState.value = state.copy(isChangingPassword = true, errorMessage = null)  
  
        viewModelScope.launch {  
            try {  
                withContext(Dispatchers.IO) {  
                    val json = JSONObject().apply {  
                        put("password", newPassword)  
                    }  
                    val request = Request.Builder()  
                        .url("$baseUrl/daily/api/account")  
                        .addHeader("X-App-Version", APP_VERSION)  
                        .put(json.toString().toRequestBody(JSON_MEDIA_TYPE))  
                        .build()  
                    httpClient.newCall(request).execute().use { resp ->  
                        val text = resp.body?.string() ?: ""  
                        if (!resp.isSuccessful) throw Exception(text.ifBlank { "修改失败 (${resp.code})" })  
                    }  
                }  
                // 同步更新本地保存的密码  
                val qq = state.qqInput.trim()  
                settingsDataStore.saveDailyAccount(qq, newPassword)  
  
                _uiState.value = _uiState.value.copy(  
                    passwordInput = newPassword,  
                    showChangePasswordDialog = false,  
                    newPasswordInput = "",  
                    isChangingPassword = false,  
                    errorMessage = "密码修改成功"  
                )  
            } catch (e: Exception) {  
                Log.e(TAG, "Change password failed: ${e.message}", e)  
                _uiState.value = _uiState.value.copy(  
                    isChangingPassword = false,  
                    errorMessage = "修改密码失败: ${e.message}"  
                )  
            }  
        }  
    }
	
	// ==================== 关闭结果弹窗 ====================  
  
    fun dismissResult() {  
		_uiState.value = _uiState.value.copy(  
			showResultDialog = false,  
			executionResult = null,  
			resultImageBase64 = null    // ← 新增  
		)  
	}  
  
    // ==================== 清除定时错误 ====================  
  
    fun clearCronError() {  
        _uiState.value = _uiState.value.copy(cronError = null)  
    }  
  
    // ==================== 返回 ====================  
  
    fun goBack() {  
        val state = _uiState.value  
        when (state.phase) {  
            DailyPhase.COMMANDS -> {    
                _uiState.value = state.copy(    
                    phase = DailyPhase.ACCOUNTS,    
                    selectedAccount = null,    
                    cronConfigs = emptyList(),    
                    showCronSection = false,    
                    cronError = null,  
                    dailyModules = emptyList(),  
                    showDailySection = false,  
                    dailyError = null,  
                    expandedModuleKey = null,
                    // 返回时重置工具模块状态  
                    toolModules = emptyList(),  
                    showToolSection = false,  
                    toolError = null,  
                    expandedToolModuleKey = null,  
                    executingModuleKey = null,  
                    // 返回时重置角色模块状态  
                    unitModules = emptyList(),  
                    showUnitSection = false,  
                    unitError = null,  
                    expandedUnitModuleKey = null,  
                    // 返回时重置规划模块状态  
                    planningModules = emptyList(),  
                    showPlanningSection = false,  
                    planningError = null,  
                    expandedPlanningModuleKey = null,  
                    // 返回时重置公会模块状态  
                    clanModules = emptyList(),  
                    showClanSection = false,  
                    clanError = null,  
                    expandedClanModuleKey = null,  
                    // 返回时重置危险模块状态  
                    dangerModules = emptyList(),  
                    showDangerSection = false,  
                    dangerError = null,  
                    expandedDangerModuleKey = null					
                )    
            }  
            DailyPhase.ACCOUNTS -> {  
                autoDefPollJob?.cancel()  // ← 新增  
				cookieStore.clear()  
                _uiState.value = DailyUiState(serverUrl = state.serverUrl)  
            }  
            DailyPhase.LOGIN -> { /* 由 Screen 层处理导航返回 */ }  
        }  
    }  
  
    // ==================== 已保存账号管理 ====================  
  
	private fun loadSavedAccounts() {  
		viewModelScope.launch {  
			try {  
				val accounts = settingsDataStore.getDailySavedAccounts()  
				_uiState.value = _uiState.value.copy(  
					savedAccounts = accounts.map { SavedDailyAccount(it.first, it.second) }  
				)  
			} catch (e: Exception) {  
				Log.e(TAG, "Load saved accounts failed: ${e.message}", e)  
			}  
		}  
	}  
	  
	private fun saveCurrentAccount() {  
		val state = _uiState.value  
		val qq = state.qqInput.trim()  
		val password = state.passwordInput.trim()  
		if (qq.isBlank() || password.isBlank()) return  
		viewModelScope.launch {  
			try {  
				settingsDataStore.saveDailyAccount(qq, password)  
				loadSavedAccounts()  
			} catch (e: Exception) {  
				Log.e(TAG, "Save account failed: ${e.message}", e)  
			}  
		}  
	}  
	  
	fun selectSavedAccount(account: SavedDailyAccount) {  
		_uiState.value = _uiState.value.copy(  
			qqInput = account.qq,  
			passwordInput = account.password  
		)  
	}  
	  
	fun deleteSavedAccount(account: SavedDailyAccount) {  
		viewModelScope.launch {  
			try {  
				settingsDataStore.deleteDailyAccount(account.qq)  
				loadSavedAccounts()  
			} catch (e: Exception) {  
				Log.e(TAG, "Delete account failed: ${e.message}", e)  
			}  
		}  
	}
	
	// ==================== 清除错误 ====================  
  
    fun clearError() {  
        _uiState.value = _uiState.value.copy(errorMessage = null)  
    }  
}