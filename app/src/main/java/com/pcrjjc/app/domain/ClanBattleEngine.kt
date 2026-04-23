 package com.pcrjjc.app.domain  
  
import android.util.Log  
import com.pcrjjc.app.data.local.entity.Account  
import com.pcrjjc.app.data.local.entity.BossState  
import com.pcrjjc.app.data.local.entity.ClanBattleState  
import com.pcrjjc.app.data.local.entity.DamageRecord  
import com.pcrjjc.app.data.remote.ApiException  
import com.pcrjjc.app.data.remote.PcrClient  
import com.pcrjjc.app.util.findItem  
import com.pcrjjc.app.util.formatBigNum  
import com.pcrjjc.app.util.formatPercent  
import com.pcrjjc.app.util.formatTime  
import com.pcrjjc.app.util.lap2stage  
import com.pcrjjc.app.util.rateScore  
import com.pcrjjc.app.util.stageToNum  
import kotlinx.coroutines.CancellationException  
import kotlinx.coroutines.delay  
import kotlinx.coroutines.flow.MutableStateFlow  
import kotlinx.coroutines.flow.StateFlow  
import kotlinx.coroutines.flow.asStateFlow  
  
/**  
 * 会战引擎，移植自 zi-dong-bao-dao/clanbattle/model.py + __init__.py 监控循环  
 *  
 * 负责：  
 *  - 登录并初始化会战数据（clan_id, clan_battle_id）  
 *  - 循环轮询 boss 状态  
 *  - 检测血量变化、击破、换面等事件  
 *  - 生成状态文本和战报数据  
 */  
class ClanBattleEngine {  
  
    companion object {  
        private const val TAG = "ClanBattleEngine"  
        private const val MAX_ERROR_COUNT = 3  
        private const val MAX_RELOGIN_COUNT = 2 // 单次监控循环内最大重新登录次数  
        private const val COIN_CACHE_DURATION_MS = 60_000L // coin 缓存 60 秒  
    }  
  
    // ---- 内部状态 ----  
    private var client: PcrClient? = null  
    private var clientManager: ClientManager? = null  
    private var account: Account? = null  
    private var clanId: Int = 0  
    private var clanBattleId: Int = 0  
    private var lapNum: Int = 0  
    private var period: Int = 0          // 阶段编号  
    private var rank: Int = 0  
    private var latestTime: Long = 0     // 最新出刀时间戳  
    private var loopNum: Int = 0         // 循环编号，用于取消  
    private var errorCount: Int = 0  
  
    // ---- coin 缓存 ----  
    private var cachedCoin: Int = 0  
    private var coinCacheExpiry: Long = 0L  
  
    private val bosses = Array(5) { MutableBoss() }  
  
    // ---- 对外暴露的状态流 ----  
    private val _state = MutableStateFlow(ClanBattleState())  
    val state: StateFlow<ClanBattleState> = _state.asStateFlow()  
  
    private val _events = MutableStateFlow<List<String>>(emptyList())  
    /** 监控过程中产生的事件消息（出刀播报、换面提醒等） */  
    val events: StateFlow<List<String>> = _events.asStateFlow()  
  
    val isInitialized: Boolean get() = clanId != 0  
  
    // ======================== 初始化 ========================  
  
