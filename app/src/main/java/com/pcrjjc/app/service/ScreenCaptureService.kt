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
  
        imageReader = ImageReader.newInstance(  
            screenWidth, screenHeight,  
            PixelFormat.RGBA_8888, 2  
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
     * 解决服务在竖屏启动、游戏切到横屏后截图尺寸不匹配的问题。  
     *  
     * @return true 如果发生了重建（调用方需要等待新帧）  
     */  
    private fun refreshVirtualDisplay(): Boolean {  
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager  
        val metrics = DisplayMetrics()  
        @Suppress("DEPRECATION")  
        wm.defaultDisplay.getRealMetrics(metrics)  
  
        val currentW = metrics.widthPixels  
        val currentH = metrics.heightPixels  
  
        if (currentW == screenWidth && currentH == screenHeight) return false  
  
        Log.i(TAG, "屏幕方向变化: ${screenWidth}x${screenHeight} -> ${currentW}x${currentH}, 重建 VirtualDisplay")  
  
        screenWidth = currentW  
        screenHeight = currentH  
  
        // 释放旧资源  
        virtualDisplay?.release()  
        imageReader?.close()  
  
        // 重建 ImageReader  
        imageReader = ImageReader.newInstance(  
            screenWidth, screenHeight,  
            PixelFormat.RGBA_8888, 2  
        )  
  
        // 重建 VirtualDisplay  
        virtualDisplay = mediaProjection?.createVirtualDisplay(  
            "ScreenCapture",  
            screenWidth, screenHeight, screenDensity,  
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,  
            imageReader!!.surface,  
            null, Handler(Looper.getMainLooper())  
        )  
  
        Log.i(TAG, "VirtualDisplay 已重建: ${screenWidth}x${screenHeight}")  
        return true  
    }  
  
    /**  
     * 截取当前屏幕，返回 Bitmap。  
     * 调用方应在协程/后台线程中调用。  
     */  
    fun captureScreen(): Bitmap? {  
        // 检测屏幕方向变化，必要时重建 VirtualDisplay  
        val rebuilt = refreshVirtualDisplay()  
  
        val reader = imageReader ?: return null  
  
        // 如果刚重建了 VirtualDisplay，首帧可能还没渲染，需要重试  
        val maxAttempts = if (rebuilt) 5 else 2  
        var image: Image? = null  
  
        for (attempt in 1..maxAttempts) {  
            try {  
                image = reader.acquireLatestImage()  
            } catch (e: Exception) {  
                Log.w(TAG, "获取图像失败，尝试 $attempt/$maxAttempts", e)  
            }  
            if (image != null) break  
            if (attempt < maxAttempts) {  
                try {  
                    Thread.sleep(150)  
                } catch (_: InterruptedException) {}  
            }  
        }  
  
        if (image == null) {  
            Log.w(TAG, "多次尝试后仍无法获取图像")  
            return null  
        }  
  
        try {  
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
  
            // 裁剪掉 padding  
            return if (rowPadding > 0) {  
                val cropped = Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight)  
                bitmap.recycle()  
                cropped  
            } else {  
                bitmap  
            }  
        } catch (e: Exception) {  
            Log.e(TAG, "截图失败", e)  
            return null  
        } finally {  
            image.close()  
        }  
    }  
  
    fun getScreenWidth(): Int = screenWidth  
    fun getScreenHeight(): Int = screenHeight  
  
    override fun onDestroy() {  
        super.onDestroy()  
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