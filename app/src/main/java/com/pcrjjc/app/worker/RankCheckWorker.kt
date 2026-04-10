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
    private val clientManager: ClientManager  
) : CoroutineWorker(appContext, workerParams) {  
    companion object {  
        const val TAG = "RankCheckWorker"  
        const val WORK_NAME = "rank_check_work"  
    }  
    override suspend fun doWork(): Result {  
        Log.i(TAG, "Starting rank check...")  
        try {  
            val accounts = accountDao.getAllAccountsSync()  
            if (accounts.isEmpty()) {  
                Log.w(TAG, "No accounts configured, skipping rank check")  
                return Result.success()  
            }  
            val queryEngine = QueryEngine()  
            val rankMonitor = RankMonitor(applicationContext, historyDao, bindDao, rankCacheDao)  
            rankMonitor.loadCacheFromDb()  
            for (account in accounts) {  
                try {  
                    val binds = bindDao.getBindsByPlatformSync(account.platform)  
                    if (binds.isEmpty()) continue  
                    val client = clientManager.getClient(account)  
                    queryEngine.queryAll(binds, client) { result ->  
                        rankMonitor.processResult(result)  
                    }  
                } catch (e: Exception) {  
                    Log.e(TAG, "Error querying platform ${account.platform}: ${e.message}", e)  
                    clientManager.clearClient(account.id)  
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