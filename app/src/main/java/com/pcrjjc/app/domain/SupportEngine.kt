package com.pcrjjc.app.domain  
  
import android.util.Log  
import com.pcrjjc.app.data.remote.PcrClient  
  
/**  
 * 我的支援引擎，移植自 PPPPPP autopcr/module/modules/tools.py 的 get_my_support  
 *  
 * - loadSupport: 调 /support_unit/get_setting 拿 clan_support_units(地下城+会战) 与  
 *   friend_support_units(好友)，再调 /load/index 拿每个角色的 ex_equip_slot /  
 *   cb_ex_equip_slot 和 user_ex_equip 背包(serial_id -> 装备详情)  
 * - changeSupport: 调 /support_unit/change_setting 更换挂载角色（action 1=挂上 2=撤下）  
 * - equipEx: 调 /unit/equip_ex 穿/卸普通EX或会战EX（复用 ExEquipEngine 的请求格式）  
 */  
class SupportEngine {  
  
    companion object {  
        private const val TAG = "SupportEngine"  
  
        // 对应 PPPPPP tools.py:1199-1200：  
        // 地下城=clan_support_units中position 1/2，会战=3/4（eClanSupportMemberType）  
        val DUNGEON_POSITIONS = setOf(1, 2)  
        val CB_POSITIONS = setOf(3, 4)  
        val FRIEND_POSITIONS = setOf(1, 2)  
    }  
  
    data class ExEquipItem(  
        val serialId: Long,  
        val exEquipId: Int,  
        val rank: Int,  
        val enhancementPt: Int  
    )  
  
    data class SupportSlot(  
        val position: Int,  
        val unitId: Long,          // 0 = 空支援位  
        val supportStartTime: Long = 0L,  
        // 普通EX槽 / 会战EX槽: slot号 -> serial_id  
        val exSlots: List<Pair<Int, Long>> = emptyList(),  
        val cbExSlots: List<Pair<Int, Long>> = emptyList()  
    )  
  
    data class SupportData(  
        // serial_id -> (持有者unitId, 槽位号) ，普通EX  
        val exWearer: Map<Long, Pair<Long, Int>>,  
        // serial_id -> (持有者unitId, 槽位号) ，会战EX  
        val cbExWearer: Map<Long, Pair<Long, Int>>,
		val dungeon: List<SupportSlot>,  
        val clanBattle: List<SupportSlot>,  
        val friend: List<SupportSlot>,  
        // serial_id -> 装备详情（EX背包）  
        val exInventory: Map<Long, ExEquipItem>,  
        // unit_id -> (普通EX槽, 会战EX槽)，含未挂支援的角色  
        val unitExSlots: Map<Long, Pair<List<Pair<Int, Long>>, List<Pair<Int, Long>>>>,  
        // 会战EX限制 serial_id 列表  
        val cbExRestriction: Set<Long>  
    )  
  