    /**  
     * 初始化会战引擎  
     * 移植自 model.py:29-46 ClanBattle.init()  
     *  
     * @param pcrClient 已登录的客户端  
     * @param clientMgr ClientManager，用于会话过期时重新登录  
     * @param acct 当前监控使用的账号  
     */  
    @Suppress("UNCHECKED_CAST")  
    suspend fun init(pcrClient: PcrClient, clientMgr: ClientManager? = null, acct: Account? = null) {  
        try {  
            loopNum++  
            client = pcrClient  
            clientManager = clientMgr  
            account = acct  
  
            // 1. 获取 clan_id  
            val homeIndex = pcrClient.callApi(  
                "/home/index",  
                mutableMapOf(  
                    "message_id" to 1,  
                    "tips_id_list" to emptyList<Any>(),  
                    "is_first" to 1,  
                    "gold_history" to 0  
                )  
            )  
            val userClan = homeIndex["user_clan"] as? Map<String, Any?>  
                ?: throw Exception("无法获取公会信息，该账号可能未加入公会")  
            clanId = (userClan["clan_id"] as? Number)?.toInt()  
                ?: throw Exception("clan_id 解析失败")  
  
            // 2. 获取会战 top 信息  
            val clanBattleTop = getClanBattleTop()  
            clanBattleId = (clanBattleTop["clan_battle_id"] as? Number)?.toInt()  
                ?: throw Exception("clan_battle_id 解析失败，可能当前没有会战")  
            lapNum = (clanBattleTop["lap_num"] as? Number)?.toInt() ?: 0  
            period = stageToNum(lap2stage(lapNum))  
            rank = (clanBattleTop["period_rank"] as? Number)?.toInt() ?: 0  
            refreshLatestTime(clanBattleTop)  
  
            // 3. 初始化 boss 状态  
            refreshBossesFromTop(clanBattleTop)  
  
            Log.i(TAG, "ClanBattleEngine initialized: clanId=$clanId, battleId=$clanBattleId, lap=$lapNum")  
            emitState()  
        } catch (e: CancellationException) {  
            throw e  
        } catch (e: Exception) {  
            Log.e(TAG, "Init failed", e)  
            throw Exception("会战初始化失败: ${e.message}")  
        }  
    }  
  
    // ======================== 监控循环 ========================  
  
