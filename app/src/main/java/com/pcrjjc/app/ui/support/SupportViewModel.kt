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
  
    fun changeSupport(unitId: Long) {  
        val c = client ?: return  
        val (supportType, position) = _uiState.value.editingSlot ?: return  
        runTask {  
            // 同一角色若已挂在其他支援位，先撤下（action=2）再挂（action=1）  
            val data = _uiState.value.data  
            if (data != null) {  
                val occupied = (data.dungeon + data.clanBattle + data.friend)  
                    .firstOrNull { it.unitId == unitId &&  
                            !(it.position == position) }  
                if (occupied != null) {  
                    val occupiedType = if (occupied in data.friend) 2 else 1  
                    engine.changeSupport(c, occupiedType, occupied.position, unitId, action = 2)  
                }  
            }  
            engine.changeSupport(c, supportType, position, unitId, action = 1)  
            "已更换支援角色"  
        }  
    }  
  
    // isCb: false=普通EX穿戴界面, true=会战EX穿戴界面  
    fun equipEx(unitId: Long, slots: List<Pair<Int, Long>>, isCb: Boolean) {  
        val c = client ?: return  
        runTask {  
            engine.equipEx(c, unitId, slots, isCb)  
            "EX装备已更新"  
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