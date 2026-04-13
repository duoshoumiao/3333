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
import org.json.JSONObject  
import java.util.concurrent.TimeUnit  
import javax.inject.Inject  
  
// ==================== 指令数据 ====================  
  
data class CommandItem(  
    val command: String,  
    val description: String  
)  
  
val DAILY_COMMANDS: List<CommandItem> = listOf(  
    CommandItem("#清日常 [昵称]", "无昵称则默认账号"),  
    CommandItem("#清日常所有", "清该qq号下所有号的日常"),  
    CommandItem("#日常记录", "查看清日常状态"),  
    CommandItem("#日常报告 [0|1|2|3]", "最近四次清日常报告"),  
    CommandItem("#定时日志", "查看定时运行状态"),  
    CommandItem("#查角色 [昵称]", "查看角色练度"),  
    CommandItem("#查缺角色", "查看缺少的限定常驻角色"),  
    CommandItem("#查ex装备 [会战]", "查看ex装备库存"),  
    CommandItem("#查探险编队", "根据记忆碎片角色编队战力相当的队伍"),  
    CommandItem("#查兑换角色碎片 [开换]", "查询兑换特别角色的记忆碎片策略"),  
    CommandItem("#查心碎", "查询缺口心碎"),  
    CommandItem("#查纯净碎片", "查询缺口纯净碎片，国服六星+日服二专需求"),  
    CommandItem("#查记忆碎片 [可刷取|大师币]", "查询缺口记忆碎片，可按地图可刷取或大师币商店过滤"),  
    CommandItem("#查装备 [<rank>] [fav]", "查询缺口装备，rank为数字，只查询>=rank的角色缺口装备，fav表示只查询favorite的角色"),  
    CommandItem("#刷图推荐 [<rank>] [fav]", "查询缺口装备的刷图推荐，格式同上"),  
    CommandItem("#公会支援", "查询公会支援角色配置"),  
    CommandItem("#卡池", "查看当前卡池"),  
    CommandItem("#半月刊", ""),  
    CommandItem("#返钻", ""),  
    CommandItem("#查box 角色名（or所有）", ""),  
    CommandItem("#刷新box", ""),  
    CommandItem("#查缺称号", "查看缺少的称号"),  
    CommandItem("#jjc透视", "查前51名"),  
    CommandItem("#pjjc透视", "查前51名"),  
    CommandItem("#jjc回刺", "比如 #jjc回刺 19 2 就是打19 选择阵容2进攻"),  
    CommandItem("#pjjc回刺", "比如 #pjjc回刺 -1（或者不填） 就是打记录里第一条"),  
    CommandItem("#pjjc换防", "将pjjc防守阵容随机错排"),  
    CommandItem("#免费十连 <卡池id>", "卡池id来自【#卡池】"),  
    CommandItem("#来发十连 <卡池id> [抽到出] [单抽券|单抽] [编号小优先] [开抽]", "赛博抽卡，谨慎使用"),  
    CommandItem("#智能刷h图", ""),  
    CommandItem("#智能刷外传", ""),  
    CommandItem("#刷专二", ""),  
    CommandItem("#查深域", ""),  
    CommandItem("#强化ex装", ""),  
    CommandItem("#合成ex装", ""),  
    CommandItem("#穿ex彩装 角色名 彩装ID", "示例：#穿ex彩装 凯露 12345  #查ex装备 看ID"),  
    CommandItem("#穿ex粉装 角色名 粉装serial_id", "#查ID 看ID"),  
    CommandItem("#穿ex金装 角色名 金装serial_id", "#查ID 看ID"),  
    CommandItem("#查ID 泪", "模糊匹配，会匹配所有名称含「泪」的装备"),  
    CommandItem("#领小屋体力", ""),  
    CommandItem("#公会点赞", ""),  
    CommandItem("#领每日体力", ""),  
    CommandItem("#领取礼物箱", ""),  
    CommandItem("#查公会深域进度", ""),  
    CommandItem("#收菜", "探险续航哦"),  
    CommandItem("#一键编队 1 1 队名1 星级角色1 ...", "设置多队编队，队伍不足5人结尾"),  
    CommandItem("#导入编队 第几页 第几队", "如 #导入编队 1 1 ，代表第一页第一队"),  
    CommandItem("#识图", "用于提取图中队伍"),  
    CommandItem("#兑天井 卡池id 角色名", "如 #兑天井 10283 火电  用 #卡池 获取ID"),  
    CommandItem("#拉角色练度 339 31 339 339 339 339 5 5 5 5 5 5 0 0 角色名", "等级 品级 ub s1 s2 ex 装备星级 专武1 专武2 角色名（不输入则全选）"),  
    CommandItem("#大富翁 [保留骰子数] [搬空商店为止|不止搬空商店] [到达次数]", "运行大富翁游戏"),  
    CommandItem("#商店购买 [上期|当期]", "购买大富翁商店物品，默认购买当期"),  
    CommandItem("#查玩家 uid", ""),  
    CommandItem("#炼金 物贯 物贯 物贯 物贯 1 彩装ID +(看属性/看概率/炼成)", "炼成之前去网站设置参数"),  
    CommandItem("#撤下会战ex装", ""),  
    CommandItem("#撤下普通ex装", ""),  
    CommandItem("#买记忆碎片 角色 星级 专武 开买 界限突破", "分别代表:角色 星级 专武 是否购买 是否突破"),  
    CommandItem("#角色升星 5 忽略盈余 升至最高 角色名", "分别代表 星级 是否保留盈余 升到可升最高星 角色名"),  
    CommandItem("#角色突破 忽略盈余 角色名", "忽略盈余：选这个，碎片不溢出就不突破"),  
    CommandItem("#pjjc自动换防", "不挨打时6分钟换一次，挨打缩短换防时间"),  
    CommandItem("#挂地下城/会战/好友支援 [星级]角色", "设置角色为支援，星级可选(3/4/5)，如：#挂好友支援 3水电"),  
    CommandItem("#一键穿ex +角色名 试穿/数字 1 2 3", "数字0表示不改动"),  
    CommandItem("#添加好友", ""),  
    CommandItem("#日常面板 [昵称]", "查看日常功能开关及配置（图片版）"),  
    CommandItem("#日常详情 [昵称] 模块名", "查看模块详细配置和可选值"),  
    CommandItem("#日常开启 [昵称] 模块名/序号", "开启指定日常功能"),  
    CommandItem("#日常关闭 [昵称] 模块名/序号", "关闭指定日常功能"),  
    CommandItem("#日常设置 [昵称] 模块序号 选项序号 值", "设置模块子选项"),  
    CommandItem("#保存ex状态", "保存当前所有角色的普通EX装备穿戴状态"),  
    CommandItem("#恢复ex状态", "恢复之前保存的普通EX装备穿戴状态"),  
)  
  
