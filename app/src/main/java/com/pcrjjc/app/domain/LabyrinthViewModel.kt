package com.pcrjjc.app.ui.labyrinth  
  
import android.util.Log  
import androidx.lifecycle.ViewModel  
import androidx.lifecycle.viewModelScope  
import com.pcrjjc.app.data.local.dao.AccountDao  
import com.pcrjjc.app.domain.ClientManager  
import com.pcrjjc.app.domain.LabyrinthDb  
import com.pcrjjc.app.domain.LabyrinthRouteFinder  
import com.pcrjjc.app.domain.QueryEngine  
import com.pcrjjc.app.domain.QueryEngine  
import com.pcrjjc.app.domain.CaptchaManager  
import com.pcrjjc.app.domain.CaptchaRequest  
import com.pcrjjc.app.data.remote.CaptchaRequiredException
import com.pcrjjc.app.util.Platform  
import dagger.hilt.android.lifecycle.HiltViewModel  
import kotlinx.coroutines.Dispatchers  
import kotlinx.coroutines.flow.MutableStateFlow  
import kotlinx.coroutines.flow.StateFlow  
import kotlinx.coroutines.launch  
import kotlinx.coroutines.withContext  
import javax.inject.Inject  
import com.pcrjjc.app.data.local.SettingsDataStore
  
data class LabyrinthUiState(  
    val selectedPlatform: Platform = Platform.B_SERVER,  
    val selectedGuildId: Int = 5,                 // 对应 labyrinth.py 装饰器默认公会 5  
    val difficulty: Int = 5,                      // 默认难度 5  
    val perfectStart: Boolean = false,            // 默认完美开局 false  
    val thirdBlockType: String = "两者都行",       // 默认第3格「两者都行」  
    // 默认选「简单」档位 boss（对应 LabyrinthBossConfig 的默认值）  
    val area3Bosses: Set<Int> = LabyrinthRouteFinder.AREA3_BOSSES  
        .filter { it.third == "简单" }.map { it.first }.toSet(),  
    val area5Bosses: Set<Int> = LabyrinthRouteFinder.AREA5_BOSSES  
        .filter { it.third == "简单" }.map { it.first }.toSet(),  
    val isLoading: Boolean = false,  
    val logs: List<String> = emptyList(),  
    val errorMessage: String? = null  
)  
  
