package com.pcrjjc.app.data.local.entity    
    
import org.json.JSONArray    
import org.json.JSONObject    
    
/**    
 * 会战数据模型，移植自 zi-dong-bao-dao/clanbattle/model.py + sql.py    
 * 通过房间消息 JSON 序列化实现全员共享    
 */    
    
// ======================== Boss 状态 ========================    
    
/**    
 * 单个 Boss 状态    
 * 对应 zi-dong-bao-dao model.py:167-196 的 Boss 类    
 */    
data class BossState(    
    val order: Int = 0,          // 第几王 (1-5)    
    val lapNum: Int = 0,         // 当前周目    
    val currentHp: Long = 0,     // 当前血量    
    val maxHp: Long = 0,         // 最大血量    
    val stage: String = "",      // 阶段字母 (A/B/C/D)    
    val fighterNum: Int = 0      // 当前挑战人数    
) {    
    /** 血量百分比 */    
    val hpPercent: Double    
        get() = if (maxHp > 0) currentHp.toDouble() / maxHp else 0.0    
    
    /** 是否可挑战（不能超过两个周目，不能跨阶段） */    
    fun checkAvailable(clanLapNum: Int, period: Int): Boolean {    
        val stageNum = com.pcrjjc.app.util.stageToNum(stage)    
        return lapNum - clanLapNum < 2 && stageNum == period    
    }    
    
    fun toJson(): JSONObject = JSONObject().apply {    
        put("order", order)    
        put("lap_num", lapNum)    
        put("current_hp", currentHp)    
        put("max_hp", maxHp)    
        put("stage", stage)    
        put("fighter_num", fighterNum)    
    }    
    
    companion object {    
        fun fromJson(obj: JSONObject) = BossState(    
            order = obj.optInt("order", 0),    
            lapNum = obj.optInt("lap_num", 0),    
            currentHp = obj.optLong("current_hp", 0),    
            maxHp = obj.optLong("max_hp", 0),    
            stage = obj.optString("stage", ""),    
            fighterNum = obj.optInt("fighter_num", 0)    
        )    
    }    
}    
    
// ======================== 申请出刀 ========================    
    
/**    
 * 申请出刀记录    
 * 对应 zi-dong-bao-dao sql.py:265-330 的 ApplyDao    
 */    
data class ApplyRecord(    
    val playerName: String,    
    val playerQq: String,    
    val bossOrder: Int,        // 第几王 (1-5)    
    val timestamp: Long,    
    val text: String = ""    
) {    
    fun toJson(): JSONObject = JSONObject().apply {    
        put("player_name", playerName)    
        put("player_qq", playerQq)    
        put("boss_order", bossOrder)    
        put("timestamp", timestamp)    
        put("text", text)    
    }    
    
    companion object {    
        fun fromJson(obj: JSONObject) = ApplyRecord(    
            playerName = obj.optString("player_name", ""),    
            playerQq = obj.optString("player_qq", ""),    
            bossOrder = obj.optInt("boss_order", 0),    
            timestamp = obj.optLong("timestamp", 0),    
            text = obj.optString("text", "")    
        )    
    }    
}    
    
// ======================== 挂树 ========================    
    
/**    
 * 挂树记录    
 * 对应 zi-dong-bao-dao sql.py:189-262 的 TreeDao    
 */    
data class TreeRecord(    
    val playerName: String,    
    val playerQq: String,    
    val bossOrder: Int,    
    val timestamp: Long,    
    val text: String = ""    
) {    
    fun toJson(): JSONObject = JSONObject().apply {    
        put("player_name", playerName)    
        put("player_qq", playerQq)    
        put("boss_order", bossOrder)    
        put("timestamp", timestamp)    
        put("text", text)    
    }    
    
    companion object {    
        fun fromJson(obj: JSONObject) = TreeRecord(    
            playerName = obj.optString("player_name", ""),    
            playerQq = obj.optString("player_qq", ""),    
            bossOrder = obj.optInt("boss_order", 0),    
            timestamp = obj.optLong("timestamp", 0),    
            text = obj.optString("text", "")    
        )    
    }    
}    
    