    /**  
     * 开始循环监控 boss 状态  
     * 移植自 __init__.py:373-498 的 while True 监控循环  
     *  
     * @param onEvent 每次有事件（出刀播报、换面等）时回调  
     * @param onBossKill boss 被击杀时回调，传入被击杀的 boss 编号  
     */  
    suspend fun startMonitorLoop(  
		onEvent: suspend (String) -> Unit,  
		onBossKill: suspend (bossOrder: Int) -> Unit = {},  
		shouldRelogin: suspend () -> Boolean = { true }  
	) {  
        val currentLoopNum = loopNum  
        errorCount = 0  
        var reloginCount = 0  
  
        while (true) {  
            try {  
                // 检查是否被取消（loopNum 变化表示外部调用了 stopMonitor）  
                if (currentLoopNum != loopNum) {  
                    Log.i(TAG, "Monitor loop #$currentLoopNum cancelled (new loop: $loopNum)")  
                    return  
                }  
  
                val pcrClient = client ?: throw Exception("客户端未初始化")  
  
                // 1. 获取最新 top 数据  
                val clanBattleTop = getClanBattleTop()  
  
                // ★ 会话过期检测：检查返回数据是否包含 server_error 或缺少关键字段  
                if (clanBattleTop.containsKey("server_error")) {  
                    val serverError = clanBattleTop["server_error"] as? Map<String, Any?>  
                    val errorMsg = serverError?.get("message")?.toString() ?: "unknown server error"  
                    Log.w(TAG, "Server error detected in monitor loop: $errorMsg")  
                    throw SessionExpiredException("服务器返回错误: $errorMsg")  
                }  
                if (!clanBattleTop.containsKey("boss_info") && !clanBattleTop.containsKey("clan_battle_id")) {  
                    Log.w(TAG, "Response missing critical fields, possible session expiry")  
                    throw SessionExpiredException("返回数据缺少关键字段，可能会话已过期")  
                }  
  
                lapNum = (clanBattleTop["lap_num"] as? Number)?.toInt() ?: lapNum  
                rank = (clanBattleTop["period_rank"] as? Number)?.toInt() ?: rank  
  
                // 2. 换面检测  
                val newPeriod = stageToNum(lap2stage(lapNum))  
                if (period != newPeriod) {  
                    val oldStage = period  
                    period = newPeriod  
                    val msg = "阶段从${oldStage}面到了${lap2stage(lapNum)}面，请注意轴的切换"  
                    onEvent(msg)  
                }  
  
                // 3. 逐个 boss 检测变化  
                val bossInfoList = clanBattleTop["boss_info"] as? List<Map<String, Any?>> ?: emptyList()  
                var changed = false  
                val eventMessages = mutableListOf<String>()  
  
                for (i in 0 until minOf(5, bossInfoList.size)) {  
                    val currentBoss = bossInfoList[i]  
                    val currentHp = (currentBoss["current_hp"] as? Number)?.toLong() ?: 0  
                    val order = (currentBoss["order_num"] as? Number)?.toInt() ?: (i + 1)  
                    val maxHp = (currentBoss["max_hp"] as? Number)?.toLong() ?: 0  
                    val bossLapNum = (currentBoss["lap_num"] as? Number)?.toInt() ?: 0  
  
                    // 检测血量/周目变化  
                    if (currentHp != bosses[i].currentHp || bossLapNum != bosses[i].lapNum) {  
                        changed = true  
                        bosses[i].refresh(currentHp, bossLapNum, order, maxHp)  
  
                        // 仅在血量有变化时才刷新出刀人数  
                        val oldFighterNum = bosses[i].fighterNum  
                        val newFighterNum = refreshFighterNum(bossLapNum, order)  
                        if (newFighterNum != null && newFighterNum != oldFighterNum && newFighterNum > 0) {  
                            eventMessages.add("${i + 1}王当前有${newFighterNum}人出刀")  
                        }  
                    }  
                }  
  
                // 发送出刀人数变化事件  
                for (msg in eventMessages) {  
                    onEvent(msg)  
                }  
  
                // 4. 如果有血量变化，播报出刀记录  
                if (changed) {  
                    val damageHistory = clanBattleTop["damage_history"] as? List<Map<String, Any?>> ?: emptyList()  
                    val daoMessages = mutableListOf<String>()  
  
                    for (history in damageHistory) {  
                        val createTime = (history["create_time"] as? Number)?.toLong() ?: continue  
                        if (createTime > latestTime) {  
                            val name = history["name"]?.toString() ?: "未知"  
                            val histLapNum = (history["lap_num"] as? Number)?.toInt() ?: 0  
                            val histOrder = (history["order_num"] as? Number)?.toInt() ?: 0  
                            val damage = (history["damage"] as? Number)?.toLong() ?: 0  
                            val kill = history["kill"] as? Boolean ?: false  
                            val killText = if (kill) "并击破" else ""  
                            val msg = "${name}对${histLapNum}周目${histOrder}王造成了${damage}点伤害${killText}。"  
                            daoMessages.add(msg)  
  
                            // 击破时播报当前进度  
                            if (kill) {  
                                onEvent(generalBoss())  
                                onBossKill(histOrder)  
                            }  
                        }  
                    }  
  
                    refreshLatestTime(clanBattleTop)  
  
                    // 倒序播报（最新的在最后）  
                    for (msg in daoMessages.reversed()) {  
                        onEvent(msg)  
                    }  
                }  
  
                // 5. 更新状态  
                emitState()  
                errorCount = 0  
  
            } catch (e: CancellationException) {  
                throw e  
            } catch (e: SessionExpiredException) {  
				// ★ 会话过期，先检查是否有其他人在监控，避免互相抢登  
				Log.w(TAG, "Session expired detected: ${e.message}")  
				if (!shouldRelogin()) {  
					onEvent("检测到会话过期，但有其他人正在监控，避免互相抢登，监控已退出")  
					return  
				}  
				if (reloginCount >= MAX_RELOGIN_COUNT) {  
					onEvent("会话过期，已尝试重新登录${MAX_RELOGIN_COUNT}次仍失败，监控已退出")  
					return  
				}  
                val mgr = clientManager  
                val acct = account  
                if (mgr != null && acct != null) {  
                    try {  
                        reloginCount++  
                        onEvent("检测到会话过期，正在尝试重新登录（第${reloginCount}次）...")  
                        val newClient = mgr.relogin(acct)  
                        if (newClient is PcrClient) {  
                            client = newClient  
                            // 清除 coin 缓存，强制下次重新获取  
                            coinCacheExpiry = 0L  
                            onEvent("重新登录成功，继续监控")  
                            Log.i(TAG, "Re-login succeeded, resuming monitor loop")  
                            errorCount = 0  
                        } else {  
                            onEvent("重新登录返回了非预期的客户端类型，监控已退出")  
                            return  
                        }  
                    } catch (reloginEx: Exception) {  
                        Log.e(TAG, "Re-login failed", reloginEx)  
                        onEvent("重新登录失败: ${reloginEx.message}，监控已退出")  
                        return  
                    }  
                } else {  
                    onEvent("会话过期，但未配置 ClientManager，无法自动重新登录，监控已退出")  
                    return  
                }  
            } catch (e: Exception) {  
                Log.e(TAG, "Monitor loop error", e)  
                errorCount++  
                if (currentLoopNum != loopNum) {  
                    onEvent("监控编号#${currentLoopNum}已关闭")  
                    return  
                }  
                if (errorCount > MAX_ERROR_COUNT) {  
                    errorCount = 0  
                    onEvent("超过最大重试次数(${MAX_ERROR_COUNT})，监控已退出")  
                    return  
                }  
            }  
  
            delay(10000) // 每10秒轮询一次  
        }  
    }  
  
