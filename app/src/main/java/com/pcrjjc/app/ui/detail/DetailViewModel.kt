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
import com.pcrjjc.app.util.Platform
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DetailUiState(
    val bind: PcrBind? = null,
    val userName: String = "",
    val teamLevel: Int = 0,
    val totalPower: Int = 0,
    val arenaRank: Int = 0,
    val grandArenaRank: Int = 0,
    val unitNum: Int = 0,
    val towerStatus: String = "",
    val clanName: String = "",
    val favoriteUnit: Map<String, Any?> = emptyMap(),
    val arenaDefenseUnits: List<Map<String, Any?>> = emptyList(),
    val grandArenaDefenseUnits: List<Map<String, Any?>> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
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

                    val arenaInfo = fullRes["user_info"] as? Map<String, Any?> ?: emptyMap()

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        userName = info["user_name"]?.toString() ?: "",
                        teamLevel = (info["team_level"] as? Number)?.toInt() ?: 0,
                        totalPower = (info["total_power"] as? Number)?.toInt() ?: 0,
                        arenaRank = (info["arena_rank"] as? Number)?.toInt() ?: 0,
                        grandArenaRank = (info["grand_arena_rank"] as? Number)?.toInt() ?: 0,
                        unitNum = (info["unit_num"] as? Number)?.toInt() ?: 0,
                        clanName = (info["clan_name"]?.toString()) ?: "",
                        favoriteUnit = (info["favorite_unit"] as? Map<String, Any?>) ?: emptyMap()
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
