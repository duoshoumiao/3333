package com.pcrjjc.app.ui.exequip  
  
import android.content.Context  
import android.util.Log  
import androidx.lifecycle.ViewModel  
import androidx.lifecycle.viewModelScope  
import com.pcrjjc.app.data.local.dao.AccountDao  
import com.pcrjjc.app.data.local.entity.Account  
import com.pcrjjc.app.data.remote.PcrClient  
import com.pcrjjc.app.domain.ClientManager  
import com.pcrjjc.app.domain.ExEquipEngine  
import dagger.hilt.android.lifecycle.HiltViewModel  
import dagger.hilt.android.qualifiers.ApplicationContext  
import kotlinx.coroutines.Dispatchers  
import kotlinx.coroutines.flow.MutableStateFlow  
import kotlinx.coroutines.flow.StateFlow  
import kotlinx.coroutines.flow.asStateFlow  
import kotlinx.coroutines.launch  
import kotlinx.coroutines.withContext  
import javax.inject.Inject  
  
data class ExEquipUiState(  
    val masterAccounts: List<Account> = emptyList(),  
    val selectedAccount: Account? = null,  
    val isLoading: Boolean = false,  
    val log: String = "",  
    val error: String? = null  
)  
  
@HiltViewModel  
class ExEquipViewModel @Inject constructor(  
    private val accountDao: AccountDao,  
    private val clientManager: ClientManager,  
    @ApplicationContext private val appContext: Context  
) : ViewModel() {  
  
    companion object {  
        private const val TAG = "ExEquipVM"  
    }  
  
    private val _uiState = MutableStateFlow(ExEquipUiState())  
    val uiState: StateFlow<ExEquipUiState> = _uiState.asStateFlow()  
  
    private val engine = ExEquipEngine(appContext)  
  
    init {  
        // 加载"我的账号"列表  
        viewModelScope.launch {  
            try {  
                val masters = accountDao.getMasterAccountsByPlatform(2) // B服=2  
                    .ifEmpty { accountDao.getAllAccountsSync().filter { it.isMaster } }  
                _uiState.value = _uiState.value.copy(  
                    masterAccounts = masters,  
                    selectedAccount = masters.firstOrNull()  
                )  
            } catch (e: Exception) {  
                Log.e(TAG, "Failed to load master accounts", e)  
            }  
        }  
    }  
  
    fun selectAccount(account: Account) {  
        _uiState.value = _uiState.value.copy(selectedAccount = account)  
    }  
  
    fun onSave() = runTask { client, account ->  
        engine.saveExState(client, account.id)  
    }  
  
    fun onRestore() = runTask { client, account ->  
        engine.restoreExState(client, account.id)  
    }  
  
    private fun runTask(block: suspend (PcrClient, Account) -> String) {  
        val account = _uiState.value.selectedAccount  
        if (account == null) {  
            _uiState.value = _uiState.value.copy(error = "请先选择账号")  
            return  
        }  
        if (_uiState.value.isLoading) return  
  
        viewModelScope.launch {  
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)  
            try {  
                val client = withContext(Dispatchers.IO) {  
                    clientManager.getClient(account)  
                }  
                if (client !is PcrClient) {  
                    _uiState.value = _uiState.value.copy(  
                        isLoading = false,  
                        error = "暂不支持该平台的账号"  
                    )  
                    return@launch  
                }  
                val msg = withContext(Dispatchers.IO) { block(client, account) }  
                _uiState.value = _uiState.value.copy(isLoading = false, log = msg)  
            } catch (e: Exception) {  
                Log.e(TAG, "task failed", e)  
                _uiState.value = _uiState.value.copy(  
                    isLoading = false,  
                    error = e.message ?: "执行失败"  
                )  
            }  
        }  
    }  
  
    fun clearError() {  
        _uiState.value = _uiState.value.copy(error = null)  
    }  
}