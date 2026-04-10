package com.pcrjjc.app.ui.account  
  
import androidx.lifecycle.ViewModel  
import androidx.lifecycle.viewModelScope  
import com.pcrjjc.app.data.local.dao.AccountDao  
import com.pcrjjc.app.data.local.entity.Account  
import com.pcrjjc.app.data.remote.CaptchaRequiredException  
import com.pcrjjc.app.domain.ClientManager  
import com.pcrjjc.app.util.Platform  
import dagger.hilt.android.lifecycle.HiltViewModel  
import kotlinx.coroutines.flow.MutableStateFlow  
import kotlinx.coroutines.flow.SharingStarted  
import kotlinx.coroutines.flow.StateFlow  
import kotlinx.coroutines.flow.asStateFlow  
import kotlinx.coroutines.flow.stateIn  
import kotlinx.coroutines.launch  
import javax.inject.Inject  
  
data class AddAccountState(  
    val account: String = "",  
    val password: String = "",  
    val viewerId: String = "",  
    val selectedPlatform: Platform = Platform.B_SERVER,  
    val isLoading: Boolean = false,  
    val errorMessage: String? = null,  
    val isSuccess: Boolean = false  
)  
  
data class CaptchaState(  
    val isRequired: Boolean = false,  
    val gt: String = "",  
    val challenge: String = "",  
    val gtUserId: String = "",  
    val accountId: Int = -1,  
    val account: String = "",  
    val password: String = "",  
    val platform: Int = 0,  
    val isSubmitting: Boolean = false,  
    val submitError: String? = null  
)  
  
@HiltViewModel  
class AccountViewModel @Inject constructor(  
    private val accountDao: AccountDao,  
    private val clientManager: ClientManager  
) : ViewModel() {  
  
    val accounts: StateFlow<List<Account>> = accountDao.getNonMasterAccounts()  
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())  
  
    private val _addState = MutableStateFlow(AddAccountState())  
    val addState: StateFlow<AddAccountState> = _addState  
  
    private val _captchaState = MutableStateFlow(CaptchaState())  
    val captchaState: StateFlow<CaptchaState> = _captchaState.asStateFlow()  
  
    fun updateAccount(value: String) {  
        _addState.value = _addState.value.copy(account = value, errorMessage = null)  
    }  
  
    fun updatePassword(value: String) {  
        _addState.value = _addState.value.copy(password = value, errorMessage = null)  
    }  
  
    fun updateViewerId(value: String) {  
        _addState.value = _addState.value.copy(viewerId = value, errorMessage = null)  
    }  
  
    fun updatePlatform(platform: Platform) {  
        _addState.value = _addState.value.copy(selectedPlatform = platform, errorMessage = null)  
    }  
  
    fun addAccount() {  
        val state = _addState.value  
  
        if (state.account.isBlank()) {  
            _addState.value = state.copy(errorMessage = "请输入账号")  
            return  
        }  
        if (state.password.isBlank()) {  
            _addState.value = state.copy(errorMessage = "请输入密码")  
            return  
        }  
  
        viewModelScope.launch {  
            _addState.value = state.copy(isLoading = true)  
  
            val account = Account(  
                viewerId = state.viewerId.ifBlank { "" },  
                account = state.account,  
                password = state.password,  
                platform = state.selectedPlatform.id  
            )  
            accountDao.insert(account)  
  
            _addState.value = AddAccountState(isSuccess = true)  
        }  
    }  
  
    fun deleteAccount(account: Account) {  
        viewModelScope.launch {  
            accountDao.deleteById(account.id)  
        }  
    }  
  
    fun resetAddState() {  
        _addState.value = AddAccountState()  
    }  
  
    // ==================== 手动过码相关 ====================  
  
    /**  
     * 由外部（如 MainActivity 处理通知 Intent 时）调用，设置需要手动过码的状态  
     */  
    fun setCaptchaRequired(  
        gt: String,  
        challenge: String,  
        gtUserId: String,  
        accountId: Int,  
        account: String,  
        password: String,  
        platform: Int  
    ) {  
        _captchaState.value = CaptchaState(  
            isRequired = true,  
            gt = gt,  
            challenge = challenge,  
            gtUserId = gtUserId,  
            accountId = accountId,  
            account = account,  
            password = password,  
            platform = platform  
        )  
    }  
  
    /**  
     * 用户在手动过码对话框中提交 validate 结果  
     */  
    fun submitCaptchaResult(validate: String) {  
        val state = _captchaState.value  
        if (!state.isRequired || validate.isBlank()) return  
  
        viewModelScope.launch {  
            _captchaState.value = state.copy(isSubmitting = true, submitError = null)  
            try {  
                clientManager.loginWithCaptchaResult(  
                    accountId = state.accountId,  
                    account = state.account,  
                    password = state.password,  
                    platform = state.platform,  
                    challenge = state.challenge,  
                    gtUserId = state.gtUserId,  
                    validate = validate  
                )  
                // 成功，清除验证码状态  
                _captchaState.value = CaptchaState()  
            } catch (e: Exception) {  
                _captchaState.value = state.copy(  
                    isSubmitting = false,  
                    submitError = "手动过码后登录失败: ${e.message}"  
                )  
            }  
        }  
    }  
  
    /**  
     * 用户取消手动过码  
     */  
    fun dismissCaptcha() {  
        _captchaState.value = CaptchaState()  
    }  
  
    /**  
     * 测试登录（可选功能），触发登录流程，若遇到验证码则进入手动过码  
     */  
    fun testLogin(account: Account) {  
        viewModelScope.launch {  
            try {  
                clientManager.getClient(account, forceRelogin = true)  
                // 登录成功  
            } catch (e: CaptchaRequiredException) {  
                _captchaState.value = CaptchaState(  
                    isRequired = true,  
                    gt = e.gt,  
                    challenge = e.challenge,  
                    gtUserId = e.gtUserId,  
                    accountId = account.id,  
                    account = account.account,  
                    password = account.password,  
                    platform = account.platform  
                )  
            } catch (e: Exception) {  
                // 其他登录错误  
            }  
        }  
    }  
}