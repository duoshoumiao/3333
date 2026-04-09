package com.pcrjjc.app.ui.query  
  
import android.util.Log  
import androidx.lifecycle.SavedStateHandle  
import androidx.lifecycle.ViewModel  
import androidx.lifecycle.viewModelScope  
import com.pcrjjc.app.data.local.dao.AccountDao  
import com.pcrjjc.app.data.local.dao.BindDao  
import com.pcrjjc.app.data.local.dao.HistoryDao  
import com.pcrjjc.app.data.local.entity.PcrBind  
import com.pcrjjc.app.domain.ClientManager  
import com.pcrjjc.app.domain.QueryEngine  
import dagger.hilt.android.lifecycle.HiltViewModel  
import kotlinx.coroutines.Dispatchers  
import kotlinx.coroutines.flow.MutableStateFlow  
import kotlinx.coroutines.flow.StateFlow  
import kotlinx.coroutines.launch  
import javax.inject.Inject  
  
data class QueryUiState(  
    val bind: PcrBind? = null,  
    val userName: String = "",  
    val arenaRank: Int = 0,  
    val arenaGroup: Int = 0,  
    val grandArenaRank: Int = 0,  
    val grandArenaGroup: Int = 0,  
    val lastLoginTime: Long = 0,  
    val teamLevel: Int = 0,  
    val totalPower: Int = 0,  
    val isLoading: Boolean = false,  
    val errorMessage: String? = null,  
    val isQueried: Boolean = false  
)  
  
@HiltViewModel  
class QueryViewModel @Inject constructor(  
    savedStateHandle: SavedStateHandle,  
    private val bindDao: BindDao,  
    private val accountDao: AccountDao,  
    private val historyDao: HistoryDao,  
    private val clientManager: ClientManager  
) : ViewModel() {  
  
    companion object {  
        private const val TAG = "QueryViewModel"  
    }  
  
    private val bindId: Int = savedStateHandle["bindId"] ?: 0  
    private val _uiState = MutableStateFlow(QueryUiState())  
    val uiState: StateFlow<QueryUiState> = _uiState  
  
    init {  
        loadBind()  
    }  
  
    private fun loadBind() {  
        viewModelScope.launch {  
            try {  
                val bind = bindDao.getBindById(bindId)  
                _uiState.value = _uiState.value.copy(bind = bind)  
                if (bind != null) {  
                    query(bind)  
                }  
            } catch (e: Throwable) {  
                _uiState.value = _uiState.value.copy(  
                    errorMessage = "加载绑定失败: ${e.message}"  
                )  
                Log.e(TAG, "loadBind failed", e)  
            }  
        }  
    }  
  
    @Suppress("UNCHECKED_CAST")  
    private fun query(bind: PcrBind) {  
        viewModelScope.launch(Dispatchers.IO) {  
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)  
  
            try {  
                val accounts = accountDao.getAccountsByPlatform(bind.platform)  
                if (accounts.isEmpty()) {  
                    _uiState.value = _uiState.value.copy(  
                        isLoading = false,  
                        errorMessage = "未配置查询账号，请先在账号管理中添加"  
                    )  
                    return@launch  
                }  
  
                val account = accounts.first()  
                val queryEngine = QueryEngine()  
  
                val client = clientManager.getClient(account)  
  
                val result = queryEngine.queryProfile(client, bind, clientManager, account)  
                if (result != null) {  
                    val info = result.userInfo  
                    _uiState.value = _uiState.value.copy(  
                        isLoading = false,  
                        isQueried = true,  
                        userName = info["user_name"]?.toString() ?: "",  
                        arenaRank = (info["arena_rank"] as? Number)?.toInt() ?: 0,  
                        arenaGroup = (info["arena_group"] as? Number)?.toInt() ?: 0,  
                        grandArenaRank = (info["grand_arena_rank"] as? Number)?.toInt() ?: 0,  
                        grandArenaGroup = (info["grand_arena_group"] as? Number)?.toInt() ?: 0,  
                        lastLoginTime = (info["last_login_time"] as? Number)?.toLong() ?: 0,  
                        teamLevel = (info["team_level"] as? Number)?.toInt() ?: 0,  
                        totalPower = (info["total_power"] as? Number)?.toInt() ?: 0  
                    )  
                } else {  
                    _uiState.value = _uiState.value.copy(  
                        isLoading = false,  
                        errorMessage = "查询失败"  
                    )  
                }  
            } catch (e: Throwable) {  
                Log.e(TAG, "Query failed", e)  
                try {  
                    val accounts = accountDao.getAccountsByPlatform(bind.platform)  
                    if (accounts.isNotEmpty()) {  
                        clientManager.clearClient(accounts.first().id)  
                    }  
                } catch (_: Exception) {}  
  
                _uiState.value = _uiState.value.copy(  
                    isLoading = false,  
                    errorMessage = "查询出错: ${e.message}"  
                )  
            }  
        }  
    }  
  
    fun retry() {  
        val bind = _uiState.value.bind ?: return  
        query(bind)  
    }  
}