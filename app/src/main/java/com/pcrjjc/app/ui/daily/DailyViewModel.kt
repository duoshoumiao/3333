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
    CommandItem("#卡池", "查看当前卡池"),  
    CommandItem("#刷新box", ""),  
    CommandItem("#查缺称号", "查看缺少的称号"),  
    CommandItem("#jjc透视", "查前51名"),  
    CommandItem("#pjjc透视", "查前51名"),  
    CommandItem("#jjc回刺 排名 阵容编号", "比如 #jjc回刺 19 2 就是打19 选择阵容2进攻"),  
    CommandItem("#pjjc回刺 记录编号", "比如 #pjjc回刺 -1 就是打记录里第一条"),  
    CommandItem("#pjjc换防", "将pjjc防守阵容随机错排"),  
    CommandItem("#免费十连 卡池id", "卡池id来自【#卡池】"),  
    CommandItem("#来发十连 卡池id [抽到出] [单抽券|单抽] [编号小优先] [开抽]", "赛博抽卡，谨慎使用"),  
    CommandItem("#智能刷h图", ""),  
    CommandItem("#智能刷外传", ""),  
    CommandItem("#刷专二", ""),  
    CommandItem("#查深域", ""),  
    CommandItem("#强化ex装", ""),  
    CommandItem("#合成ex装", ""),  
    CommandItem("#穿ex彩装 角色名 彩装ID", "示例：#穿ex彩装 凯露 12345  #查ex装备 看ID"),  
    CommandItem("#穿ex粉装 角色名 粉装serial_id", "#查ID 看ID"),  
    CommandItem("#穿ex金装 角色名 金装serial_id", "#查ID 看ID"),  
    CommandItem("#查ID 关键字", "模糊匹配，会匹配所有名称含关键字的装备"),  
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
    CommandItem("#挂地下城支援 [星级]角色", "星级可选(3/4/5)，如：#挂好友支援 3水电"),  
    CommandItem("#挂会战支援 [星级]角色", ""),  
    CommandItem("#挂好友支援 [星级]角色", ""),  
    CommandItem("#一键穿ex +角色名 试穿/数字 1 2 3", "数字0表示不改动"),  
    CommandItem("#添加好友", ""),  
    CommandItem("#日常面板", "查看日常功能开关及配置（图片版）"),  
    CommandItem("#日常详情 模块名", "查看模块详细配置和可选值"),  
    CommandItem("#日常开启 模块名/序号", "开启指定日常功能"),  
    CommandItem("#日常关闭 模块名/序号", "关闭指定日常功能"),  
    CommandItem("#日常设置 模块序号 选项序号 值", "设置模块子选项"),  
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
  
// ==================== UI 状态 ====================  
  
enum class DailyPhase { LOGIN, ACCOUNTS, COMMANDS }  
  
data class DailyUiState(  
    val phase: DailyPhase = DailyPhase.LOGIN,  
    val isLoading: Boolean = false,  
    val serverUrl: String? = null,  
    val qqInput: String = "",  
    val passwordInput: String = "",  
    val accounts: List<String> = emptyList(),  
    val selectedAccount: String? = null,  
    val errorMessage: String? = null,  
    val isExecuting: Boolean = false,  
    val executionResult: String? = null,  
    val showResultDialog: Boolean = false,  
    // ---- 定时任务 ----  
    val cronConfigs: List<CronConfig> = emptyList(),  
    val isLoadingCron: Boolean = false,  
    val isSavingCron: Boolean = false,  
    val cronError: String? = null,  
    val showCronSection: Boolean = false,
    val savedAccounts: List<SavedDailyAccount> = emptyList()  
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
  
    // ==================== 选择账号 ====================  
  
    fun selectAccount(alias: String) {  
        _uiState.value = _uiState.value.copy(  
            phase = DailyPhase.COMMANDS,  
            selectedAccount = alias,  
            // 切换账号时重置定时状态  
            cronConfigs = emptyList(),  
            showCronSection = false,  
            cronError = null  
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
		
		// ===== 新增：拦截 #清日常所有 =====  
		if (commandText.trim() == "#清日常所有") {  
			executeDailyAll(baseUrl)  
			return  
		}  
		  
			_uiState.value = _uiState.value.copy(isExecuting = true, errorMessage = null)  
		  
			viewModelScope.launch {  
				try {  
					val results = withContext(Dispatchers.IO) {  
						// 对每个账号并发调用 do_daily  
						val deferreds = accounts.map { accName ->  
							kotlinx.coroutines.async {  
								try {  
									val request = Request.Builder()  
										.url("$baseUrl/daily/api/account/$accName/do_daily")  
										.addHeader("X-App-Version", APP_VERSION)  
										.post("{}".toRequestBody(JSON_MEDIA_TYPE))  
										.build()  
									httpClient.newCall(request).execute().use { resp ->  
										val text = resp.body?.string() ?: ""  
										if (!resp.isSuccessful) {  
											accName to "失败: ${text.ifBlank { "HTTP ${resp.code}" }}"  
										} else {  
											// 解析返回的 JSON，提取 name 字段  
											try {  
												val json = JSONObject(text)  
												val name = json.optString("name", accName)  
												accName to "成功"  
											} catch (e: Exception) {  
												accName to "成功"  
											}  
										}  
									}  
								} catch (e: Exception) {  
									accName to "失败: ${e.message}"  
								}  
							}  
						}  
						deferreds.map { it.await() }  
					}  
		  
					// 汇总结果  
					val resultText = buildString {  
						appendLine("清日常所有 执行结果：")  
						appendLine("=" .repeat(30))  
						for ((accName, status) in results) {  
							appendLine("【$accName】$status")  
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
  
                val displayText = parseRelayResponse(resultJson)  
                _uiState.value = _uiState.value.copy(  
                    isExecuting = false,  
                    executionResult = displayText,  
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
  
    // ==================== 关闭结果弹窗 ====================  
  
    fun dismissResult() {  
        _uiState.value = _uiState.value.copy(showResultDialog = false, executionResult = null)  
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
                    cronError = null  
                )  
            }  
            DailyPhase.ACCOUNTS -> {  
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