package com.pcrjjc.app.ui.autodef  
  
import android.util.Log  
import androidx.lifecycle.ViewModel  
import androidx.lifecycle.viewModelScope  
import com.pcrjjc.app.data.local.dao.AccountDao  
import com.pcrjjc.app.data.local.entity.Account  
import com.pcrjjc.app.domain.ClientManager  
import com.pcrjjc.app.domain.QueryEngine  
import com.pcrjjc.app.util.Platform  
import dagger.hilt.android.lifecycle.HiltViewModel  
import kotlinx.coroutines.Dispatchers  
import kotlinx.coroutines.Job  
import kotlinx.coroutines.delay  
import kotlinx.coroutines.flow.MutableStateFlow  
import kotlinx.coroutines.flow.StateFlow  
import kotlinx.coroutines.isActive  
import kotlinx.coroutines.launch  
import kotlinx.coroutines.withContext  
import java.text.SimpleDateFormat  
import java.util.Date  
import java.util.Locale  
import javax.inject.Inject  
  
data class AutoDefUiState(  
    val selectedPlatform: Platform = Platform.B_SERVER,  
    val availableAccounts: List<Account> = emptyList(),  
    val selectedAccountId: Int? = null,  
    val isRunning: Boolean = false,  
    val logs: List<String> = emptyList(),  
    val errorMessage: String? = null  
)  
  
