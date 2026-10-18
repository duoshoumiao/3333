package com.pcrjjc.app.worker  
  
import android.content.Context  
import android.util.Log  
import androidx.hilt.work.HiltWorker  
import androidx.work.CoroutineWorker  
import androidx.work.WorkerParameters  
import com.pcrjjc.app.data.local.dao.AccountDao  
import com.pcrjjc.app.data.local.dao.BindDao  
import com.pcrjjc.app.data.local.dao.HistoryDao  
import com.pcrjjc.app.data.local.dao.RankCacheDao  
import com.pcrjjc.app.domain.ClientManager  
import com.pcrjjc.app.domain.QueryEngine  
import com.pcrjjc.app.domain.RankMonitor  
import com.pcrjjc.app.data.local.SettingsDataStore
import com.pcrjjc.app.data.remote.AppPushClient
import com.pcrjjc.app.data.remote.RemotePcrClient
import dagger.assisted.Assisted  
import dagger.assisted.AssistedInject  
  
@HiltWorker  
class RankCheckWorker @AssistedInject constructor(  
    @Assisted appContext: Context,  
    @Assisted workerParams: WorkerParameters,  
    private val accountDao: AccountDao,  
    private val bindDao: BindDao,  
    private val historyDao: HistoryDao,  
    private val rankCacheDao: RankCacheDao,  
    private val clientManager: ClientManager,  
    private val settingsDataStore: SettingsDataStore
) : CoroutineWorker(appContext, workerParams) {  
  
    companion object {  
        const val TAG = "RankCheckWorker"  
        const val WORK_NAME = "rank_check_work"  
    }  
  
    override suspend fun doWork(): Result {  
        Log.i(TAG, "Starting rank check...")  
  
        try {  
            val accounts = accountDao.getNonMasterAccountsSync()  
            val pcrServerUrl = settingsDataStore.getPcrServerUrl()  
  
            if (accounts.isEmpty() && pcrServerUrl.isNotBlank()) {  
                // ★ 无本地监控账号：用服务器 pcrjjc2 查询，本地做对比/推送/记录  
                val rankMonitor = RankMonitor(applicationContext, historyDao, bindDao, rankCacheDao, settingsDataStore)  
                rankMonitor.initCacheFromDb()  // Worker 每次新建实例，必须先从 DB 恢复基准  
                val pushClient = AppPushClient(pcrServerUrl)  
                val bindsByPlatform = bindDao.getAllBindsSync().groupBy { it.platform }  
                for ((platform, binds) in bindsByPlatform) {  
                    try {  
                        val results = pushClient.queryProfiles(  
                            platform, binds.map { it.pcrid }  
                        ).mapNotNull { res ->  
                            val info = res["user_info"] as? Map<String, Any?>  
                                ?: return@mapNotNull null  
                            val vid = (info["viewer_id"] as? Number)?.toLong()  
                            val bind = binds.firstOrNull { it.pcrid == vid }  
                                ?: return@mapNotNull null  
                            QueryEngine.QueryResult(bind, info, res)  
                        }  
                        rankMonitor.processResults(results)  
                    } catch (e: Exception) {  
                        Log.e(TAG, "Remote query failed for platform $platform: ${e.message}", e)  
                    }  
                }  
                rankMonitor.flushHistories()  
                return Result.success()  
            }
  
            if (accounts.isEmpty()) {  
                Log.w(TAG, "No accounts configured, skipping rank check")  
                if (settingsDataStore.shouldWarnNoConfig()) {  
                    RankMonitor(applicationContext, historyDao, bindDao, rankCacheDao, settingsDataStore)  
                        .notifySystem(  
                            "竞技场监控未启动：未配置监控账号，也未连接服务器。" +  
                            "请在【监控账号管理】添加账号，或在【设置】填写服务器地址。"  
                        )  
                }  
                return Result.success()  
            }
  
            val queryEngine = QueryEngine()  
            val rankMonitor = RankMonitor(applicationContext, historyDao, bindDao, rankCacheDao, settingsDataStore)  
  
            // ★ 修复：从数据库初始化缓存，避免 Worker 每次运行都丢失比较基准  
            rankMonitor.initCacheFromDb()  
  
            for (account in accounts) {  
                try {  
                    val binds = bindDao.getBindsByPlatformSync(account.platform)  
                    if (binds.isEmpty()) continue  
  
                    val client = clientManager.getClient(account)  
  
                    // 全部查询完后再统一推送
                    val results = queryEngine.queryAll(
                        binds, client,
                        clientManager = clientManager, account = account
                    )
                    rankMonitor.processResults(results)
                } catch (e: Exception) {  
                    Log.e(TAG, "Error querying platform ${account.platform}: ${e.message}", e)  
                    // 不清会话：会话失效由 ClientManager 自动重登处理  
                }  
            }
            
            // ★ 没有监控账号的平台，改用服务器 pcrjjc2 批量查询代替本地查询  
            if (pcrServerUrl.isNotBlank()) {  
                val accountPlatforms = accounts.map { it.platform }.toSet()  
                val remoteBinds = bindDao.getAllBindsSync()  
                    .filter { it.platform !in accountPlatforms }  
                    .groupBy { it.platform }  
                for ((platform, binds) in remoteBinds) {  
                    try {  
                        Log.i(TAG, "Platform $platform has no local account, querying via pcrjjc2 server")  
                        RemotePcrClient(pcrServerUrl).queryAll(binds) { result ->  
                            rankMonitor.processResult(result)  
                        }  
                    } catch (e: Exception) {  
                        Log.e(TAG, "Remote query failed for platform $platform: ${e.message}", e)  
                    }  
                }  
            }			
  
            rankMonitor.flushHistories()  
  
            Log.i(TAG, "Rank check completed")  
            return Result.success()  
        } catch (e: Exception) {  
            Log.e(TAG, "Rank check failed: ${e.message}", e)  
            return Result.retry()  
        }  
    }  
}