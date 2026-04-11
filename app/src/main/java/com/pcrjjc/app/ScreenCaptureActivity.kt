package com.pcrjjc.app  
  
import android.app.Activity  
import android.content.Context  
import android.content.Intent  
import android.media.projection.MediaProjectionManager  
import android.os.Build  
import android.os.Bundle  
import android.widget.Toast  
import com.pcrjjc.app.service.FloatingWindowService  
import com.pcrjjc.app.service.ScreenCaptureService  
  
/**  
 * 透明 Activity，用于请求 MediaProjection 用户授权。  
 * 授权成功后启动 ScreenCaptureService 和 FloatingWindowService，然后自行 finish。  
 */  
class ScreenCaptureActivity : Activity() {  
  
    companion object {  
        private const val REQUEST_MEDIA_PROJECTION = 1001  
    }  
  
    override fun onCreate(savedInstanceState: Bundle?) {  
        super.onCreate(savedInstanceState)  
  
        // 如果浮窗服务已在运行，直接最小化  
        if (FloatingWindowService.isRunning) {  
            Toast.makeText(this, "怎么拆浮窗已在运行", Toast.LENGTH_SHORT).show()  
            finish()  
            return  
        }  
  
        val projectionManager =  
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager  
        startActivityForResult(projectionManager.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION)  
    }  
  
    @Deprecated("Deprecated in Java")  
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {  
        super.onActivityResult(requestCode, resultCode, data)  
  
        if (requestCode == REQUEST_MEDIA_PROJECTION) {  
            if (resultCode == RESULT_OK && data != null) {  
                // 启动截图前台服务  
                val captureIntent = Intent(this, ScreenCaptureService::class.java).apply {  
                    putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)  
                    putExtra(ScreenCaptureService.EXTRA_DATA, data)  
                }  
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {  
                    startForegroundService(captureIntent)  
                } else {  
                    startService(captureIntent)  
                }  
  
                // 启动浮窗服务  
                val floatingIntent = Intent(this, FloatingWindowService::class.java)  
                startService(floatingIntent)  
  
                Toast.makeText(this, "怎么拆浮窗已启动", Toast.LENGTH_SHORT).show()  
  
                // 最小化应用  
                moveTaskToBack(true)  
            } else {  
                Toast.makeText(this, "截图授权被拒绝", Toast.LENGTH_SHORT).show()  
            }  
        }  
        finish()  
    }  
}