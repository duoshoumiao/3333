package com.pcrjjc.app.ui.clanranking  
  
import android.util.Base64  
import android.util.Log  
import androidx.lifecycle.ViewModel  
import androidx.lifecycle.viewModelScope  
import dagger.hilt.android.lifecycle.HiltViewModel  
import kotlinx.coroutines.Dispatchers  
import kotlinx.coroutines.flow.MutableStateFlow  
import kotlinx.coroutines.flow.StateFlow  
import kotlinx.coroutines.launch  
import kotlinx.coroutines.withContext  
import okhttp3.OkHttpClient  
import okhttp3.Request  
import org.json.JSONObject  
import java.util.concurrent.TimeUnit  
import javax.inject.Inject  
import android.content.Context  
import dagger.hilt.android.qualifiers.ApplicationContext  
import java.io.File
  
// ==================== 数据模型 ====================  
  
data class ClanInfo(  
    val rank: Int,  
    val clanName: String,  
    val leaderName: String,  
    val damage: Long,  
    val memberNum: Int,  
    val gradeRank: String  
)  
  
// ==================== 搜索模式 ====================  
  
enum class SearchMode(val displayName: String) {  
    LEADER("查会长"),  
    CLAN("查公会"),  
    RANK("查排名")  
}  
  
// ==================== UI 状态 ====================  
  
data class ClanRankingUiState(  
    val allClans: Map<String, ClanInfo> = emptyMap(),  
    val searchResults: List<ClanInfo> = emptyList(),  
    val isLoading: Boolean = false,  
    val isDownloading: Boolean = false,  
    val errorMessage: String? = null,  
    val searchTitle: String = "",  
    val searchMode: SearchMode = SearchMode.LEADER,  
    val searchKeyword: String = "",  
    val dataLoaded: Boolean = false  
)  
  
// ==================== ViewModel ====================  
  
