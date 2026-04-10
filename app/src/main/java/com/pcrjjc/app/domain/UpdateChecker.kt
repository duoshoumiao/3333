package com.pcrjjc.app.domain  
  
import android.content.Context  
import android.content.Intent  
import android.util.Log  
import androidx.core.content.FileProvider  
import kotlinx.coroutines.Dispatchers  
import kotlinx.coroutines.withContext  
import okhttp3.OkHttpClient  
import okhttp3.Request  
import org.json.JSONObject  
import java.io.File  
import java.util.concurrent.TimeUnit  
  
data class UpdateInfo(  
    val versionName: String,  
    val downloadUrl: String,  
    val releaseNotes: String  
)  
  
class UpdateChecker(private val context: Context) {  
  
    companion object {  
        private const val TAG = "UpdateChecker"  
        private const val GITHUB_API_URL =  
            "https://api.github.com/repos/duoshoumiao/3333/releases/latest"  
    }  
  
    private val httpClient = OkHttpClient.Builder()  
        .connectTimeout(10, TimeUnit.SECONDS)  
        .readTimeout(30, TimeUnit.SECONDS)  
        .build()  
  
    /**  
     * 检查是否有新版本。返回 UpdateInfo 如果有更新，null 如果已是最新。  
     */  
    suspend fun checkForUpdate(currentVersionName: String): UpdateInfo? =  
        withContext(Dispatchers.IO) {  
            try {  
                val request = Request.Builder()  
                    .url(GITHUB_API_URL)  
                    .header("Accept", "application/vnd.github.v3+json")  
                    .build()  
  
                val response = httpClient.newCall(request).execute()  
                if (!response.isSuccessful) {  
                    Log.w(TAG, "GitHub API returned ${response.code}")  
                    return@withContext null  
                }  
  
                val body = response.body?.string() ?: return@withContext null  
                val json = JSONObject(body)  
  
                val tagName = json.getString("tag_name") // e.g. "v1.0.6"  
                val latestVersion = tagName.removePrefix("v")  
                val releaseNotes = json.optString("body", "")  
  
                if (!isNewerVersion(latestVersion, currentVersionName)) {  
                    Log.i(TAG, "Already up to date: $currentVersionName >= $latestVersion")  
                    return@withContext null  
                }  
  
                // 从 assets 中找 APK 下载链接  
                val assets = json.getJSONArray("assets")  
                var downloadUrl: String? = null  
                for (i in 0 until assets.length()) {  
                    val asset = assets.getJSONObject(i)  
                    if (asset.getString("name").endsWith(".apk")) {  
                        downloadUrl = asset.getString("browser_download_url")  
                        break  
                    }  
                }  
  
                if (downloadUrl == null) {  
                    Log.w(TAG, "No APK found in release assets")  
                    return@withContext null  
                }  
  
                UpdateInfo(  
                    versionName = latestVersion,  
                    downloadUrl = downloadUrl,  
                    releaseNotes = releaseNotes  
                )  
            } catch (e: Exception) {  
                Log.e(TAG, "Failed to check for update: ${e.message}", e)  
                null  
            }  
        }  
  
    /**  
     * 下载 APK 到本地。onProgress 回调下载进度 (0.0 ~ 1.0)  
     */  
    suspend fun downloadApk(  
        downloadUrl: String,  
        onProgress: (Float) -> Unit  
    ): File? = withContext(Dispatchers.IO) {  
        try {  
            val request = Request.Builder().url(downloadUrl).build()  
            val response = httpClient.newCall(request).execute()  
            if (!response.isSuccessful) return@withContext null  
  
            val body = response.body ?: return@withContext null  
            val contentLength = body.contentLength()  
  
            val downloadDir = File(context.getExternalFilesDir(null), "Download")  
            if (!downloadDir.exists()) downloadDir.mkdirs()  
            val file = File(downloadDir, "update.apk")  
  
            file.outputStream().use { output ->  
                body.byteStream().use { input ->  
                    val buffer = ByteArray(8192)  
                    var bytesRead: Long = 0  
                    var read: Int  
                    while (input.read(buffer).also { read = it } != -1) {  
                        output.write(buffer, 0, read)  
                        bytesRead += read  
                        if (contentLength > 0) {  
                            onProgress(bytesRead.toFloat() / contentLength)  
                        }  
                    }  
                }  
            }  
  
            file  
        } catch (e: Exception) {  
            Log.e(TAG, "Failed to download APK: ${e.message}", e)  
            null  
        }  
    }  
  
    /**  
     * 触发系统 APK 安装界面  
     */  
    fun installApk(file: File) {  
        val uri = FileProvider.getUriForFile(  
            context, "${context.packageName}.fileprovider", file  
        )  
        val intent = Intent(Intent.ACTION_VIEW).apply {  
            setDataAndType(uri, "application/vnd.android.package-archive")  
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)  
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)  
        }  
        context.startActivity(intent)  
    }  
  
    /**  
     * 比较版本号，如 "1.0.6" > "1.0.5" 返回 true  
     */  
    private fun isNewerVersion(latest: String, current: String): Boolean {  
        val latestParts = latest.split(".").mapNotNull { it.toIntOrNull() }  
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }  
        val maxLen = maxOf(latestParts.size, currentParts.size)  
        for (i in 0 until maxLen) {  
            val l = latestParts.getOrElse(i) { 0 }  
            val c = currentParts.getOrElse(i) { 0 }  
            if (l > c) return true  
            if (l < c) return false  
        }  
        return false  
    }  
}