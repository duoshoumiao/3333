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
  
// ==================== 指令类型 ====================  
  
enum class CommandType {  
    SIMPLE,          // 无参数，可直接执行  
    NEEDS_PARAMS,    // 需要参数  
    DO_DAILY,        // 清日常（走 do_daily 接口）  
    DAILY_RECORD,    // 日常记录（走 account 接口读 daily_clean_time）  
    DAILY_REPORT,    // 日常报告（走 daily_result 接口）  
    UNSUPPORTED      // 暂不支持  
}  
  
data class CommandItem(  
    val command: String,  
    val description: String,  
    val moduleKey: String? = null,  
    val type: CommandType = CommandType.UNSUPPORTED  
)  
  
val DAILY_COMMANDS: List<CommandItem> = listOf(  
    // ===== 特殊指令 =====  
    CommandItem("#清日常 [昵称]", "无昵称则默认账号", type = CommandType.DO_DAILY),  
    CommandItem("#清日常所有", "清该qq号下所有号的日常", type = CommandType.UNSUPPORTED),  
    CommandItem("#日常记录", "查看清日常状态", type = CommandType.DAILY_RECORD),  
    CommandItem("#日常报告 [0|1|2|3]", "最近四次清日常报告", type = CommandType.DAILY_REPORT),  
    CommandItem("#定时日志", "查看定时运行状态", type = CommandType.UNSUPPORTED),  
  
    // ===== 简单指令（无参数，可直接执行） =====  
    CommandItem("#查缺角色", "查看缺少的限定常驻角色", moduleKey = "missing_unit", type = CommandType.SIMPLE),  
    CommandItem("#查心碎", "查询缺口心碎", moduleKey = "get_need_xinsui", type = CommandType.SIMPLE),  
    CommandItem("#查纯净碎片", "查询缺口纯净碎片，国服六星+日服二专需求", moduleKey = "get_need_pure_memory", type = CommandType.SIMPLE),  
    CommandItem("#查探险编队", "根据记忆碎片角色编队战力相当的队伍", moduleKey = "travel_team_view", type = CommandType.SIMPLE),  
    CommandItem("#公会支援", "查询公会支援角色配置", moduleKey = "get_clan_support_unit", type = CommandType.SIMPLE),  
    CommandItem("#查缺称号", "查看缺少的称号", moduleKey = "missing_emblem", type = CommandType.SIMPLE),  
    CommandItem("#返钻", "", moduleKey = "return_jewel", type = CommandType.SIMPLE),  
    CommandItem("#刷新box", "", moduleKey = "refresh_box", type = CommandType.SIMPLE),  
    CommandItem("#jjc透视", "查前51名", moduleKey = "jjc_info", type = CommandType.SIMPLE),  
    CommandItem("#pjjc透视", "查前51名", moduleKey = "pjjc_info", type = CommandType.SIMPLE),  
    CommandItem("#pjjc换防", "将pjjc防守阵容随机错排", moduleKey = "pjjc_def_shuffle_team", type = CommandType.SIMPLE),  
    CommandItem("#智能刷h图", "", moduleKey = "smart_hard_sweep", type = CommandType.SIMPLE),  
    CommandItem("#智能刷外传", "", moduleKey = "smart_shiori_sweep", type = CommandType.SIMPLE),  
    CommandItem("#刷专二", "", moduleKey = "mirai_very_hard_sweep", type = CommandType.SIMPLE),  
    CommandItem("#查深域", "", moduleKey = "find_talent_quest", type = CommandType.SIMPLE),  
    CommandItem("#强化ex装", "", moduleKey = "ex_equip_enhance_up", type = CommandType.SIMPLE),  
    CommandItem("#合成ex装", "", moduleKey = "ex_equip_rank_up", type = CommandType.SIMPLE),  
    CommandItem("#领小屋体力", "", moduleKey = "room_accept_all", type = CommandType.SIMPLE),  
    CommandItem("#公会点赞", "", moduleKey = "clan_like", type = CommandType.SIMPLE),  
    CommandItem("#领每日体力", "", moduleKey = "mission_receive_first", type = CommandType.SIMPLE),  
    CommandItem("#领取礼物箱", "", moduleKey = "present_receive", type = CommandType.SIMPLE),  
    CommandItem("#查公会深域进度", "", moduleKey = "find_clan_talent_quest", type = CommandType.SIMPLE),  
    CommandItem("#收菜", "探险续航哦", moduleKey = "travel_quest_sweep", type = CommandType.SIMPLE),  
    CommandItem("#撤下会战ex装", "", moduleKey = "remove_cb_ex_equip", type = CommandType.SIMPLE),  
    CommandItem("#撤下普通ex装", "", moduleKey = "remove_normal_ex_equip", type = CommandType.SIMPLE),  
  
    // ===== 需要参数的指令 =====  
    CommandItem("#查角色 [昵称]", "查看角色练度", moduleKey = "search_unit", type = CommandType.NEEDS_PARAMS),  
    CommandItem("#查ex装备 [会战]", "查看ex装备库存", moduleKey = "ex_equip_info", type = CommandType.NEEDS_PARAMS),  
    CommandItem("#查兑换角色碎片 [开换]", "查询兑换特别角色的记忆碎片策略", moduleKey = "redeem_unit_swap", type = CommandType.NEEDS_PARAMS),  
    CommandItem("#查记忆碎片 [可刷取|大师币]", "查询缺口记忆碎片", moduleKey = "get_need_memory", type = CommandType.NEEDS_PARAMS),  
    CommandItem("#查装备 [<rank>] [fav]", "查询缺口装备", moduleKey = "get_need_equip", type = CommandType.NEEDS_PARAMS),  
    CommandItem("#刷图推荐 [<rank>] [fav]", "查询缺口装备的刷图推荐", moduleKey = "get_normal_quest_recommand", type = CommandType.NEEDS_PARAMS),  
    CommandItem("#查box 角色名（or所有）", "", moduleKey = "get_box_table", type = CommandType.NEEDS_PARAMS),  
    CommandItem("#jjc回刺", "比如 #jjc回刺 19 2 就是打19 选择阵容2进攻", moduleKey = "jjc_back", type = CommandType.NEEDS_PARAMS),  
    CommandItem("#pjjc回刺", "比如 #pjjc回刺 -1（或者不填） 就是打记录里第一条", moduleKey = "pjjc_back", type = CommandType.NEEDS_PARAMS),  
    CommandItem("#免费十连 <卡池id>", "卡池id来自【#卡池】", moduleKey = "free_gacha", type = CommandType.NEEDS_PARAMS),  
    CommandItem("#来发十连 <卡池id> [抽到出] [单抽券|单抽] [编号小优先] [开抽]", "赛博抽卡，谨慎使用", moduleKey = "gacha_start", type = CommandType.NEEDS_PARAMS),  
    CommandItem("#穿ex彩装 角色名 彩装ID", "示例：#穿ex彩装 凯露 12345", moduleKey = "equip_rainbow_ex", type = CommandType.NEEDS_PARAMS),  
    CommandItem("#穿ex粉装 角色名 粉装serial_id", "#查ID 看ID", moduleKey = "equip_pink_ex", type = CommandType.NEEDS_PARAMS),  
    CommandItem("#穿ex金装 角色名 金装serial_id", "#查ID 看ID", moduleKey = "equip_gold_ex", type = CommandType.NEEDS_PARAMS),  
    CommandItem("#查ID 泪", "模糊匹配，会匹配所有名称含「泪」的装备", moduleKey = "search_ex_equip_id", type = CommandType.NEEDS_PARAMS),  
    CommandItem("#一键编队 1 1 队名1 星级角色1 ...", "设置多队编队，队伍不足5人结尾", moduleKey = "set_my_party2", type = CommandType.NEEDS_PARAMS),  
    CommandItem("#导入编队 第几页 第几队", "如 #导入编队 1 1 ，代表第一页第一队", moduleKey = "set_my_party", type = CommandType.NEEDS_PARAMS),  
    CommandItem("#兑天井 卡池id 角色名", "如 #兑天井 10283 火电  用 #卡池 获取ID", moduleKey = "gacha_exchange_chara", type = CommandType.NEEDS_PARAMS),  
    CommandItem("#拉角色练度 339 31 ...", "等级 品级 ub s1 s2 ex 装备星级 专武1 专武2 角色名", moduleKey = "unit_promote", type = CommandType.NEEDS_PARAMS),  
    CommandItem("#大富翁 [保留骰子数] [搬空商店为止|不止搬空商店] [到达次数]", "运行大富翁游戏", moduleKey = "caravan_play", type = CommandType.NEEDS_PARAMS),  
    CommandItem("#商店购买 [上期|当期]", "购买大富翁商店物品，默认购买当期", moduleKey = "caravan_shop_buy", type = CommandType.NEEDS_PARAMS),  
    CommandItem("#查玩家 uid", "", moduleKey = "query_player_profile", type = CommandType.NEEDS_PARAMS),  
    CommandItem("#炼金 物贯 物贯 物贯 物贯 1 彩装ID +(看属性/看概率/炼成)", "炼成之前去网站设置参数", moduleKey = "ex_equip_rainbow_enchance", type = CommandType.NEEDS_PARAMS),  
    CommandItem("#买记忆碎片 角色 星级 专武 开买 界限突破", "分别代表:角色 星级 专武 是否购买 是否突破", moduleKey = "unit_memory_buy", type = CommandType.NEEDS_PARAMS),  
    CommandItem("#角色升星 5 忽略盈余 升至最高 角色名", "分别代表 星级 是否保留盈余 升到可升最高星 角色名", moduleKey = "unit_evolution", type = CommandType.NEEDS_PARAMS),  
    CommandItem("#角色突破 忽略盈余 角色名", "忽略盈余：选这个，碎片不溢出就不突破", moduleKey = "unit_exceed", type = CommandType.NEEDS_PARAMS), 
    CommandItem("#挂地下城支援 [星级]角色", "设置角色为支援，星级可选(3/4/5)", moduleKey = "set_dungeon_support", type = CommandType.NEEDS_PARAMS),  
    CommandItem("#挂会战支援 [星级]角色", "设置角色为支援", moduleKey = "set_cb_support", type = CommandType.NEEDS_PARAMS),  
    CommandItem("#挂好友支援 [星级]角色", "如：#挂好友支援 3水电", moduleKey = "set_friend_support", type = CommandType.NEEDS_PARAMS),  
    CommandItem("#一键穿ex +角色名 试穿/数字 1 2 3", "数字0表示不改动", moduleKey = "one_click_ex_equip", type = CommandType.NEEDS_PARAMS),  
    CommandItem("#添加好友", "", moduleKey = "add_friend", type = CommandType.NEEDS_PARAMS),  
  
    // ===== 暂不支持的指令 =====  
    CommandItem("#卡池", "查看当前卡池", moduleKey = null, type = CommandType.UNSUPPORTED),  
    CommandItem("#半月刊", "", moduleKey = null, type = CommandType.UNSUPPORTED),  
    CommandItem("#识图", "用于提取图中队伍", moduleKey = null, type = CommandType.UNSUPPORTED),  
    CommandItem("#pjjc自动换防", "不挨打时6分钟换一次，挨打缩短换防时间", moduleKey = null, type = CommandType.UNSUPPORTED),  
    CommandItem("#日常面板 [昵称]", "查看日常功能开关及配置（图片版）", moduleKey = null, type = CommandType.UNSUPPORTED),  
    CommandItem("#日常详情 [昵称] 模块名", "查看模块详细配置和可选值", moduleKey = null, type = CommandType.UNSUPPORTED),  
    CommandItem("#日常开启 [昵称] 模块名/序号", "开启指定日常功能", moduleKey = null, type = CommandType.UNSUPPORTED),  
    CommandItem("#日常关闭 [昵称] 模块名/序号", "关闭指定日常功能", moduleKey = null, type = CommandType.UNSUPPORTED),  
    CommandItem("#日常设置 [昵称] 模块序号 选项序号 值", "设置模块子选项", moduleKey = null, type = CommandType.UNSUPPORTED),  
    CommandItem("#保存ex状态", "保存当前所有角色的普通EX装备穿戴状态", moduleKey = null, type = CommandType.UNSUPPORTED),  
    CommandItem("#恢复ex状态", "恢复之前保存的普通EX装备穿戴状态", moduleKey = null, type = CommandType.UNSUPPORTED),  
)  
  