// ======================== 预约 ========================    
    
/**    
 * 预约记录    
 * 对应 zi-dong-bao-dao sql.py:112-186 的 SubscribeDao    
 */    
data class SubscribeRecord(    
    val playerName: String,    
    val playerQq: String,    
    val bossOrder: Int,    
    val lapNum: Int,           // 预约的周目，0 表示当前周目    
    val text: String = ""    
) {    
    fun toJson(): JSONObject = JSONObject().apply {    
        put("player_name", playerName)    
        put("player_qq", playerQq)    
        put("boss_order", bossOrder)    
        put("lap_num", lapNum)    
        put("text", text)    
    }    
    
    companion object {    
        fun fromJson(obj: JSONObject) = SubscribeRecord(    
            playerName = obj.optString("player_name", ""),    
            playerQq = obj.optString("player_qq", ""),    
            bossOrder = obj.optInt("boss_order", 0),    
            lapNum = obj.optInt("lap_num", 0),    
            text = obj.optString("text", "")    
        )    
    }    
}    
    
// ======================== SL 记录 ========================    
    
/**    
 * SL 记录    
 * 对应 zi-dong-bao-dao sql.py:36-109 的 SLDao    
 */    
data class SLRecord(    
    val playerName: String,    
    val playerQq: String,    
    val date: Long              // PCR 日期（以凌晨5点为界）    
) {    
    fun toJson(): JSONObject = JSONObject().apply {    
        put("player_name", playerName)    
        put("player_qq", playerQq)    
        put("date", date)    
    }    
    
    companion object {    
        fun fromJson(obj: JSONObject) = SLRecord(    
            playerName = obj.optString("player_name", ""),    
            playerQq = obj.optString("player_qq", ""),    
            date = obj.optLong("date", 0)    
        )    
    }    
}    
    
// ======================== 出刀记录（战报用） ========================    
    
/**    
 * 出刀记录，用于生成战报    
 * 对应 zi-dong-bao-dao sql.py:332-515 的 RecordDao    
 */    
data class DamageRecord(    
    val pcrid: Long,    
    val name: String,    
    val time: Long,    
    val lapNum: Int,    
    val bossOrder: Int,    
    val damage: Long,    
    val flag: Double,           // 0=完整刀, 0.5=补偿, 1=尾刀    
    val battleLogId: Long = 0    
)    
    
// ======================== 会战整体状态（房间共享） ========================    
    
/**    
 * 会战整体状态，通过房间消息同步给所有成员    
 * 对应 zi-dong-bao-dao model.py:8-27 的 ClanBattle 类    
 *    
 * 序列化为 JSON 后以特殊前缀 [CB_STATE] 发送到房间聊天，    
 * 客户端收到后解析并更新本地状态。    
 */    
