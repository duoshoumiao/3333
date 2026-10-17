package com.pcrjjc.app.ui.friend  
  
import android.util.Log  
import androidx.lifecycle.ViewModel  
import androidx.lifecycle.viewModelScope  
import com.pcrjjc.app.data.local.dao.AccountDao  
import com.pcrjjc.app.data.local.entity.Account  
import com.pcrjjc.app.data.remote.PcrClient  
import com.pcrjjc.app.data.remote.TwPcrClient  
import com.pcrjjc.app.domain.ClientManager  
import dagger.hilt.android.lifecycle.HiltViewModel  
import kotlinx.coroutines.Dispatchers  
import kotlinx.coroutines.flow.MutableStateFlow  
import kotlinx.coroutines.flow.StateFlow  
import kotlinx.coroutines.flow.asStateFlow  
import kotlinx.coroutines.launch  
import kotlinx.coroutines.withContext  
import javax.inject.Inject  
  
data class FriendUiState(  
    val masterAccounts: List<Account> = emptyList(),  
    val selectedAccount: Account? = null,  
    val targetUid: String = "",  
    val isLoading: Boolean = false,  
    val message: String? = null,    
    val error: String? = null,  
    // 好友管理  
    val friendList: List<Map<String, Any?>> = emptyList(),  
    val isLoadingFriends: Boolean = false  
)  
  
@HiltViewModel  
class FriendViewModel @Inject constructor(  
    private val accountDao: AccountDao,  
    private val clientManager: ClientManager  
) : ViewModel() {  
  
    companion object { private const val TAG = "FriendVM" }  
  
    private val _uiState = MutableStateFlow(FriendUiState())  
    val uiState: StateFlow<FriendUiState> = _uiState.asStateFlow()  
  
    init {  
        viewModelScope.launch {  
            try {  
                // 与 SupportViewModel 一致：优先 platform=2 的 master，兜底全部 master  
                val masters = accountDao.getMasterAccountsByPlatform(2)  
                    .ifEmpty { accountDao.getAllAccountsSync().filter { it.isMaster } }  
                _uiState.value = _uiState.value.copy(  
                    masterAccounts = masters,  
                    selectedAccount = masters.firstOrNull()  
                )  
            } catch (e: Exception) {  
                Log.e(TAG, "load accounts failed", e)  
            }  
        }  
    }  
  
    fun selectAccount(account: Account) {    
        _uiState.value = _uiState.value.copy(selectedAccount = account, error = null, message = null)    
        loadFriendList()  
    } 
  
    fun updateTargetUid(value: String) {  
        _uiState.value = _uiState.value.copy(targetUid = value, error = null, message = null)  
    }  
  
    /** 用选中的「我的账号」向 targetUid 发送好友申请 */  
    fun sendFriendRequest() {  
        val state = _uiState.value  
        val account = state.selectedAccount  
        if (account == null) {  
            _uiState.value = state.copy(error = "请先选择账号")  
            return  
        }  
        val uid = state.targetUid.trim()  
        val uidLong = uid.toLongOrNull()  
        if (uid.isEmpty() || !uid.all { it.isDigit() } || uidLong == null || uidLong <= 0L) {  
            _uiState.value = state.copy(error = "请输入正确的玩家UID")  
            return  
        }  
        if (state.isLoading) return  
  
        viewModelScope.launch {  
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, message = null)  
            try {  
                val client = withContext(Dispatchers.IO) {  
                    clientManager.getClient(account, forceRelogin = true)  
                }  
                withContext(Dispatchers.IO) {  
                    when (client) {  
                        is PcrClient -> client.sendFriendRequest(uidLong)  
                        is TwPcrClient -> client.sendFriendRequest(uidLong)  
                        else -> throw Exception("暂不支持该平台的账号")  
                    }  
                }  
                _uiState.value = _uiState.value.copy(  
                    isLoading = false,  
                    message = "已成功发送好友申请"  
                )
                loadFriendList()				
            } catch (e: Exception) {  
                Log.e(TAG, "sendFriendRequest failed", e)  
                val msg = e.message ?: ""  
                _uiState.value = _uiState.value.copy(  
                    isLoading = false,  
                    error = when {  
                        msg.contains("已经") || msg.contains("既に") -> "已经是你的好友"  
                        msg.contains("申请") || msg.contains("pending", true) -> "已发送过申请，请等待对方处理"  
                        msg.contains("上限") || msg.contains("limit", true) -> "好友数量已达上限"  
                        else -> "发送好友申请失败: ${msg.ifBlank { e.javaClass.simpleName }}"  
                    }  
                )  
            }  
        }  
    }  
  
    /** 拉取当前选中账号的好友列表 */  
    fun loadFriendList() {  
        val account = _uiState.value.selectedAccount ?: return  
        if (_uiState.value.isLoadingFriends) return  
        viewModelScope.launch {  
            _uiState.value = _uiState.value.copy(isLoadingFriends = true)  
            try {  
                val client = withContext(Dispatchers.IO) {  
                    clientManager.getClient(account, forceRelogin = false)  
                }  
                val list = withContext(Dispatchers.IO) {  
                    when (client) {  
                        is PcrClient -> client.getFriendList()  
                        is TwPcrClient -> client.getFriendList()  
                        else -> emptyList()  
                    }  
                }  
                _uiState.value = _uiState.value.copy(friendList = list, isLoadingFriends = false)  
            } catch (e: Exception) {  
                Log.e(TAG, "loadFriendList failed", e)  
                _uiState.value = _uiState.value.copy(  
                    isLoadingFriends = false,  
                    error = "获取好友列表失败: ${e.message ?: e.javaClass.simpleName}"  
                )  
            }  
        }  
    }  
  
    /** 用当前账号删除指定 UID 的好友（对应 PPPPPP friend/remove） */  
    fun removeFriend(viewerId: Long) {  
        val account = _uiState.value.selectedAccount ?: return  
        if (_uiState.value.isLoadingFriends) return  
        viewModelScope.launch {  
            _uiState.value = _uiState.value.copy(isLoadingFriends = true, error = null, message = null)  
            try {  
                val client = withContext(Dispatchers.IO) {  
                    clientManager.getClient(account, forceRelogin = false)  
                }  
                withContext(Dispatchers.IO) {  
                    when (client) {  
                        is PcrClient -> client.removeFriend(viewerId)  
                        is TwPcrClient -> client.removeFriend(viewerId)  
                        else -> throw Exception("暂不支持该平台的账号")  
                    }  
                }  
                _uiState.value = _uiState.value.copy(  
                    isLoadingFriends = false,  
                    message = "已删除好友 $viewerId",  
                    friendList = _uiState.value.friendList.filter {  
                        (it["viewer_id"] as? Number)?.toLong() != viewerId  
                    }  
                )  
            } catch (e: Exception) {  
                Log.e(TAG, "removeFriend failed", e)  
                _uiState.value = _uiState.value.copy(  
                    isLoadingFriends = false,  
                    error = "删除好友失败: ${e.message ?: e.javaClass.simpleName}"  
                )  
            }  
        }  
    }
	
	fun clearError() { _uiState.value = _uiState.value.copy(error = null) }  
    fun clearMessage() { _uiState.value = _uiState.value.copy(message = null) }  
}