    /**  
     * 停止监控（通过递增 loopNum 使当前循环退出）  
     */  
    fun stopMonitor() {  
        loopNum++  
        Log.i(TAG, "Monitor stopped, loopNum incremented to $loopNum")  
    }  
  
    // ======================== API 调用 ========================  
  
    /**  
     * 获取会战 top 信息  
     * 移植自 model.py:52-53  
     */  
    @Suppress("UNCHECKED_CAST")  
    private suspend fun getClanBattleTop(): Map<String, Any?> {  
        val pcrClient = client ?: throw Exception("客户端未初始化")  
        val coin = getCoinCached()  
        return pcrClient.callApi(  
            "/clan_battle/top",  
            mutableMapOf(  
                "clan_id" to clanId,  
                "is_first" to 0,  
                "current_clan_battle_coin" to coin  
            )  
        )  
    }  
  
    /**  
     * 获取会战币（带缓存，60 秒内复用）  
     */  
    @Suppress("UNCHECKED_CAST")  
    private suspend fun getCoinCached(): Int {  
        val now = System.currentTimeMillis()  
        if (now < coinCacheExpiry) {  
            return cachedCoin  
        }  
        val pcrClient = client ?: throw Exception("客户端未初始化")  
        val loadIndex = pcrClient.callApi(  
            "/load/index",  
            mutableMapOf("carrier" to "OPPO")  
        )  
        val itemList = loadIndex["item_list"] as? List<Any?> ?: emptyList()  
        cachedCoin = findItem(itemList, 90006)  
        coinCacheExpiry = now + COIN_CACHE_DURATION_MS  
        return cachedCoin  
    }  
  
    /**  
     * 获取会战币（无缓存，init 等场景使用）  
     * 移植自 model.py:48-50  
     */  
    @Suppress("UNCHECKED_CAST")  
    private suspend fun getCoin(): Int {  
        val pcrClient = client ?: throw Exception("客户端未初始化")  
        val loadIndex = pcrClient.callApi(  
            "/load/index",  
            mutableMapOf("carrier" to "OPPO")  
        )  
        val itemList = loadIndex["item_list"] as? List<Any?> ?: emptyList()  
        return findItem(itemList, 90006)  
    }  
  
    /**  
     * 刷新某个 boss 的出刀人数  
     * 移植自 model.py:64-76  
     */  
    @Suppress("UNCHECKED_CAST")  
    private suspend fun refreshFighterNum(bossLapNum: Int, order: Int): Int? {  
        val boss = bosses[order - 1]  
        try {  
            val stageNum = stageToNum(boss.stage)  
            if (boss.lapNum - lapNum < 2 && stageNum == period) {  
                val pcrClient = client ?: return null  
                val result = pcrClient.callApi(  
                    "/clan_battle/reload_detail_info",  
                    mutableMapOf(  
                        "clan_id" to clanId,  
                        "clan_battle_id" to clanBattleId,  
                        "lap_num" to bossLapNum,  
                        "order_num" to order  
                    )  
                )  
                val fighterNum = (result["fighter_num"] as? Number)?.toInt() ?: 0  
                if (fighterNum != boss.fighterNum) {  
                    boss.fighterNum = fighterNum  
                    return fighterNum  
                }  
            }  
        } catch (e: Exception) {  
            Log.w(TAG, "refreshFighterNum failed for boss $order", e)  
        }  
        return null  
    }  
  
    /**  
     * 获取出刀记录（用于战报）  
     * 移植自 model.py:78-89  
     */  
    @Suppress("UNCHECKED_CAST")  
    suspend fun getBattleLog(page: Int): Map<String, Any?> {  
        val pcrClient = client ?: throw Exception("客户端未初始化")  
        return pcrClient.callApi(  
            "/clan_battle/battle_log_list",  
            mutableMapOf(  
                "clan_battle_id" to clanBattleId,  
                "order_num" to 0,  
                "phases" to listOf(1, 2, 3, 4),  
                "report_types" to listOf(1, 2, 3),  
                "hide_same_units" to 0,  
                "favorite_ids" to emptyList<Any>(),  
                "sort_type" to 4,  
                "page" to page  
            )  
        )  
    }  
  
