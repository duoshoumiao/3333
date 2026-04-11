// app/src/main/java/com/pcrjjc/app/ui/detail/DetailViewModel.kt  
  
package com.pcrjjc.app.ui.detail  
  
import android.content.Context  
import androidx.lifecycle.SavedStateHandle  
import androidx.lifecycle.ViewModel  
import androidx.lifecycle.viewModelScope  
import coil.ImageLoader  
import coil.request.CachePolicy  
import coil.request.ImageRequest  
import com.pcrjjc.app.data.local.dao.AccountDao  
import com.pcrjjc.app.data.local.dao.BindDao  
import com.pcrjjc.app.data.local.entity.PcrBind  
import com.pcrjjc.app.data.remote.BiliAuth  
import com.pcrjjc.app.data.remote.PcrClient  
import com.pcrjjc.app.data.remote.TwPcrClient  
import com.pcrjjc.app.domain.QueryEngine  
import com.pcrjjc.app.util.CharaIconUtil  
import com.pcrjjc.app.util.KnightRankCalculator  
import com.pcrjjc.app.util.Platform  
import dagger.hilt.android.lifecycle.HiltViewModel  
import kotlinx.coroutines.Dispatchers  
import kotlinx.coroutines.flow.MutableStateFlow  
import kotlinx.coroutines.flow.StateFlow  
import kotlinx.coroutines.launch  
import kotlinx.coroutines.suspendCancellableCoroutine  
import kotlinx.coroutines.withContext  
import javax.inject.Inject  
import kotlin.coroutines.resume  
  
data class DetailUiState(  
    val bind: PcrBind? = null,  
    // 个人信息  
    val userName: String = "",  
    val teamLevel: Int = 0,  
    val totalPower: Int = 0,  
    val unitNum: Int = 0,  
    val clanName: String = "",  
    val userComment: String = "",  
    val lastLoginTime: Long = 0,  
    val serverName: String = "",  
    val viewerId: String = "",  
    // 看板角色  
    val favoriteUnit: Map<String, Any?> = emptyMap(),  
    // 竞技场  
    val arenaRank: Int = 0,  
    val arenaGroup: Int = 0,  
    val arenaTime: Long = 0,  
    val grandArenaRank: Int = 0,  
    val grandArenaGroup: Int = 0,  
    val grandArenaTime: Long = 0,  
    // 冒险经历  
    val normalQuest: String = "",  
    val hardQuest: String = "",  
    val veryHardQuest: String = "",  
    val openStoryNum: Int = 0,  
    // 露娜塔  
    val towerClearedFloorNum: Int = 0,  
    val towerClearedExQuestCount: Int = 0,  
    // 支援角色  
    val friendSupportUnits: List<Map<String, Any?>> = emptyList(),  
    val clanSupportUnits: List<Map<String, Any?>> = emptyList(),  
    // 深域进度  
    val talentQuest: List<Map<String, Any?>> = emptyList(),  
    val knightExp: Int = 0,  
    val knightRank: Int = 0,  
    // 防守阵容  
    val arenaDefenseUnits: List<Map<String, Any?>> = emptyList(),  
    val grandArenaDefenseUnits: List<Map<String, Any?>> = emptyList(),  
    // 状态  
    val isLoading: Boolean = false,  
    val errorMessage: String? = null,  
    // 头像预下载状态  
    val isPreloadingAvatars: Boolean = false,  
    val preloadProgress: String? = null  
)  
  
