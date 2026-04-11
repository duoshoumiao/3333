package com.pcrjjc.app  
  
import android.Manifest  
import android.content.Intent  
import android.content.pm.PackageManager  
import android.net.Uri  
import android.os.Build  
import android.os.Bundle  
import android.widget.Toast  
import androidx.activity.ComponentActivity  
import androidx.activity.compose.setContent  
import androidx.activity.result.contract.ActivityResultContracts  
import androidx.compose.foundation.layout.Column  
import androidx.compose.foundation.layout.Spacer  
import androidx.compose.foundation.layout.fillMaxSize  
import androidx.compose.foundation.layout.fillMaxWidth  
import androidx.compose.foundation.layout.height  
import androidx.compose.foundation.layout.size  
import androidx.compose.material3.AlertDialog  
import androidx.compose.material3.Button  
import androidx.compose.material3.CircularProgressIndicator  
import androidx.compose.material3.MaterialTheme  
import androidx.compose.material3.OutlinedTextField  
import androidx.compose.material3.Surface  
import androidx.compose.material3.Text  
import androidx.compose.material3.TextButton  
import androidx.compose.runtime.Composable  
import androidx.compose.runtime.collectAsState  
import androidx.compose.runtime.getValue  
import androidx.compose.runtime.mutableStateOf  
import androidx.compose.runtime.remember  
import androidx.compose.runtime.rememberCoroutineScope  
import androidx.compose.runtime.setValue  
import androidx.compose.ui.Alignment  
import androidx.compose.ui.Modifier  
import androidx.compose.ui.platform.LocalContext  
import androidx.compose.ui.unit.dp  
import androidx.core.content.ContextCompat  
import com.pcrjjc.app.domain.CaptchaManager  
import com.pcrjjc.app.domain.CaptchaRequest  
import com.pcrjjc.app.domain.ClientManager  
import com.pcrjjc.app.ui.navigation.PcrJjcNavHost  
import com.pcrjjc.app.ui.theme.PcrJjcTheme  
import dagger.hilt.android.AndroidEntryPoint  
import kotlinx.coroutines.launch  
import javax.inject.Inject  
  
@AndroidEntryPoint  
class MainActivity : ComponentActivity() {  
  
    @Inject  
    lateinit var clientManager: ClientManager  
  
    @Inject  
    lateinit var captchaManager: CaptchaManager  
  
    private val requestPermissionLauncher =  
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _: Boolean ->  
        }  
  
    override fun onCreate(savedInstanceState: Bundle?) {  
        super.onCreate(savedInstanceState)  
        requestNotificationPermission()  
        parseCaptchaFromIntent(intent)  
  
        setContent {  
            PcrJjcTheme {  
                Surface(  
                    modifier = Modifier.fillMaxSize(),  
                    color = MaterialTheme.colorScheme.background  
                ) {  
                    PcrJjcNavHost()  
  
                    // 手动过码对话框覆盖层（全局）  
                    val captchaRequest by captchaManager.pendingCaptcha.collectAsState()  
                    captchaRequest?.let { data ->  
                        ManualCaptchaDialog(  
                            captchaRequest = data,  
                            clientManager = clientManager,  
                            onDismiss = { captchaManager.clearCaptcha() },  
                            onSuccess = {  
                                captchaManager.clearCaptcha()  
                                Toast.makeText(  
                                    this@MainActivity,  
                                    "手动过码成功，登录完成",  
                                    Toast.LENGTH_SHORT  
                                ).show()  
                            }  
                        )  
                    }  
                }  
            }  
        }  
    }  
  
    override fun onNewIntent(intent: Intent) {  
        super.onNewIntent(intent)  
        setIntent(intent)  
        parseCaptchaFromIntent(intent)  
    }  
  
    private fun parseCaptchaFromIntent(intent: Intent?) {  
        val gt = intent?.getStringExtra("captcha_gt") ?: return  
        val challenge = intent.getStringExtra("captcha_challenge") ?: return  
        val gtUserId = intent.getStringExtra("captcha_gt_user_id") ?: return  
        val accountId = intent.getIntExtra("captcha_account_id", -1)  
        val account = intent.getStringExtra("captcha_account") ?: return  
        val password = intent.getStringExtra("captcha_password") ?: return  
        val platform = intent.getIntExtra("captcha_platform", 0)  
  
        captchaManager.requestCaptcha(  
            CaptchaRequest(  
                gt = gt,  
                challenge = challenge,  
                gtUserId = gtUserId,  
                accountId = accountId,  
                account = account,  
                password = password,  
                platform = platform  
            )  
        )  
  
        // 清除 intent extras 防止重复触发  
        intent.removeExtra("captcha_gt")  
    }  
  
    private fun requestNotificationPermission() {  
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {  
            if (ContextCompat.checkSelfPermission(  
                    this,  
                    Manifest.permission.POST_NOTIFICATIONS  
                ) != PackageManager.PERMISSION_GRANTED  
            ) {  
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)  
            }  
        }  
    }  
}  
  
