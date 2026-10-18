package com.pcrjjc.app.ui.support

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pcrjjc.app.data.local.dao.AccountDao
import com.pcrjjc.app.data.local.entity.Account
import com.pcrjjc.app.data.remote.PcrClient
import com.pcrjjc.app.domain.ClientManager
import com.pcrjjc.app.domain.SupportEngine
import com.pcrjjc.app.domain.SupportEngine.SupportData
import com.pcrjjc.app.util.CharaRoster
import com.pcrjjc.app.util.ExMeta
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class SupportUiState(
    val masterAccounts: List<Account> = emptyList(),
    val selectedAccount: Account? = null,
    val isLoading: Boolean = false,
    val data: SupportData? = null,
    // 更换模式: 正在编辑的支援位(supportType, position)，null=非编辑
    val editingSlot: Pair<Int, Int>? = null,
    // 该支援位当前占位角色（0/空位或待挂），startEdit 时写入
    val editingUnitId: Long? = null,
    val message: String? = null,
    // baseId -> 名字列表（花名册）
    val nameMap: Map<Int, List<String>> = emptyMap(),
    val error: String? = null
)

@HiltViewModel
class SupportViewModel @Inject constructor(
    private val app: Application,
    private val accountDao: AccountDao,
    private val clientManager: ClientManager
) : ViewModel() {

    companion object { private const val TAG = "SupportVM" }

    private val _uiState = MutableStateFlow(SupportUiState())
    val uiState: StateFlow<SupportUiState> = _uiState.asStateFlow()

    private val engine = SupportEngine()

    init {
        // 必须先加载 ex_meta.json：否则 slotCategory/equips/starPt 全为空，
        // 表现为槽位显示"类别?"、装备名显示成数字、★0、三槽候选完全相同
        ExMeta.load(app)

        viewModelScope.launch {
            try {
                val masters = accountDao.getMasterAccountsByPlatform(2)
                    .ifEmpty { accountDao.getAllAccountsSync().filter { it.isMaster } }
                _uiState.value = _uiState.value.copy(
                    masterAccounts = masters,
                    selectedAccount = masters.firstOrNull()
                )
                // 进入页面即加载：登录会话由 ClientManager 全局共享，
                // 已有会话时不会有任何登录请求
                if (masters.isNotEmpty()) loadSupport()
            } catch (e: Exception) {
                Log.e(TAG, "load accounts failed", e)
            }
            // 加载花名册名字映射（用于角色搜索）：缓存为空则强制拉取
            try {
                var map = CharaRoster.getCachedNameMap(app)
                Log.i(TAG, "cached nameMap = ${map?.size ?: 0}")
                if (map.isNullOrEmpty()) {
                    val http = OkHttpClient.Builder()
                        .connectTimeout(15, TimeUnit.SECONDS)
                        .readTimeout(15, TimeUnit.SECONDS)
                        .build()
                    map = withContext(Dispatchers.IO) { CharaRoster.fetchNameMap(http, app) }
                }
                Log.i(TAG, "nameMap size = ${map?.size ?: 0}")
                if (!map.isNullOrEmpty()) {
                    _uiState.value = _uiState.value.copy(nameMap = map)
                }
            } catch (e: Exception) {
                Log.w(TAG, "load nameMap failed", e)
            }
        }
    }

    fun selectAccount(account: Account) {
        if (_uiState.value.selectedAccount?.id == account.id && _uiState.value.data != null) return
        _uiState.value = _uiState.value.copy(selectedAccount = account, data = null)
        loadSupport()
    }

    fun loadSupport() {
        val account = _uiState.value.selectedAccount ?: run {
            _uiState.value = _uiState.value.copy(error = "请先选择账号")
            return
        }
        if (_uiState.value.isLoading) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                // 复用全局登录会话：进入页面不再重新登录；
                // 只有被顶下线时 ClientManager 才会自动重登并重试
                val data = withContext(Dispatchers.IO) {
                    clientManager.withClient(account) { c ->
                        if (c !is PcrClient) throw Exception("暂不支持该平台的账号")
                        engine.loadSupport(c)
                    }
                }
                _uiState.value = _uiState.value.copy(isLoading = false, data = data)
            } catch (e: Exception) {
                Log.e(TAG, "loadSupport failed", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false, error = e.message ?: "加载失败"
                )
            }
        }
    }

    fun startEdit(supportType: Int, position: Int, unitId: Long) {
        _uiState.value = _uiState.value.copy(
            editingSlot = supportType to position,
            editingUnitId = unitId
        )
    }

    fun cancelEdit() {
        _uiState.value = _uiState.value.copy(editingSlot = null, editingUnitId = null)
    }

    /**
     * 更换支援角色 + 穿EX 合并为单个任务。
     *
     * 修复点：
     * ① 旧版"确定更换"连调 changeSupport()+equipEx()，第二个 runTask 因
     *    isLoading=true 被静默丢弃 → 表现为点击无反应。
     * ② 旧版没撤下目标位当前占位角色，对已占位置发 action=1 会静默失败
     *    → 表现为角色没有被真的替换。必须先 action=2 撤旧再 action=1 挂新
     *    （对齐 PPPPPP tools.py:1324/1353）。
     * ③ 占用检测比较 (supportType, position) 二元组：地下城/好友的 position
     *    都是 1/2，只比 position 会误判；好友位撤下要用 supportType=2。
     *
     * @param unitId        新支援角色
     * @param normalChanges 普通EX槽改动 slot -> serial_id (0=卸下)
     * @param cbChanges     会战EX槽改动 slot -> serial_id，一般传 emptyList
     * @param autoCb        true=换完角色后自动穿会战EX（团队战支援位用）
     */
    fun applyEdit(
        unitId: Long,
        normalChanges: List<Pair<Int, Long>> = emptyList(),
        cbChanges: List<Pair<Int, Long>> = emptyList(),
        autoCb: Boolean = false
    ) {
        val (supportType, position) = _uiState.value.editingSlot ?: return
        val oldUnitId = _uiState.value.editingUnitId ?: 0L
        runTask {
            val c = requireClient()
            // ① 目标位已有旧角色，先撤下（action=2），否则 action=1 静默失败。
            //    注意：若旧角色是30分钟内刚挂上的会触发服务器冷却期(SUPPORT_COOLDOWN=1800)，
            //    撤下/挂上都会被拒——属游戏机制。
            if (oldUnitId != 0L && oldUnitId != unitId) {
                engine.changeSupport(c, supportType, position, oldUnitId, action = 2)
            }

            // ② 新角色若已挂在其他支援位，先撤下。
            //    比较 (supportType, position) 二元组，避免地下城/好友 position 1/2 误判
            val data = _uiState.value.data
            if (data != null) {
                val occupiedClan = (data.dungeon + data.clanBattle)
                    .firstOrNull {
                        it.unitId == unitId &&
                                !(supportType == 1 && it.position == position)
                    }
                if (occupiedClan != null) {
                    engine.changeSupport(c, 1, occupiedClan.position, unitId, action = 2)
                }
                val occupiedFriend = data.friend
                    .firstOrNull {
                        it.unitId == unitId &&
                                !(supportType == 2 && it.position == position)
                    }
                if (occupiedFriend != null) {
                    engine.changeSupport(c, 2, occupiedFriend.position, unitId, action = 2)
                }
            }

            // ③ 挂上新角色
            engine.changeSupport(c, supportType, position, unitId, action = 1)

            // ④ 换角色后 unitExSlots 归属已变，重拉一次再穿EX
            val fresh = engine.loadSupport(c)
            _uiState.value = _uiState.value.copy(data = fresh)

            if (normalChanges.isNotEmpty()) {
                engine.equipEx(c, unitId, normalChanges, false, fresh)
            }
            if (cbChanges.isNotEmpty()) {
                engine.equipEx(c, unitId, cbChanges, true, fresh)
            }
            if (autoCb) {
                engine.autoEquipCbEx(c, unitId, fresh)
            }
            "已更换支援角色"
        }
    }

    // 会战支援位：不换角色，仅给当前角色自动穿会战EX（套用 PPPPPP 挂会战支援）
    fun autoEquipCbEx(unitId: Long) {
        runTask {
            val c = requireClient()
            val data = _uiState.value.data ?: throw Exception("数据未加载")
            engine.autoEquipCbEx(c, unitId, data)
            "会战EX已自动穿戴"
        }
    }

    private suspend fun requireClient(): PcrClient {
        val account = _uiState.value.selectedAccount ?: throw Exception("请先选择账号")
        val c = clientManager.getClient(account)
        return c as? PcrClient ?: throw Exception("暂不支持该平台的账号")
    }

    /**
     * 执行写操作。整体通过 [ClientManager.withClient] 运行，
     * 中途掉线会自动重登后重试，不需要用户重新登录。
     */
    private fun runTask(block: suspend () -> String) {
        if (_uiState.value.isLoading) return
        val account = _uiState.value.selectedAccount
        if (account == null) {
            _uiState.value = _uiState.value.copy(error = "请先选择账号")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val msg = withContext(Dispatchers.IO) {
                    clientManager.withClient(account) { block() }
                }
                val data = withContext(Dispatchers.IO) {
                    clientManager.withClient(account) { c ->
                        if (c !is PcrClient) throw Exception("暂不支持该平台的账号")
                        engine.loadSupport(c)
                    }
                }
                _uiState.value = _uiState.value.copy(
                    isLoading = false, data = data, message = msg, editingSlot = null
                )
            } catch (e: Exception) {
                Log.e(TAG, "task failed", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false, error = e.message ?: "执行失败"
                )
            }
        }
    }

    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }
    fun clearMessage() { _uiState.value = _uiState.value.copy(message = null) }
}
