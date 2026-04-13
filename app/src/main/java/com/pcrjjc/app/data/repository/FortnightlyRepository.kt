package com.pcrjjc.app.data.repository  
  
import android.content.Context  
import com.pcrjjc.app.data.model.ActivityEntry  
import com.pcrjjc.app.data.model.ClassifiedActivity  
import com.pcrjjc.app.domain.ActivityClassifier  
import dagger.hilt.android.qualifiers.ApplicationContext  
import kotlinx.coroutines.Dispatchers  
import kotlinx.coroutines.withContext  
import okhttp3.OkHttpClient  
import okhttp3.Request  
import org.json.JSONArray  
import java.io.File  
import java.security.MessageDigest  
import java.text.SimpleDateFormat  
import java.util.Locale  
import java.util.TimeZone  
import javax.inject.Inject  
import javax.inject.Singleton  
  
@Singleton  
class FortnightlyRepository @Inject constructor(  
    @ApplicationContext private val context: Context,  
    private val client: OkHttpClient  
) {  
    companion object {  
        private const val DATA_FILE_NAME = "fortnightly_data.json"  
        private const val GITHUB_URL =  
            "https://raw.githubusercontent.com/duoshoumiao/PCR--Fortnightly-magazine-/main/data.json"  
    }  
  
    private val dataFile: File get() = File(context.filesDir, DATA_FILE_NAME)  
  
    private var lastDataHash: String? = null  
  
    /**  
     * 从 GitHub 更新 data.json — 移植自 huodong.py line 798-840  
     * @return Pair<是否有更新, 消息>  
     */  
    suspend fun updateFromGitHub(): Pair<Boolean, String> = withContext(Dispatchers.IO) {  
        try {  
            val request = Request.Builder().url(GITHUB_URL).build()  
            val response = client.newCall(request).execute()  
            if (!response.isSuccessful) {  
                return@withContext false to "下载失败: HTTP ${response.code}"  
            }  
            val body = response.body?.string()  
                ?: return@withContext false to "下载失败: 响应为空"  
  
            // 验证 JSON 格式  
            try {  
                JSONArray(body)  
            } catch (e: Exception) {  
                return@withContext false to "下载的数据不是有效的JSON格式"  
            }  
  
            // 计算哈希  
            val newHash = md5(body)  
            if (newHash == lastDataHash) {  
                return@withContext false to "数据无变化，已是最新"  
            }  
  
            // 备份旧文件  
            if (dataFile.exists()) {  
                val backup = File(context.filesDir, "$DATA_FILE_NAME.bak")  
                dataFile.copyTo(backup, overwrite = true)  
            }  
  
            // 保存新文件  
            dataFile.writeText(body, Charsets.UTF_8)  
            lastDataHash = newHash  
  
            val count = JSONArray(body).length()  
            return@withContext true to "更新成功！已加载 $count 条活动数据"  
        } catch (e: Exception) {  
            return@withContext false to "更新出错: ${e.message}"  
        }  
    }  
  
    /**  
     * 从本地加载并分类活动数据  
     * 过滤逻辑移植自 huodong.py line 949-958:  
     *   当前进行中 或 未来15天内开始  
     */  
    suspend fun loadClassifiedActivities(): Map<String, List<ClassifiedActivity>> =  
        withContext(Dispatchers.IO) {  
            val entries = loadLocal()  
            val currentTimeSec = System.currentTimeMillis() / 1000  
            val fifteenDays = 15L * 24 * 3600  
  
            val result = mutableMapOf<String, MutableList<ClassifiedActivity>>()  
            for (cat in ActivityClassifier.categoryOrder) {  
                result[cat] = mutableListOf()  
            }  
  
            val dateFormat = SimpleDateFormat("yyyy/M/d H", Locale.CHINA).apply {  
                timeZone = TimeZone.getTimeZone("Asia/Shanghai")  
            }  
  
            for (entry in entries) {  
                try {  
                    val startSec = dateFormat.parse(entry.startTime)!!.time / 1000  
                    val endSec = dateFormat.parse(entry.endTime)!!.time / 1000  
  
                    // 过滤：当前进行中 或 未来15天内开始  
                    val isOngoing = startSec <= currentTimeSec && currentTimeSec <= endSec  
                    val isUpcoming = startSec > currentTimeSec && (startSec - currentTimeSec) <= fifteenDays  
                    if (!isOngoing && !isUpcoming) continue  
  
                    // 提取子活动: 【xxx】  
                    val subRegex = Regex("【(.*?)】")  
                    val subs = subRegex.findAll(entry.activityName).map { it.groupValues[1] }.toList()  
  
                    for (sub in subs) {  
                        val category = ActivityClassifier.classify(sub)  
                        val (cleanedName, charIds) = ActivityClassifier.extractCharacterIds(sub)  
                        val (timeStatus, isEnding, isFuture) = ActivityClassifier.formatActivityStatus(  
                            startSec, endSec, currentTimeSec  
                        )  
  
                        result.getOrPut(category) { mutableListOf() }.add(  
                            ClassifiedActivity(  
                                category = category,  
                                subName = cleanedName,  
                                timeStatus = timeStatus,  
                                characterIds = charIds,  
                                isEnding = isEnding,  
                                isFuture = isFuture  
                            )  
                        )  
                    }  
                } catch (e: Exception) {  
                    // 跳过解析失败的条目  
                    continue  
                }  
            }  
  
            // 移除空分类  
            result.filter { it.value.isNotEmpty() }  
        }  
  
    /**  
     * 从本地文件加载原始数据 — 移植自 huodong.py line 756-778  
     */  
    private fun loadLocal(): List<ActivityEntry> {  
        if (!dataFile.exists()) return emptyList()  
        return try {  
            val text = dataFile.readText(Charsets.UTF_8)  
            val jsonArray = JSONArray(text)  
            val list = mutableListOf<ActivityEntry>()  
            for (i in 0 until jsonArray.length()) {  
                val obj = jsonArray.getJSONObject(i)  
                list.add(  
                    ActivityEntry(  
                        startTime = obj.getString("开始时间"),  
                        endTime = obj.getString("结束时间"),  
                        activityName = obj.getString("活动名")  
                    )  
                )  
            }  
            list  
        } catch (e: Exception) {  
            emptyList()  
        }  
    }  
  
    /** 本地是否已有数据文件 */  
    fun hasLocalData(): Boolean = dataFile.exists() && dataFile.length() > 0  
  
    private fun md5(input: String): String {  
        val md = MessageDigest.getInstance("MD5")  
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))  
        return digest.joinToString("") { "%02x".format(it) }  
    }  
}