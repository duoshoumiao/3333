package com.pcrjjc.app.service  
  
import android.Manifest  
import android.app.Notification  
import android.app.NotificationManager  
import android.app.PendingIntent  
import android.app.Service  
import android.content.Intent  
import android.content.pm.PackageManager  
import android.content.pm.ServiceInfo  
import android.os.Build  
import android.os.IBinder  
import android.util.Log  
import androidx.core.app.NotificationCompat  
import androidx.core.app.ServiceCompat  
import androidx.core.content.ContextCompat  
import com.pcrjjc.app.MainActivity  
import com.pcrjjc.app.PcrJjcApp  
import com.pcrjjc.app.R  
import com.pcrjjc.app.data.local.dao.AccountDao  
import com.pcrjjc.app.data.local.dao.BindDao  
import com.pcrjjc.app.data.local.dao.HistoryDao  
import com.pcrjjc.app.data.local.dao.RankCacheDao  
import com.pcrjjc.app.data.local.entity.Account  
import com.pcrjjc.app.data.remote.CaptchaRequiredException  
import com.pcrjjc.app.domain.CaptchaManager  
import com.pcrjjc.app.domain.CaptchaRequest  
import com.pcrjjc.app.domain.ClientManager  
import com.pcrjjc.app.domain.QueryEngine  
import com.pcrjjc.app.domain.RankMonitor  
import dagger.hilt.android.AndroidEntryPoint  
import kotlinx.coroutines.CoroutineScope  
import kotlinx.coroutines.Dispatchers  
import kotlinx.coroutines.Job  
import kotlinx.coroutines.SupervisorJob  
import kotlinx.coroutines.async  
import kotlinx.coroutines.awaitAll  
import kotlinx.coroutines.cancel  
import kotlinx.coroutines.coroutineScope  
import kotlinx.coroutines.delay  
import kotlinx.coroutines.isActive  
import kotlinx.coroutines.launch  
import javax.inject.Inject  
  
@AndroidEntryPoint  
class RankMonitorService : Service() {  
  
    companion object {  
        const val NOTIFICATION_ID = 1  
        const val CAPTCHA_NOTIFICATION_ID_BASE = 1000  
        const val EXTRA_INTERVAL_SECONDS = "extra_interval_seconds"  
        private const val TAG = "RankMonitorService"  
    }  
  
