package com.pcrjjc.app.ui.eqa  
  
import androidx.lifecycle.ViewModel  
import androidx.lifecycle.viewModelScope  
import com.pcrjjc.app.data.local.SettingsDataStore  
import dagger.hilt.android.lifecycle.HiltViewModel  
import kotlinx.coroutines.Dispatchers  
import kotlinx.coroutines.flow.MutableStateFlow  
import kotlinx.coroutines.flow.StateFlow  
import kotlinx.coroutines.launch  
import kotlinx.coroutines.withContext  
import okhttp3.OkHttpClient  
import okhttp3.Request  
import org.json.JSONObject  
import java.net.URLEncoder  
import java.util.concurrent.TimeUnit  
import javax.inject.Inject  
import android.content.Context  
import android.util.Base64  
import android.util.Log  
import dagger.hilt.android.qualifiers.ApplicationContext  
import com.pcrjjc.app.util.EqaImageCache  
import kotlinx.coroutines.coroutineScope  
import kotlinx.coroutines.async  
import kotlinx.coroutines.awaitAll  
import java.security.MessageDigest 

data class EqaQuestion(  
    val question: String,  
    val answerCount: Int  
)  
  
/** 回答中的一个内容片段：文本或图片 */  
data class ContentSegment(  
    val type: String,   // "text" 或 "image"  
    val data: String    // 文本内容 或 图片URL  
)  
  
data class EqaAnswer(  
    val userId: Long,  
    val isMe: Boolean,  
    val segments: List<ContentSegment>  
)  
  
data class EqaUiState(  
    val isLoading: Boolean = false,  
    val questions: List<EqaQuestion> = emptyList(),  
    val selectedQuestion: String? = null,  
    val answers: List<EqaAnswer> = emptyList(),  
    val isLoadingAnswer: Boolean = false,  
    val errorMessage: String? = null,  
    val hasUpdate: Boolean = false,          // 服务端有新数据  
    val showUpdateDialog: Boolean = false,   // 是否弹出更新提示  
    val isDownloading: Boolean = false,      // 一键下载中  
    val downloadProgress: Float = 0f,        // 0f~1f  
    val downloadMessage: String? = null      // 进度文本  
)
  