@HiltViewModel  
class LabyrinthViewModel @Inject constructor(  
    private val accountDao: AccountDao,  
    private val clientManager: ClientManager,  
    private val labyrinthDb: LabyrinthDb,  
    private val settingsDataStore: SettingsDataStore,  
    private val captchaManager: CaptchaManager  
) : ViewModel() {
  
    companion object {  
        private const val TAG = "LabyrinthViewModel"  
        private const val MAX_COUNT = 9999  
    }  
  
    private val queryEngine = QueryEngine()  
    private val routeFinder = LabyrinthRouteFinder(labyrinthDb)  
  
    private val _uiState = MutableStateFlow(LabyrinthUiState())  
    val uiState: StateFlow<LabyrinthUiState> = _uiState  
  
    /** 公会下拉选项 (guildId -> guildName) */  
    val guildOptions: List<Pair<Int, String>> by lazy { labyrinthDb.guildOptions() }  
  
    init {  
        viewModelScope.launch {  
            val cfg = settingsDataStore.getLabyrinthConfig()  
            val cur = _uiState.value  
            _uiState.value = cur.copy(  
                selectedPlatform = cfg.platformId  
                    ?.let { id -> Platform.entries.firstOrNull { it.id == id } }  
                    ?: cur.selectedPlatform,  
                selectedGuildId = cfg.guildId ?: cur.selectedGuildId,  
                difficulty = cfg.difficulty ?: cur.difficulty,  
                perfectStart = cfg.perfect ?: cur.perfectStart,  
                thirdBlockType = cfg.third ?: cur.thirdBlockType,  
                area3Bosses = cfg.area3Bosses ?: cur.area3Bosses,  
                area5Bosses = cfg.area5Bosses ?: cur.area5Bosses  
            )  
        }  
    }
	
	fun updatePlatform(p: Platform) { _uiState.value = _uiState.value.copy(selectedPlatform = p, errorMessage = null) }  
    fun updateGuild(id: Int) { _uiState.value = _uiState.value.copy(selectedGuildId = id) }  
    fun updateDifficulty(d: Int) { _uiState.value = _uiState.value.copy(difficulty = d) }  
    fun updatePerfectStart(v: Boolean) { _uiState.value = _uiState.value.copy(perfectStart = v) }  
    fun updateThirdBlockType(v: String) { _uiState.value = _uiState.value.copy(thirdBlockType = v) }  
  
    fun toggleArea3Boss(unitId: Int) {  
        val cur = _uiState.value.area3Bosses  
        _uiState.value = _uiState.value.copy(  
            area3Bosses = if (unitId in cur) cur - unitId else cur + unitId  
        )  
    }  
  
    fun toggleArea5Boss(unitId: Int) {  
        val cur = _uiState.value.area5Bosses  
        _uiState.value = _uiState.value.copy(  
            area5Bosses = if (unitId in cur) cur - unitId else cur + unitId  
        )  
    }  
  
    fun clearError() { _uiState.value = _uiState.value.copy(errorMessage = null) }  
  
    private fun appendLog(line: String) {  
        _uiState.value = _uiState.value.copy(logs = _uiState.value.logs + line)  
    }  
  
    /** 对应 labyrinth.py do_task 第 218-252 行 */  
    fun startReroll() {  
        val state = _uiState.value  
        viewModelScope.launch {  
            _uiState.value = state.copy(isLoading = true, errorMessage = null, logs = emptyList())  
            try {  
                withContext(Dispatchers.IO) {  
                    // 点击开始时持久化当前选项  
                    val s = _uiState.value  
                    settingsDataStore.saveLabyrinthConfig(  
                        platformId = s.selectedPlatform.id,  
                        guildId = s.selectedGuildId,  
                        difficulty = s.difficulty,  
                        perfect = s.perfectStart,  
                        third = s.thirdBlockType,  
                        area3 = s.area3Bosses,  
                        area5 = s.area5Bosses  
                    )
					val accounts = accountDao.getMasterAccountsByPlatform(state.selectedPlatform.id)  
                    if (accounts.isEmpty()) {  
                        throw IllegalStateException("没有${state.selectedPlatform.displayName}的账号，请先在“我的账号”里添加")  
                    }  
                    var activeClient = clientManager.getClient(accounts.first()) 
  
                    // 1. 校验难度是否解锁（_max_unlocked_difficulty）  
                    val top = queryEngine.labyrinthTop(activeClient, clientManager, accounts.first())  
                    if (state.difficulty > top.maxUnlockedDifficulty) {  
                        throw IllegalStateException(  
                            "黎明界难度${state.difficulty}尚未解锁，当前最大可挑战难度为${top.maxUnlockedDifficulty}"  
                        )  
                    }  
  
                    // 2. 已有开局先撤退  
                    if (top.enterId != 0L) {  
                        appendLog("检测到已有黎明界开局，先撤退。")  
                        queryEngine.labyrinthRetire(activeClient, top.enterId)  
                    }  
  
                    // 3. 最多 9999 次重开  
                    var lastReason = ""  
                    var consecutiveFailures = 0  
                    for (attempt in 1..MAX_COUNT) {  
                        val enter = queryEngine.labyrinthEnter(activeClient, state.selectedGuildId, state.difficulty)  
  
                        // 会话失效检测：进入黎明界返回空（既无 enter_id 也无 blocks），疑似被挤号/会话过期  
                        if (enter.enterId == 0L && enter.blocks.isEmpty()) {  
                            consecutiveFailures++  
                            if (consecutiveFailures >= 3) {  
                                throw IllegalStateException("会话失效，重新登录后仍无法进入黎明界，请稍后重试")  
                            }  
                            appendLog("检测到会话失效，重新登录后重试（第 $consecutiveFailures 次）")  
                            activeClient = clientManager.relogin(accounts.first())  
                            continue  
                        }  
                        consecutiveFailures = 0  
  
                        val (routes, reason) = routeFinder.findRoutes(  
                            enter.blocks, state.difficulty,  
                            state.area3Bosses, state.area5Bosses,  
                            state.thirdBlockType, state.perfectStart  
                        )  
                        if (routes != null) {  
                            appendLog("刷到${if (state.perfectStart) "完美" else ""}路线，总尝试次数：$attempt")  
                            for (area in routes.keys.sorted()) {  
                                appendLog(  
                                    routeFinder.formatRoute(  
                                        area, routes.getValue(area), enter.blocks,  
                                        state.area3Bosses, state.area5Bosses  
                                    )  
                                )  
                            }  
                            return@withContext  
                        }  
                        lastReason = reason  
                        if (enter.enterId != 0L) queryEngine.labyrinthRetire(activeClient, enter.enterId)  
                        queryEngine.labyrinthTop(activeClient)  
                    }  
                    throw IllegalStateException("重开${MAX_COUNT}次仍未刷到目标路线，最后失败原因：$lastReason")
                }  
                _uiState.value = _uiState.value.copy(isLoading = false)  
            } catch (e: CaptchaRequiredException) {  
                Log.w(TAG, "startReroll needs manual captcha")  
                try {  
                    val accounts = accountDao.getMasterAccountsByPlatform(_uiState.value.selectedPlatform.id)  
                    val account = accounts.firstOrNull()  
                    if (account != null) {  
                        captchaManager.requestCaptcha(  
                            CaptchaRequest(  
                                gt = e.gt,  
                                challenge = e.challenge,  
                                gtUserId = e.gtUserId,  
                                accountId = account.id,  
                                account = account.account,  
                                password = account.password,  
                                platform = account.platform  
                            )  
                        )  
                        clientManager.clearClient(account.id)  
                    }  
                } catch (ignore: Exception) {  
                    Log.w(TAG, "requestCaptcha failed: ${ignore.message}")  
                }  
                _uiState.value = _uiState.value.copy(  
                    isLoading = false,  
                    errorMessage = "需要手动过码，请在弹窗中完成验证后重试"  
                )
			} catch (e: Exception) {  
                Log.e(TAG, "startReroll failed: ${e.message}", e)  
                // セッション失効/顶号の可能性があるので、失効した可能性のあるクライアントを破棄し  
                // 次回クリック時に必ず再ログインさせる  
                try {  
                    val accounts = accountDao.getMasterAccountsByPlatform(_uiState.value.selectedPlatform.id)  
                    if (accounts.isNotEmpty()) {  
                        clientManager.clearClient(accounts.first().id)  
                    }  
                } catch (ignore: Exception) {  
                    Log.w(TAG, "clearClient on error failed: ${ignore.message}")  
                }  
                _uiState.value = _uiState.value.copy(  
                    isLoading = false,  
                    errorMessage = e.message ?: e.javaClass.simpleName  
                )  
            }
        }  
    }  
}