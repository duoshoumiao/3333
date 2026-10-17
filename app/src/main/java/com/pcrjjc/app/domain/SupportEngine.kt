package com.pcrjjc.app.domain  
  
import android.util.Log  
import com.pcrjjc.app.data.remote.PcrClient  
  
/**  
 * 我的支援引擎，移植自 PPPPPP autopcr/module/modules/tools.py 的 get_my_support  
 *  
 * - loadSupport: 调 /support_unit/get_setting 拿 clan_support_units(地下城+会战) 与  
 *   friend_support_units(好友)，再调 /load/index 拿每个角色的 ex_equip_slot /  
 *   cb_ex_equip_slot 和 ex_equips 背包(serial_id -> 装备详情)  
 * - changeSupport: 调 /support_unit/change_setting 更换挂载角色  
 * - equipEx: 调 /unit/equip_ex 穿/卸普通EX或会战EX（复用 ExEquipEngine 的请求格式）  
 */  
class SupportEngine {  
  
    companion object {  
        private const val TAG = "SupportEngine"  
  
        // 对应 PPPPPP eClanSupportMemberType：地下城=3/4，会战=1/2（若实测不符按返回的 position 值调整）  
        val DUNGEON_POSITIONS = setOf(3, 4)  
        val CB_POSITIONS = setOf(1, 2)  
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
        val unitId: Long,  
        val supportStartTime: Long = 0L,  
        // 普通EX槽 / 会战EX槽: slot号 -> serial_id  
        val exSlots: List<Pair<Int, Long>> = emptyList(),  
        val cbExSlots: List<Pair<Int, Long>> = emptyList()  
    )  
  
    data class SupportData(  
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
        // ⚠ 端点路径需与 PPPPPP pcrclient.py 中 support_unit_get_setting 的  
        //    实际 path 一致（一般为 /support_unit/get_setting，对照 requests.py 核实）  
        val setting = client.callApi("/support_unit/get_setting", mutableMapOf())  
        val loadIndex = client.callApi("/load/index", mutableMapOf("carrier" to "OPPO"))  
  
        // 1. 解析支援位  
        fun parseSupports(key: String): List<SupportSlot> {  
            val list = setting[key] as? List<Map<String, Any?>> ?: return emptyList()  
            return list.mapNotNull { s ->  
                val unitId = (s["unit_id"] as? Number)?.toLong() ?: 0L  
                val position = (s["position"] as? Number)?.toInt() ?: return@mapNotNull null  
                if (unitId == 0L) return@mapNotNull null  
                SupportSlot(  
                    position = position,  
                    unitId = unitId,  
                    supportStartTime = (s["support_start_time"] as? Number)?.toLong() ?: 0L  
                )  
            }  
        }  
  
        val clanUnits = parseSupports("clan_support_units")  
        val dungeon = clanUnits.filter { it.position in DUNGEON_POSITIONS }  
        val clanBattle = clanUnits.filter { it.position in CB_POSITIONS }  
        val friend = parseSupports("friend_support_units")  
            .filter { it.position in FRIEND_POSITIONS }  
  
        // 2. 解析 unit_list 的 ex_equip_slot / cb_ex_equip_slot  
        val unitList = loadIndex["unit_list"] as? List<Map<String, Any?>> ?: emptyList()  
        val unitExSlots = LinkedHashMap<Long, Pair<List<Pair<Int, Long>>, List<Pair<Int, Long>>>>()  
        for (unit in unitList) {  
            val unitId = (unit["id"] as? Number)?.toLong() ?: continue  
            fun slotsOf(field: String) =  
                (unit[field] as? List<Map<String, Any?>> ?: emptyList()).map { s ->  
                    ((s["slot"] as? Number)?.toInt() ?: 0) to  
                            ((s["serial_id"] as? Number)?.toLong() ?: 0L)  
                }  
            unitExSlots[unitId] = slotsOf("ex_equip_slot") to slotsOf("cb_ex_equip_slot")  
        }  
  
        // 3. EX背包 serial_id -> 详情  
        val exInventory = HashMap<Long, ExEquipItem>()  
        val exList = (loadIndex["ex_equips"] as? List<Map<String, Any?>>) ?: emptyList()  
        for (ex in exList) {  
            val serial = (ex["serial_id"] as? Number)?.toLong() ?: continue  
            exInventory[serial] = ExEquipItem(  
                serialId = serial,  
                exEquipId = (ex["ex_equipment_id"] as? Number)?.toInt() ?: 0,  
                rank = (ex["rank"] as? Number)?.toInt() ?: 0,  
                enhancementPt = (ex["enhancement_pt"] as? Number)?.toInt() ?: 0  
            )  
        }  
  
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
    // ⚠ 参数格式对照 PPPPPP requests.py 的 SupportUnitChangeSettingRequest 核实  
    //    原版调用: support_unit_change_setting(support_type=1, position, change_type=2, unit_id)  
    suspend fun changeSupport(  
        client: PcrClient,  
        supportType: Int,   // 1=公会支援(地下城/会战), 2=好友支援  
        position: Int,  
        unitId: Long  
    ) {  
        client.callApi(  
            "/support_unit/change_setting",  
            mutableMapOf(  
                "support_type" to supportType,  
                "position" to position,  
                "change_type" to 2,  
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