@HiltViewModel  
class DetailViewModel @Inject constructor(  
    savedStateHandle: SavedStateHandle,  
    private val bindDao: BindDao,  
    private val accountDao: AccountDao  
) : ViewModel() {  
  
    private val bindId: Int = savedStateHandle["bindId"] ?: 0  
    private val _uiState = MutableStateFlow(DetailUiState())  
    val uiState: StateFlow<DetailUiState> = _uiState  
  
    init {  
        loadDetail()  
    }  
  
    /**  
     * 收集当前页面所有需要显示头像的 unitId  
     */  
    @Suppress("UNCHECKED_CAST")  
    private fun collectAllUnitIds(): Set<Int> {  
        val state = _uiState.value  
        val unitIds = mutableSetOf<Int>()  
  
        // 看板角色  
        (state.favoriteUnit["id"] as? Number)?.toInt()?.let { unitIds.add(it) }  
  
        // 好友支援角色  
        state.friendSupportUnits.forEach { unit ->  
            val unitData = unit["unit_data"] as? Map<String, Any?>  
            (unitData?.get("id") as? Number)?.toInt()?.let { unitIds.add(it) }  
        }  
  
        // 公会支援角色  
        state.clanSupportUnits.forEach { unit ->  
            val unitData = unit["unit_data"] as? Map<String, Any?>  
            (unitData?.get("id") as? Number)?.toInt()?.let { unitIds.add(it) }  
        }  
  
        // 竞技场防守阵容  
        state.arenaDefenseUnits.forEach { unit ->  
            val unitData = unit["unit_data"] as? Map<String, Any?>  
            (unitData?.get("id") as? Number)?.toInt()?.let { unitIds.add(it) }  
        }  
        state.grandArenaDefenseUnits.forEach { unit ->  
            val unitData = unit["unit_data"] as? Map<String, Any?>  
            (unitData?.get("id") as? Number)?.toInt()?.let { unitIds.add(it) }  
        }  
  
        // 过滤掉无效 ID  
        return unitIds.filter { it > 0 }.toSet()  
    }  
  
    /**  
     * 预下载所有头像到本地磁盘缓存  
     */  
    fun preloadAvatars(context: Context) {  
        val unitIds = collectAllUnitIds()  
        if (unitIds.isEmpty()) {  
            _uiState.value = _uiState.value.copy(preloadProgress = "没有需要下载的头像")  
            return  
        }  
  
        viewModelScope.launch {  
            _uiState.value = _uiState.value.copy(  
                isPreloadingAvatars = true,  
                preloadProgress = "准备下载 ${unitIds.size} 个角色头像..."  
            )  
  
            val imageLoader = ImageLoader(context)  
            val allUrls = unitIds.flatMap { CharaIconUtil.getIconUrls(it) }  
            var completed = 0  
            var failed = 0  
  
            withContext(Dispatchers.IO) {  
                for (url in allUrls) {  
                    try {  
                        val request = ImageRequest.Builder(context)  
                            .data(url)  
                            .diskCachePolicy(CachePolicy.ENABLED)  
                            .memoryCachePolicy(CachePolicy.ENABLED)  
                            .build()  
  
                        suspendCancellableCoroutine { cont ->  
                            val disposable = imageLoader.enqueue(  
                                request.newBuilder()  
                                    .listener(  
                                        onSuccess = { _, _ -> cont.resume(true) },  
                                        onError = { _, _ -> cont.resume(false) },  
                                        onCancel = { _ -> cont.resume(false) }  
                                    )  
                                    .build()  
                            )  
                            cont.invokeOnCancellation { disposable.dispose() }  
                        }.let { success ->  
                            if (success == false) failed++  
                        }  
                    } catch (_: Exception) {  
                        failed++  
                    }  
                    completed++  
                    _uiState.value = _uiState.value.copy(  
                        preloadProgress = "下载中 $completed/${allUrls.size}..."  
                    )  
                }  
            }  
  
            val msg = if (failed == 0) {  
                "全部下载完成 (${unitIds.size}个角色)"  
            } else {  
                "下载完成，${failed}个失败"  
            }  
            _uiState.value = _uiState.value.copy(  
                isPreloadingAvatars = false,  
                preloadProgress = msg  
            )  
        }  
    }  
  
    /**  
     * 清除预下载提示信息  
     */  
    fun clearPreloadMessage() {  
        _uiState.value = _uiState.value.copy(preloadProgress = null)  
    }  
  
    @Suppress("UNCHECKED_CAST")  
    private fun loadDetail() {  
        viewModelScope.launch(Dispatchers.IO) {  
            _uiState.value = _uiState.value.copy(isLoading = true)  
  
            try {  
                val bind = bindDao.getBindById(bindId)  
                if (bind == null) {  
                    _uiState.value = _uiState.value.copy(  
                        isLoading = false,  
                        errorMessage = "绑定不存在"  
                    )  
                    return@launch  
                }  
  
                _uiState.value = _uiState.value.copy(bind = bind)  
  
                val accounts = accountDao.getAccountsByPlatform(bind.platform)  
                if (accounts.isEmpty()) {  
                    _uiState.value = _uiState.value.copy(  
                        isLoading = false,  
                        errorMessage = "未配置查询账号"  
                    )  
                    return@launch  
                }  
  
                val account = accounts.first()  
                val queryEngine = QueryEngine()  
  
                val client: Any = when (bind.platform) {  
                    Platform.TW_SERVER.id -> {  
                        val twPlatform = (account.viewerId.toLong() / 1000000000).toInt()  
                        val twClient = TwPcrClient(  
                            account.account, account.password,  
                            account.viewerId, twPlatform  
                        )  
                        twClient.login()  
                        twClient  
                    }  
                    else -> {  
                        val biliAuth = BiliAuth(account.account, account.password, account.platform)  
                        val pcrClient = PcrClient(biliAuth)  
                        pcrClient.login()  
                        pcrClient  
                    }  
                }  
  
                val result = queryEngine.queryProfile(client, bind)  
                if (result != null) {  
                    val info = result.userInfo  
                    val fullRes = result.fullResponse  
  
                    val clanName = fullRes["clan_name"]?.toString() ?: ""  
                    val serverName = Platform.fromId(bind.platform).displayName  
  
                    val questInfo = fullRes["quest_info"] as? Map<String, Any?> ?: emptyMap()  
                    val normalQuestList = questInfo["normal_quest"] as? List<Any?> ?: emptyList()  
                    val hardQuestList = questInfo["hard_quest"] as? List<Any?> ?: emptyList()  
                    val veryHardQuestList = questInfo["very_hard_quest"] as? List<Any?> ?: emptyList()  
                    val normalQuest = normalQuestList.getOrNull(2)?.toString() ?: ""  
                    val hardQuest = hardQuestList.getOrNull(2)?.toString() ?: ""  
                    val veryHardQuest = veryHardQuestList.getOrNull(2)?.toString() ?: ""  
  
                    val talentQuest = (questInfo["talent_quest"] as? List<Map<String, Any?>>) ?: emptyList()  
  
                    val knightExp = (info["princess_knight_rank_total_exp"] as? Number)?.toInt() ?: 0  
                    val knightRank = if (knightExp > 0) KnightRankCalculator.calculateRank(knightExp) else 0  
  
                    val friendSupportUnits = (fullRes["friend_support_units"] as? List<Map<String, Any?>>) ?: emptyList()  
                    val clanSupportUnits = (fullRes["clan_support_units"] as? List<Map<String, Any?>>) ?: emptyList()  
  
                    val favoriteUnit = (fullRes["favorite_unit"] as? Map<String, Any?>)  
                        ?: (info["favorite_unit"] as? Map<String, Any?>)  
                        ?: emptyMap()  
  
                    _uiState.value = _uiState.value.copy(  
                        isLoading = false,  
                        userName = info["user_name"]?.toString() ?: "",  
                        teamLevel = (info["team_level"] as? Number)?.toInt() ?: 0,  
                        totalPower = (info["total_power"] as? Number)?.toInt() ?: 0,  
                        unitNum = (info["unit_num"] as? Number)?.toInt() ?: 0,  
                        clanName = clanName,  
                        userComment = info["user_comment"]?.toString() ?: "",  
                        lastLoginTime = (info["last_login_time"] as? Number)?.toLong() ?: 0,  
                        serverName = serverName,  
                        viewerId = info["viewer_id"]?.toString() ?: "",  
                        favoriteUnit = favoriteUnit,  
                        arenaRank = (info["arena_rank"] as? Number)?.toInt() ?: 0,  
                        arenaGroup = (info["arena_group"] as? Number)?.toInt() ?: 0,  
                        arenaTime = (info["arena_time"] as? Number)?.toLong() ?: 0,  
                        grandArenaRank = (info["grand_arena_rank"] as? Number)?.toInt() ?: 0,  
                        grandArenaGroup = (info["grand_arena_group"] as? Number)?.toInt() ?: 0,  
                        grandArenaTime = (info["grand_arena_time"] as? Number)?.toLong() ?: 0,  
                        normalQuest = normalQuest,  
                        hardQuest = hardQuest,  
                        veryHardQuest = veryHardQuest,  
                        openStoryNum = (info["open_story_num"] as? Number)?.toInt() ?: 0,  
                        towerClearedFloorNum = (info["tower_cleared_floor_num"] as? Number)?.toInt() ?: 0,  
                        towerClearedExQuestCount = (info["tower_cleared_ex_quest_count"] as? Number)?.toInt() ?: 0,  
                        friendSupportUnits = friendSupportUnits,  
                        clanSupportUnits = clanSupportUnits,  
                        talentQuest = talentQuest,  
                        knightExp = knightExp,  
                        knightRank = knightRank  
                    )  
                } else {  
                    _uiState.value = _uiState.value.copy(  
                        isLoading = false,  
                        errorMessage = "查询详情失败"  
                    )  
                }  
            } catch (e: Exception) {  
                _uiState.value = _uiState.value.copy(  
                    isLoading = false,  
                    errorMessage = "查询出错: ${e.message}"  
                )  
            }  
        }  
    }  
  
    fun retry() {  
        loadDetail()  
    }  
}