@HiltViewModel  
class ClanRankingViewModel @Inject constructor(  
		@ApplicationContext private val context: Context  
	) : ViewModel() {
  
    companion object {  
        private const val TAG = "ClanRankingVM"  
        private const val GITHUB_REPO = "duoshoumiao/chagonghui"  
        private const val GITHUB_FILE_PATH = "clan_scan/clan_ranking_global.json"  
        private const val GITHUB_API_BASE =  
            "https://api.github.com/repos/$GITHUB_REPO/contents/$GITHUB_FILE_PATH"  
        private const val MAX_RESULTS = 80  
    }  
  
    private val _uiState = MutableStateFlow(ClanRankingUiState())  
    val uiState: StateFlow<ClanRankingUiState> = _uiState  
  
    private val httpClient = OkHttpClient.Builder()  
        .connectTimeout(30, TimeUnit.SECONDS)  
        .readTimeout(60, TimeUnit.SECONDS)  
        .build()  
  
    init {  
		viewModelScope.launch(Dispatchers.IO) {  
			val localClans = loadFromLocal()  
			if (localClans.isNotEmpty()) {  
				_uiState.value = _uiState.value.copy(  
					allClans = localClans,  
					dataLoaded = true  
				)  
				Log.d(TAG, "从本地加载了 ${localClans.size} 个公会数据")  
			}  
		}  
	}
	
	// ==================== 输入 ====================  
  
    fun updateSearchMode(mode: SearchMode) {  
        _uiState.value = _uiState.value.copy(searchMode = mode, errorMessage = null)  
    }  
  
    fun updateSearchKeyword(keyword: String) {  
        _uiState.value = _uiState.value.copy(searchKeyword = keyword)  
    }  
  
    fun clearError() {  
        _uiState.value = _uiState.value.copy(errorMessage = null)  
    }  
  
    fun clearResults() {  
        _uiState.value = _uiState.value.copy(searchResults = emptyList(), searchTitle = "")  
    }  
  
    // ==================== 从 GitHub 下载数据 ====================  
  
    fun downloadFromGitHub() {  
        _uiState.value = _uiState.value.copy(isDownloading = true, errorMessage = null)  
  
        viewModelScope.launch {  
            try {  
                val clans = withContext(Dispatchers.IO) {  
                    val request = Request.Builder()  
                        .url(GITHUB_API_BASE)  
                        .addHeader("Accept", "application/vnd.github.v3+json")  
                        .get()  
                        .build()  
  
                    httpClient.newCall(request).execute().use { resp ->  
                        val text = resp.body?.string() ?: ""  
                        if (!resp.isSuccessful) {  
                            throw Exception("下载失败: HTTP ${resp.code}")  
                        }  
  
                        val json = JSONObject(text)  
                        val contentB64 = json.optString("content", "")  
                        val content: String  
  
                        if (contentB64.isNotBlank()) {  
                            // 文件 < 1MB，API 直接返回 base64 内容  
                            val cleaned = contentB64.replace("\n", "")  
                            content = String(Base64.decode(cleaned, Base64.DEFAULT), Charsets.UTF_8)  
                        } else {  
                            // 文件 >= 1MB，通过 Git Blobs API 获取  
                            val fileSha = json.optString("sha", "")  
                            if (fileSha.isBlank()) throw Exception("无法获取文件 SHA")  
  
                            val blobUrl =  
                                "https://api.github.com/repos/$GITHUB_REPO/git/blobs/$fileSha"  
                            val blobRequest = Request.Builder()  
                                .url(blobUrl)  
                                .addHeader("Accept", "application/vnd.github.v3+json")  
                                .get()  
                                .build()  
  
                            content = httpClient.newCall(blobRequest).execute().use { blobResp -> 
                                val blobText = blobResp.body?.string() ?: ""  
                                if (!blobResp.isSuccessful) {  
                                    throw Exception("下载文件失败: HTTP ${blobResp.code}")  
                                }  
                                val blobJson = JSONObject(blobText)  
                                val blobContent = blobJson.optString("content", "").replace("\n", "")  
                                String(Base64.decode(blobContent, Base64.DEFAULT), Charsets.UTF_8)  
                            }  
                        }  
                        // 保存原始 JSON 到本地  
                        saveToLocal(content)  
                        // 解析 JSON  
                        parseClanData(content)  
                    }  
                }  
  
                _uiState.value = _uiState.value.copy(  
                    isDownloading = false,  
                    allClans = clans,  
                    dataLoaded = true,  
                    errorMessage = null  
                )  
                Log.d(TAG, "下载成功，共 ${clans.size} 个公会")  
  
            } catch (e: Exception) {  
                Log.e(TAG, "下载公会数据失败: ${e.message}", e)  
                _uiState.value = _uiState.value.copy(  
                    isDownloading = false,  
                    errorMessage = "下载失败: ${e.message}"  
                )  
            }  
        }  
    }  
  
    private fun parseClanData(content: String): Map<String, ClanInfo> {  
        val jsonObj = JSONObject(content)  
        val result = mutableMapOf<String, ClanInfo>()  
        val keys = jsonObj.keys()  
        while (keys.hasNext()) {  
            val key = keys.next()  
            val obj = jsonObj.getJSONObject(key)  
            val clan = ClanInfo(  
                rank = obj.optInt("rank", 0),  
                clanName = obj.optString("clan_name", ""),  
                leaderName = obj.optString("leader_name", ""),  
                damage = obj.optLong("damage", 0),  
                memberNum = obj.optInt("member_num", 0),  
                gradeRank = obj.optString("grade_rank", "-")  
            )  
            result[key] = clan  
        }  
        return result  
    }  
  
    private fun getLocalFile(): File = File(context.filesDir, LOCAL_FILE_NAME)  
  
	private fun saveToLocal(content: String) {  
		try {  
			getLocalFile().writeText(content, Charsets.UTF_8)  
			Log.d(TAG, "公会数据已保存到本地")  
		} catch (e: Exception) {  
			Log.e(TAG, "保存本地文件失败: ${e.message}", e)  
		}  
	}  
	  
	private fun loadFromLocal(): Map<String, ClanInfo> {  
		val file = getLocalFile()  
		if (!file.exists()) return emptyMap()  
		return try {  
			val content = file.readText(Charsets.UTF_8)  
			parseClanData(content)  
		} catch (e: Exception) {  
			Log.e(TAG, "读取本地文件失败: ${e.message}", e)  
			emptyMap()  
		}  
	}
	
	// ==================== 搜索 ====================  
  
    fun search() {  
        val state = _uiState.value  
        val keyword = state.searchKeyword.trim()  
  
        if (keyword.isBlank()) {  
            _uiState.value = state.copy(errorMessage = "请输入搜索关键词")  
            return  
        }  
  
        if (state.allClans.isEmpty()) {  
            _uiState.value = state.copy(errorMessage = "尚未下载公会数据，请先点击「更新数据」")  
            return  
        }  
  
        when (state.searchMode) {  
            SearchMode.LEADER -> searchByLeader(keyword)  
            SearchMode.CLAN -> searchByClanName(keyword)  
            SearchMode.RANK -> searchByRank(keyword)  
        }  
    }  
  
    private fun searchByLeader(keyword: String) {  
        val state = _uiState.value  
        val results = state.allClans.values  
            .filter { keyword in it.leaderName }  
            .sortedBy { it.rank }  
  
        val total = results.size  
        val show = results.take(MAX_RESULTS)  
        val title = "查会长「$keyword」 找到${total}个结果" +  
                if (total > MAX_RESULTS) "（仅显示前${MAX_RESULTS}条）" else ""  
  
        _uiState.value = state.copy(  
            searchResults = show,  
            searchTitle = title,  
            errorMessage = if (results.isEmpty()) "未找到会长名包含「$keyword」的公会" else null  
        )  
    }  
  
    private fun searchByClanName(keyword: String) {  
        val state = _uiState.value  
        val results = state.allClans.values  
            .filter { keyword in it.clanName }  
            .sortedBy { it.rank }  
  
        val total = results.size  
        val show = results.take(MAX_RESULTS)  
        val title = "查公会「$keyword」 找到${total}个结果" +  
                if (total > MAX_RESULTS) "（仅显示前${MAX_RESULTS}条）" else ""  
  
        _uiState.value = state.copy(  
            searchResults = show,  
            searchTitle = title,  
            errorMessage = if (results.isEmpty()) "未找到公会名包含「$keyword」的公会" else null  
        )  
    }  
  
    private fun searchByRank(input: String) {  
        val state = _uiState.value  
        val targetRanks = mutableListOf<Int>()  
        val errorStrs = mutableListOf<String>()  
  
        val parts = input.split(",").map { it.trim() }  
        for (part in parts) {  
            if ("-" in part) {  
                val rangeParts = part.split("-", limit = 2)  
                if (rangeParts.size != 2) {  
                    errorStrs.add(part); continue  
                }  
                val startStr = rangeParts[0].trim()  
                val endStr = rangeParts[1].trim()  
                if (!startStr.all { it.isDigit() } || !endStr.all { it.isDigit() } ||  
                    startStr.isBlank() || endStr.isBlank()) {  
                    errorStrs.add(part); continue  
                }  
                var start = startStr.toInt()  
                var end = endStr.toInt()  
                if (start > end) { val tmp = start; start = end; end = tmp }  
                if (end - start + 1 > 100) {  
                    _uiState.value = state.copy(  
                        errorMessage = "范围查询最多支持100个排名，当前范围($start-$end)包含${end - start + 1}个排名"  
                    )  
                    return  
                }  
                targetRanks.addAll(start..end)  
            } else if (part.all { it.isDigit() } && part.isNotBlank()) {  
                targetRanks.add(part.toInt())  
            } else {  
                errorStrs.add(part)  
            }  
        }  
  
        if (errorStrs.isNotEmpty()) {  
            _uiState.value = state.copy(  
                errorMessage = "排名格式有误：「${errorStrs.joinToString(", ")}」\n" +  
                        "支持格式：单个(100)、多个(100,200,300)、范围(1-10)"  
            )  
            return  
        }  
  
        val sortedRanks = targetRanks.distinct().sorted()  
        val matched = mutableListOf<ClanInfo>()  
        val notFound = mutableListOf<String>()  
  
        for (rank in sortedRanks) {  
            val c = state.allClans[rank.toString()]  
            if (c != null) matched.add(c) else notFound.add(rank.toString())  
        }  
  
        if (matched.isEmpty()) {  
            _uiState.value = state.copy(  
                errorMessage = "未找到排名 ${notFound.joinToString(", ")} 的公会数据"  
            )  
            return  
        }  
  
        val show = matched.take(MAX_RESULTS)  
        var title = "查排名 共${matched.size}条结果" +  
                if (matched.size > MAX_RESULTS) "（仅显示前${MAX_RESULTS}条）" else ""  
        if (notFound.isNotEmpty()) {  
            val preview = notFound.take(10)  
            title += "  未找到: ${preview.joinToString(", ")}" +  
                    if (notFound.size > 10) "..." else ""  
        }  
  
        _uiState.value = state.copy(  
            searchResults = show,  
            searchTitle = title,  
            errorMessage = null  
        )  
    }  
}