@HiltViewModel  
class EqaViewModel @Inject constructor(  
    private val settingsDataStore: SettingsDataStore,  
    @ApplicationContext private val context: Context  
) : ViewModel() {
  
    private val _uiState = MutableStateFlow(EqaUiState())  
    val uiState: StateFlow<EqaUiState> = _uiState  
  
    private val httpClient = OkHttpClient.Builder()  
        .connectTimeout(15, TimeUnit.SECONDS)  
        .readTimeout(30, TimeUnit.SECONDS)  
        .build()  
  
    // 缓存 baseUrl 避免每次都读 DataStore  
    private var cachedBaseUrl: String? = null  
  
    private suspend fun getBaseUrl(): String {  
        return cachedBaseUrl ?: settingsDataStore.getEqaServerUrl().also { cachedBaseUrl = it }  
    }  
  
    /** 将相对路径的图片 URL 转为完整 URL */  
    private suspend fun resolveImageUrl(data: String): String {  
        return if (data.startsWith("/")) {  
            "${getBaseUrl()}$data"  
        } else {  
            data  
        }  
    }
    
    /** 用问题列表 JSON 的 MD5 作为版本标识 */  
    private fun computeVersion(json: String): String {  
        val md = MessageDigest.getInstance("MD5")  
        val digest = md.digest(json.toByteArray(Charsets.UTF_8))  
        return digest.joinToString("") { "%02x".format(it) }  
    }	
  
    fun loadQuestions() {  
        viewModelScope.launch {  
            // 1) 优先读本地缓存直接展示  
            val cached = withContext(Dispatchers.IO) {  
                EqaImageCache.getCachedQuestions(context)  
            }  
            if (cached != null) {  
                parseAndShowQuestions(cached)  
                // 后台检查是否有更新  
                checkForUpdate()  
                return@launch  
            }  
  
            // 2) 无缓存才联网  
            _uiState.value = _uiState.value.copy(  
                isLoading = true, errorMessage = null,  
                selectedQuestion = null, answers = emptyList()  
            )  
            try {  
                val result = fetchQuestionsJson()  
                parseAndShowQuestions(result)  
                _uiState.value = _uiState.value.copy(isLoading = false)  
                // 无缓存时提示用户可一键下载保存  
                _uiState.value = _uiState.value.copy(  
                    hasUpdate = true, showUpdateDialog = true  
                )  
            } catch (e: Exception) {  
                _uiState.value = _uiState.value.copy(  
                    isLoading = false, errorMessage = "加载失败: ${e.message}"  
                )  
            }  
        }  
    }  
  
    /** 联网获取问题列表 JSON 原文 */  
    private suspend fun fetchQuestionsJson(): String {  
        val baseUrl = getBaseUrl()  
        return withContext(Dispatchers.IO) {  
            val request = Request.Builder()  
                .url("$baseUrl/eqa/api/questions")  
                .get()  
                .build()  
            httpClient.newCall(request).execute().use { resp ->  
                resp.body?.string() ?: ""  
            }  
        }  
    }  
  
    /** 解析问题列表 JSON 并更新 UI */  
    private fun parseAndShowQuestions(result: String) {  
        val json = JSONObject(result)  
        val arr = json.getJSONArray("questions")  
        val list = mutableListOf<EqaQuestion>()  
        for (i in 0 until arr.length()) {  
            val obj = arr.getJSONObject(i)  
            list.add(  
                EqaQuestion(  
                    question = obj.getString("question"),  
                    answerCount = obj.getInt("answer_count")  
                )  
            )  
        }  
        _uiState.value = _uiState.value.copy(isLoading = false, questions = list)  
    }  
  
    /** 后台比对服务端版本，判断是否有更新 */  
    private fun checkForUpdate() {  
        viewModelScope.launch {  
            try {  
                val serverJson = fetchQuestionsJson()  
                val serverVersion = computeVersion(serverJson)  
                val localVersion = withContext(Dispatchers.IO) {  
                    EqaImageCache.getCacheVersion(context)  
                }  
                if (serverVersion != localVersion) {  
                    _uiState.value = _uiState.value.copy(  
                        hasUpdate = true, showUpdateDialog = true  
                    )  
                }  
            } catch (e: Exception) {  
                Log.w("EqaVM", "检查更新失败: ${e.message}")  
            }  
        }  
    }
  
    fun loadAnswer(question: String) {  
        viewModelScope.launch {  
            _uiState.value = _uiState.value.copy(  
                isLoadingAnswer = true, selectedQuestion = question, answers = emptyList()  
            ) 
            // 优先读本地缓存的回答  
            val cachedAnswer = withContext(Dispatchers.IO) {  
                EqaImageCache.getCachedAnswer(context, question)  
            }  
            if (cachedAnswer != null) {  
                try {  
                    val list = parseAnswerJson(cachedAnswer)  
                    _uiState.value = _uiState.value.copy(isLoadingAnswer = false, answers = list)  
                    return@launch  
                } catch (_: Exception) {  
                    // 缓存损坏则回退联网  
                }  
            }			
            try {  
                val baseUrl = getBaseUrl()  
                val encodedQ = withContext(Dispatchers.IO) {  
                    URLEncoder.encode(question, "UTF-8")  
                }  
                val result = withContext(Dispatchers.IO) {  
                    val request = Request.Builder()  
                        .url("$baseUrl/eqa/api/answer?question=$encodedQ")  
                        .get()  
                        .build()  
                    httpClient.newCall(request).execute().use { resp ->  
                        resp.body?.string() ?: ""  
                    }  
                }  
                val json = JSONObject(result)  
                val arr = json.getJSONArray("answers")  
  
                // 同名问答覆盖：先清空旧缓存  
                withContext(Dispatchers.IO) {  
                    EqaImageCache.clearQuestion(context, question)  
                }  
  
                val list = mutableListOf<EqaAnswer>()  
                for (i in 0 until arr.length()) {  
                    val obj = arr.getJSONObject(i)  
                    val segArr = obj.getJSONArray("segments")  
                    val segs = mutableListOf<ContentSegment>()  
                    for (j in 0 until segArr.length()) {  
                        val segObj = segArr.getJSONObject(j)  
                        val type = segObj.getString("type")  
                        var data = segObj.getString("data")  
  
                        if (type == "image") {  
                            // 将相对路径转为完整 URL  
                            if (data.startsWith("/")) {  
                                data = "$baseUrl$data"  
                            }  
  
                            // 下载图片并保存到本地  
                            val localPath = withContext(Dispatchers.IO) {  
                                try {  
                                    if (data.startsWith("base64://")) {  
                                        val b64 = data.removePrefix("base64://")  
                                        val bytes = Base64.decode(b64, Base64.DEFAULT)  
                                        EqaImageCache.saveImage(context, question, i, j, bytes)  
                                    } else {  
                                        val imgRequest = Request.Builder().url(data).build()  
                                        httpClient.newCall(imgRequest).execute().use { resp ->  
                                            val bytes = resp.body?.bytes()  
                                            if (resp.isSuccessful && bytes != null && bytes.isNotEmpty()) {  
                                                EqaImageCache.saveImage(context, question, i, j, bytes)  
                                            } else null  
                                        }  
                                    }  
                                } catch (e: Exception) {  
                                    Log.w("EqaVM", "下载图片失败: ${e.message}")  
                                    null  
                                }  
                            }  
  
                            // 下载成功用本地路径，否则保留原始 URL/base64  
                            if (localPath != null) {  
                                data = "file://$localPath"  
                            }  
                        }  
  
                        segs.add(ContentSegment(type = type, data = data))  
                    }  
                    list.add(  
                        EqaAnswer(  
                            userId = obj.getLong("user_id"),  
                            isMe = obj.getBoolean("is_me"),  
                            segments = segs  
                        )  
                    )  
                }  
                // 保存该问题的回答 JSON（图片已替换为本地 file:// 路径）到缓存  
                withContext(Dispatchers.IO) {  
                    EqaImageCache.saveAnswer(context, question, buildAnswerJson(list))  
                }  
                _uiState.value = _uiState.value.copy(isLoadingAnswer = false, answers = list)
            } catch (e: Exception) {  
                _uiState.value = _uiState.value.copy(  
                    isLoadingAnswer = false, errorMessage = "获取回答失败: ${e.message}"  
                )  
            }  
        }  
    }  
  
    fun clearAnswer() {  
        _uiState.value = _uiState.value.copy(selectedQuestion = null, answers = emptyList())  
    }  
	/** 从缓存 JSON 解析回答列表（图片为本地 file:// 路径，直接使用） */  
    private fun parseAnswerJson(result: String): List<EqaAnswer> {  
        val json = JSONObject(result)  
        val arr = json.getJSONArray("answers")  
        val list = mutableListOf<EqaAnswer>()  
        for (i in 0 until arr.length()) {  
            val obj = arr.getJSONObject(i)  
            val segArr = obj.getJSONArray("segments")  
            val segs = mutableListOf<ContentSegment>()  
            for (j in 0 until segArr.length()) {  
                val segObj = segArr.getJSONObject(j)  
                segs.add(ContentSegment(segObj.getString("type"), segObj.getString("data")))  
            }  
            list.add(  
                EqaAnswer(  
                    userId = obj.getLong("user_id"),  
                    isMe = obj.getBoolean("is_me"),  
                    segments = segs  
                )  
            )  
        }  
        return list  
    }  
  
    /** 把内存中的回答列表（含 file:// 本地图片路径）序列化为 JSON 存盘 */  
    private fun buildAnswerJson(list: List<EqaAnswer>): String {  
        val answersArr = org.json.JSONArray()  
        for (a in list) {  
            val segArr = org.json.JSONArray()  
            for (s in a.segments) {  
                segArr.put(JSONObject().put("type", s.type).put("data", s.data))  
            }  
            answersArr.put(  
                JSONObject()  
                    .put("user_id", a.userId)  
                    .put("is_me", a.isMe)  
                    .put("segments", segArr)  
            )  
        }  
        return JSONObject().put("answers", answersArr).toString()  
    }  
  
    /** 关闭更新提示弹窗 */  
    fun dismissUpdateDialog() {  
        _uiState.value = _uiState.value.copy(showUpdateDialog = false)  
    }  
  
    /** 一键全量下载：问题列表 + 每个问题的回答与图片，落盘并清除旧缓存 */  
    fun downloadAll() {  
        if (_uiState.value.isDownloading) return  
        viewModelScope.launch {  
            _uiState.value = _uiState.value.copy(  
                isDownloading = true, downloadProgress = 0f,  
                downloadMessage = "正在获取问题列表..."  
            )  
            try {  
                // 1) 拉取问题列表原文  
                val questionsJson = fetchQuestionsJson()  
                val version = computeVersion(questionsJson)  
                val json = JSONObject(questionsJson)  
                val arr = json.getJSONArray("questions")  
                val questions = mutableListOf<String>()  
                for (i in 0 until arr.length()) {  
                    questions.add(arr.getJSONObject(i).getString("question"))  
                }  
  
                // 2) 先清除旧缓存（移除旧问答）  
                withContext(Dispatchers.IO) {  
                    EqaImageCache.clearAll(context)  
                    EqaImageCache.saveQuestions(context, questionsJson)  
                }  
  
                // 3) 逐个下载每个问题的回答与图片  
                val total = questions.size  
                for ((index, q) in questions.withIndex()) {  
                    _uiState.value = _uiState.value.copy(  
                        downloadMessage = "下载中 ${index + 1}/$total"  
                    )  
                    try {  
                        val answerList = fetchAndCacheAnswer(q)  
                        withContext(Dispatchers.IO) {  
                            EqaImageCache.saveAnswer(context, q, buildAnswerJson(answerList))  
                        }  
                    } catch (e: Exception) {  
                        Log.w("EqaVM", "下载问题回答失败: $q ${e.message}")  
                    }  
                    _uiState.value = _uiState.value.copy(  
                        downloadProgress = (index + 1).toFloat() / total  
                    )  
                }  
  
                // 4) 记录版本、刷新状态  
                withContext(Dispatchers.IO) {  
                    EqaImageCache.saveCacheVersion(context, version)  
                }  
                parseAndShowQuestions(questionsJson)  
                _uiState.value = _uiState.value.copy(  
                    isDownloading = false, downloadProgress = 1f,  
                    downloadMessage = "下载完成", hasUpdate = false, showUpdateDialog = false  
                )  
            } catch (e: Exception) {  
                _uiState.value = _uiState.value.copy(  
                    isDownloading = false, downloadMessage = "下载失败: ${e.message}"  
                )  
            }  
        }  
    }  
  
    /** 联网获取某问题回答并下载图片到本地，返回内存回答列表（图片为 file:// 路径） */  
    private suspend fun fetchAndCacheAnswer(question: String): List<EqaAnswer> {  
        val baseUrl = getBaseUrl()  
        val encodedQ = withContext(Dispatchers.IO) { URLEncoder.encode(question, "UTF-8") }  
        val result = withContext(Dispatchers.IO) {  
            val request = Request.Builder()  
                .url("$baseUrl/eqa/api/answer?question=$encodedQ")  
                .get().build()  
            httpClient.newCall(request).execute().use { resp -> resp.body?.string() ?: "" }  
        }  
        val json = JSONObject(result)  
        val arr = json.getJSONArray("answers")  
        val list = mutableListOf<EqaAnswer>()  
        for (i in 0 until arr.length()) {  
            val obj = arr.getJSONObject(i)  
            val segArr = obj.getJSONArray("segments")  
            val segs = mutableListOf<ContentSegment>()  
            for (j in 0 until segArr.length()) {  
                val segObj = segArr.getJSONObject(j)  
                val type = segObj.getString("type")  
                var data = segObj.getString("data")  
                if (type == "image") {  
                    if (data.startsWith("/")) data = "$baseUrl$data"  
                    val localPath = withContext(Dispatchers.IO) {  
                        try {  
                            if (data.startsWith("base64://")) {  
                                val bytes = Base64.decode(data.removePrefix("base64://"), Base64.DEFAULT)  
                                EqaImageCache.saveImage(context, question, i, j, bytes)  
                            } else {  
                                val imgRequest = Request.Builder().url(data).build()  
                                httpClient.newCall(imgRequest).execute().use { resp ->  
                                    val bytes = resp.body?.bytes()  
                                    if (resp.isSuccessful && bytes != null && bytes.isNotEmpty())  
                                        EqaImageCache.saveImage(context, question, i, j, bytes)  
                                    else null  
                                }  
                            }  
                        } catch (e: Exception) { null }  
                    }  
                    if (localPath != null) data = "file://$localPath"  
                }  
                segs.add(ContentSegment(type, data))  
            }  
            list.add(EqaAnswer(obj.getLong("user_id"), obj.getBoolean("is_me"), segs))  
        }  
        return list  
    }
}