    @Inject lateinit var accountDao: AccountDao  
    @Inject lateinit var bindDao: BindDao  
    @Inject lateinit var historyDao: HistoryDao  
    @Inject lateinit var rankCacheDao: RankCacheDao  
    @Inject lateinit var clientManager: ClientManager  
    @Inject lateinit var captchaManager: CaptchaManager
	@Inject lateinit var settingsDataStore: com.pcrjjc.app.data.local.SettingsDataStore	
  
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)  
    private var pollingJob: Job? = null  
  
    override fun onBind(intent: Intent?): IBinder? = null  
  
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {  
        val intervalSeconds = intent?.getLongExtra(EXTRA_INTERVAL_SECONDS, 30) ?: 30  
  
        // Check notification permission on Android 13+  
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {  
            if (ContextCompat.checkSelfPermission(  
                    this, Manifest.permission.POST_NOTIFICATIONS  
                ) != PackageManager.PERMISSION_GRANTED  
            ) {  
                Log.w(TAG, "通知权限未授予，停止前台服务")  
                stopSelf()  
                return START_NOT_STICKY  
            }  
        }  
  
        // Start foreground  
        val notification = createNotification(intervalSeconds)  
        try {  
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {  
                ServiceCompat.startForeground(  
                    this, NOTIFICATION_ID, notification,  
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC  
                )  
            } else {  
                startForeground(NOTIFICATION_ID, notification)  
            }  
        } catch (e: Exception) {  
            Log.e(TAG, "启动前台服务失败", e)  
            stopSelf()  
            return START_NOT_STICKY  
        }  
  
        val oldJob = pollingJob  
        pollingJob = serviceScope.launch {  
            oldJob?.cancel()  
            oldJob?.join()  
  
            Log.i(TAG, "开始轮询，间隔 ${intervalSeconds} 秒")  
            val queryEngine = QueryEngine()  
            val rankMonitor = RankMonitor(this@RankMonitorService, historyDao, bindDao, rankCacheDao, settingsDataStore)  
  
            rankMonitor.initCacheFromDb()  
  
            while (isActive) {  
                try {  
                    val accounts = accountDao.getNonMasterAccountsSync()  
                    if (accounts.isEmpty()) {  
                        Log.w(TAG, "No accounts configured, waiting...")  
                    } else {  
coroutineScope {
                            accounts.map { account ->
                                async {
                                    try {
                                        val binds = bindDao.getBindsByPlatformSync(account.platform)
                                        if (binds.isEmpty()) return@async

                                        val client = clientManager.getClient(account)

                                        // 全部查询完后再统一推送
                                        val results = queryEngine.queryAll(binds, client)
                                        rankMonitor.processResults(results)
                                    } catch (e: CaptchaRequiredException) {
                                        Log.w(TAG, "Captcha required for account ${account.id}, sending notification")  
                                        sendCaptchaNotification(account, e)  
                                        // 同时通过全局 CaptchaManager 触发弹窗（如果 app 在前台则立即弹出）  
                                        captchaManager.requestCaptcha(  
                                            CaptchaRequest(  
                                                gt = e.gt,  
                                                challenge = e.challenge,  
                                                gtUserId = e.gtUserId,  
                                                accountId = account.id,  
                                                account = account.account,  
                                                password = account.password,  
                                                platform = account.platform  
                                            )  
                                        )  
                                    } catch (e: Exception) {  
                                        Log.e(TAG, "Error querying platform ${account.platform}: ${e.message}", e)  
                                        clientManager.clearClient(account.id)  
                                    }  
                                }  
                            }.awaitAll()  
                        }  
                        rankMonitor.flushHistories()  
                    }  
                } catch (e: Exception) {  
                    Log.e(TAG, "Polling cycle failed: ${e.message}", e)  
                }  
  
                delay(intervalSeconds * 1000)  
            }  
        }  
  
        return START_STICKY  
    }  
  
    override fun onDestroy() {  
        super.onDestroy()  
        pollingJob?.cancel()  
        serviceScope.cancel()  
        Log.i(TAG, "Service destroyed, polling stopped")  
    }  
  
    private fun createNotification(intervalSeconds: Long): Notification {  
        return NotificationCompat.Builder(this, PcrJjcApp.SERVICE_CHANNEL_ID)  
            .setSmallIcon(R.drawable.ic_notification)  
            .setContentTitle("竞技场监控")  
            .setContentText("正在监控排名变动，间隔 $intervalSeconds 秒")  
            .setPriority(NotificationCompat.PRIORITY_LOW)  
            .setOngoing(true)  
            .build()  
    }  
  
    private fun sendCaptchaNotification(account: Account, e: CaptchaRequiredException) {  
        val intent = Intent(this, MainActivity::class.java).apply {  
            putExtra("captcha_gt", e.gt)  
            putExtra("captcha_challenge", e.challenge)  
            putExtra("captcha_gt_user_id", e.gtUserId)  
            putExtra("captcha_account_id", account.id)  
            putExtra("captcha_account", account.account)  
            putExtra("captcha_password", account.password)  
            putExtra("captcha_platform", account.platform)  
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP  
        }  
  
        val pendingIntent = PendingIntent.getActivity(  
            this,  
            account.id,  
            intent,  
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE  
        )  
  
        val notification = NotificationCompat.Builder(this, PcrJjcApp.CAPTCHA_CHANNEL_ID)  
            .setSmallIcon(R.drawable.ic_notification)  
            .setContentTitle("需要手动验证")  
            .setContentText("账号 ${account.account} 登录需要验证码，点击进行手动验证")  
            .setContentIntent(pendingIntent)  
            .setAutoCancel(true)  
            .setPriority(NotificationCompat.PRIORITY_HIGH)  
            .build()  
  
        val notificationManager = getSystemService(NotificationManager::class.java)  
        notificationManager.notify(CAPTCHA_NOTIFICATION_ID_BASE + account.id, notification)  
    }  
}