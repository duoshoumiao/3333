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
import com.pcrjjc.app.domain.SupportEngine.SupportSlot  
import com.pcrjjc.app.util.CharaRoster  
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
    private var client: PcrClient? = null  
  
    init {  
        viewModelScope.launch {  
            try {  
                val masters = accountDao.getMasterAccountsByPlatform(2)  
                    .ifEmpty { accountDao.getAllAccountsSync().filter { it.isMaster } }  
                _uiState.value = _uiState.value.copy(  
                    masterAccounts = masters,  
                    selectedAccount = masters.firstOrNull()  
                )  
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
        _uiState.value = _uiState.value.copy(selectedAccount = account, data = null)  
        client = null  
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
                val c = withContext(Dispatchers.IO) {  
                    clientManager.getClient(account, forceRelogin = true)  
                }  
                if (c !is PcrClient) throw Exception("暂不支持该平台的账号")  
                client = c  
                val data = withContext(Dispatchers.IO) { engine.loadSupport(c) }  
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
     * 修复：旧版连调 changeSupport()+equipEx()，第二次因 isLoading=true  
     * 被 runTask 开头的判断静默丢弃，导致"点击更换无反应"。  
     *  
     * @param normalChanges 普通EX槽改动 (slot -> serial)  
     * @param cbChanges     会战EX槽改动 (slot -> serial)，一般传 emptyList  
     * @param autoCb        true=换完角色后自动穿会战EX（团队战支援位）  
     */  
    fun applyEdit(  
        unitId: Long,  
        normalChanges: List<Pair<Int, Long>> = emptyList(),  
        cbChanges: List<Pair<Int, Long>> = emptyList(),  
        autoCb: Boolean = false  
    ) {  
        val c = client ?: return  
        val (supportType, position) = _uiState.value.editingSlot ?: return  
        runTask {  
            val data = _uiState.value.data  
            // 同一角色若已挂在其他支援位，先撤下（action=2）  
            // 修复：占用检测必须比较 (supportType, position) 二元组。  
            // 旧版只比较 position，地下城/好友的 position 都是 1/2 会误判；  
            // 且好友位要用 supportType=2 撤下，公会位用 1。  
            if (data != null) {  
                val occupiedClan = (data.dungeon + data.clanBattle)  
                    .firstOrNull {  
                        it.unitId == unitId &&  
                                !(supportType == 1 && it.position == position)  
                    }  
                val occupiedFriend = data.friend  
                    .firstOrNull {  
                        it.unitId == unitId &&  
                                !(supportType == 2 && it.position == position)  
                    }  
                if (occupiedClan != null) {  
                    engine.changeSupport(c, 1, occupiedClan.position, unitId, action = 2)  
                }  
                if (occupiedFriend != null) {  
                    engine.changeSupport(c, 2, occupiedFriend.position, unitId, action = 2)  
                }  
                engine.changeSupport(c, supportType, position, unitId, action = 1)  
                // 换角色后重新拉数据再穿EX（unitExSlots 归属已变）  
                val fresh = engine.loadSupport(c)  
                _uiState.value = _uiState.value.copy(data = fresh)  
                if (normalChanges.isNotEmpty())  
                    engine.equipEx(c, unitId, normalChanges, false, fresh)  
                if (cbChanges.isNotEmpty())  
                    engine.equipEx(c, unitId, cbChanges, true, fresh)  
                if (autoCb)  
                    engine.autoEquipCbEx(c, unitId, fresh)  
            }  
            "已更换支援角色"  
        }  
    }  
  
    // 会战支援位自动穿会战EX（套用 PPPPPP 挂会战支援，不换角色时使用）  
    fun autoEquipCbEx(unitId: Long) {  
        val c = client ?: return  
        runTask {  
            val data = _uiState.value.data ?: throw Exception("数据未加载")  
            engine.autoEquipCbEx(c, unitId, data)  
            "会战EX已自动穿戴"  
        }  
    }  
  
    private fun runTask(block: suspend () -> String) {  
        if (_uiState.value.isLoading) return  
        viewModelScope.launch {  
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)  
            try {  
                val msg = withContext(Dispatchers.IO) { block() }  
                val c = client  
                val data = if (c != null) {  
                    withContext(Dispatchers.IO) { engine.loadSupport(c) }  
                } else _uiState.value.data  
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