package com.pcrjjc.app.data.local.entity  
  
import org.json.JSONArray  
import org.json.JSONObject  
  
/**  
 * 手动报刀数据模型，移植自 yobot_remix-stable  
 * 通过房间消息 JSON 序列化实现全员共享  
 */  
  
// ======================== Boss 配置 ========================  
  
/**  
 * Boss 血量配置，对应 yobot default_config.json 中的 boss 字段  
 */  
object BossConfig {  
    /** boss血量 [服务器][阶段][boss编号] */  
    var bossHp: Map<String, List<List<Long>>> = mapOf(  // val -> var
        "cn" to listOf(  
            listOf(6_000_000, 8_000_000, 10_000_000, 12_000_000, 20_000_000),  
            listOf(6_000_000, 8_000_000, 10_000_000, 12_000_000, 20_000_000),  
            listOf(6_000_000, 8_000_000, 10_000_000, 12_000_000, 20_000_000)  
        ),  
        "jp" to listOf(  
            listOf(6_000_000, 8_000_000, 10_000_000, 12_000_000, 15_000_000),  
            listOf(6_000_000, 8_000_000, 10_000_000, 12_000_000, 15_000_000),  
            listOf(7_000_000, 9_000_000, 13_000_000, 15_000_000, 20_000_000),  
            listOf(15_000_000, 16_000_000, 18_000_000, 19_000_000, 20_000_000)  
        ),  
        "tw" to listOf(  
            listOf(6_000_000, 8_000_000, 10_000_000, 12_000_000, 15_000_000),  
            listOf(6_000_000, 8_000_000, 10_000_000, 12_000_000, 15_000_000),  
            listOf(6_000_000, 8_000_000, 10_000_000, 12_000_000, 15_000_000)  
        )  
    )  
  
    /** 阶段对应的周目范围 [服务器][[起始周目, 结束周目], ...] */  
    var levelByCycle: Map<String, List<List<Int>>> = mapOf(  // val -> var  
        "cn" to listOf(listOf(1, 3), listOf(4, 10), listOf(11, 999)),  
        "jp" to listOf(listOf(1, 3), listOf(4, 10), listOf(11, 45), listOf(46, 999)),  
        "tw" to listOf(listOf(1, 3), listOf(4, 10), listOf(11, 999))  
    )  
  
    /** 根据周目获取阶段编号 */  
    fun levelByCycle(cycle: Int, gameServer: String): Int {  
        val levels = levelByCycle[gameServer] ?: return 0  
        for ((index, lv) in levels.withIndex()) {  
            if (cycle in lv[0]..lv[1]) return index  
        }  
        return levels.size - 1  
    }  
  
    /** 获取指定服务器、阶段、boss编号的满血量 */  
    fun getFullHp(gameServer: String, level: Int, bossIndex: Int): Long {  
        val serverHp = bossHp[gameServer] ?: return 0  
        if (level >= serverHp.size) return serverHp.last().getOrElse(bossIndex) { 0 }  
        return serverHp[level].getOrElse(bossIndex) { 0 }  
    }  
  
    /** 阶段名称 */  
    fun levelName(level: Int): String = when (level) {  
        0 -> "A"  
        1 -> "B"  
        2 -> "C"  
        3 -> "D"  
        4 -> "E"  
        else -> "${level + 1}"  
    }  
}  
  
// ======================== 手动报刀 Boss 状态 ========================  
  
/**  
 * 单个 Boss 的手动报刀状态  
 */  