// ==================== UI 状态 ====================  
  
enum class DailyPhase { LOGIN, ACCOUNTS, COMMANDS }  
  
data class DailyUiState(  
    val phase: DailyPhase = DailyPhase.LOGIN,  
    val qqInput: String = "",  
    val passwordInput: String = "",  
    val isLoading: Boolean = false,  
    val errorMessage: String? = null,  
    val accounts: List<String> = emptyList(),  
    val selectedAccount: String? = null,  
    val serverUrl: String? = null  
)  
  
// ==================== ViewModel ====================  
  
@HiltViewModel  
class DailyViewModel @Inject constructor(  
    private val settingsDataStore: SettingsDataStore  
) : ViewModel() {  
  
    companion object {  
        private const val TAG = "DailyViewModel"  
        private const val APP_VERSION = "1.7.0"  
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()  
    }  
  
    private val _uiState = MutableStateFlow(DailyUiState())  
    val uiState: StateFlow<DailyUiState> = _uiState  
  
    // 带 Cookie 管理的 OkHttpClient（独立于全局单例）  
    private val cookieStore = mutableMapOf<String, List<Cookie>>()  
    private val httpClient: OkHttpClient = OkHttpClient.Builder()  
        .connectTimeout(20, TimeUnit.SECONDS)  
        .readTimeout(20, TimeUnit.SECONDS)  
        .writeTimeout(20, TimeUnit.SECONDS)  
        .cookieJar(object : CookieJar {  
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {  
                cookieStore[url.host] = cookies  
            }  
            override fun loadForRequest(url: HttpUrl): List<Cookie> {  
                return cookieStore[url.host] ?: emptyList()  
            }  
        })  
        .build()  
  
    init {  
        viewModelScope.launch {  
            val url = settingsDataStore.getServerUrl()  
            _uiState.value = _uiState.value.copy(serverUrl = url)  
        }  
    }  
  
    // ==================== 输入 ====================  
  
    fun onQqChanged(value: String) {  
        _uiState.value = _uiState.value.copy(qqInput = value, errorMessage = null)  
    }  
  
    fun onPasswordChanged(value: String) {  
        _uiState.value = _uiState.value.copy(passwordInput = value, errorMessage = null)  
    }  
  
    // ==================== 登录 ====================  
  
    fun login() {  
        val state = _uiState.value  
        if (state.qqInput.isBlank()) {  
            _uiState.value = state.copy(errorMessage = "请输入QQ号")  
            return  
        }  
        if (state.passwordInput.isBlank()) {  
            _uiState.value = state.copy(errorMessage = "请输入密码")  
            return  
        }  
        val baseUrl = state.serverUrl  
        if (baseUrl.isNullOrBlank()) {  
            _uiState.value = state.copy(errorMessage = "请先在设置中配置服务器地址")  
            return  
        }  
  
        viewModelScope.launch {  
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)  
            try {  
                val body = JSONObject().apply {  
                    put("qq", state.qqInput.trim())  
                    put("password", state.passwordInput.trim())  
                }.toString()  
  
                val responseText = withContext(Dispatchers.IO) {  
                    val request = Request.Builder()  
                        .url("$baseUrl/daily/api/login/qq")  
                        .addHeader("X-App-Version", APP_VERSION)  
                        .post(body.toRequestBody(JSON_MEDIA_TYPE))  
                        .build()  
                    httpClient.newCall(request).execute().use { resp ->  
                        val text = resp.body?.string() ?: ""  
                        if (!resp.isSuccessful) throw Exception(text.ifBlank { "登录失败 (${resp.code})" })  
                        text  
                    }  
                }  
                Log.d(TAG, "Login success: $responseText")  
                // 登录成功，加载账号列表  
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
                        list.add(obj.optString("alias", "账号${i + 1}"))  
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
  
    // ==================== 选择账号 → 显示指令 ====================  
  
    fun selectAccount(alias: String) {  
        _uiState.value = _uiState.value.copy(  
            phase = DailyPhase.COMMANDS,  
            selectedAccount = alias  
        )  
    }  
  
    // ==================== 返回 ====================  
  
    fun goBack() {  
        val state = _uiState.value  
        when (state.phase) {  
            DailyPhase.COMMANDS -> {  
                _uiState.value = state.copy(  
                    phase = DailyPhase.ACCOUNTS,  
                    selectedAccount = null  
                )  
            }  
            DailyPhase.ACCOUNTS -> {  
                cookieStore.clear()  
                _uiState.value = DailyUiState(serverUrl = state.serverUrl)  
            }  
            DailyPhase.LOGIN -> { /* 由 Screen 层处理导航返回 */ }  
        }  
    }  
  
    // ==================== 清除错误 ====================  
  
    fun clearError() {  
        _uiState.value = _uiState.value.copy(errorMessage = null)  
    }  
}