@HiltViewModel  
class AutoDefViewModel @Inject constructor(  
    private val accountDao: AccountDao,  
    private val clientManager: ClientManager  
) : ViewModel() {  
  
    companion object {  
        private const val TAG = "AutoDefViewModel"  
        private const val CHECK_INTERVAL_MS = 2000L  
    }  
  
    private val queryEngine = QueryEngine()  
  
    private val _uiState = MutableStateFlow(AutoDefUiState())  
    val uiState: StateFlow<AutoDefUiState> = _uiState  
  
    private var runJob: Job? = null  
  
    init {  
        viewModelScope.launch { loadAccountsForPlatform(_uiState.value.selectedPlatform.id) }  
    }  
  
    fun updatePlatform(p: Platform) {  
        _uiState.value = _uiState.value.copy(selectedPlatform = p, errorMessage = null)  
        viewModelScope.launch { loadAccountsForPlatform(p.id) }  
    }  
  
    private suspend fun loadAccountsForPlatform(platformId: Int) {  
        val accounts = accountDao.getMasterAccountsByPlatform(platformId)  
        val cur = _uiState.value  
        val newSelectedId = accounts.firstOrNull { it.id == cur.selectedAccountId }?.id  
            ?: accounts.firstOrNull()?.id  
        _uiState.value = cur.copy(availableAccounts = accounts, selectedAccountId = newSelectedId)  
    }  
  
    fun updateSelectedAccount(id: Int) {  
        _uiState.value = _uiState.value.copy(selectedAccountId = id)  
    }  
  
    fun clearError() { _uiState.value = _uiState.value.copy(errorMessage = null) }  
  
    private fun appendLog(line: String) {  
        _uiState.value = _uiState.value.copy(logs = _uiState.value.logs + line)  
    }  
  
    /** 对应 server.py pjjc_auto_def_switch 3167-3362 行 */  
    fun startAutoDef() {  
        if (_uiState.value.isRunning) return  
        val state = _uiState.value  
        _uiState.value = state.copy(isRunning = true, errorMessage = null, logs = emptyList())  
  
        runJob = viewModelScope.launch {  
            try {  
                withContext(Dispatchers.IO) {  
                    // 选账号（对应 LabyrinthViewModel 157-170）  
                    val accounts = accountDao.getMasterAccountsByPlatform(state.selectedPlatform.id)  
                    if (accounts.isEmpty()) {  
                        throw IllegalStateException("没有${state.selectedPlatform.displayName}的账号，请先在“我的账号”里添加")  
                    }  
                    val account = accounts.firstOrNull { it.id == state.selectedAccountId }  
                        ?: throw IllegalStateException("未找到所选账号，请重新选择账号后再试")  
  
                    // 点击运行重新登录（对应 server.py 3191-3197）  
                    var client = clientManager.getClient(account, forceRelogin = true)  
                    appendLog("已登录 ${account.account}，开始每2秒检测被刺，被刺立即换防")  
  
                    // 初始基线（对应 server.py 3199-3204）  
                    val knownLogIds = HashSet<Long>()  
                    queryEngine.grandArenaHistory(client).logIds().forEach { knownLogIds.add(it) }  
  
                    var shuffleCount = 0  
                    val fmt = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())  
                    var lastHistorySig = ""   // 上次已推送的历史记录指纹，用于去重
					
					// 主循环（对应 server.py 3260-3350）  
                    while (isActive) {  
                        delay(CHECK_INTERVAL_MS)  
  
                        val history = queryEngine.grandArenaHistory(client)  
						Log.d(TAG, "history size=${history.historyList().size}, raw=${history.historyList().firstOrNull()}")
                        // 会话失效（顶号）检测：响应含 server_error 或缺少 grand_arena_history_list  
                        if (history.looksExpired()) {  
                            appendLog("账号已被顶号/异常掉线，自动换防已停止")  
                            _uiState.value = _uiState.value.copy(errorMessage = "顶号/异常掉线")  
                            break  
                        }
						// 历史记录：内容有变化才推送，无变化不刷屏  
                        val historyLines = history.historyList().map { h ->  
                            val isCh = (h["is_challenge"] as? Number)?.toInt()?.let { it != 0 }  
                                ?: (h["is_challenge"] as? Boolean) ?: true  
                            val opp = h["opponent_user"] as? Map<*, *>  
                            val name = opp?.get("user_name")?.toString() ?: "?"  
                            val vid = (opp?.get("viewer_id") as? Number)?.toLong() ?: 0L  
                            val ts = (h["versus_time"] as? Number)?.toLong() ?: 0L  
                            val timeStr = if (ts > 0) fmt.format(Date(ts * 1000)) else ""  
                            "$name($vid) $timeStr ${if (isCh) "主动挑战" else "被刺"}"  
                        }  
                        val historySig = historyLines.joinToString("|")  
                        if (historySig != lastHistorySig) {  
                            lastHistorySig = historySig  
                            if (historyLines.isEmpty()) {  
                                appendLog("历史记录：暂无")  
                            } else {  
                                appendLog("历史记录（${historyLines.size}）：\n" + historyLines.joinToString("\n"))  
                            }  
                        }
						val newAttacks = mutableListOf<String>()  
                        history.historyList().forEach { h ->  
                            val logId = (h["log_id"] as? Number)?.toLong() ?: return@forEach  
                            if (logId !in knownLogIds) {  
                                knownLogIds.add(logId)  
                                // is_challenge == false 表示被别人刺（对应 server.py 3278）  
                                val isChallenge = (h["is_challenge"] as? Number)?.toInt()?.let { it != 0 }  
                                    ?: (h["is_challenge"] as? Boolean) ?: true  
                                if (!isChallenge) {  
                                    val opp = h["opponent_user"] as? Map<*, *>  
                                    val name = opp?.get("user_name")?.toString() ?: "?"  
                                    val vid = (opp?.get("viewer_id") as? Number)?.toLong() ?: 0L  
                                    val ts = (h["versus_time"] as? Number)?.toLong() ?: 0L  
                                    val timeStr = if (ts > 0) fmt.format(Date(ts * 1000)) else ""  
                                    newAttacks.add("$name($vid) $timeStr 被刺")  
                                }  
                            }  
                        }  
  
                        if (newAttacks.isNotEmpty()) {  
                            appendLog("检测到被刺：\n" + newAttacks.joinToString("\n"))  
                            // 立即换防（对应 server.py do_shuffle 3207-3251）  
                            val ok = doShuffle(client)  
                            if (!ok) break  
                            shuffleCount++  
                            appendLog("已执行第 $shuffleCount 次换防，正在重新上线...")  
  
                            // 重新上线（对应 server.py 3303-3319）  
                            client = clientManager.relogin(account)  
                            knownLogIds.clear()  
                            queryEngine.grandArenaHistory(client).logIds().forEach { knownLogIds.add(it) }  
                            appendLog("已重新上线，继续每2秒检测被刺")  
                        }  
                    }  
                    appendLog("自动换防已结束，共执行换防 $shuffleCount 次")  
                }  
            } catch (e: Exception) {  
                Log.e(TAG, "startAutoDef failed: ${e.message}", e)  
                _uiState.value = _uiState.value.copy(errorMessage = e.message ?: e.javaClass.simpleName)  
            } finally {  
                _uiState.value = _uiState.value.copy(isRunning = false)  
            }  
        }  
    }  
  
    fun stopAutoDef() {  
        runJob?.cancel()  
        runJob = null  
        _uiState.value = _uiState.value.copy(isRunning = false)  
        appendLog("收到终止信号，自动换防已停止")  
    }  
  
    /** 返回 false 表示达上限需终止 */  
    private suspend fun doShuffle(client: Any): Boolean {  
        val info = queryEngine.grandArenaInfo(client)  
        val limit = info["update_deck_times_limit"] as? Map<*, *>  
        val roundTimes = (limit?.get("round_times") as? Number)?.toInt() ?: 0  
        val roundMax = (limit?.get("round_max_limited_times") as? Number)?.toInt() ?: 0  
        val dailyTimes = (limit?.get("daily_times") as? Number)?.toInt() ?: 0  
        val dailyMax = (limit?.get("daily_max_limited_times") as? Number)?.toInt() ?: 0  
        if (roundMax > 0 && roundTimes >= roundMax) {  
            appendLog("已达本轮换防次数上限 $roundMax，自动换防终止")  
            return false  
        }  
        if (dailyMax > 0 && dailyTimes >= dailyMax) {  
            appendLog("已达每日换防次数上限 $dailyMax，自动换防终止")  
            return false  
        }  
  
        // 读取当前 3 支防守队伍单位（字段名需按实际响应确认，见文末说明）  
        val decks = queryEngine.grandArenaDefenseDecks(info) // List<List<Int>> size=3  
        if (decks.size < 3) {  
            appendLog("未能读取到3支防守队伍，换防跳过")  
            return true  
        }  
  
        // 3队错排（对应 server.py 3224-3228）：位置全部改变  
        val order = listOf(listOf(1, 2, 0), listOf(2, 0, 1)).random()  
  
        // GRAND_ARENA_DEF_1/2/3 的 deck_number（见文末说明，需确认整数值）  
        val deckNumbers = QueryEngine.GRAND_ARENA_DEF_NUMBERS  
        val deckList = (0 until 3).map { i ->  
            mutableMapOf<String, Any?>(  
                "deck_number" to deckNumbers[order[i]],  
                "unit_list" to decks[i]  
            )  
        }.sortedBy { (it["deck_number"] as Number).toInt() }  
  
        queryEngine.deckUpdateList(client, deckList)  
        appendLog("本轮 $roundTimes/$roundMax，今日 $dailyTimes/$dailyMax")  
        return true  
    }  
  
    // 从原始响应 Map 里取 history 列表与 log_id 列表的小工具  
    @Suppress("UNCHECKED_CAST")  
    private fun Map<String, Any?>.historyList(): List<Map<String, Any?>> =  
        (this["grand_arena_history_list"] as? List<Map<String, Any?>>) ?: emptyList()  
  
    private fun Map<String, Any?>.logIds(): List<Long> =  
        historyList().mapNotNull { (it["log_id"] as? Number)?.toLong() }  
	
    /** 顶号/会话失效判定：响应含 server_error，或缺少 grand_arena_history_list 关键字段 */  
    private fun Map<String, Any?>.looksExpired(): Boolean =  
        this.containsKey("server_error") || !this.containsKey("grand_arena_history_list")	
}