data class ManualBossState(  
    val bossNum: Int = 0,  
    val currentHp: Long = 0,        // = now_cycle_boss_health[boss_num]  
    val maxHp: Long = 0,            // 满血量（用于显示血条）  
    val cycle: Int = 1,  
    val isNext: Boolean = false,    // 保留向后兼容，可由 currentHp <= 0 推导  
    val nextCycleHp: Long = -1      // = next_cycle_boss_health[boss_num]，-1表示未初始化  
) {  
    val hpPercent: Double  
        get() = if (maxHp > 0) currentHp.toDouble() / maxHp else 0.0  
  
    fun toJson(): JSONObject = JSONObject().apply {  
        put("boss_num", bossNum)  
        put("current_hp", currentHp)  
        put("max_hp", maxHp)  
        put("cycle", cycle)  
        put("is_next", isNext)  
        put("next_cycle_hp", nextCycleHp)   // ← 新增  
    }
	
    companion object {  
        fun fromJson(obj: JSONObject) = ManualBossState(  
            bossNum = obj.optInt("boss_num", 0),  
            currentHp = obj.optLong("current_hp", 0),  
            maxHp = obj.optLong("max_hp", 0),  
            cycle = obj.optInt("cycle", 1),  
            isNext = obj.optBoolean("is_next", false),  
            nextCycleHp = obj.optLong("next_cycle_hp", -1)  // ← 新增，-1=旧数据兼容   
        )  
    }  
}  
  
// ======================== 出刀中的成员信息 ========================  
  
/**  
 * 正在挑战某个boss的成员信息  
 * 对应 yobot challenging_member_list 中的单个成员  
 */  
data class ChallengingMember(  
    val playerQq: String,  
    val playerName: String,  
    val bossNum: Int,                   // 挑战哪个王  
    val isContinue: Boolean = false,    // 是否补偿刀  
    val behalf: String? = null,         // 代刀人QQ  
    val behalfName: String? = null,     // 代刀人名字  
    val seconds: Int = 0,              // 暂停报伤害的秒数  
    val reportedDamage: Long = 0,      // 暂停报的伤害（万）  
    val isOnTree: Boolean = false,     // 是否挂树  
    val treeMessage: String? = null    // 挂树留言  
) {  
    fun toJson(): JSONObject = JSONObject().apply {  
        put("player_qq", playerQq)  
        put("player_name", playerName)  
        put("boss_num", bossNum)  
        put("is_continue", isContinue)  
        put("behalf", behalf ?: "")  
        put("behalf_name", behalfName ?: "")  
        put("seconds", seconds)  
        put("reported_damage", reportedDamage)  
        put("is_on_tree", isOnTree)  
        put("tree_message", treeMessage ?: "")  
    }  
  
    companion object {  
        fun fromJson(obj: JSONObject) = ChallengingMember(  
            playerQq = obj.optString("player_qq", ""),  
            playerName = obj.optString("player_name", ""),  
            bossNum = obj.optInt("boss_num", 0),  
            isContinue = obj.optBoolean("is_continue", false),  
            behalf = obj.optString("behalf", "").ifBlank { null },  
            behalfName = obj.optString("behalf_name", "").ifBlank { null },  
            seconds = obj.optInt("seconds", 0),  
            reportedDamage = obj.optLong("reported_damage", 0),  
            isOnTree = obj.optBoolean("is_on_tree", false),  
            treeMessage = obj.optString("tree_message", "").ifBlank { null }  
        )  
    }  
}  
  
// ======================== 出刀记录 ========================  
  
/**  
 * 单条出刀记录，对应 yobot Clan_challenge 表  
 */  
