package com.pcrjjc.app.domain  
  
import android.util.Log  
import com.pcrjjc.app.data.remote.PcrClient  
import com.pcrjjc.app.util.ExMeta  
  
/**  
 * 我的支援引擎，移植自 PPPPPP autopcr/module/modules/tools.py 的 get_my_support  
 *  
 * - loadSupport: 调 /support_unit/get_setting 拿 clan_support_units(地下城+会战) 与  
 *   friend_support_units(好友)，再调 /load/index 拿每个角色的 ex_equip_slot /  
 *   cb_ex_equip_slot 和 user_ex_equip 背包(serial_id -> 装备详情，含 sub_status 词条)  
 * - changeSupport: 调 /support_unit/change_setting 更换挂载角色（action 1=挂上 2=撤下）  
 * - equipEx: 四步事务穿EX（对齐 PPPPPP one_click_ex_equip tools.py:2001-2039）  
 * - autoEquipCbEx: 会战EX自动穿戴（对齐 PPPPPP「挂会战支援」）  
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
        val enhancementPt: Int,  
        // user_ex_equip.sub_status：彩装随机词条，每项 (status=属性类型, step=档位)  
        // 对齐 PPPPPP common.py ExtraEquipSubStatus  
        val subStatus: List<Pair<Int, Int>> = emptyList()  
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
        val dungeon: List<SupportSlot>,  
        val clanBattle: List<SupportSlot>,  
        val friend: List<SupportSlot>,  
        // serial_id -> 装备详情（EX背包）  
        val exInventory: Map<Long, ExEquipItem>,  
        // unit_id -> (普通EX槽, 会战EX槽)，含未挂支援的角色  
        val unitExSlots: Map<Long, Pair<List<Pair<Int, Long>>, List<Pair<Int, Long>>>>,  
        // 会战EX限制 serial_id 列表  
        val cbExRestriction: Set<Long>,  
        // serial_id -> (unitId, slotNum)，装备正被谁穿（普通EX/会战EX分开统计）  
        val exWearer: Map<Long, Pair<Long, Int>> = emptyMap(),  
        val cbExWearer: Map<Long, Pair<Long, Int>> = emptyMap()  
    )  
  
    // ---------- 读取 ----------  
    suspend fun loadSupport(client: PcrClient): SupportData {  
        val setting = client.callApi("/support_unit/get_setting", mutableMapOf())  
        val loadIndex = client.callApi("/load/index", mutableMapOf("carrier" to "OPPO"))  
  
        // 1. 解析支援位（保留 unit_id=0 的空槽，界面上显示可点击占位框）  
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
  
        // 补齐空支援位：服务器可能不返回 unit_id=0 的条目  
        fun fillMissing(parsed: List<SupportSlot>, positions: Set<Int>): List<SupportSlot> {  
            val existing = parsed.associateBy { it.position }.toMutableMap()  
            positions.forEach { p -> existing.putIfAbsent(p, SupportSlot(position = p, unitId = 0L)) }  
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
            val unitId = (unit["id"] as? Number)?.toLong() ?: continue  
            fun slotsOf(field: String) =  
                (unit[field] as? List<Map<String, Any?>> ?: emptyList()).map { s ->  
                    ((s["slot"] as? Number)?.toInt() ?: 0) to  
                            ((s["serial_id"] as? Number)?.toLong() ?: 0L)  
                }  
            unitExSlots[unitId] = slotsOf("ex_equip_slot") to slotsOf("cb_ex_equip_slot")  
        }  
  
        // 3. EX背包 serial_id -> 详情  
        // ⚠ /load/index 里字段名是 user_ex_equip，不是 ex_equips  
        val exInventory = HashMap<Long, ExEquipItem>()  
        val exList = (loadIndex["user_ex_equip"] as? List<Map<String, Any?>>) ?: emptyList()  
        for (ex in exList) {  
            val serial = (ex["serial_id"] as? Number)?.toLong() ?: continue  
            // 彩装随机副词条 sub_status: [{status, step}]（PPPPPP ExtraEquipSubStatus）  
            val subStatus = (ex["sub_status"] as? List<Map<String, Any?>> ?: emptyList())  
                .map { s ->  
                    ((s["status"] as? Number)?.toInt() ?: 0) to  
                            ((s["step"] as? Number)?.toInt() ?: 0)  
                }  
            exInventory[serial] = ExEquipItem(  
                serialId = serial,  
                exEquipId = (ex["ex_equipment_id"] as? Number)?.toInt() ?: 0,  
                rank = (ex["rank"] as? Number)?.toInt() ?: 0,  
                enhancementPt = (ex["enhancement_pt"] as? Number)?.toInt() ?: 0,  
                subStatus = subStatus  
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
  
        // 5. serial_id -> (unitId, slotNum) 反查：这件EX正穿在谁的哪个槽  
        val exWearer = HashMap<Long, Pair<Long, Int>>()  
        val cbExWearer = HashMap<Long, Pair<Long, Int>>()  
        for ((unitId, pair) in unitExSlots) {  
            pair.first.forEach { (slot, serial) ->  
                if (serial != 0L) exWearer[serial] = unitId to slot  
            }  
            pair.second.forEach { (slot, serial) ->  
                if (serial != 0L) cbExWearer[serial] = unitId to slot  
            }  
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
            cbExRestriction = cbRestriction,  
            exWearer = exWearer,  
            cbExWearer = cbExWearer  
        )  
    }  
  
    // ---------- 更换支援角色 ----------  
    // PPPPPP requests.py SupportUnitChangeSettingRequest:  
    //   support_type(1=公会 2=好友), position, action(1=挂上 2=撤下), unit_id  
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
  
    // ---------- EX装备穿戴 ----------  
    /**  
     * 对齐 PPPPPP tools.py one_click_ex_equip 的四步事务（tools.py:2001-2039）：  
     * ① 把目标 serial 从其他角色身上卸下（isCb 则查 cbExWearer）  
     * ② 清空目标角色"将被改动的槽"和"serial 冲突的槽"  
     * ③ 给目标角色穿上新装备  
     * ④ 把目标角色被替换下的旧装备换给原持有者（互换，serial=0 跳过）  
     *  
     * @param slots slot -> serial_id (0=卸下)  
     * @param isCb  false=普通EX, true=会战EX  
     * @param data  当前 SupportData（用于 wearer 反查 + 目标当前槽）  
     */  
    suspend fun equipEx(  
        client: PcrClient,  
        unitId: Long,  
        slots: List<Pair<Int, Long>>,  
        isCb: Boolean,  
        data: SupportData  
    ) {  
        val wearer = if (isCb) data.cbExWearer else data.exWearer  
        val curSlots = data.unitExSlots[unitId]  
            ?.let { if (isCb) it.second else it.first } ?: emptyList()  
        val slotsToChange = slots.map { it.first }.toSet()  
        val usedSerials = slots.map { it.second }.filter { it != 0L }.toSet()  
  
        // 互换配对：(原持有者unitId, 原持有者slot, 目标该槽换下的旧serial)  
        data class Swap(val ownerUid: Long, val ownerSlot: Int, val oldSerial: Long)  
        val swapPairs = mutableListOf<Swap>()  
  
        // Step 1: 从其他角色身上卸下要穿的 serial  
        for ((slotNum, serial) in slots) {  
            if (serial == 0L) continue  
            val owner = wearer[serial] ?: continue  
            if (owner.first == unitId) continue  
            // 目标该槽的旧装备，之后换给对方  
            val oldSerial = curSlots.firstOrNull { it.first == slotNum }?.second ?: 0L  
            swapPairs += Swap(owner.first, owner.second, oldSerial)  
            callEquipEx(client, owner.first, listOf(owner.second to 0L), isCb)  
        }  
  
        // Step 2: 清空目标角色将被改动或 serial 冲突的槽  
        val clearList = curSlots.filter { (slot, serial) ->  
            serial != 0L && (slot in slotsToChange || serial in usedSerials)  
        }.map { it.first to 0L }  
        if (clearList.isNotEmpty()) callEquipEx(client, unitId, clearList, isCb)  
  
        // Step 3: 穿上新装备  
        if (slots.isNotEmpty()) callEquipEx(client, unitId, slots, isCb)  
  
        // Step 4: 把旧装备换给原持有者（互换）  
        for (s in swapPairs) {  
            if (s.oldSerial != 0L) {  
                callEquipEx(client, s.ownerUid, listOf(s.ownerSlot to s.oldSerial), isCb)  
            }  
        }  
        Log.i(TAG, "equipEx unit=$unitId isCb=$isCb slots=$slots swaps=${swapPairs.size}")  
    }  
  
    private suspend fun callEquipEx(  
        client: PcrClient, unitId: Long,  
        slots: List<Pair<Int, Long>>, isCb: Boolean  
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
    }  
  
    // ---------- 会战EX自动穿戴（套用 PPPPPP「挂会战支援」） ----------  
    /**  
     * 为会战支援位角色自动选择最优会战EX并穿戴。  
     * 选取规则：排除 cbExRestriction；每个槽按 unit_ex_equipment_slot 的类别匹配  
     * （category == slot_category_N，对齐 exequip.py:740-751 与 tools.py:1810），  
     * 排序：会战专用 cbFlag -> 稀有度 -> 强化pt；同一 serial 不能占两槽。  
     */  
    suspend fun autoEquipCbEx(  
        client: PcrClient,  
        unitId: Long,  
        data: SupportData  
    ) {  
        val slots = data.unitExSlots[unitId]?.second ?: emptyList()  
        if (slots.isEmpty()) return  
  
        val changes = mutableListOf<Pair<Int, Long>>()  
        val used = HashSet<Long>()  
        for ((slotNum, curSerial) in slots) {  
            val cat = ExMeta.slotCategory(unitId.toInt(), slotNum) ?: continue  
            val best = data.exInventory.values  
                .filter { it.serialId !in data.cbExRestriction }   // 排除会战CD装备  
                .filter { it.serialId !in used }                    // 一件不能穿两槽  
                .filter { ex -> ExMeta.equips[ex.exEquipId]?.category == cat }  
                .sortedWith(  
                    compareByDescending<ExEquipItem> { ExMeta.equips[it.exEquipId]?.cbFlag ?: 0 }  
                        .thenByDescending { ExMeta.equips[it.exEquipId]?.rarity ?: 0 }  
                        .thenByDescending { it.enhancementPt }  
                )  
                .firstOrNull() ?: continue  
            used += best.serialId  
            if (best.serialId != curSerial) changes += slotNum to best.serialId  
        }  
        if (changes.isNotEmpty()) equipEx(client, unitId, changes, true, data)  
        Log.i(TAG, "autoEquipCbEx unit=$unitId changes=$changes")  
    }  
}