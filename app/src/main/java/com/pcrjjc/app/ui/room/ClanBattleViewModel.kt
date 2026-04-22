package com.pcrjjc.app.ui.room      
  
import android.app.NotificationManager  
import android.content.Context  
import android.util.Log      
import androidx.core.app.NotificationCompat  
import androidx.lifecycle.SavedStateHandle      
import androidx.lifecycle.ViewModel      
import androidx.lifecycle.viewModelScope      
import com.pcrjjc.app.PcrJjcApp  
import com.pcrjjc.app.R  
import com.pcrjjc.app.data.local.dao.AccountDao      
import com.pcrjjc.app.data.local.entity.Account      
import com.pcrjjc.app.data.local.entity.ApplyRecord      
import com.pcrjjc.app.data.local.entity.ClanBattleAction      
import com.pcrjjc.app.data.local.entity.ClanBattleActionMessage      
import com.pcrjjc.app.data.local.entity.ClanBattleState      
import com.pcrjjc.app.data.local.entity.SLRecord      
import com.pcrjjc.app.data.local.entity.SubscribeRecord      
import com.pcrjjc.app.data.local.entity.TreeRecord      
import com.pcrjjc.app.data.remote.CaptchaRequiredException      
import com.pcrjjc.app.data.remote.PcrClient      
import com.pcrjjc.app.data.remote.RoomClient      
import com.pcrjjc.app.domain.ClanBattleEngine      
import com.pcrjjc.app.domain.ClientManager      
import com.pcrjjc.app.service.ClanBattleFloatingService      
import com.pcrjjc.app.util.pcrDateMillis      
import dagger.hilt.android.lifecycle.HiltViewModel      
import dagger.hilt.android.qualifiers.ApplicationContext  
import kotlinx.coroutines.Dispatchers      
import kotlinx.coroutines.Job      
import kotlinx.coroutines.delay      
import kotlinx.coroutines.flow.MutableStateFlow      
import kotlinx.coroutines.flow.StateFlow      
import kotlinx.coroutines.flow.asStateFlow      
import kotlinx.coroutines.isActive      
import kotlinx.coroutines.launch      
import kotlinx.coroutines.withContext      
import javax.inject.Inject      
  
data class ClanBattleUiState(      
    val roomId: String = "",      
    val roomName: String = "",      
    val playerQq: String = "",      
    val playerName: String = "",      
    val hostQq: String = "",      
  
    // 会战状态（房间共享）      
    val battleState: ClanBattleState = ClanBattleState(),      
  
    // 监控相关      
    val isMonitoring: Boolean = false,      
    val monitorAccount: Account? = null,      
    val masterAccounts: List<Account> = emptyList(),      
  
    // 战报      
    val reportText: String = "",      
    val isLoadingReport: Boolean = false,      
  
    // 通用      
    val isInitializing: Boolean = false,      
    val error: String? = null,      
    val toastMessage: String? = null      
)      
  