data class ManualChallengeRecord(  
    val id: Long = 0,                   // 自增ID  
    val playerQq: String,  
    val playerName: String,  
    val pcrDate: Int = 0,               // PCR日期  
    val bossCycle: Int = 1,             // 出刀时的周目  
    val bossNum: Int = 1,               // 几王  
    val bossHealthRemain: Long = 0,     // boss剩余血量（0=击杀）  
    val challengeDamage: Long = 0,      // 造成的伤害  
    val isContinue: Boolean = false,    // 是否补偿刀  
    val behalf: String? = null,         // 代刀人QQ  
    val behalfName: String? = null,     // 代刀人名字  
    val timestamp: Long = System.currentTimeMillis()  
) {  
    /** 是否是尾刀（击杀boss且不是补偿刀） */  
    val isTailBlade: Boolean get() = bossHealthRemain == 0L && !isContinue  
  
    fun toJson(): JSONObject = JSONObject().apply {  
        put("id", id)  
        put("player_qq", playerQq)  
        put("player_name", playerName)  
        put("pcr_date", pcrDate)  
        put("boss_cycle", bossCycle)  
        put("boss_num", bossNum)  
        put("boss_health_remain", bossHealthRemain)  
        put("challenge_damage", challengeDamage)  
        put("is_continue", isContinue)  
        put("behalf", behalf ?: "")  
        put("behalf_name", behalfName ?: "")  
        put("timestamp", timestamp)  
    }  
  
    companion object {  
        fun fromJson(obj: JSONObject) = ManualChallengeRecord(  
            id = obj.optLong("id", 0),  
            playerQq = obj.optString("player_qq", ""),  
            playerName = obj.optString("player_name", ""),  
            pcrDate = obj.optInt("pcr_date", 0),  
            bossCycle = obj.optInt("boss_cycle", 1),  
            bossNum = obj.optInt("boss_num", 1),  
            bossHealthRemain = obj.optLong("boss_health_remain", 0),  
            challengeDamage = obj.optLong("challenge_damage", 0),  
            isContinue = obj.optBoolean("is_continue", false),  
            behalf = obj.optString("behalf", "").ifBlank { null },  
            behalfName = obj.optString("behalf_name", "").ifBlank { null },  
            timestamp = obj.optLong("timestamp", 0)  
        )  
    }  
}  
  
// ======================== 公会成员 ========================  
  
/**  
 * 公会成员  
 */  
data class GuildMember(  
    val playerQq: String,  
    val playerName: String,  
    val lastSaveSlot: Int = 0           // 上次SL的PCR日期  
) {  
    fun toJson(): JSONObject = JSONObject().apply {  
        put("player_qq", playerQq)  
        put("player_name", playerName)  
        put("last_save_slot", lastSaveSlot)  
    }  
  
    companion object {  
        fun fromJson(obj: JSONObject) = GuildMember(  
            playerQq = obj.optString("player_qq", ""),  
            playerName = obj.optString("player_name", ""),  
            lastSaveSlot = obj.optInt("last_save_slot", 0)  
        )  
    }  
}  
  
// ======================== 预约记录 ========================  
  
/**  
 * 手动报刀的预约记录  
 */  
data class ManualSubscribeRecord(  
    val playerQq: String,  
    val playerName: String,  
    val bossNum: Int,  
    val note: String = ""  
) {  
    fun toJson(): JSONObject = JSONObject().apply {  
        put("player_qq", playerQq)  
        put("player_name", playerName)  
        put("boss_num", bossNum)  
        put("note", note)  
    }  
  
    companion object {  
        fun fromJson(obj: JSONObject) = ManualSubscribeRecord(  
            playerQq = obj.optString("player_qq", ""),  
            playerName = obj.optString("player_name", ""),  
            bossNum = obj.optInt("boss_num", 0),  
            note = obj.optString("note", "")  
        )  
    }  
}  
  
// ======================== 手动报刀整体状态 ========================  
  
/**  
 * 手动报刀的完整状态，通过房间消息同步  
 * 移植自 yobot_remix-stable 的 Clan_group 数据模型  
 */  