data class ClanBattleState(    
    val rank: Int = 0,                                  // 会战排名    
    val lapNum: Int = 0,                                // 当前周目    
    val period: Int = 0,                                // 当前阶段编号    
    val periodName: String = "",                        // 如 "B面2阶段"    
    val isMonitoring: Boolean = false,                  // 是否正在监控    
    val monitorPlayerName: String = "",                 // 监控人名称    
    val monitorAccountId: Int = -1,                     // 监控使用的账号ID    
    val bosses: List<BossState> = List(5) { BossState(order = it + 1) },    
    val applies: List<ApplyRecord> = emptyList(),       // 所有申请出刀    
    val trees: List<TreeRecord> = emptyList(),          // 所有挂树    
    val subscribes: List<SubscribeRecord> = emptyList(),// 所有预约    
    val slRecords: List<SLRecord> = emptyList(),        // 所有SL记录    
    val lastUpdateTime: Long = 0                        // 最后更新时间戳    
) {    
    // ---- 按 boss 查询 ----    
    
    fun getAppliesForBoss(bossOrder: Int): List<ApplyRecord> =    
        applies.filter { it.bossOrder == bossOrder }    
    
    fun getTreesForBoss(bossOrder: Int): List<TreeRecord> =    
        trees.filter { it.bossOrder == bossOrder }    
    
    fun getSubscribesForBoss(bossOrder: Int): List<SubscribeRecord> =    
        subscribes.filter { it.bossOrder == bossOrder }    
    
    // ---- 判断当前用户是否已操作 ----    
    
    fun hasApplied(playerQq: String, bossOrder: Int): Boolean =    
        applies.any { it.playerQq == playerQq && it.bossOrder == bossOrder }    
    
    fun hasTree(playerQq: String, bossOrder: Int): Boolean =    
        trees.any { it.playerQq == playerQq && it.bossOrder == bossOrder }    
    
    fun hasSubscribed(playerQq: String, bossOrder: Int): Boolean =    
        subscribes.any { it.playerQq == playerQq && it.bossOrder == bossOrder }    
    
    fun hasSLToday(playerQq: String, todayPcrDate: Long): Boolean =    
        slRecords.any { it.playerQq == playerQq && it.date == todayPcrDate }    
    
    // ---- JSON 序列化 ----    
    
    fun toJson(): JSONObject = JSONObject().apply {    
        put("rank", rank)    
        put("lap_num", lapNum)    
        put("period", period)    
        put("period_name", periodName)    
        put("is_monitoring", isMonitoring)    
        put("monitor_player_name", monitorPlayerName)    
        put("monitor_account_id", monitorAccountId)    
        put("bosses", JSONArray().apply { bosses.forEach { put(it.toJson()) } })    
        put("applies", JSONArray().apply { applies.forEach { put(it.toJson()) } })    
        put("trees", JSONArray().apply { trees.forEach { put(it.toJson()) } })    
        put("subscribes", JSONArray().apply { subscribes.forEach { put(it.toJson()) } })    
        put("sl_records", JSONArray().apply { slRecords.forEach { put(it.toJson()) } })    
        put("last_update_time", lastUpdateTime)    
    }    
    
    companion object {    
        /** 房间消息中用于标识会战状态的前缀 */    
        const val MESSAGE_PREFIX = "[CB_STATE]"    
    
        /** 房间消息中用于标识会战操作的前缀 */    
        const val ACTION_PREFIX = "[CB_ACTION]"    
  
        /** 房间消息中用于标识战报结果的前缀 */    
        const val REPORT_PREFIX = "[CB_REPORT]"    
    
        fun fromJson(obj: JSONObject): ClanBattleState {    
            val bossesArr = obj.optJSONArray("bosses")    
            val appliesArr = obj.optJSONArray("applies")    
            val treesArr = obj.optJSONArray("trees")    
            val subscribesArr = obj.optJSONArray("subscribes")    
            val slArr = obj.optJSONArray("sl_records")    
    
            return ClanBattleState(    
                rank = obj.optInt("rank", 0),    
                lapNum = obj.optInt("lap_num", 0),    
                period = obj.optInt("period", 0),    
                periodName = obj.optString("period_name", ""),    
                isMonitoring = obj.optBoolean("is_monitoring", false),    
                monitorPlayerName = obj.optString("monitor_player_name", ""),    
                monitorAccountId = obj.optInt("monitor_account_id", -1),    
                bosses = parseList(bossesArr) { BossState.fromJson(it) },    
                applies = parseList(appliesArr) { ApplyRecord.fromJson(it) },    
                trees = parseList(treesArr) { TreeRecord.fromJson(it) },    
                subscribes = parseList(subscribesArr) { SubscribeRecord.fromJson(it) },    
                slRecords = parseList(slArr) { SLRecord.fromJson(it) },    
                lastUpdateTime = obj.optLong("last_update_time", 0)    
            )    
        }    
    
        /** 从房间聊天消息中解析会战状态 */    
        fun fromMessage(content: String): ClanBattleState? {    
            if (!content.startsWith(MESSAGE_PREFIX)) return null    
            return try {    
                fromJson(JSONObject(content.removePrefix(MESSAGE_PREFIX)))    
            } catch (e: Exception) {    
                null    
            }    
        }    
    
        private fun <T> parseList(arr: JSONArray?, mapper: (JSONObject) -> T): List<T> {    
            if (arr == null) return emptyList()    
            return (0 until arr.length()).mapNotNull { i ->    
                try { mapper(arr.getJSONObject(i)) } catch (_: Exception) { null }    
            }    
        }    
    }    
}    
    
