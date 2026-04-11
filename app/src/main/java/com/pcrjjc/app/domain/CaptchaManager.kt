package com.pcrjjc.app.domain  
  
import kotlinx.coroutines.flow.MutableStateFlow  
import kotlinx.coroutines.flow.StateFlow  
import kotlinx.coroutines.flow.asStateFlow  
import javax.inject.Inject  
import javax.inject.Singleton  
  
data class CaptchaRequest(  
    val gt: String,  
    val challenge: String,  
    val gtUserId: String,  
    val accountId: Int,  
    val account: String,  
    val password: String,  
    val platform: Int  
)  
  
@Singleton  
class CaptchaManager @Inject constructor() {  
    private val _pendingCaptcha = MutableStateFlow<CaptchaRequest?>(null)  
    val pendingCaptcha: StateFlow<CaptchaRequest?> = _pendingCaptcha.asStateFlow()  
  
    fun requestCaptcha(request: CaptchaRequest) {  
        _pendingCaptcha.value = request  
    }  
  
    fun clearCaptcha() {  
        _pendingCaptcha.value = null  
    }  
}