@HiltViewModel      
class ClanBattleViewModel @Inject constructor(      
    private val roomClient: RoomClient,      
    private val accountDao: AccountDao,      
    private val clientManager: ClientManager,      
    savedStateHandle: SavedStateHandle,  
    @ApplicationContext private val appContext: Context  
) : ViewModel() {      
  
    companion object {      
        private const val TAG = "ClanBattleVM"      
    }      
  
    private val _uiState = MutableStateFlow(ClanBattleUiState())      
    val uiState: StateFlow<ClanBattleUiState> = _uiState.asStateFlow()      
  
    private val engine = ClanBattleEngine()      
    private var monitorJob: Job? = null      
    private var statePollingJob: Job? = null      
  
    init {      
        val roomId = savedStateHandle.get<String>("roomId") ?: ""      
        val playerQq = savedStateHandle.get<String>("playerQq") ?: ""      
        val playerName = savedStateHandle.get<String>("playerName") ?: ""      
        val roomName = savedStateHandle.get<String>("roomName") ?: ""      
        val hostQq = savedStateHandle.get<String>("hostQq") ?: ""      
  
        _uiState.value = _uiState.value.copy(      
            roomId = roomId,      
            playerQq = playerQq,      
            playerName = playerName,      
            roomName = roomName,      
            hostQq = hostQq      
        )      
  
        // 加载"我的账号"列表      
        viewModelScope.launch {      
            try {      
                val masters = accountDao.getMasterAccountsByPlatform(2) // B服=2      
                    .ifEmpty { accountDao.getAllAccountsSync().filter { it.isMaster } }      
                _uiState.value = _uiState.value.copy(masterAccounts = masters)      
            } catch (e: Exception) {      
                Log.e(TAG, "Failed to load master accounts", e)      
            }      
        }      
  
        // 开始轮询房间消息中的会战状态      
        startStatePolling()      
    }      
  
    // ======================== 监控 ========================      
  
    /**      
     * 使用指定的"我的账号"开始出刀监控      
     */      
    fun startMonitor(account: Account) {      
        if (_uiState.value.isMonitoring) return      
  
        monitorJob?.cancel()      
        monitorJob = viewModelScope.launch {      
            _uiState.value = _uiState.value.copy(      
                isInitializing = true,      
                error = null      
            )      
  
            try {      
                // 1. 登录账号（在 IO 线程执行网络请求）      
                val client = withContext(Dispatchers.IO) {      
                    clientManager.getClient(account)      
                }      
                if (client !is PcrClient) {      
                    _uiState.value = _uiState.value.copy(      
                        isInitializing = false,      
                        error = "暂不支持该平台的会战监控"      
                    )      
                    return@launch      
                }      
  
                // 2. 初始化引擎（在 IO 线程执行网络请求）      
                withContext(Dispatchers.IO) {      
                    engine.init(client)      
                }      
  
                _uiState.value = _uiState.value.copy(      
                    isMonitoring = true,      
                    isInitializing = false,      
                    monitorAccount = account      
                )      
  
                // 3. 发送开始监控消息到房间      
                val actionMsg = ClanBattleActionMessage(      
                    action = ClanBattleAction.START_MONITOR,      
                    playerName = _uiState.value.playerName,      
                    playerQq = _uiState.value.playerQq      
                )      
                sendActionToRoom(actionMsg)      
  
                // 4. 同步初始状态到房间      
                syncStateToRoom()      
  
                // 5. 开始监控循环（在 IO 线程执行网络请求）      
                withContext(Dispatchers.IO) {      
                    engine.startMonitorLoop(  
                        onEvent = { eventMsg ->  
                            // 每次有事件（出刀播报等），发送到房间聊天  
                            sendChatMessage(eventMsg)  
                            // 同步最新状态  
                            syncStateToRoom()  
                        },  
                        onBossKill = { bossOrder ->  
                            val state = _uiState.value.battleState  
                            val myQq = _uiState.value.playerQq  
  
                            // 检查当前玩家是否有被清除的记录  
                            val myApply = state.applies.any { it.playerQq == myQq && it.bossOrder == bossOrder }  
                            val myTree = state.trees.any { it.playerQq == myQq && it.bossOrder == bossOrder }  
                            val mySub = state.subscribes.any {  
                                it.playerQq == myQq && it.bossOrder == bossOrder && it.lapNum <= state.lapNum  
                            }  
  
                            val cleared = mutableListOf<String>()  
                            if (myApply) cleared.add("申请出刀")  
                            if (myTree) cleared.add("挂树")  
                            if (mySub) cleared.add("预约")  
  
                            // 清除该 boss 的所有记录  
                            val clearedState = state.copy(  
                                applies = state.applies.filter { it.bossOrder != bossOrder },  
                                trees = state.trees.filter { it.bossOrder != bossOrder },  
                                subscribes = state.subscribes.filter {  
                                    !(it.bossOrder == bossOrder && it.lapNum <= state.lapNum)  
                                }  
                            )  
  
                            _uiState.value = _uiState.value.copy(battleState = clearedState)  
  
                            // 如果当前玩家有被清除的记录，发送系统通知（和 JJC 排名变动一样的通知栏推送）  
                            if (cleared.isNotEmpty()) {  
                                val msg = "" + bossOrder + "王已击破，你的" + cleared.joinToString("/") + "已自动清除"  
                                val notification = NotificationCompat.Builder(appContext, PcrJjcApp.CLAN_BATTLE_CHANNEL_ID)  
                                    .setSmallIcon(R.drawable.ic_notification)  
                                    .setContentTitle("会战状态变动")  
                                    .setContentText(msg)  
                                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)  
                                    .setAutoCancel(true)  
                                    .build()  
                                val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager  
                                nm.notify(System.currentTimeMillis().toInt(), notification)  
                            }  
  
                            sendChatMessage("" + bossOrder + "王已击破，自动清除该王的申请/挂树记录")  
                            syncStateToRoom()  
                        }  
                    )  
                }      
  
            } catch (e: CaptchaRequiredException) {      
                Log.e(TAG, "Captcha required during monitor start", e)      
                _uiState.value = _uiState.value.copy(      
                    isMonitoring = false,      
                    isInitializing = false,      
                    error = "登录需要验证码，请先在账号管理中测试登录"      
                )      
            } catch (e: Exception) {      
                Log.e(TAG, "Monitor failed", e)      
                _uiState.value = _uiState.value.copy(      
                    isMonitoring = false,      
                    isInitializing = false,      
                    error = e.message ?: "监控启动失败"      
                )      
            }      
        }      
    }      
  
    /**      
     * 停止出刀监控      
     */      
    fun stopMonitor() {      
        engine.stopMonitor()      
        monitorJob?.cancel()      
        monitorJob = null      
  
        _uiState.value = _uiState.value.copy(isMonitoring = false)      
  
        // 发送停止监控消息      
        val actionMsg = ClanBattleActionMessage(      
            action = ClanBattleAction.STOP_MONITOR,      
            playerName = _uiState.value.playerName,      
            playerQq = _uiState.value.playerQq      
        )      
        viewModelScope.launch { sendActionToRoom(actionMsg) }      
    }      
  
    // ======================== 申请出刀 / 挂树 / 预约 ========================      
  
    /**      
     * 切换申请出刀状态（toggle）      
     */      
    fun toggleApply(bossOrder: Int) {      
        val state = _uiState.value.battleState      
        val qq = _uiState.value.playerQq      
        val name = _uiState.value.playerName      
        val hasApplied = state.hasApplied(qq, bossOrder)      
  
        val action = if (hasApplied) ClanBattleAction.CANCEL_APPLY else ClanBattleAction.APPLY      
        val actionMsg = ClanBattleActionMessage(      
            action = action,      
            bossOrder = bossOrder,      
            playerName = name,      
            playerQq = qq      
        )      
  
        // 本地立即更新      
        val newApplies = if (hasApplied) {      
            state.applies.filter { !(it.playerQq == qq && it.bossOrder == bossOrder) }      
        } else {      
            state.applies + ApplyRecord(      
                playerName = name,      
                playerQq = qq,      
                bossOrder = bossOrder,      
                timestamp = System.currentTimeMillis()      
            )      
        }      
        _uiState.value = _uiState.value.copy(      
            battleState = state.copy(applies = newApplies)      
        )      
  
        // 发送到房间      
        viewModelScope.launch {      
            sendActionToRoom(actionMsg)      
            // 同时发送人类可读消息      
            sendChatMessage(actionMsg.toReadableMessage())      
        }      
    }      
  
    /**      
     * 切换挂树状态（toggle）      
     */      
    fun toggleTree(bossOrder: Int) {      
        val state = _uiState.value.battleState      
        val qq = _uiState.value.playerQq      
        val name = _uiState.value.playerName      
        val hasTree = state.hasTree(qq, bossOrder)      
  
        val action = if (hasTree) ClanBattleAction.CANCEL_TREE else ClanBattleAction.TREE      
        val actionMsg = ClanBattleActionMessage(      
            action = action,      
            bossOrder = bossOrder,      
            playerName = name,      
            playerQq = qq      
        )      
  
        val newTrees = if (hasTree) {      
            state.trees.filter { !(it.playerQq == qq && it.bossOrder == bossOrder) }      
        } else {      
            state.trees + TreeRecord(      
                playerName = name,      
                playerQq = qq,      
                bossOrder = bossOrder,      
                timestamp = System.currentTimeMillis()      
            )      
        }      
        _uiState.value = _uiState.value.copy(      
            battleState = state.copy(trees = newTrees)      
        )      
  
        viewModelScope.launch {      
            sendActionToRoom(actionMsg)      
            sendChatMessage(actionMsg.toReadableMessage())      
        }      
    }      
  
    /**      
     * 切换预约下一周目状态（toggle）      
     */      
    fun toggleSubscribe(bossOrder: Int) {      
        val state = _uiState.value.battleState      
        val qq = _uiState.value.playerQq      
        val name = _uiState.value.playerName      
        val hasSub = state.hasSubscribed(qq, bossOrder)      
  
        val action = if (hasSub) ClanBattleAction.CANCEL_SUBSCRIBE else ClanBattleAction.SUBSCRIBE      
        val actionMsg = ClanBattleActionMessage(      
            action = action,      
            bossOrder = bossOrder,      
            playerName = name,      
            playerQq = qq      
        )      
  
        val newSubs = if (hasSub) {      
            state.subscribes.filter { !(it.playerQq == qq && it.bossOrder == bossOrder) }      
        } else {      
            state.subscribes + SubscribeRecord(      
                playerName = name,      
                playerQq = qq,      
                bossOrder = bossOrder,      
                lapNum = state.lapNum + 1      
            )      
        }      
        _uiState.value = _uiState.value.copy(      
            battleState = state.copy(subscribes = newSubs)      
        )      
  
        viewModelScope.launch {      
            sendActionToRoom(actionMsg)      
            sendChatMessage(actionMsg.toReadableMessage())      
        }      
    }      
  
    /**      
     * 记录 SL      
     */      
    fun recordSL() {      
        val state = _uiState.value.battleState      
        val qq = _uiState.value.playerQq      
        val name = _uiState.value.playerName      
        val todayPcrDate = pcrDateMillis(System.currentTimeMillis())      
  
        if (state.hasSLToday(qq, todayPcrDate)) {      
            _uiState.value = _uiState.value.copy(      
                toastMessage = "今天已经记录过SL了"      
            )      
            return      
        }      
  
        val newSLRecords = state.slRecords + SLRecord(      
            playerName = name,      
            playerQq = qq,      
            date = todayPcrDate      
        )      
        _uiState.value = _uiState.value.copy(      
            battleState = state.copy(slRecords = newSLRecords)      
        )      
  
        val actionMsg = ClanBattleActionMessage(      
            action = ClanBattleAction.SL,      
            playerName = name,      
            playerQq = qq      
        )      
        viewModelScope.launch {      
            sendActionToRoom(actionMsg)      
            sendChatMessage(actionMsg.toReadableMessage())      
        }      
    }      
  
    // ======================== 战报 ========================      
  
    /**      
     * 获取当前战报      
     */      
    fun fetchCurrentReport() {      
        if (!engine.isInitialized) {      
            _uiState.value = _uiState.value.copy(error = "请先开启出刀监控")      
            return      
        }      
        viewModelScope.launch {      
            _uiState.value = _uiState.value.copy(isLoadingReport = true)      
            try {      
                val records = withContext(Dispatchers.IO) { engine.getAllRecords() }      
                val report = engine.generateReport(records)      
                _uiState.value = _uiState.value.copy(      
                    reportText = report,      
                    isLoadingReport = false      
                )      
                sendChatMessage(report)      
            } catch (e: Exception) {      
                _uiState.value = _uiState.value.copy(      
                    isLoadingReport = false,      
                    error = "获取战报失败: ${e.message}"      
                )      
            }      
        }      
    }      
  
    /**      
     * 获取个人战报      
     */      
    fun fetchMyReport(gameName: String) {      
        if (!engine.isInitialized) {      
            _uiState.value = _uiState.value.copy(error = "请先开启出刀监控")      
            return      
        }      
        if (gameName.isBlank()) {      
            _uiState.value = _uiState.value.copy(error = "请输入游戏名称")      
            return      
        }      
        viewModelScope.launch {      
            _uiState.value = _uiState.value.copy(isLoadingReport = true)      
            try {      
                val records = withContext(Dispatchers.IO) { engine.getAllRecords() }      
                val report = engine.generatePlayerReport(records, gameName)      
                _uiState.value = _uiState.value.copy(      
                    reportText = report,      
                    isLoadingReport = false      
                )      
                sendChatMessage(report)      
            } catch (e: Exception) {      
                _uiState.value = _uiState.value.copy(      
                    isLoadingReport = false,      
                    error = "获取个人战报失败: ${e.message}"      
                )      
            }      
        }      
    }      
  
    /**      
     * 获取今日出刀情况      
     */      
    fun fetchTodayReport() {      
        fetchDayReport(offsetDays = 0, label = "今日")      
    }      
  
    /**      
     * 获取昨日出刀情况      
     */      
    fun fetchYesterdayReport() {      
        fetchDayReport(offsetDays = -1, label = "昨日")      
    }      
  
    private fun fetchDayReport(offsetDays: Int, label: String) {      
        if (!engine.isInitialized) {      
            _uiState.value = _uiState.value.copy(error = "请先开启出刀监控")      
            return      
        }      
        viewModelScope.launch {      
            _uiState.value = _uiState.value.copy(isLoadingReport = true)      
            try {      
                val allRecords = withContext(Dispatchers.IO) { engine.getAllRecords() }      
                val todayStart = pcrDateMillis(System.currentTimeMillis())      
                val targetStart = todayStart + offsetDays.toLong() * 86400_000      
                val targetEnd = targetStart + 86400_000      
  
                val filtered = allRecords.filter { it.time * 1000 in targetStart until targetEnd }      
                val members = withContext(Dispatchers.IO) {      
                    try { engine.getClanMembers() } catch (_: Exception) { emptyMap() }      
                }      
                val report = "===== ${label}出刀 =====\n" + engine.generateDayReport(filtered, members)      
  
                _uiState.value = _uiState.value.copy(      
                    reportText = report,      
                    isLoadingReport = false      
                )      
                sendChatMessage(report)      
            } catch (e: Exception) {      
                _uiState.value = _uiState.value.copy(      
                    isLoadingReport = false,      
                    error = "获取${label}出刀失败: ${e.message}"      
                )      
            }      
        }      
    }      
  
    // ======================== 房间消息 ========================      
  
    /**      
     * 发送聊天消息到房间      
     */      
    private suspend fun sendChatMessage(content: String) {      
        try {      
            withContext(Dispatchers.IO) {      
                roomClient.sendMessage(      
                    roomId = _uiState.value.roomId,      
                    senderQq = _uiState.value.playerQq,      
                    senderName = _uiState.value.playerName.ifBlank { "系统" },      
                    content = content      
                )      
            }      
        } catch (e: Exception) {      
            Log.e(TAG, "Failed to send chat message", e)      
        }      
    }      
  
    /**      
     * 发送会战操作消息到房间（机器可读格式）      
     */      
    private suspend fun sendActionToRoom(actionMsg: ClanBattleActionMessage) {      
        try {      
            withContext(Dispatchers.IO) {      
                roomClient.sendMessage(      
                    roomId = _uiState.value.roomId,      
                    senderQq = _uiState.value.playerQq,      
                    senderName = _uiState.value.playerName.ifBlank { "系统" },      
                    content = actionMsg.toMessageContent()      
                )      
            }      
        } catch (e: Exception) {      
            Log.e(TAG, "Failed to send action message", e)      
        }      
    }      
  
    /**      
     * 同步当前会战状态到房间      
     */      
    private suspend fun syncStateToRoom() {      
        try {      
            val currentEngineState = engine.state.value      
            val mergedState = _uiState.value.battleState.copy(      
                rank = currentEngineState.rank,      
                lapNum = currentEngineState.lapNum,      
                period = currentEngineState.period,      
                periodName = currentEngineState.periodName,      
                bosses = currentEngineState.bosses,      
                isMonitoring = _uiState.value.isMonitoring,      
                monitorPlayerName = _uiState.value.playerName,      
                lastUpdateTime = System.currentTimeMillis()      
            )      
            _uiState.value = _uiState.value.copy(battleState = mergedState)      
  
            withContext(Dispatchers.IO) {      
                roomClient.sendMessage(      
                    roomId = _uiState.value.roomId,      
                    senderQq = "system",      
                    senderName = "会战系统",      
                    content = ClanBattleState.MESSAGE_PREFIX + mergedState.toJson().toString()      
                )      
            }      
  
            // 直接更新浮窗（进程内），避免依赖浮窗自身的网络轮询      
            ClanBattleFloatingService.instance?.updateText(      
                engine.generateFloatingText(mergedState)      
            )      
        } catch (e: Exception) {      
            Log.e(TAG, "Failed to sync state to room", e)      
        }      
    }      
  
    /**      
     * 轮询房间消息，解析会战状态和操作      
     */      
    private fun startStatePolling() {      
        statePollingJob?.cancel()      
        statePollingJob = viewModelScope.launch {      
            var lastTimestamp = 0L      
            while (isActive) {      
                delay(10000) // 每10秒轮询一次      
                try {      
                    val messages = withContext(Dispatchers.IO) {      
                        roomClient.getMessages(      
                            _uiState.value.roomId,      
                            since = lastTimestamp      
                        )      
                    }      
                    if (messages.isEmpty()) continue      
                    lastTimestamp = messages.maxOf { it.timestamp }      
  
                    for (msg in messages) {      
                        // 解析会战状态消息      
                        val cbState = ClanBattleState.fromMessage(msg.content)      
                        if (cbState != null && msg.senderQq != _uiState.value.playerQq) {      
                            // 来自其他成员的状态更新，合并本地操作      
                            _uiState.value = _uiState.value.copy(battleState = cbState)      
                            continue      
                        }      
  
                        // 解析会战操作消息      
                        val cbAction = ClanBattleActionMessage.fromMessage(msg.content)      
                        if (cbAction != null && msg.senderQq != _uiState.value.playerQq) {      
                            applyRemoteAction(cbAction)      
                        }      
                    }      
                } catch (_: Exception) {      
                    // 轮询失败静默忽略      
                }      
            }      
        }      
    }      
  
    /**      
     * 应用来自其他成员的操作      
     */      
    private fun applyRemoteAction(actionMsg: ClanBattleActionMessage) {      
        val state = _uiState.value.battleState      
        val newState = when (actionMsg.action) {      
            ClanBattleAction.APPLY -> state.copy(      
                applies = state.applies + ApplyRecord(      
                    playerName = actionMsg.playerName,      
                    playerQq = actionMsg.playerQq,      
                    bossOrder = actionMsg.bossOrder,      
                    timestamp = System.currentTimeMillis()      
                )      
            )      
            ClanBattleAction.CANCEL_APPLY -> state.copy(      
                applies = state.applies.filter {      
                    !(it.playerQq == actionMsg.playerQq && it.bossOrder == actionMsg.bossOrder)      
                }      
            )      
            ClanBattleAction.TREE -> state.copy(      
                trees = state.trees + TreeRecord(      
                    playerName = actionMsg.playerName,      
                    playerQq = actionMsg.playerQq,      
                    bossOrder = actionMsg.bossOrder,      
                    timestamp = System.currentTimeMillis()      
                )      
            )      
            ClanBattleAction.CANCEL_TREE -> state.copy(      
                trees = state.trees.filter {      
                    !(it.playerQq == actionMsg.playerQq && it.bossOrder == actionMsg.bossOrder)      
                }      
            )      
            ClanBattleAction.SUBSCRIBE -> state.copy(      
                subscribes = state.subscribes + SubscribeRecord(      
                    playerName = actionMsg.playerName,      
                    playerQq = actionMsg.playerQq,      
                    bossOrder = actionMsg.bossOrder,      
                    lapNum = state.lapNum + 1      
                )      
            )      
            ClanBattleAction.CANCEL_SUBSCRIBE -> state.copy(      
                subscribes = state.subscribes.filter {      
                    !(it.playerQq == actionMsg.playerQq && it.bossOrder == actionMsg.bossOrder)      
                }      
            )      
            ClanBattleAction.SL -> state.copy(      
                slRecords = state.slRecords + SLRecord(      
                    playerName = actionMsg.playerName,      
                    playerQq = actionMsg.playerQq,      
                    date = pcrDateMillis(System.currentTimeMillis())      
                )      
            )      
            ClanBattleAction.START_MONITOR -> state.copy(      
                isMonitoring = true,      
                monitorPlayerName = actionMsg.playerName      
            )      
            ClanBattleAction.STOP_MONITOR -> state.copy(      
                isMonitoring = false,      
                monitorPlayerName = ""      
            )      
        }      
        _uiState.value = _uiState.value.copy(battleState = newState)      
    }      
  
    // ======================== UI 辅助 ========================      
  
    fun clearError() {      
        _uiState.value = _uiState.value.copy(error = null)      
    }      
  
    fun clearToast() {      
        _uiState.value = _uiState.value.copy(toastMessage = null)      
    }      
  
    fun clearReport() {      
        _uiState.value = _uiState.value.copy(reportText = "")      
    }      
  
    /**      
     * 生成浮窗显示文本      
     */      
    fun getFloatingText(): String {      
        return engine.generateFloatingText(_uiState.value.battleState)      
    }      
  
    override fun onCleared() {      
        super.onCleared()      
        monitorJob?.cancel()      
        statePollingJob?.cancel()      
        engine.stopMonitor()      
    }      
}