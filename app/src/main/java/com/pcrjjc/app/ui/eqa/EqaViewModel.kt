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
  
data class EqaQuestion(  
    val question: String,  
    val answerCount: Int  
)  
  
data class EqaAnswer(  
    val userId: Long,  
    val isMe: Boolean,  
    val content: String  
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
    private val settingsDataStore: SettingsDataStore  
) : ViewModel() {  
  
    private val _uiState = MutableStateFlow(EqaUiState())  
    val uiState: StateFlow<EqaUiState> = _uiState  
  
    private val httpClient = OkHttpClient.Builder()  
        .connectTimeout(15, TimeUnit.SECONDS)  
        .readTimeout(30, TimeUnit.SECONDS)  
        .build()  
  
    /** 进入页面自动加载所有问题 */  
    fun loadQuestions() {  
        viewModelScope.launch {  
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null, selectedQuestion = null, answers = emptyList())  
            try {  
                val baseUrl = settingsDataStore.getEqaServerUrl()  
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
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "加载失败: ${e.message}")  
            }  
        }  
    }  
  
    /** 加载某问题的回答 */  
    fun loadAnswer(question: String) {  
        viewModelScope.launch {  
            _uiState.value = _uiState.value.copy(isLoadingAnswer = true, selectedQuestion = question, answers = emptyList())  
            try {  
                val baseUrl = settingsDataStore.getEqaServerUrl()  
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
                val list = mutableListOf<EqaAnswer>()  
                for (i in 0 until arr.length()) {  
                    val obj = arr.getJSONObject(i)  
                    list.add(  
                        EqaAnswer(  
                            userId = obj.getLong("user_id"),  
                            isMe = obj.getBoolean("is_me"),  
                            content = obj.getString("content")  
                        )  
                    )  
                }  
                _uiState.value = _uiState.value.copy(isLoadingAnswer = false, answers = list)  
            } catch (e: Exception) {  
                _uiState.value = _uiState.value.copy(isLoadingAnswer = false, errorMessage = "获取回答失败: ${e.message}")  
            }  
        }  
    }  
  
    fun clearAnswer() {  
        _uiState.value = _uiState.value.copy(selectedQuestion = null, answers = emptyList())  
    }  
}