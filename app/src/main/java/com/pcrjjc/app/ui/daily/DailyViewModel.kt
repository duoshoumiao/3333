// ...接上文 DAILY_COMMANDS 列表...  
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