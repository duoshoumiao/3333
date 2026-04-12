package com.pcrjjc.app.service  
  
import android.app.Activity  
import android.app.Notification  
import android.app.Service  
import android.content.Intent  
import android.content.pm.ServiceInfo  
import android.graphics.Bitmap  
import android.graphics.PixelFormat  
import android.hardware.display.DisplayManager  
import android.hardware.display.VirtualDisplay  
import android.media.Image  
import android.media.ImageReader  
import android.media.projection.MediaProjection  
import android.media.projection.MediaProjectionManager  
import android.os.Build  
import android.os.Handler  
import android.os.IBinder  
import android.os.Looper  
import android.util.DisplayMetrics  
import android.util.Log  
import android.view.WindowManager  
import androidx.core.app.NotificationCompat  
import androidx.core.app.ServiceCompat  
import com.pcrjjc.app.PcrJjcApp  
  
class ScreenCaptureService : Service() {  
  
    companion object {  
        const val EXTRA_RESULT_CODE = "result_code"  
        const val EXTRA_DATA = "data"  
        private const val NOTIFICATION_ID = 2  
        private const val TAG = "ScreenCaptureService"  
  
        @Volatile  
        var instance: ScreenCaptureService? = null  
            private set  
    }  
  
    private var mediaProjection: MediaProjection? = null  
    private var imageReader: ImageReader? = null  
    private var virtualDisplay: VirtualDisplay? = null  
    private var screenWidth = 0  
    private var screenHeight = 0  
    private var screenDensity = 0  
  
    private val projectionCallback = object : MediaProjection.Callback() {  
        override fun onStop() {  
            Log.w(TAG, "MediaProjection 被系统终止")  
            virtualDisplay?.release()  
            imageReader?.close()  
            virtualDisplay = null  
            imageReader = null  
            mediaProjection = null  
        }  
    }  
  
    override fun onBind(intent: Intent?): IBinder? = null  
  
    override fun onCreate() {  
        super.onCreate()  
        instance = this  
  
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager  
        val metrics = DisplayMetrics()  
        @Suppress("DEPRECATION")  
        wm.defaultDisplay.getRealMetrics(metrics)  
        screenWidth = metrics.widthPixels  
        screenHeight = metrics.heightPixels  
        screenDensity = metrics.densityDpi  
    }  
  
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {  
        val notification = createNotification()  
        try {  
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {  
                ServiceCompat.startForeground(  
                    this, NOTIFICATION_ID, notification,  
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION  
                )  
            } else {  
                startForeground(NOTIFICATION_ID, notification)  
            }  
        } catch (e: Exception) {  
            Log.e(TAG, "启动前台服务失败", e)  
            stopSelf()  
            return START_NOT_STICKY  
        }  
  
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0  
        val data: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {  
            intent?.getParcelableExtra(EXTRA_DATA, Intent::class.java)  
        } else {  
            @Suppress("DEPRECATION")  
            intent?.getParcelableExtra(EXTRA_DATA)  
        }  
  
        if (resultCode != Activity.RESULT_OK || data == null) {  
            Log.e(TAG, "Invalid result code or data")  
            stopSelf()  
            return START_NOT_STICKY  
        }  
  
        val projectionManager =  
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager  
        mediaProjection = projectionManager.getMediaProjection(resultCode, data)  
  
        // 注册回调，监听 MediaProjection 被系统终止  
        mediaProjection?.registerCallback(projectionCallback, Handler(Looper.getMainLooper()))  
  
        imageReader = ImageReader.newInstance(  
            screenWidth, screenHeight,  
            PixelFormat.RGBA_8888, 3  // 增大 buffer 为 3  
        )  
  
        virtualDisplay = mediaProjection?.createVirtualDisplay(  
            "ScreenCapture",  
            screenWidth, screenHeight, screenDensity,  
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,  
            imageReader!!.surface,  
            null, Handler(Looper.getMainLooper())  
        )  
  
        Log.i(TAG, "截图服务已启动 ${screenWidth}x${screenHeight}")  
        return START_STICKY  
    }  
  
