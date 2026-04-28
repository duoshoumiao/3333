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
    val errorMessage: String? = null  
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
  
    fun loadQuestions() {  
        viewModelScope.launch {  
            _uiState.value = _uiState.value.copy(  
                isLoading = true, errorMessage = null,  
                selectedQuestion = null, answers = emptyList()  
            )  
            try {  
                val baseUrl = getBaseUrl()  
                val result = withContext(Dispatchers.IO) {  
                    val request = Request.Builder()  
                        .url("$baseUrl/eqa/api/questions")  
                        .get()  
                        .build()  
                    httpClient.newCall(request).execute().use { resp ->  
                        resp.body?.string() ?: ""  
                    }  
                }  
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
            } catch (e: Exception) {  
                _uiState.value = _uiState.value.copy(  
                    isLoading = false, errorMessage = "加载失败: ${e.message}"  
                )  
            }  
        }  
    }  
  
    fun loadAnswer(question: String) {  
        viewModelScope.launch {  
            _uiState.value = _uiState.value.copy(  
                isLoadingAnswer = true, selectedQuestion = question, answers = emptyList()  
            )  
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
}