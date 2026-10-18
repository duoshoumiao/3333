package com.pcrjjc.app.worker  
  
import android.content.Context  
import android.util.Log  
import androidx.hilt.work.HiltWorker  
import androidx.work.CoroutineWorker  
import androidx.work.ExistingPeriodicWorkPolicy  
import androidx.work.PeriodicWorkRequestBuilder  
import androidx.work.WorkManager  
import androidx.work.WorkerParameters  
import com.pcrjjc.app.domain.FortnightlyPushManager  
import dagger.assisted.Assisted  
import dagger.assisted.AssistedInject  
import java.util.concurrent.TimeUnit  
  
/**  
 * 半月刊新活动定时检查。  
 *  
 * WorkManager 周期任务最小间隔 15 分钟；配合「即将开始提前 30 分钟」的提醒窗口，  
 * 实际到点误差在 15 分钟以内，足够覆盖活动开始提醒。  
 */  
@HiltWorker  
class FortnightlyCheckWorker @AssistedInject constructor(  
    @Assisted appContext: Context,  
    @Assisted workerParams: WorkerParameters,  
    private val pushManager: FortnightlyPushManager  
) : CoroutineWorker(appContext, workerParams) {  
  
    companion object {  
        private const val TAG = "FortnightlyCheckWorker"  
        const val WORK_NAME = "fortnightly_check_work"  
  
        /** 注册周期任务（幂等，重复调用不会重建） */  
        fun schedule(context: Context) {  
            val request = PeriodicWorkRequestBuilder<FortnightlyCheckWorker>(  
                15, TimeUnit.MINUTES  
            ).setInitialDelay(1, TimeUnit.MINUTES).build()  
  
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(  
                WORK_NAME,  
                ExistingPeriodicWorkPolicy.KEEP,  
                request  
            )  
            Log.i(TAG, "已注册半月刊新活动检查任务（15 分钟）")  
        }  
  
        fun cancel(context: Context) {  
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)  
            Log.i(TAG, "已取消半月刊新活动检查任务")  
        }  
    }  
  
    override suspend fun doWork(): Result {  
        return try {  
            val result = pushManager.checkAndPush(refresh = true)  
            Log.i(TAG, "检查完成: ${result.message}")  
            Result.success()  
        } catch (e: Exception) {  
            Log.e(TAG, "检查失败: ${e.message}", e)  
            Result.retry()  
        }  
    }  
}  
