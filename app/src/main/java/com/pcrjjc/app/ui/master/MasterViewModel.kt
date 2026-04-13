package com.pcrjjc.app.ui.master  
  
import android.util.Log  
import androidx.lifecycle.ViewModel  
import androidx.lifecycle.viewModelScope  
import com.pcrjjc.app.data.local.dao.AccountDao  
import com.pcrjjc.app.data.local.dao.BindDao  
import com.pcrjjc.app.data.local.entity.Account  
import com.pcrjjc.app.data.local.entity.PcrBind  
import com.pcrjjc.app.domain.ClientManager  
import com.pcrjjc.app.domain.QueryEngine  
import com.pcrjjc.app.util.Platform  
import dagger.hilt.android.lifecycle.HiltViewModel  
import kotlinx.coroutines.Dispatchers  
import kotlinx.coroutines.flow.MutableStateFlow  
import kotlinx.coroutines.flow.SharingStarted  
import kotlinx.coroutines.flow.StateFlow  
import kotlinx.coroutines.flow.stateIn  
import kotlinx.coroutines.launch  
import kotlinx.coroutines.withContext  
import javax.inject.Inject  
  
enum class ArenaType(val displayName: String) {  
    JJC("JJC透视"),  
    PJJC("PJJC透视")  
}  
  
data class MasterUiState(  
    // 分场显示  
    val jjcPlayers: List<QueryEngine.ArenaRankingPlayer> = emptyList(),  
    val pjjcPlayers: List<QueryEngine.ArenaRankingPlayer> = emptyList(),  
    val isLoading: Boolean = false,  
    val errorMessage: String? = null,  
    val selectedType: ArenaType = ArenaType.JJC,  
    val selectedPlatform: Platform = Platform.B_SERVER,  
    val boundJjcPcrIds: Set<Long> = emptySet(),  
	val boundPjjcPcrIds: Set<Long> = emptySet(),  
    val bindingId: Long? = null,  
    val bindSuccessIds: Set<Long> = emptySet(),  
    // 一键全绑定  
    val isBindingAll: Boolean = false,  
    // 添加账号相关  
    val addAccount: String = "",  
    val addPassword: String = "",  
    val addViewerId: String = "",  
    val isAddingAccount: Boolean = false,  
    val addError: String? = null  
)  
  