    /**  
     * 获取公会成员列表  
     * 移植自 model.py:55-62  
     */  
    @Suppress("UNCHECKED_CAST")  
    suspend fun getClanMembers(): Map<String, Long> {  
        val pcrClient = client ?: throw Exception("客户端未初始化")  
        val result = pcrClient.callApi(  
            "/clan/info",  
            mutableMapOf("clan_id" to clanId, "get_user_equip" to 0)  
        )  
        val clan = result["clan"] as? Map<String, Any?> ?: emptyMap()  
        val members = clan["members"] as? List<Map<String, Any?>> ?: emptyList()  
        val memberMap = mutableMapOf<String, Long>()  
        for (member in members) {  
            val name = member["name"]?.toString() ?: continue  
            val viewerId = (member["viewer_id"] as? Number)?.toLong() ?: continue  
            memberMap[name] = viewerId  
        }  
        return memberMap  
    }  
  
    // ======================== 战报生成 ========================  
  
    /**  
     * 获取所有出刀记录（当期）  
     * 移植自 sql.py:419-431 + base.py:54-70  
     */  
    @Suppress("UNCHECKED_CAST")  
    suspend fun getAllRecords(): List<DamageRecord> {  
        val records = mutableListOf<DamageRecord>()  
        try {  
            val logTemp = getBattleLog(1)  
            val battleList = logTemp["battle_list"] as? List<Map<String, Any?>>  
            if (battleList.isNullOrEmpty()) return records  
  
            val maxPage = (logTemp["max_page"] as? Number)?.toInt() ?: 1  
            for (page in maxPage downTo 1) {  
                val log = getBattleLog(page)  
                val list = log["battle_list"] as? List<Map<String, Any?>> ?: continue  
                for (record in list.reversed()) {  
                    if ((record["battle_type"] as? Number)?.toInt() != 1) continue  
                    val pcrid = (record["target_viewer_id"] as? Number)?.toLong() ?: continue  
                    val name = record["user_name"]?.toString() ?: ""  
                    val time = (record["battle_end_time"] as? Number)?.toLong() ?: 0  
                    val lap = (record["lap_num"] as? Number)?.toInt() ?: 0  
                    val boss = (record["order_num"] as? Number)?.toInt() ?: 0  
                    val damage = (record["total_damage"] as? Number)?.toLong() ?: 0  
                    val battleLogId = (record["battle_log_id"] as? Number)?.toLong() ?: 0  
  
                    // 简化的 flag 判断（完整版需要 timeline_report，这里用 damage_history 近似）  
                    val flag = 0.0 // 简化处理，完整版需额外 API 调用  
  
                    records.add(  
                        DamageRecord(  
                            pcrid = pcrid, name = name, time = time,  
                            lapNum = lap, bossOrder = boss, damage = damage,  
                            flag = flag, battleLogId = battleLogId  
                        )  
                    )  
                }  
            }  
        } catch (e: Exception) {  
            Log.e(TAG, "getAllRecords failed", e)  
        }  
        return records  
    }  
  
