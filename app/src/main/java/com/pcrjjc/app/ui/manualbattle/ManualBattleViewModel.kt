package com.pcrjjc.app.ui.manualbattle  

import android.app.NotificationManager  
import android.content.Context  
import androidx.core.app.NotificationCompat  
import com.pcrjjc.app.PcrJjcApp  
import com.pcrjjc.app.R  
import android.util.Log  
import androidx.lifecycle.SavedStateHandle  
import androidx.lifecycle.ViewModel  
import androidx.lifecycle.viewModelScope  
import com.pcrjjc.app.data.local.entity.ManualBattleState  
import com.pcrjjc.app.data.remote.RoomClient  
import com.pcrjjc.app.domain.ManualBattleEngine  
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
  
data class ManualBattleUiState(  
    val roomId: String = "",  
    val roomName: String = "",  
    val playerQq: String = "",  
    val playerName: String = "",  
    val hostQq: String = "",  
  
    // 手动报刀状态（房间共享）  
    val battleState: ManualBattleState = ManualBattleState(),  
  
    // 操作结果消息（显示在 Snackbar / Toast）  
    val resultMessage: String? = null,  
  
    // 自动获取boss数据  
    val isFetchingBossData: Boolean = false,  
  
    // 通用  
    val isLoading: Boolean = false,  
    val error: String? = null,  
    val toastMessage: String? = null,   // ← 加逗号  
    val bladeQueryData: List<Pair<String, List<ManualBattleEngine.BladeDetail>>>? = null  
) {  
    val isHost: Boolean get() = playerQq.isNotBlank() && playerQq == hostQq  
}  
  