// ==================== UI 状态 ====================  
  
enum class DailyPhase { LOGIN, ACCOUNTS, COMMANDS }  
  
data class DailyUiState(  
    val phase: DailyPhase = DailyPhase.LOGIN,  
    val serverUrl: String? = null,  
    val qqInput: String = "",  
    val passwordInput: String = "",  
    val isLoading: Boolean = false,  
    val errorMessage: String? = null,  
    val accounts: List<String> = emptyList(),  
    val selectedAccount: String? = null,  
    val isExecuting: Boolean = false,  
    val executionResult: String? = null,  
    val showResultDialog: Boolean = false  
)  
  
// ==================== ViewModel ====================  
  
private const val TAG = "DailyViewModel"  
private const val APP_VERSION = "1.7.0"  
private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()  
  
@HiltViewModel  
class DailyViewModel @Inject constructor(  
    private val settingsDataStore: SettingsDataStore  
) : ViewModel() {  
  
    private val _uiState = MutableStateFlow(DailyUiState())  
    val uiState: StateFlow<DailyUiState> = _uiState  
  
    private val cookieStore = mutableListOf<Cookie>()  
  
    private val httpClient = OkHttpClient.Builder()  
        .connectTimeout(30, TimeUnit.SECONDS)  
        .readTimeout(120, TimeUnit.SECONDS)  
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
        }  
    }  
  
    fun onQqChanged(value: String) {  
        _uiState.value = _uiState.value.copy(qqInput = value, errorMessage = null)  
    }  
  
    fun onPasswordChanged(value: String) {  
        _uiState.value = _uiState.value.copy(passwordInput = value, errorMessage = null)  
    }  
  
    fun login() {  
        val state = _uiState.value  
        if (state.qqInput.isBlank()) {  
            _uiState.value = state.copy(errorMessage = "请输入QQ号"); return  
        }  
        if (state.passwordInput.isBlank()) {  
            _uiState.value = state.copy(errorMessage = "请输入密码"); return  
        }  
        val baseUrl = state.serverUrl  
        if (baseUrl.isNullOrBlank()) {  
            _uiState.value = state.copy(errorMessage = "请先在设置中配置清日常服务器地址"); return  
        }  
        viewModelScope.launch {  
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)  
            try {  
                val body = JSONObject().apply {  
                    put("qq", state.qqInput.trim())  
                    put("password", state.passwordInput.trim())  
                }.toString()  
                withContext(Dispatchers.IO) {  
                    val request = Request.Builder()  
                        .url("$baseUrl/daily/api/login/qq")  
                        .addHeader("X-App-Version", APP_VERSION)  
                        .post(body.toRequestBody(JSON_MEDIA_TYPE))  
                        .build()  
                    httpClient.newCall(request).execute().use { resp ->  
                        val text = resp.body?.string() ?: ""  
                        if (!resp.isSuccessful) throw Exception(text.ifBlank { "登录失败 (${resp.code})" })  
                    }  
                }  
                loadAccounts()  
            } catch (e: Exception) {  
                Log.e(TAG, "Login failed: ${e.message}", e)  
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "登录失败: ${e.message}")  
            }  
        }  
    }  
  
    private suspend fun loadAccounts() {  
        val baseUrl = _uiState.value.serverUrl ?: return  
        try {  
            val accounts = withContext(Dispatchers.IO) {  
                val request = Request.Builder()  
                    .url("$baseUrl/daily/api/account")  
                    .addHeader("X-App-Version", APP_VERSION)  
                    .get().build()  
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
                isLoading = false, phase = DailyPhase.ACCOUNTS,  
                accounts = accounts, errorMessage = null  
            )  
        } catch (e: Exception) {  
            Log.e(TAG, "Load accounts failed: ${e.message}", e)  
            _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "获取账号列表失败: ${e.message}")  
        }  
    }  
  
    fun selectAccount(alias: String) {  
        _uiState.value = _uiState.value.copy(phase = DailyPhase.COMMANDS, selectedAccount = alias)  
    }  
  
    // ==================== 执行指令 ====================  
  
    fun executeCommand(cmd: CommandItem) {  
        when (cmd.type) {  
            CommandType.SIMPLE -> executeSimpleCommand(cmd)  
            CommandType.DO_DAILY -> executeDailyClean()  
            CommandType.NEEDS_PARAMS -> {  
                _uiState.value = _uiState.value.copy(  
                    executionResult = "该指令需要参数，暂不支持在App中直接执行。\n\n用法：${cmd.command}\n${cmd.description}",  
                    showResultDialog = true  
                )  
            }  
            CommandType.UNSUPPORTED -> {  
                _uiState.value = _uiState.value.copy(  
                    executionResult = "该指令暂不支持在App中执行。",  
                    showResultDialog = true  
                )  
            }  
        }  
    }  
  
    private fun executeDailyClean() {  
        val baseUrl = _uiState.value.serverUrl ?: return  
        val acc = _uiState.value.selectedAccount ?: return  
        viewModelScope.launch {  
            _uiState.value = _uiState.value.copy(isExecuting = true)  
            try {  
                val resultText = withContext(Dispatchers.IO) {  
                    val request = Request.Builder()  
                        .url("$baseUrl/daily/api/account/$acc/do_daily")  
                        .addHeader("X-App-Version", APP_VERSION)  
                        .post("{}".toRequestBody(JSON_MEDIA_TYPE))  
                        .build()  
                    httpClient.newCall(request).execute().use { resp ->  
                        val text = resp.body?.string() ?: ""  
                        if (!resp.isSuccessful) throw Exception(text.ifBlank { "清日常失败 (${resp.code})" })  
                        text  
                    }  
                }  
                _uiState.value = _uiState.value.copy(  
                    isExecuting = false,  
                    executionResult = "清日常完成\n\n$resultText",  
                    showResultDialog = true  
                )  
            } catch (e: Exception) {  
                Log.e(TAG, "Do daily failed: ${e.message}", e)  
                _uiState.value = _uiState.value.copy(  
                    isExecuting = false,  
                    executionResult = "执行失败: ${e.message}",  
                    showResultDialog = true  
                )  
            }  
        }  
    }  
  
    private fun executeSimpleCommand(cmd: CommandItem) {  
        val baseUrl = _uiState.value.serverUrl ?: return  
        val acc = _uiState.value.selectedAccount ?: return  
        val moduleKey = cmd.moduleKey ?: return  
        viewModelScope.launch {  
            _uiState.value = _uiState.value.copy(isExecuting = true)  
            try {  
                val resultList = withContext(Dispatchers.IO) {  
                    val body = JSONObject().apply { put("order", moduleKey) }.toString()  
                    val request = Request.Builder()  
                        .url("$baseUrl/daily/api/account/$acc/do_single")  
                        .addHeader("X-App-Version", APP_VERSION)  
                        .post(body.toRequestBody(JSON_MEDIA_TYPE))  
                        .build()  
                    httpClient.newCall(request).execute().use { resp ->  
                        val text = resp.body?.string() ?: ""  
                        if (!resp.isSuccessful) throw Exception(text.ifBlank { "执行失败 (${resp.code})" })  
                        text  
                    }  
                }  
                val resultUrl = parseFirstResultUrl(resultList)  
                if (resultUrl != null) {  
                    val textResult = withContext(Dispatchers.IO) {  
                        val separator = if (resultUrl.contains("?")) "&" else "?"  
                        val request = Request.Builder()  
                            .url("$baseUrl$resultUrl${separator}text=true")  
                            .addHeader("X-App-Version", APP_VERSION)  
                            .get().build()  
                        httpClient.newCall(request).execute().use { resp ->  
                            val text = resp.body?.string() ?: ""  
                            if (!resp.isSuccessful) "获取结果失败 (${resp.code}): $text" else text  
                        }  
                    }  
                    _uiState.value = _uiState.value.copy(  
                        isExecuting = false,  
                        executionResult = parseResultText(textResult),  
                        showResultDialog = true  
                    )  
                } else {  
                    _uiState.value = _uiState.value.copy(  
                        isExecuting = false,  
                        executionResult = "指令已执行\n\n$resultList",  
                        showResultDialog = true  
                    )  
                }  
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
  
    private fun parseFirstResultUrl(json: String): String? {  
        return try {  
            val arr = JSONArray(json)  
            if (arr.length() > 0) {  
                val first = arr.getJSONObject(0)  
                first.optString("url", "").ifBlank { null }  
            } else null  
        } catch (e: Exception) {  
            Log.w(TAG, "Parse result URL failed: ${e.message}")  
            null  
        }  
    }  
  
    private fun parseResultText(json: String): String {  
        return try {  
            val obj = JSONObject(json)  
            val name = obj.optString("name", "")  
            val status = obj.optString("status", "")  
            val log = obj.optString("log", "")  
            buildString {  
                if (name.isNotBlank()) {  
                    append("【$name】")  
                    if (status.isNotBlank()) append(" $status")  
                    append("\n\n")  
                }  
                if (log.isNotBlank()) append(log) else append("(无日志输出)")  
            }  
        } catch (e: Exception) {  
            json  
        }  
    }  
  
    fun dismissResult() {  
        _uiState.value = _uiState.value.copy(showResultDialog = false, executionResult = null)  
    }  
  
    fun goBack() {  
        val state = _uiState.value  
        when (state.phase) {  
            DailyPhase.COMMANDS -> _uiState.value = state.copy(phase = DailyPhase.ACCOUNTS, selectedAccount = null)  
            DailyPhase.ACCOUNTS -> {  
                cookieStore.clear()  
                _uiState.value = DailyUiState(serverUrl = state.serverUrl)  
            }  
            DailyPhase.LOGIN -> {}  
        }  
    }  
  
    fun clearError() {  
        _uiState.value = _uiState.value.copy(errorMessage = null)  
    }  
}