    // ---------- 读取 ----------  
    suspend fun loadSupport(client: PcrClient): SupportData {  
        val setting = client.callApi("/support_unit/get_setting", mutableMapOf())  
        val loadIndex = client.callApi("/load/index", mutableMapOf("carrier" to "OPPO"))  
  
        // 1. 解析支援位（保留 unit_id=0 的空槽，否则空位无法点击进入更换）  
        fun parseSupports(key: String): List<SupportSlot> {  
            val list = setting[key] as? List<Map<String, Any?>> ?: return emptyList()  
            return list.mapNotNull { s ->  
                val position = (s["position"] as? Number)?.toInt() ?: return@mapNotNull null  
                SupportSlot(  
                    position = position,  
                    unitId = (s["unit_id"] as? Number)?.toLong() ?: 0L,  
                    supportStartTime = (s["support_start_time"] as? Number)?.toLong() ?: 0L  
                )  
            }  
        }  
  
        // 补齐空支援位：服务器可能不返回 unit_id=0 的条目，强制每个 position 都有槽  
        fun fillMissing(parsed: List<SupportSlot>, positions: Set<Int>): List<SupportSlot> {  
            val existing = parsed.associateBy { it.position }.toMutableMap()  
            positions.forEach { p ->  
                existing.putIfAbsent(p, SupportSlot(position = p, unitId = 0L))  
            }  
            return existing.values.sortedBy { it.position }  
        }  
  
        val clanUnits = parseSupports("clan_support_units")  
        val dungeon = fillMissing(  
            clanUnits.filter { it.position in DUNGEON_POSITIONS }, DUNGEON_POSITIONS)  
        val clanBattle = fillMissing(  
            clanUnits.filter { it.position in CB_POSITIONS }, CB_POSITIONS)  
        val friend = fillMissing(  
            parseSupports("friend_support_units").filter { it.position in FRIEND_POSITIONS },  
            FRIEND_POSITIONS)  
  
        // 2. 解析 unit_list 的 ex_equip_slot / cb_ex_equip_slot  
        val unitList = loadIndex["unit_list"] as? List<Map<String, Any?>> ?: emptyList()  
        val unitExSlots = LinkedHashMap<Long, Pair<List<Pair<Int, Long>>, List<Pair<Int, Long>>>>()  
        for (unit in unitList) {  
            // serial -> 持有者反查表（用于显示"装备在谁身上"）  
			val exWearer = HashMap<Long, Pair<Long, Int>>()  
			val cbExWearer = HashMap<Long, Pair<Long, Int>>()  
			for ((uid, slots) in unitExSlots) {  
				slots.first.forEach { (slot, serial) ->  
					if (serial != 0L) exWearer[serial] = uid to slot  
				}  
				slots.second.forEach { (slot, serial) ->  
					if (serial != 0L) cbExWearer[serial] = uid to slot  
				}  
			}
			val unitId = (unit["id"] as? Number)?.toLong() ?: continue  
            fun slotsOf(field: String) =  
                (unit[field] as? List<Map<String, Any?>> ?: emptyList()).map { s ->  
                    ((s["slot"] as? Number)?.toInt() ?: 0) to  
                            ((s["serial_id"] as? Number)?.toLong() ?: 0L)  
                }  
            unitExSlots[unitId] = slotsOf("ex_equip_slot") to slotsOf("cb_ex_equip_slot")  
        }  
  
        // 3. EX背包 serial_id -> 详情  
        // /load/index 里字段名是 user_ex_equip，不是 ex_equips  
        val exInventory = HashMap<Long, ExEquipItem>()  
        val exList = (loadIndex["user_ex_equip"] as? List<Map<String, Any?>>) ?: emptyList()  
        for (ex in exList) {  
            val serial = (ex["serial_id"] as? Number)?.toLong() ?: continue  
            exInventory[serial] = ExEquipItem(  
                serialId = serial,  
                exEquipId = (ex["ex_equipment_id"] as? Number)?.toInt() ?: 0,  
                rank = (ex["rank"] as? Number)?.toInt() ?: 0,  
                enhancementPt = (ex["enhancement_pt"] as? Number)?.toInt() ?: 0  
            )  
        }  
        Log.i(TAG, "exInventory size=${exInventory.size}")  
  
        // 4. 会战EX限制  
        val cbRestriction = HashSet<Long>()  
        val restrictions = loadIndex["user_clan_battle_ex_equip_restriction"]  
                as? List<Map<String, Any?>> ?: emptyList()  
        for (r in restrictions) {  
            (r["serial_id"] as? Number)?.toLong()?.let { cbRestriction.add(it) }  
        }  
  
        // 给每个支援位附上该角色的EX槽信息  
        fun attach(slot: SupportSlot) = slot.copy(  
            exSlots = unitExSlots[slot.unitId]?.first ?: emptyList(),  
            cbExSlots = unitExSlots[slot.unitId]?.second ?: emptyList()  
        )  
  
        return SupportData(  
            dungeon = dungeon.map { attach(it) },  
            clanBattle = clanBattle.map { attach(it) },  
            friend = friend.map { attach(it) },  
            exInventory = exInventory,  
            unitExSlots = unitExSlots,  
            cbExRestriction = cbRestriction  
        )  
    }  
  
    // ---------- 更换支援角色 ----------  
    // 对照 PPPPPP requests.py SupportUnitChangeSettingRequest：字段为 action  
    // action: 1=挂上支援, 2=撤下支援  
    suspend fun changeSupport(  
        client: PcrClient,  
        supportType: Int,   // 1=公会支援(地下城/会战), 2=好友支援  
        position: Int,  
        unitId: Long,  
        action: Int = 1  
    ) {  
        client.callApi(  
            "/support_unit/change_setting",  
            mutableMapOf(  
                "support_type" to supportType,  
                "position" to position,  
                "action" to action,   // 1=挂上支援，2=撤下支援  
                "unit_id" to unitId  
            )  
        )  
    }  
  
    // ---------- EX装备穿戴（isCb=false 普通EX，true 会战EX） ----------  
    suspend fun equipEx(  
        client: PcrClient,  
        unitId: Long,  
        slots: List<Pair<Int, Long>>,  // slot -> serial_id (0=卸下)  
        isCb: Boolean  
    ) {  
        val slotList = slots.map { (slot, serial) ->  
            mutableMapOf<String, Any?>("slot" to slot, "serial_id" to serial)  
        }  
        val changeUnit = if (isCb) {  
            mutableMapOf<String, Any?>(  
                "unit_id" to unitId,  
                "ex_equip_slot" to null,  
                "cb_ex_equip_slot" to slotList  
            )  
        } else {  
            mutableMapOf<String, Any?>(  
                "unit_id" to unitId,  
                "ex_equip_slot" to slotList,  
                "cb_ex_equip_slot" to null  
            )  
        }  
        client.callApi(  
            "/unit/equip_ex",  
            mutableMapOf("ex_equip_change_unit_list" to listOf(changeUnit))  
        )  
        Log.i(TAG, "equipEx unit=$unitId isCb=$isCb slots=$slots")  
    }  
}