data class ManualBattleState(  
    val isCreated: Boolean = false,                         // 公会是否已创建  
    val gameServer: String = "cn",                          // 游戏服务器  
    val bossCycle: Int = 1,                                 // 当前周目  
    val bosses: List<ManualBossState> = List(5) { ManualBossState(bossNum = it + 1) },  
    val challengingMembers: List<ChallengingMember> = emptyList(),   // 正在出刀的成员  
    val members: List<GuildMember> = emptyList(),                    // 公会成员列表  
    val records: List<ManualChallengeRecord> = emptyList(),          // 出刀记录  
    val subscribes: List<ManualSubscribeRecord> = emptyList(),       // 预约列表  
    val lastUpdateTime: Long = 0,  
    val nextRecordId: Long = 1                              // 下一条记录的ID  
) {  
    /** 获取某个boss正在出刀的成员 */  
    fun getChallengersForBoss(bossNum: Int): List<ChallengingMember> =  
        challengingMembers.filter { it.bossNum == bossNum }  
  
    /** 获取某个boss挂树的成员 */  
    fun getTreesForBoss(bossNum: Int): List<ChallengingMember> =  
        challengingMembers.filter { it.bossNum == bossNum && it.isOnTree }  
  
    /** 获取某个boss的预约 */  
    fun getSubscribesForBoss(bossNum: Int): List<ManualSubscribeRecord> =  
        subscribes.filter { it.bossNum == bossNum }  
  
    /** 检查某人是否已申请出刀 */  
    fun hasApplied(playerQq: String): Boolean =  
        challengingMembers.any { it.playerQq == playerQq }  
  
    /** 获取某人申请出刀的boss编号，未申请返回0 */  
    fun getAppliedBossNum(playerQq: String): Int =  
        challengingMembers.firstOrNull { it.playerQq == playerQq }?.bossNum ?: 0  
  
    /** 检查某人是否挂树 */  
    fun isOnTree(playerQq: String): Boolean =  
        challengingMembers.any { it.playerQq == playerQq && it.isOnTree }  
  
    /** 检查某人是否已预约某boss */  
    fun hasSubscribed(playerQq: String, bossNum: Int): Boolean =  
        subscribes.any { it.playerQq == playerQq && it.bossNum == bossNum }  
  
    /** 检查某人今日是否已SL */  
    fun hasSLToday(playerQq: String, todayPcrDate: Int): Boolean =  
        members.any { it.playerQq == playerQq && it.lastSaveSlot == todayPcrDate }  
  
    /** 是否是公会成员 */  
    fun isMember(playerQq: String): Boolean =  
        members.any { it.playerQq == playerQq }  
  
    /** 获取当前阶段编号 */  
    val currentLevel: Int get() = BossConfig.levelByCycle(bossCycle, gameServer)  
  
    /** 获取当前阶段名称 */  
    val currentLevelName: String get() = BossConfig.levelName(currentLevel)  
  
    // ---- JSON 序列化 ----  
  
    fun toJson(): JSONObject = JSONObject().apply {  
        put("is_created", isCreated)  
        put("game_server", gameServer)  
        put("boss_cycle", bossCycle)  
        put("bosses", JSONArray().apply { bosses.forEach { put(it.toJson()) } })  
        put("challenging_members", JSONArray().apply { challengingMembers.forEach { put(it.toJson()) } })  
        put("members", JSONArray().apply { members.forEach { put(it.toJson()) } })  
        put("records", JSONArray().apply { records.forEach { put(it.toJson()) } })  
        put("subscribes", JSONArray().apply { subscribes.forEach { put(it.toJson()) } })  
        put("last_update_time", lastUpdateTime)  
        put("next_record_id", nextRecordId)  
    }  
  
    companion object {  
        const val MESSAGE_PREFIX = "[MB_STATE]"  
  
        fun fromJson(obj: JSONObject): ManualBattleState {  
            return ManualBattleState(  
                isCreated = obj.optBoolean("is_created", false),  
                gameServer = obj.optString("game_server", "cn"),  
                bossCycle = obj.optInt("boss_cycle", 1),  
                bosses = parseList(obj.optJSONArray("bosses")) { ManualBossState.fromJson(it) },  
                challengingMembers = parseList(obj.optJSONArray("challenging_members")) { ChallengingMember.fromJson(it) },  
                members = parseList(obj.optJSONArray("members")) { GuildMember.fromJson(it) },  
                records = parseList(obj.optJSONArray("records")) { ManualChallengeRecord.fromJson(it) },  
                subscribes = parseList(obj.optJSONArray("subscribes")) { ManualSubscribeRecord.fromJson(it) },  
                lastUpdateTime = obj.optLong("last_update_time", 0),  
                nextRecordId = obj.optLong("next_record_id", 1)  
            )  
        }  
  
        fun fromMessage(content: String): ManualBattleState? {  
            if (!content.startsWith(MESSAGE_PREFIX)) return null  
            return try {  
                fromJson(JSONObject(content.removePrefix(MESSAGE_PREFIX)))  
            } catch (_: Exception) {  
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