// ======================== 会战操作（房间消息传递） ========================    
    
/**    
 * 会战操作类型，通过房间消息同步    
 * 发送格式: [CB_ACTION]{"action":"apply","boss":1,"player_name":"xxx",...}    
 */    
enum class ClanBattleAction(val key: String) {    
    APPLY("apply"),                 // 申请出刀    
    CANCEL_APPLY("cancel_apply"),   // 取消申请    
    TREE("tree"),                   // 挂树    
    CANCEL_TREE("cancel_tree"),     // 取消挂树（下树）    
    SUBSCRIBE("subscribe"),         // 预约下一周目    
    CANCEL_SUBSCRIBE("cancel_subscribe"), // 取消预约    
    SL("sl"),                       // 记录SL    
    START_MONITOR("start_monitor"), // 开始监控    
    STOP_MONITOR("stop_monitor"),   // 停止监控    
    REQUEST_REPORT("request_report"); // 请求战报（非监控者 -> 监控者）    
    
    companion object {    
        fun fromKey(key: String): ClanBattleAction? = entries.find { it.key == key }    
    }    
}    
    
/**    
 * 会战操作消息    
 */    
data class ClanBattleActionMessage(    
    val action: ClanBattleAction,    
    val bossOrder: Int = 0,    
    val playerName: String = "",    
    val playerQq: String = "",    
    val text: String = ""    
) {    
    /** 序列化为房间消息内容 */    
    fun toMessageContent(): String {    
        val json = JSONObject().apply {    
            put("action", action.key)    
            put("boss_order", bossOrder)    
            put("player_name", playerName)    
            put("player_qq", playerQq)    
            put("text", text)    
        }    
        return "${ClanBattleState.ACTION_PREFIX}$json"    
    }    
    
    /** 生成人类可读的聊天消息 */    
    fun toReadableMessage(): String = when (action) {    
        ClanBattleAction.APPLY -> "📋 $playerName 申请出刀 ${bossOrder}王"    
        ClanBattleAction.CANCEL_APPLY -> "❌ $playerName 取消申请出刀 ${bossOrder}王"    
        ClanBattleAction.TREE -> "🌲 $playerName 挂树 ${bossOrder}王"    
        ClanBattleAction.CANCEL_TREE -> "⬇️ $playerName 下树 ${bossOrder}王"    
        ClanBattleAction.SUBSCRIBE -> "📌 $playerName 预约下一周目 ${bossOrder}王"    
        ClanBattleAction.CANCEL_SUBSCRIBE -> "❌ $playerName 取消预约 ${bossOrder}王"    
        ClanBattleAction.SL -> "🔄 $playerName 记录SL"    
        ClanBattleAction.START_MONITOR -> "▶️ $playerName 开启出刀监控"    
        ClanBattleAction.STOP_MONITOR -> "⏹️ $playerName 关闭出刀监控"    
        ClanBattleAction.REQUEST_REPORT -> "📊 $playerName 请求查看战报"    
    }    
    
    companion object {    
        fun fromMessage(content: String): ClanBattleActionMessage? {    
            if (!content.startsWith(ClanBattleState.ACTION_PREFIX)) return null    
            return try {    
                val json = JSONObject(content.removePrefix(ClanBattleState.ACTION_PREFIX))    
                val action = ClanBattleAction.fromKey(json.getString("action")) ?: return null    
                ClanBattleActionMessage(    
                    action = action,    
                    bossOrder = json.optInt("boss_order", 0),    
                    playerName = json.optString("player_name", ""),    
                    playerQq = json.optString("player_qq", ""),    
                    text = json.optString("text", "")    
                )    
            } catch (e: Exception) {    
                null    
            }    
        }    
    }    
}