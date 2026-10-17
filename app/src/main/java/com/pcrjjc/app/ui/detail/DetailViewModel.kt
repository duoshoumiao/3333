package com.pcrjjc.app.ui.detail  
  
import androidx.lifecycle.SavedStateHandle  
import androidx.lifecycle.ViewModel  
import androidx.lifecycle.viewModelScope  
import com.pcrjjc.app.data.local.dao.AccountDao  
import com.pcrjjc.app.data.local.dao.BindDao  
import com.pcrjjc.app.data.local.entity.PcrBind  
import com.pcrjjc.app.data.remote.BiliAuth  
import com.pcrjjc.app.data.remote.PcrClient  
import com.pcrjjc.app.data.remote.TwPcrClient  
import com.pcrjjc.app.domain.QueryEngine  
import com.pcrjjc.app.data.local.SettingsDataStore  
import com.pcrjjc.app.data.remote.AppPushClient
import com.pcrjjc.app.util.KnightRankCalculator  
import com.pcrjjc.app.util.Platform  
import dagger.hilt.android.lifecycle.HiltViewModel  
import kotlinx.coroutines.Dispatchers  
import kotlinx.coroutines.flow.MutableStateFlow  
import kotlinx.coroutines.flow.StateFlow  
import kotlinx.coroutines.launch  
import javax.inject.Inject  
  
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
    // 添加好友结果提示  
    val friendRequestMessage: String? = null
)  
  
@HiltViewModel  
class DetailViewModel @Inject constructor(  
    savedStateHandle: SavedStateHandle,  
    private val bindDao: BindDao,  
    private val accountDao: AccountDao,  
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {  
  
    private val bindId: Int = savedStateHandle["bindId"] ?: 0  
    private val _uiState = MutableStateFlow(DetailUiState())  
    val uiState: StateFlow<DetailUiState> = _uiState 
    // 保存查询时登录的游戏客户端，用于发送好友申请  
    private var loggedClient: Any? = null	
  
    init {  
        loadDetail()  
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
  
                val accounts = accountDao.getNonMasterAccountsByPlatform(bind.platform)  
                var info: Map<String, Any?>? = null  
                var fullRes: Map<String, Any?> = emptyMap()  
                if (accounts.isEmpty()) {  
                    // 无本地查询账号，改走服务器 pcrjjc2  
                    fullRes = AppPushClient(settingsDataStore.getPcrServerUrl())  
                        .queryProfile(bind.platform, bind.pcrid) ?: emptyMap()  
                    info = fullRes["user_info"] as? Map<String, Any?>  
                } else {  
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
                    loggedClient = client
					val result = queryEngine.queryProfile(client, bind)  
                    info = result?.userInfo  
                    fullRes = result?.fullResponse ?: emptyMap()  
                }  
  
                if (info != null) {  
  
                    // clan_name 在 fullResponse 顶层，不在 user_info 内  
                    val clanName = fullRes["clan_name"]?.toString() ?: ""  
  
                    // 服务器名称  
                    val serverName = Platform.fromId(bind.platform).displayName  
  
                    // 冒险经历 - quest_info  
                    val questInfo = fullRes["quest_info"] as? Map<String, Any?> ?: emptyMap()  
                    val normalQuestList = questInfo["normal_quest"] as? List<Any?> ?: emptyList()  
                    val hardQuestList = questInfo["hard_quest"] as? List<Any?> ?: emptyList()  
                    val veryHardQuestList = questInfo["very_hard_quest"] as? List<Any?> ?: emptyList()  
                    val normalQuest = normalQuestList.getOrNull(2)?.toString() ?: ""  
                    val hardQuest = hardQuestList.getOrNull(2)?.toString() ?: ""  
                    val veryHardQuest = veryHardQuestList.getOrNull(2)?.toString() ?: ""  
  
                    // 深域进度  
                    val talentQuest = (questInfo["talent_quest"] as? List<Map<String, Any?>>) ?: emptyList()  
  
                    // 公主骑士经验和RANK  
                    val knightExp = (info["princess_knight_rank_total_exp"] as? Number)?.toInt() ?: 0  
                    val knightRank = if (knightExp > 0) KnightRankCalculator.calculateRank(knightExp) else 0  
  
                    // 支援角色  
                    val friendSupportUnits = (fullRes["friend_support_units"] as? List<Map<String, Any?>>) ?: emptyList()  
                    val clanSupportUnits = (fullRes["clan_support_units"] as? List<Map<String, Any?>>) ?: emptyList()  
  
                    // 看板角色 - 在 fullResponse 顶层  
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
                        // 竞技场  
                        arenaRank = (info["arena_rank"] as? Number)?.toInt() ?: 0,  
                        arenaGroup = (info["arena_group"] as? Number)?.toInt() ?: 0,  
                        arenaTime = (info["arena_time"] as? Number)?.toLong() ?: 0,  
                        grandArenaRank = (info["grand_arena_rank"] as? Number)?.toInt() ?: 0,  
                        grandArenaGroup = (info["grand_arena_group"] as? Number)?.toInt() ?: 0,  
                        grandArenaTime = (info["grand_arena_time"] as? Number)?.toLong() ?: 0,  
                        // 冒险经历  
                        normalQuest = normalQuest,  
                        hardQuest = hardQuest,  
                        veryHardQuest = veryHardQuest,  
                        openStoryNum = (info["open_story_num"] as? Number)?.toInt() ?: 0,  
                        // 露娜塔  
                        towerClearedFloorNum = (info["tower_cleared_floor_num"] as? Number)?.toInt() ?: 0,  
                        towerClearedExQuestCount = (info["tower_cleared_ex_quest_count"] as? Number)?.toInt() ?: 0,  
                        // 支援角色  
                        friendSupportUnits = friendSupportUnits,  
                        clanSupportUnits = clanSupportUnits,  
                        // 深域  
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
  
    /**  
     * 向指定玩家 UID 发送好友申请  
     */  
    fun addFriend(targetUid: String) {  
        val uid = targetUid.trim()  
        val uidLong = uid.toLongOrNull()  
        if (uid.isEmpty() || !uid.all { it.isDigit() } || uidLong == null || uidLong <= 0L) {  
            _uiState.value = _uiState.value.copy(friendRequestMessage = "请输入正确的玩家UID")  
            return  
        }  
        val client = loggedClient  
        if (client == null) {  
            _uiState.value = _uiState.value.copy(friendRequestMessage = "无可用查询账号，无法发送好友申请")  
            return  
        }  
        viewModelScope.launch(Dispatchers.IO) {  
            try {  
                when (client) {  
                    is PcrClient -> client.sendFriendRequest(uidLong)  
                    is TwPcrClient -> client.sendFriendRequest(uidLong)  
                }  
                _uiState.value = _uiState.value.copy(friendRequestMessage = "已成功发送好友申请")  
            } catch (e: Exception) {  
                val msg = e.message ?: ""  
                _uiState.value = _uiState.value.copy(  
                    friendRequestMessage = when {  
                        msg.contains("已经") || msg.contains("既に") -> "已经是你的好友"  
                        msg.contains("申请") || msg.contains("pending", true) -> "已发送过申请，请等待对方处理"  
                        msg.contains("上限") || msg.contains("limit", true) -> "好友数量已达上限"  
                        else -> "发送好友申请失败: $msg"  
                    }  
                )  
            }  
        }  
    }
	
	fun retry() {  
        loadDetail()  
    }  
}