    /**  
     * 生成当前战报文本  
     * 移植自 base.py:54-70 clanbattle_report()  
     */  
    fun generateReport(records: List<DamageRecord>): String {  
        if (records.isEmpty()) return "无出刀记录"  
  
        data class PlayerStat(  
            val pcrid: Long, val name: String,  
            var knife: Double = 0.0, var damage: Long = 0,  
            var score: Double = 0.0  
        )  
  
        val playerMap = mutableMapOf<Long, PlayerStat>()  
        var allDamage = 0L  
        var allScore = 0.0  
  
        for (r in records) {  
            val stat = playerMap.getOrPut(r.pcrid) { PlayerStat(r.pcrid, r.name) }  
            stat.knife += if (r.flag == 0.0) 1.0 else 0.5  
            stat.damage += r.damage  
            allDamage += r.damage  
            val stage = lap2stage(r.lapNum)  
            val bossRate = rateScore[stage]?.getOrNull(r.bossOrder - 1) ?: 1.0  
            stat.score += bossRate * r.damage  
            allScore += bossRate * r.damage  
        }  
  
        val sorted = playerMap.values.sortedByDescending { it.score.toLong() }  
        val sb = StringBuilder("===== 当前战报 =====\n")  
        for ((index, p) in sorted.withIndex()) {  
            val knifeStr = if (p.knife == p.knife.toLong().toDouble()) "${p.knife.toLong()}" else "${p.knife}"  
            val dmgRate = if (allDamage > 0) "%.2f%%".format(p.damage.toDouble() / allDamage * 100) else "0%"  
            val scoreRate = if (allScore > 0) "%.2f%%".format(p.score / allScore * 100) else "0%"  
            sb.appendLine("${index + 1}. ${p.name} | ${knifeStr}刀 | 伤害:${formatBigNum(p.damage)} | 分数:${p.score.toLong()} | 伤害占比:$dmgRate | 分数占比:$scoreRate")  
        }  
        sb.appendLine("总伤害: ${formatBigNum(allDamage)} | 总分数: ${allScore.toLong()}")  
        return sb.toString()  
    }  
  
    /**  
     * 生成个人战报文本  
     * 移植自 base.py:172-182 get_plyerreport()  
     */  
    fun generatePlayerReport(records: List<DamageRecord>, playerName: String): String {  
        val filtered = records.filter { it.name == playerName }  
        if (filtered.isEmpty()) return "未找到 $playerName 的出刀记录"  
  
        val sb = StringBuilder("===== ${playerName} 的战报 =====\n")  
        var knife = 0.0  
        for (r in filtered.sortedBy { it.time }) {  
            knife += if (r.flag == 0.0) 1.0 else 0.5  
            val type = when {  
                r.flag == 0.0 -> "完整刀"  
                r.flag == 1.0 -> "尾刀"  
                else -> "补偿"  
            }  
            val stage = lap2stage(r.lapNum)  
            val bossRate = rateScore[stage]?.getOrNull(r.bossOrder - 1) ?: 1.0  
            val score = (bossRate * r.damage).toLong()  
            val knifeStr = if (knife == knife.toLong().toDouble()) "${knife.toLong()}" else "$knife"  
            sb.appendLine("第${knifeStr}刀 | ${r.lapNum}周目${r.bossOrder}王 | 伤害:${r.damage} | 分数:$score | $type")  
        }  
        return sb.toString()  
    }  
  
    /**  
     * 生成今日/昨日出刀统计文本  
     * 移植自 base.py:72-80 day_report() + base.py:82-104 get_stat()  
     */  
    fun generateDayReport(records: List<DamageRecord>, members: Map<String, Long>): String {  
        if (records.isEmpty()) return "无出刀记录"  
  
        data class DayStat(val pcrid: Long, val name: String, var knife: Double = 0.0)  
  
        val playerMap = mutableMapOf<Long, DayStat>()  
        for (r in records) {  
            val stat = playerMap.getOrPut(r.pcrid) { DayStat(r.pcrid, r.name) }  
            stat.knife += if (r.flag == 0.0) 1.0 else 0.5  
        }  
  
        val statMap = mutableMapOf<Double, MutableList<String>>()  
        val daoNames = mutableSetOf<String>()  
        var total = 0.0  
  
        for (p in playerMap.values) {  
            val k = minOf(p.knife, 3.0)  
            statMap.getOrPut(k) { mutableListOf() }.add(p.name)  
            daoNames.add(p.name)  
            total += k  
        }  
  
        // 未出刀成员  
        val notDaoNames = members.keys.filter { it !in daoNames }  
        if (notDaoNames.isNotEmpty()) {  
            statMap.getOrPut(0.0) { mutableListOf() }.addAll(notDaoNames)  
        }  
  
        val sb = StringBuilder("===== 出刀统计 =====\n")  
        val totalStr = if (total == total.toLong().toDouble()) "${total.toLong()}" else "$total"  
        sb.appendLine("总计出刀：$totalStr")  
  
        for (k in listOf(3.0, 2.5, 2.0, 1.5, 1.0, 0.5, 0.0)) {  
            val names = statMap[k] ?: continue  
            if (names.isNotEmpty()) {  
                val kStr = if (k == k.toLong().toDouble()) "${k.toLong()}" else "$k"  
                sb.appendLine("----------")  
                sb.appendLine("以下是出了${kStr}刀的成员：")  
                sb.appendLine(names.joinToString(" | "))  
            }  
        }  
        return sb.toString()  
    }  
  