@Composable  
private fun ManualCaptchaDialog(  
    captchaRequest: CaptchaRequest,  
    clientManager: ClientManager,  
    onDismiss: () -> Unit,  
    onSuccess: () -> Unit  
) {  
    var validate by remember { mutableStateOf("") }  
    var isSubmitting by remember { mutableStateOf(false) }  
    var errorMessage by remember { mutableStateOf<String?>(null) }  
    val context = LocalContext.current  
    val scope = rememberCoroutineScope()  
  
    val geetestUrl = "https://help.tencentbot.top/geetest/" +  
            "?captcha_type=1" +  
            "&challenge=${captchaRequest.challenge}" +  
            "&gt=${captchaRequest.gt}" +  
            "&userid=${captchaRequest.gtUserId}" +  
            "&gs=1"  
  
    AlertDialog(  
        onDismissRequest = { if (!isSubmitting) onDismiss() },  
        title = { Text("手动验证码") },  
        text = {  
            Column(modifier = Modifier.fillMaxWidth()) {  
                Text(  
                    "账号 ${captchaRequest.account} 登录触发验证码，自动过码失败。",  
                    style = MaterialTheme.typography.bodyMedium  
                )  
                Spacer(modifier = Modifier.height(8.dp))  
                Text(  
                    "1. 点击下方按钮打开验证页面\n" +  
                            "2. 完成验证后复制第1个方框中的内容\n" +  
                            "3. 粘贴到下方输入框并点击提交",  
                    style = MaterialTheme.typography.bodySmall  
                )  
  
                Spacer(modifier = Modifier.height(12.dp))  
  
                Button(  
                    onClick = {  
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(geetestUrl))  
                        context.startActivity(intent)  
                    },  
                    modifier = Modifier.fillMaxWidth(),  
                    enabled = !isSubmitting  
                ) {  
                    Text("打开验证页面")  
                }  
  
                Spacer(modifier = Modifier.height(12.dp))  
  
                OutlinedTextField(  
                    value = validate,  
                    onValueChange = {  
                        validate = it  
                        errorMessage = null  
                    },  
                    label = { Text("验证结果 (validate)") },  
                    enabled = !isSubmitting,  
                    singleLine = true,  
                    modifier = Modifier.fillMaxWidth()  
                )  
  
                if (errorMessage != null) {  
                    Spacer(modifier = Modifier.height(4.dp))  
                    Text(  
                        text = errorMessage!!,  
                        color = MaterialTheme.colorScheme.error,  
                        style = MaterialTheme.typography.bodySmall  
                    )  
                }  
  
                if (isSubmitting) {  
                    Spacer(modifier = Modifier.height(8.dp))  
                    CircularProgressIndicator(  
                        modifier = Modifier  
                            .size(24.dp)  
                            .align(Alignment.CenterHorizontally)  
                    )  
                }  
            }  
        },  
        confirmButton = {  
            Button(  
                onClick = {  
                    if (validate.isBlank()) {  
                        errorMessage = "请输入验证结果"  
                        return@Button  
                    }  
                    scope.launch {  
                        isSubmitting = true  
                        errorMessage = null  
                        try {  
                            clientManager.loginWithCaptchaResult(  
                                accountId = captchaRequest.accountId,  
                                account = captchaRequest.account,  
                                password = captchaRequest.password,  
                                platform = captchaRequest.platform,  
                                challenge = captchaRequest.challenge,  
                                gtUserId = captchaRequest.gtUserId,  
                                validate = validate  
                            )  
                            onSuccess()  
                        } catch (e: Exception) {  
                            isSubmitting = false  
                            errorMessage = "登录失败: ${e.message}"  
                        }  
                    }  
                },  
                enabled = validate.isNotBlank() && !isSubmitting  
            ) {  
                Text("提交")  
            }  
        },  
        dismissButton = {  
            TextButton(  
                onClick = onDismiss,  
                enabled = !isSubmitting  
            ) {  
                Text("取消")  
            }  
        }  
    )  
}