@HiltViewModel  
class MasterViewModel @Inject constructor(  
    private val accountDao: AccountDao,  
    private val bindDao: BindDao,  
    private val clientManager: ClientManager  
) : ViewModel() {  
  
    companion object {  
        private const val TAG = "MasterViewModel"  
    }  
  
    private val queryEngine = QueryEngine()  
    private val _uiState = MutableStateFlow(MasterUiState())  
    val uiState: StateFlow<MasterUiState> = _uiState  
  
    /** 账号列表（实时 Flow） */  
    val masterAccounts: StateFlow<List<Account>> = accountDao.getMasterAccounts()  
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())  
  
    init {  
        loadBoundIds()  
    }  
  
    private fun loadBoundIds() {  
		viewModelScope.launch {  
			val allBinds = bindDao.getAllBindsSync()  
			val jjcIds = allBinds.filter { it.arenaType == 1 }.map { it.pcrid }.toSet()  
			val pjjcIds = allBinds.filter { it.arenaType == 2 }.map { it.pcrid }.toSet()  
			_uiState.value = _uiState.value.copy(boundJjcPcrIds = jjcIds, boundPjjcPcrIds = pjjcIds)  
		}  
	}  
  
    // ==================== 账号管理 ====================  
  
    fun updateAddAccount(value: String) {  
        _uiState.value = _uiState.value.copy(addAccount = value, addError = null)  
    }  
  
    fun updateAddPassword(value: String) {  
        _uiState.value = _uiState.value.copy(addPassword = value, addError = null)  
    }  
  
    fun updateAddViewerId(value: String) {  
        _uiState.value = _uiState.value.copy(addViewerId = value, addError = null)  
    }  
  
    fun addMasterAccount() {  
        val state = _uiState.value  
        if (state.addAccount.isBlank()) {  
            _uiState.value = state.copy(addError = "请输入账号")  
            return  
        }  
        if (state.addPassword.isBlank()) {  
            _uiState.value = state.copy(addError = "请输入密码")  
            return  
        }  
  
        viewModelScope.launch {  
            _uiState.value = state.copy(isAddingAccount = true)  
  
            val account = Account(  
                viewerId = state.addViewerId.ifBlank { "" },  
                account = state.addAccount,  
                password = state.addPassword,  
                platform = state.selectedPlatform.id,  
                isMaster = true  
            )  
            accountDao.insert(account)  
  
            _uiState.value = _uiState.value.copy(  
                isAddingAccount = false,  
                addAccount = "",  
                addPassword = "",  
                addViewerId = "",  
                addError = null  
            )  
        }  
    }  
  
    fun deleteMasterAccount(account: Account) {  
        viewModelScope.launch {  
            accountDao.deleteById(account.id)  
        }  
    }  
  
    // ==================== 透视 & 绑定 ====================  
  
    fun updateType(type: ArenaType) {  
        _uiState.value = _uiState.value.copy(selectedType = type, errorMessage = null)  
    }  
  
    fun updatePlatform(platform: Platform) {  
        _uiState.value = _uiState.value.copy(  
            selectedPlatform = platform,  
            errorMessage = null,  
            jjcPlayers = emptyList(),  
            pjjcPlayers = emptyList()  
        )  
    }  
  
    fun queryRanking() {  
        val state = _uiState.value  
        viewModelScope.launch {  
            _uiState.value = state.copy(isLoading = true, errorMessage = null)  
  
            try {  
                val players = withContext(Dispatchers.IO) {  
                    val accounts = accountDao.getMasterAccountsByPlatform(state.selectedPlatform.id)  
                    if (accounts.isEmpty()) {  
                        throw IllegalStateException("没有${state.selectedPlatform.displayName}的账号，请先在上方添加")  
                    }  
  
                    val account = accounts.first()  
                    val client = clientManager.getClient(account)  
  
                    when (state.selectedType) {  
                        ArenaType.JJC -> queryEngine.queryArenaRanking(client)  
                        ArenaType.PJJC -> queryEngine.queryGrandArenaRanking(client)  
                    }  
                }  
  
                val allBinds = bindDao.getAllBindsSync()  
				val jjcIds = allBinds.filter { it.arenaType == 1 }.map { it.pcrid }.toSet()  
				val pjjcIds = allBinds.filter { it.arenaType == 2 }.map { it.pcrid }.toSet()  
				  
				_uiState.value = when (state.selectedType) {  
					ArenaType.JJC -> _uiState.value.copy(  
						isLoading = false,  
						jjcPlayers = players,  
						boundJjcPcrIds = jjcIds,  
						boundPjjcPcrIds = pjjcIds  
					)  
					ArenaType.PJJC -> _uiState.value.copy(  
						isLoading = false,  
						pjjcPlayers = players,  
						boundJjcPcrIds = jjcIds,  
						boundPjjcPcrIds = pjjcIds  
					)  
				} 
            } catch (e: Exception) {  
                Log.e(TAG, "Query ranking failed: ${e.message}", e)  
                _uiState.value = _uiState.value.copy(  
                    isLoading = false,  
                    errorMessage = "查询失败: ${e.message ?: e.javaClass.simpleName}"  
                )  
            }  
        }  
    }  
  
    fun bindPlayer(player: QueryEngine.ArenaRankingPlayer, fromType: ArenaType) {  
		val state = _uiState.value  
		viewModelScope.launch {  
			_uiState.value = state.copy(bindingId = player.viewerId)  
	  
			try {  
				val arenaTypeInt = when (fromType) {  
					ArenaType.JJC -> 1  
					ArenaType.PJJC -> 2  
				}  
				val existing = bindDao.getBindByPcridAndType(player.viewerId, state.selectedPlatform.id, arenaTypeInt)  
				if (existing != null) {  
					_uiState.value = _uiState.value.copy(  
						bindingId = null,  
						errorMessage = "${player.userName} 已经绑定过了"  
					)  
					return@launch  
				}  
	  
				val count = bindDao.getBindCount(state.selectedPlatform.id)  
				if (count >= 999) {  
					_uiState.value = _uiState.value.copy(  
						bindingId = null,  
						errorMessage = "绑定数量已达上限"  
					)  
					return@launch  
				}  
	  
				val bind = PcrBind(  
					pcrid = player.viewerId,  
					platform = state.selectedPlatform.id,  
					name = player.userName,  
					arenaType = arenaTypeInt  
				)  
				bindDao.insert(bind)  
	  
				val newSuccessIds = _uiState.value.bindSuccessIds + player.viewerId  
				when (fromType) {  
					ArenaType.JJC -> {  
						val newIds = _uiState.value.boundJjcPcrIds + player.viewerId  
						_uiState.value = _uiState.value.copy(  
							bindingId = null,  
							boundJjcPcrIds = newIds,  
							bindSuccessIds = newSuccessIds  
						)  
					}  
					ArenaType.PJJC -> {  
						val newIds = _uiState.value.boundPjjcPcrIds + player.viewerId  
						_uiState.value = _uiState.value.copy(  
							bindingId = null,  
							boundPjjcPcrIds = newIds,  
							bindSuccessIds = newSuccessIds  
						)  
					}  
				}  
			} catch (e: Exception) {  
				Log.e(TAG, "Bind failed: ${e.message}", e)  
				_uiState.value = _uiState.value.copy(  
					bindingId = null,  
					errorMessage = "绑定失败: ${e.message}"  
				)  
			}  
		}  
	}  
  
    // ==================== 一键全绑定 ====================  
  
    fun bindAllPlayers(type: ArenaType) {  
		val state = _uiState.value  
		val players = when (type) {  
			ArenaType.JJC -> state.jjcPlayers  
			ArenaType.PJJC -> state.pjjcPlayers  
		}  
		val currentBoundIds = when (type) {  
			ArenaType.JJC -> state.boundJjcPcrIds  
			ArenaType.PJJC -> state.boundPjjcPcrIds  
		}  
		val unboundPlayers = players.filter { !currentBoundIds.contains(it.viewerId) }  
		if (unboundPlayers.isEmpty()) return  
	  
		val arenaTypeInt = when (type) {  
			ArenaType.JJC -> 1  
			ArenaType.PJJC -> 2  
		}  
	  
		viewModelScope.launch {  
			_uiState.value = _uiState.value.copy(isBindingAll = true)  
			var successCount = 0  
			val newBoundIds = currentBoundIds.toMutableSet()  
			val newSuccessIds = _uiState.value.bindSuccessIds.toMutableSet()  
	  
			for (player in unboundPlayers) {  
				try {  
					val existing = bindDao.getBindByPcridAndType(player.viewerId, state.selectedPlatform.id, arenaTypeInt)  
					if (existing != null) {  
						newBoundIds.add(player.viewerId)  
						continue  
					}  
	  
					val count = bindDao.getBindCount(state.selectedPlatform.id)  
					if (count >= 999) {  
						_uiState.value = _uiState.value.copy(  
							errorMessage = "绑定数量已达上限，已成功绑定 $successCount 人"  
						)  
						break  
					}  
	  
					val bind = PcrBind(  
						pcrid = player.viewerId,  
						platform = state.selectedPlatform.id,  
						name = player.userName,  
						arenaType = arenaTypeInt  
					)  
					bindDao.insert(bind)  
					newBoundIds.add(player.viewerId)  
					newSuccessIds.add(player.viewerId)  
					successCount++  
				} catch (e: Exception) {  
					Log.e(TAG, "Bind all - failed for ${player.viewerId}: ${e.message}")  
				}  
			}  
	  
			_uiState.value = when (type) {  
				ArenaType.JJC -> _uiState.value.copy(  
					isBindingAll = false,  
					boundJjcPcrIds = newBoundIds,  
					bindSuccessIds = newSuccessIds,  
					errorMessage = if (successCount > 0) "成功绑定 $successCount 人" else null  
				)  
				ArenaType.PJJC -> _uiState.value.copy(  
					isBindingAll = false,  
					boundPjjcPcrIds = newBoundIds,  
					bindSuccessIds = newSuccessIds,  
					errorMessage = if (successCount > 0) "成功绑定 $successCount 人" else null  
				)  
			}  
		}  
	}  
  
    fun clearError() {  
        _uiState.value = _uiState.value.copy(errorMessage = null)  
    }  
}