    // ======================== 状态文本生成 ========================  
  
    /**  
     * 生成 boss 状态文本  
     * 移植自 model.py:163-164 general_boss()  
     */  
    fun generalBoss(): String {  
        val stage = lap2stage(lapNum)  
        val sb = StringBuilder("当前进度：${stage}面${period}阶段\n")  
        for (boss in bosses) {  
            sb.appendLine(boss.bossInfo(lapNum, period))  
        }  
        return sb.toString().trimEnd()  
    }  
  
  
    // ======================== 内部辅助 ========================  
  
    @Suppress("UNCHECKED_CAST")  
    private fun refreshBossesFromTop(clanBattleTop: Map<String, Any?>) {  
        val bossInfoList = clanBattleTop["boss_info"] as? List<Map<String, Any?>> ?: return  
        for (i in 0 until minOf(5, bossInfoList.size)) {  
            val info = bossInfoList[i]  
            val currentHp = (info["current_hp"] as? Number)?.toLong() ?: 0  
            val order = (info["order_num"] as? Number)?.toInt() ?: (i + 1)  
            val maxHp = (info["max_hp"] as? Number)?.toLong() ?: 0  
            val bossLapNum = (info["lap_num"] as? Number)?.toInt() ?: 0  
            bosses[i].refresh(currentHp, bossLapNum, order, maxHp)  
        }  
    }  
  
    @Suppress("UNCHECKED_CAST")  
    private fun refreshLatestTime(clanBattleTop: Map<String, Any?>) {  
        try {  
            val history = clanBattleTop["damage_history"] as? List<Map<String, Any?>>  
            latestTime = if (!history.isNullOrEmpty()) {  
                (history[0]["create_time"] as? Number)?.toLong() ?: 0  
            } else 0  
        } catch (_: Exception) {  
            latestTime = 0  
        }  
    }  
  
    private fun emitState() {  
        val stage = lap2stage(lapNum)  
        _state.value = _state.value.copy(  
            rank = rank,  
            lapNum = lapNum,  
            period = period,  
            periodName = "${stage}面${period}阶段",  
            bosses = bosses.map { it.toBossState() },  
            lastUpdateTime = System.currentTimeMillis()  
        )  
    }  
  
    // ======================== 内部 Boss 类 ========================  
  
    /**  
     * 可变 Boss 状态，引擎内部使用  
     * 移植自 model.py:167-196 Boss 类  
     */  
    private class MutableBoss {  
        var order: Int = 0  
        var lapNum: Int = 0  
        var currentHp: Long = 0  
        var maxHp: Long = 0  
        var stage: String = ""  
        var fighterNum: Int = 0  
  
        fun refresh(currentHp: Long, lapNum: Int, order: Int, maxHp: Long) {  
            this.currentHp = currentHp  
            this.lapNum = lapNum  
            this.order = order  
            this.maxHp = maxHp  
            this.stage = lap2stage(lapNum)  
        }  
  
        /**  
         * 生成 boss 信息文本  
         * 移植自 model.py:185-194 Boss.boss_info()  
         */  
        fun bossInfo(clanLapNum: Int, period: Int): String {  
            val msg = StringBuilder("${lapNum}周目${order}王: ")  
            val stageNum = stageToNum(stage)  
            if (lapNum - clanLapNum < 2 && stageNum == period && currentHp > 0) {  
                msg.append("HP: ${formatBigNum(currentHp)}/${formatBigNum(maxHp)} ${formatPercent(currentHp.toDouble() / maxHp)}")  
                if (fighterNum > 0) {  
                    msg.append(" 当前有${fighterNum}人挑战")  
                }  
            } else {  
                msg.append("无法挑战")  
            }  
            return msg.toString()  
        }  
  
        fun toBossState() = BossState(  
            order = order,  
            lapNum = lapNum,  
            currentHp = currentHp,  
            maxHp = maxHp,  
            stage = stage,  
            fighterNum = fighterNum  
        )  
    }  
}  
  
/**  
 * 会话过期异常，用于区分普通网络错误和会话过期  
 */  
class SessionExpiredException(message: String) : Exception(message)