    /**  
     * 检测屏幕方向是否变化，如果变化则重建 ImageReader 和 VirtualDisplay。  
     * 优先使用 resize() + setSurface() 避免依赖 mediaProjection 重建。  
     * @return true 表示发生了重建  
     */  
    @Synchronized  
    private fun refreshVirtualDisplay(): Boolean {  
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager  
        val metrics = DisplayMetrics()  
        @Suppress("DEPRECATION")  
        wm.defaultDisplay.getRealMetrics(metrics)  
        val currentW = metrics.widthPixels  
        val currentH = metrics.heightPixels  
  
        val sizeChanged = currentW != screenWidth || currentH != screenHeight  
        val needsRebuild = sizeChanged || virtualDisplay == null || imageReader == null  
  
        if (!needsRebuild) return false  
  
        if (sizeChanged) {  
            Log.i(TAG, "屏幕尺寸变化: ${screenWidth}x${screenHeight} -> ${currentW}x${currentH}")  
            screenWidth = currentW  
            screenHeight = currentH  
        } else {  
            Log.i(TAG, "VirtualDisplay 或 ImageReader 为空，需要重建")  
        }  
  
        val oldReader = imageReader  
        val oldVd = virtualDisplay  
  
        // 创建新的 ImageReader  
        val newReader = ImageReader.newInstance(  
            screenWidth, screenHeight,  
            PixelFormat.RGBA_8888, 3  
        )  
        imageReader = newReader  
  
        // 方案A: 尝试 resize + setSurface（不需要 mediaProjection）  
        if (oldVd != null && sizeChanged) {  
            try {  
                oldVd.resize(screenWidth, screenHeight, screenDensity)  
                oldVd.setSurface(newReader.surface)  
                oldReader?.close()  
                Log.i(TAG, "VirtualDisplay resize + setSurface 成功")  
                return true  
            } catch (e: Exception) {  
                Log.w(TAG, "VirtualDisplay resize 失败，尝试完全重建", e)  
            }  
        }  
  
        // 方案B: 完全重建  
        oldVd?.release()  
        oldReader?.close()  
        virtualDisplay = null  
  
        val mp = mediaProjection  
        if (mp == null) {  
            Log.e(TAG, "MediaProjection 已失效，无法重建 VirtualDisplay，截图将不可用")  
            return true  
        }  
  
        try {  
            virtualDisplay = mp.createVirtualDisplay(  
                "ScreenCapture",  
                screenWidth, screenHeight, screenDensity,  
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,  
                newReader.surface,  
                null, Handler(Looper.getMainLooper())  
            )  
            if (virtualDisplay != null) {  
                Log.i(TAG, "VirtualDisplay 完全重建成功")  
            } else {  
                Log.e(TAG, "createVirtualDisplay 返回 null")  
            }  
        } catch (e: Exception) {  
            Log.e(TAG, "createVirtualDisplay 异常", e)  
        }  
  
        return true  
    }  
  
    /**  
     * 截取当前屏幕，返回 Bitmap。  
     * 调用方应在协程/后台线程中调用。  
     */  
    fun captureScreen(): Bitmap? {  
        if (mediaProjection == null && virtualDisplay == null) {  
            Log.e(TAG, "MediaProjection 和 VirtualDisplay 均已失效，截图不可用")  
            return null  
        }  
  
        val rebuilt = refreshVirtualDisplay()  
  
        val maxAttempts = if (rebuilt) 10 else 3  
        val delayMs = if (rebuilt) 200L else 100L  
  
        for (attempt in 1..maxAttempts) {  
            val reader = imageReader ?: return null  
            var image: Image? = null  
            try {  
                image = reader.acquireLatestImage()  
                if (image != null) {  
                    val planes = image.planes  
                    val buffer = planes[0].buffer  
                    val pixelStride = planes[0].pixelStride  
                    val rowStride = planes[0].rowStride  
                    val rowPadding = rowStride - pixelStride * screenWidth  
  
                    val bitmap = Bitmap.createBitmap(  
                        screenWidth + rowPadding / pixelStride,  
                        screenHeight,  
                        Bitmap.Config.ARGB_8888  
                    )  
                    bitmap.copyPixelsFromBuffer(buffer)  
  
                    return if (rowPadding > 0) {  
                        val cropped = Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight)  
                        bitmap.recycle()  
                        cropped  
                    } else {  
                        bitmap  
                    }  
                }  
            } catch (e: Exception) {  
                Log.w(TAG, "截图尝试 $attempt/$maxAttempts 失败", e)  
            } finally {  
                image?.close()  
            }  
  
            if (attempt < maxAttempts) {  
                Log.d(TAG, "截图为空，等待 ${delayMs}ms 后重试 ($attempt/$maxAttempts)")  
                Thread.sleep(delayMs)  
            }  
        }  
  
        Log.e(TAG, "截图失败，已重试 $maxAttempts 次")  
        return null  
    }  
  
    fun getScreenWidth(): Int = screenWidth  
    fun getScreenHeight(): Int = screenHeight  
  
    override fun onDestroy() {  
        super.onDestroy()  
        mediaProjection?.unregisterCallback(projectionCallback)  
        virtualDisplay?.release()  
        imageReader?.close()  
        mediaProjection?.stop()  
        instance = null  
        Log.i(TAG, "截图服务已停止")  
    }  
  
    private fun createNotification(): Notification {  
        return NotificationCompat.Builder(this, PcrJjcApp.SERVICE_CHANNEL_ID)  
            .setSmallIcon(com.pcrjjc.app.R.drawable.ic_notification)  
            .setContentTitle("怎么拆 - 截图服务")  
            .setContentText("正在运行截图服务")  
            .setPriority(NotificationCompat.PRIORITY_LOW)  
            .setOngoing(true)  
            .build()  
    }  
}