@HiltViewModel  
class ManualBattleViewModel @Inject constructor(  
    private val roomClient: RoomClient,  
    savedStateHandle: SavedStateHandle,  
    @ApplicationContext private val appContext: Context
) : ViewModel() {  
  
    companion object {  
        private const val TAG = "ManualBattleVM"  
    }  
  
    private val _uiState = MutableStateFlow(ManualBattleUiState())  
    val uiState: StateFlow<ManualBattleUiState> = _uiState.asStateFlow()  
  
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
  
        // 从房间历史消息中恢复最新状态  
        loadStateFromHistory()  
        // 开始轮询房间消息  
        startStatePolling()  
    }  
  
    // ======================== 公会管理 ========================  
  
    /** 创建公会 */  
	fun createGuild(gameServer: String = "cn") {  
		val state = _uiState.value.battleState  
		val result = ManualBattleEngine.createGuild(state, gameServer)  
		applyResult(result)  
	}  
  
    /** 加入公会 */  
    fun joinGuild() {  
        val qq = _uiState.value.playerQq  
        val name = _uiState.value.playerName  
        val state = _uiState.value.battleState  
        val result = ManualBattleEngine.joinGuild(state, qq, name)  
        applyResult(result)  
    }  
  
    // ======================== 申请出刀 ========================  
  
    /** 申请出刀 */  
    fun applyForChallenge(  
        bossNum: Int,  
        isContinue: Boolean = false,  
        behalfQq: String? = null,  
        behalfName: String? = null  
    ) {  
        val qq = _uiState.value.playerQq  
        val name = _uiState.value.playerName  
        val state = _uiState.value.battleState  
        val result = ManualBattleEngine.applyForChallenge(  
            state, qq, name, bossNum, isContinue, behalfQq, behalfName  
        )  
        applyResult(result)  
    }  
  
    /** 取消申请出刀 */  
    fun cancelApply(cancelAll: Boolean = false) {  
        val qq = _uiState.value.playerQq  
        val state = _uiState.value.battleState  
        val result = ManualBattleEngine.cancelApply(state, qq, cancelAll)  
        applyResult(result)  
    }  
  
    // ======================== 报刀 ========================  
  
    /**  
     * 报刀  
     * @param defeat 是否击败boss（尾刀）  
     * @param damage 伤害值  
     * @param bossNum 指定boss编号（1-5），0=自动  
     * @param isContinue 是否补偿刀  
     * @param behalfQq 代刀人QQ  
     * @param behalfName 代刀人名字  
     * @param previousDay 是否记录为昨日  
     */  
    fun challenge(  
        defeat: Boolean,  
        damage: Long = 0,  
        bossNum: Int = 0,  
        isContinue: Boolean = false,  
        behalfQq: String? = null,  
        behalfName: String? = null,  
        previousDay: Boolean = false  
    ) {  
        val qq = _uiState.value.playerQq  
        val name = _uiState.value.playerName  
        val state = _uiState.value.battleState  
        val result = ManualBattleEngine.challenge(  
            state, qq, name, defeat, damage, bossNum,  
            isContinue, behalfQq, behalfName, previousDay  
        )  
        applyResult(result)  
    }  
  
    /** 撤销上一刀 */  
    fun undo() {  
        val qq = _uiState.value.playerQq  
        val state = _uiState.value.battleState  
        val result = ManualBattleEngine.undo(state, qq)  
        applyResult(result)  
    }  
  
    // ======================== 挂树 ========================  
  
    /** 挂树 */  
    fun putOnTree(bossNum: Int = 0, message: String? = null) {  
        val qq = _uiState.value.playerQq  
        val name = _uiState.value.playerName  
        val state = _uiState.value.battleState  
        val result = ManualBattleEngine.putOnTree(state, qq, name, bossNum, message)  
        applyResult(result)  
    }  
  
    /** 下树 */  
    fun takeOffTree() {  
        val qq = _uiState.value.playerQq  
        val state = _uiState.value.battleState  
        val result = ManualBattleEngine.takeOffTree(state, qq)  
        applyResult(result)  
    }  
  
    /** 查树 */  
    fun queryTree(bossNum: Int = 0) {  
        val state = _uiState.value.battleState  
        val result = ManualBattleEngine.queryTree(state, bossNum)  
        // 查树不改变状态，只显示消息  
        _uiState.value = _uiState.value.copy(resultMessage = result.message)  
    }  
  
    // ======================== 预约 ========================  
  
    /** 预约 */  
    fun subscribe(bossNum: Int, note: String = "") {  
        val qq = _uiState.value.playerQq  
        val name = _uiState.value.playerName  
        val state = _uiState.value.battleState  
        val result = ManualBattleEngine.subscribe(state, qq, name, bossNum, note)  
        applyResult(result)  
    }  
  
    /** 取消预约 */  
    fun cancelSubscribe(bossNum: Int) {  
        val qq = _uiState.value.playerQq  
        val state = _uiState.value.battleState  
        val result = ManualBattleEngine.cancelSubscribe(state, qq, bossNum)  
        applyResult(result)  
    }  
  
    /** 预约表 */  
    fun subscribeTable() {  
        val state = _uiState.value.battleState  
        val result = ManualBattleEngine.subscribeTable(state)  
        _uiState.value = _uiState.value.copy(resultMessage = result.message)  
    }  
  
    // ======================== SL ========================  
  
    /** 记录SL */  
    fun recordSL() {  
        val qq = _uiState.value.playerQq  
        val name = _uiState.value.playerName  
        val state = _uiState.value.battleState  
        val result = ManualBattleEngine.saveSlot(state, qq, name)  
        applyResult(result)  
    }  
  
    /** 查询SL状态 */  
    fun checkSL() {  
        val qq = _uiState.value.playerQq  
        val name = _uiState.value.playerName  
        val state = _uiState.value.battleState  
        val result = ManualBattleEngine.saveSlot(state, qq, name, onlyCheck = true)  
        _uiState.value = _uiState.value.copy(resultMessage = result.message)  
    }  
  
    /** 取消SL */  
    fun cancelSL() {  
        val qq = _uiState.value.playerQq  
        val name = _uiState.value.playerName  
        val state = _uiState.value.battleState  
        val result = ManualBattleEngine.saveSlot(state, qq, name, cleanFlag = true)  
        applyResult(result)  
    }  
  
    // ======================== 报伤害 ========================  
  
    /** 报伤害 */  
    fun reportHurt(seconds: Int, damage: Long) {  
        val qq = _uiState.value.playerQq  
        val state = _uiState.value.battleState  
        val result = ManualBattleEngine.reportHurt(state, qq, seconds, damage)  
        applyResult(result)  
    }  
  
    /** 取消报伤害 */  
    fun cancelReportHurt() {  
        val qq = _uiState.value.playerQq  
        val state = _uiState.value.battleState  
        val result = ManualBattleEngine.reportHurt(state, qq, 0, 0, cleanType = 1)  
        applyResult(result)  
    }  
  
    // ======================== 查询类 ========================  
  
    /** 出刀记录 */  
    fun challengeRecord() {  
        val state = _uiState.value.battleState  
        val result = ManualBattleEngine.challengeRecord(state)  
        _uiState.value = _uiState.value.copy(resultMessage = result.message)  
    }  
  
    /** 查刀（所有人出刀详情，带颜色分类） */  
    fun queryAllBlades() {  
        val state = _uiState.value.battleState  
        val data = ManualBattleEngine.queryAllBlades(state)  
        _uiState.value = _uiState.value.copy(bladeQueryData = data)  
    }
	
	/** 业绩表 */  
    fun scoreTable() {  
        val state = _uiState.value.battleState  
        val result = ManualBattleEngine.scoreTable(state)  
        _uiState.value = _uiState.value.copy(resultMessage = result.message)  
    }  
  
    /** Boss状态总览 */  
    fun bossStatusSummary() {  
        val state = _uiState.value.battleState  
        val result = ManualBattleEngine.bossStatusSummary(state)  
        _uiState.value = _uiState.value.copy(resultMessage = result.message)  
    }  
  
    /** 今日出刀详情 */  
    fun memberTodayDetail(playerQq: String? = null) {  
        val qq = playerQq ?: _uiState.value.playerQq  
        val state = _uiState.value.battleState  
        val result = ManualBattleEngine.memberTodayDetail(state, qq)  
        _uiState.value = _uiState.value.copy(resultMessage = result.message)  
    }  
  
    // ======================== 管理功能 ========================  
  
    /** 修改boss状态 */  
    fun modify(cycle: Int, bossData: List<Pair<Int, Long>>) {  
        val state = _uiState.value.battleState  
        val result = ManualBattleEngine.modify(state, cycle, bossData)  
        applyResult(result)  
    }  
  
    /** 重置进度 */  
    fun resetProgress() {  
        val state = _uiState.value.battleState  
        val result = ManualBattleEngine.resetProgress(state)  
        applyResult(result)  
    }  
  
    /** 移除成员 */  
    fun removeMember(playerQq: String) {  
        val state = _uiState.value.battleState  
        val result = ManualBattleEngine.removeMember(state, playerQq)  
        applyResult(result)  
    }  
  
    // ======================== 合刀计算 ========================  
  
    /** 合刀计算（不改变状态，只显示结果） */  
    fun combineBlade(damage1: Long, damage2: Long, bossHp: Long) {  
        val result = ManualBattleEngine.combineBlade(damage1, damage2, bossHp)  
        _uiState.value = _uiState.value.copy(resultMessage = result.message)  
    }  
  
    // ======================== 不打/不进 ========================  
  
    /** 不打/不进 */  
    fun notFight(bossNum: Int = 0) {  
        val qq = _uiState.value.playerQq  
        val state = _uiState.value.battleState  
        val result = ManualBattleEngine.notFight(state, qq, bossNum)  
        applyResult(result)  
    }  
  
    // ======================== 自动获取boss数据 ========================  
  
    /** 自动获取所有boss数据（除台服） */  
    fun fetchBossData() {  
        if (_uiState.value.isFetchingBossData) return  
        _uiState.value = _uiState.value.copy(isFetchingBossData = true)  
  
        viewModelScope.launch {  
            try {  
                val fetchResult = ManualBattleEngine.fetchBossData()  
                ManualBattleEngine.applyFetchedBossData(fetchResult)  
  
                // 如果公会已创建，刷新boss血量上限  
                val state = _uiState.value.battleState  
                if (state.isCreated) {  
                    val refreshResult = ManualBattleEngine.refreshBossMaxHp(state)  
                    _uiState.value = _uiState.value.copy(  
                        battleState = refreshResult.state,  
                        isFetchingBossData = false,  
                        resultMessage = fetchResult.message + "\n" + refreshResult.message  
                    )  
                    syncStateToRoom()  
                    sendChatMessage("自动获取boss数据完成：${fetchResult.message}")  
                } else {  
                    _uiState.value = _uiState.value.copy(  
                        isFetchingBossData = false,  
                        resultMessage = fetchResult.message  
                    )  
                }  
            } catch (e: Exception) {  
                Log.e(TAG, "fetchBossData failed", e)  
                _uiState.value = _uiState.value.copy(  
                    isFetchingBossData = false,  
                    error = "获取boss数据失败: ${e.message}"  
                )  
            }  
        }  
    }  
  
    // ======================== 核心：应用操作结果 ========================  
  
    /**  
     * 应用 ManualBattleEngine 的操作结果：  
     * 1. 更新本地 UI 状态  
     * 2. 同步状态到房间  
     * 3. 发送人类可读消息到房间聊天  
     * 4. 显示结果消息给当前用户  
     */  
    private fun applyResult(result: ManualBattleEngine.Result) {  
        val oldState = _uiState.value.battleState  
        val newState = result.state  
  
        // 更新本地状态  
        _uiState.value = _uiState.value.copy(  
            battleState = newState,  
            resultMessage = result.message  
        )  
  
        // 如果状态发生了变化，同步到房间  
        if (newState != oldState) {  
            viewModelScope.launch {  
                syncStateToRoom()  
                sendChatMessage("[手动报刀] ${_uiState.value.playerName}: ${result.message}")  
            }  
        // 检查当前用户的预约是否因boss击败被清除，如果是则发送系统通知  
            val myQq = _uiState.value.playerQq  
            val oldMySubs = oldState.subscribes.filter { it.playerQq == myQq }  
            val newMySubs = newState.subscribes.filter { it.playerQq == myQq }  
            val clearedSubs = oldMySubs.filter { old ->  
                newMySubs.none { it.bossNum == old.bossNum }  
            }  
            if (clearedSubs.isNotEmpty()) {  
                val bossNums = clearedSubs.joinToString("、") { "${it.bossNum}王" }  
                val msg = "你预约的${bossNums}已出现，快来出刀！"  
                val notification = NotificationCompat.Builder(appContext, PcrJjcApp.CLAN_BATTLE_CHANNEL_ID)  
                    .setSmallIcon(R.drawable.ic_notification)  
                    .setContentTitle("会战预约提醒")  
                    .setContentText(msg)  
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)  
                    .setAutoCancel(true)  
                    .build()  
                val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager  
                nm.notify(System.currentTimeMillis().toInt(), notification)  
            }
		}  
    }  
  
    // ======================== 房间消息通信 ========================  
  
    /** 发送聊天消息到房间 */  
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
  
    /** 同步手动报刀状态到房间 */  
    private suspend fun syncStateToRoom() {  
        try {  
            val currentState = _uiState.value.battleState.copy(  
                lastUpdateTime = System.currentTimeMillis()  
            )  
            withContext(Dispatchers.IO) {  
                roomClient.sendMessage(  
                    roomId = _uiState.value.roomId,  
                    senderQq = "system",  
                    senderName = "手动报刀系统",  
                    content = ManualBattleState.MESSAGE_PREFIX + currentState.toJson().toString()  
                )  
            }  
        } catch (e: Exception) {  
            Log.e(TAG, "Failed to sync state to room", e)  
        }  
    }  
  
    /** 从房间历史消息中恢复最新的手动报刀状态 */  
    private fun loadStateFromHistory() {  
        viewModelScope.launch {  
            try {  
                val messages = withContext(Dispatchers.IO) {  
                    roomClient.getMessages(_uiState.value.roomId)  
                }  
                // 从后往前找最新的 [MB_STATE] 消息  
                for (msg in messages.reversed()) {  
                    val mbState = ManualBattleState.fromMessage(msg.content)  
                    if (mbState != null) {  
                        _uiState.value = _uiState.value.copy(battleState = mbState)  
                        Log.d(TAG, "Restored manual battle state from history")  
                        break  
                    }  
                }  
            } catch (e: Exception) {  
                Log.e(TAG, "Failed to load state from history", e)  
            }  
        }  
    }  
  
    /** 轮询房间消息，接收其他成员的状态更新 */  
    private fun startStatePolling() {  
        statePollingJob?.cancel()  
        statePollingJob = viewModelScope.launch {  
            var lastTimestamp = 0L  
            var isFirstPoll = true  
            while (isActive) {  
                if (!isFirstPoll) {  
                    delay(8000) // 每8秒轮询一次  
                }  
                isFirstPoll = false  
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
                        // 只处理其他人发送的 [MB_STATE] 消息  
                        if (msg.senderQq == _uiState.value.playerQq) continue  
                        val mbState = ManualBattleState.fromMessage(msg.content)  
                        if (mbState != null) {  
                            // 接受更新时间更晚的状态  
                            if (mbState.lastUpdateTime > _uiState.value.battleState.lastUpdateTime) {  
                                // 检查当前用户的预约是否因boss击败被清除  
                                val myQq = _uiState.value.playerQq  
                                val oldSubs = _uiState.value.battleState.subscribes.filter { it.playerQq == myQq }  
                                val newSubs = mbState.subscribes.filter { it.playerQq == myQq }  
                                val clearedSubs = oldSubs.filter { old ->  
                                    newSubs.none { it.bossNum == old.bossNum }  
                                }  
                                if (clearedSubs.isNotEmpty()) {  
                                    val bossNums = clearedSubs.joinToString("、") { "${it.bossNum}王" }  
                                    val notifMsg = "你预约的${bossNums}已出现，快来出刀！"  
                                    val notification = NotificationCompat.Builder(appContext, PcrJjcApp.CLAN_BATTLE_CHANNEL_ID)  
                                        .setSmallIcon(R.drawable.ic_notification)  
                                        .setContentTitle("会战预约提醒")  
                                        .setContentText(notifMsg)  
                                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)  
                                        .setAutoCancel(true)  
                                        .build()  
                                    val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager  
                                    nm.notify(System.currentTimeMillis().toInt(), notification)  
                                }
								_uiState.value = _uiState.value.copy(battleState = mbState)  
                                Log.d(TAG, "Received updated manual battle state from room")  
                            }  
                        }  
                    }  
                } catch (_: Exception) {  
                    // 轮询失败静默忽略  
                }  
            }  
        }  
    }  
  
    // ======================== UI 辅助 ========================  
  
    fun clearError() {  
        _uiState.value = _uiState.value.copy(error = null)  
    }  
  
    fun clearResultMessage() {  
        _uiState.value = _uiState.value.copy(resultMessage = null)  
    }  
  
    fun clearBladeQueryData() {  
        _uiState.value = _uiState.value.copy(bladeQueryData = null)  
    }
	
	fun clearToast() {  
        _uiState.value = _uiState.value.copy(toastMessage = null)  
    }  
  
    override fun onCleared() {  
        super.onCleared()  
        statePollingJob?.cancel()  
    }  
}