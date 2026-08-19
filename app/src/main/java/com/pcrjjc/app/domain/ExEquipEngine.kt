package com.pcrjjc.app.domain  
  
import android.content.Context  
import android.util.Log  
import com.pcrjjc.app.data.remote.PcrClient  
import org.json.JSONArray  
import org.json.JSONObject  
  
/**  
 * EX状态引擎，移植自 autopcr/module/modules/exequip.py 的 save_ex_state / restore_ex_state  
 *  
 * - saveExState: 读取角色当前普通EX装备的 serial_id，按账号保存到本地  
 * - restoreExState: 只对有差异的槽位调用 unit/equip_ex 恢复；目标装备在别人身上时先卸载再穿戴  
 */  
class ExEquipEngine(private val context: Context) {  
  
    companion object {  
        private const val TAG = "ExEquipEngine"  
        private const val PREFS_NAME = "ex_equip_state"  
    }  
  
    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }  
  
    // ---------- 拉取当前所有角色的 ex_equip_slot ----------  
    @Suppress("UNCHECKED_CAST")  
    private suspend fun loadUnitExSlots(client: PcrClient): Map<Long, List<Pair<Int, Long>>> {  
        // Pair<slot, serialId>  
        val loadIndex = client.callApi("/load/index", mutableMapOf("carrier" to "OPPO"))  
        // ⚠ 待核对字段名：unit_list / ex_equip_slot / slot / serial_id  
        val unitList = loadIndex["unit_list"] as? List<Map<String, Any?>> ?: emptyList()  
        val result = LinkedHashMap<Long, List<Pair<Int, Long>>>()  
        for (unit in unitList) {  
            val unitId = (unit["id"] as? Number)?.toLong() ?: continue  
            val slotsRaw = unit["ex_equip_slot"] as? List<Map<String, Any?>> ?: emptyList()  
            val slots = slotsRaw.map { s ->  
                val slot = (s["slot"] as? Number)?.toInt() ?: 0  
                val serial = (s["serial_id"] as? Number)?.toLong() ?: 0L  
                slot to serial  
            }  
            result[unitId] = slots  
        }  
        return result  
    }  
  
    // ---------- 保存 ----------  
    suspend fun saveExState(client: PcrClient, accountId: Int): String {  
        val units = loadUnitExSlots(client)  
        val state = JSONObject()  
        var total = 0  
        for ((unitId, slots) in units) {  
            val serials = slots.map { it.second }  
            if (serials.any { it != 0L }) {  
                state.put(unitId.toString(), JSONArray(serials))  
                total += serials.count { it != 0L }  
            }  
        }  
        if (state.length() == 0) {  
            throw Exception("没有角色穿戴普通EX装备，无需保存")  
        }  
        prefs.edit().putString(keyFor(accountId), state.toString()).apply()  
        val msg = "共保存了${state.length()}个角色的${total}件普通EX装备状态"  
        Log.i(TAG, msg)  
        return msg  
    }  
  
    // ---------- 恢复 ----------  
    suspend fun restoreExState(client: PcrClient, accountId: Int): String {  
        val saved = prefs.getString(keyFor(accountId), null)  
            ?: throw Exception("未找到保存的EX状态，请先执行「保存ex状态」")  
        val state = JSONObject(saved)  
        if (state.length() == 0) {  
            throw Exception("保存的EX状态数据为空，请重新执行「保存ex状态」")  
        }  
  
        val units = loadUnitExSlots(client)  
  
        // 1. 当前 serial_id -> (unitId, slot)  
        val currentOwner = HashMap<Long, Pair<Long, Int>>()  
        for ((unitId, slots) in units) {  
            for ((slot, serial) in slots) {  
                if (serial != 0L) currentOwner[serial] = unitId to slot  
            }  
        }  
  
        // 2. 找出需要变更的 (unitId, slotNum, targetSerial)  
        val changes = ArrayList<Triple<Long, Int, Long>>()  
        var skipCnt = 0  
        val keys = state.keys()  
        while (keys.hasNext()) {  
            val unitIdStr = keys.next()  
            val unitId = unitIdStr.toLong()  
            val curSlots = units[unitId]  
            if (curSlots == null) { skipCnt++; continue }  
            val savedArr = state.getJSONArray(unitIdStr)  
            for (i in 0 until savedArr.length()) {  
                if (i >= curSlots.size) continue  
                val savedSerial = savedArr.getLong(i)  
                val (slotNum, curSerial) = curSlots[i]  
                if (curSerial == savedSerial) continue  
                changes.add(Triple(unitId, slotNum, savedSerial))  
            }  
        }  
  
        if (changes.isEmpty()) {  
            if (skipCnt > 0) return "所有保存的装备均无法恢复（${skipCnt}项跳过）"  
            throw Exception("当前EX装备状态与保存的一致，无需恢复")  
        }  
  
        // 3. 需要先释放的 serial（目标装备当前在别的角色/槽位上）  
        val needFree = HashMap<Long, Pair<Long, Int>>()  
        for ((unitId, slotNum, target) in changes) {  
            if (target != 0L) {  
                val owner = currentOwner[target]  
                if (owner != null && (owner.first != unitId || owner.second != slotNum)) {  
                    needFree[target] = owner  
                }  
            }  
        }  
  
        // 4. 按角色分组卸载  
        val unequipByUnit = HashMap<Long, MutableList<Pair<Int, Long>>>()  
        for ((_, owner) in needFree) {  
            unequipByUnit.getOrPut(owner.first) { mutableListOf() }.add(owner.second to 0L)  
        }  
        var unequipCnt = 0  
        for ((uid, list) in unequipByUnit) {  
            equipEx(client, uid, list)  
            unequipCnt += list.size  
        }  
  
        // 5. 按角色分组穿戴  
        val equipByUnit = HashMap<Long, MutableList<Pair<Int, Long>>>()  
        for ((unitId, slotNum, target) in changes) {  
            equipByUnit.getOrPut(unitId) { mutableListOf() }.add(slotNum to target)  
        }  
        var equipUnitCnt = 0  
        for ((uid, list) in equipByUnit) {  
            equipEx(client, uid, list)  
            equipUnitCnt++  
        }  
  
        var msg = "共变更了${equipUnitCnt}个角色的装备"  
        if (unequipCnt > 0) msg += "（先释放了${unequipCnt}件）"  
        if (skipCnt > 0) msg += "（${skipCnt}项因角色不存在而跳过）"  
        Log.i(TAG, msg)  
        return msg  
    }  
  
    // 单次 unit/equip_ex 请求（合并同角色多槽位）  
    private suspend fun equipEx(client: PcrClient, unitId: Long, slots: List<Pair<Int, Long>>) {  
        val slotList = slots.map { (slot, serial) ->  
            mutableMapOf<String, Any?>("slot" to slot, "serial_id" to serial)  
        }  
        val changeUnit = mutableMapOf<String, Any?>(  
            "unit_id" to unitId,  
            "ex_equip_slot" to slotList,  
            "cb_ex_equip_slot" to null  
        )  
        client.callApi(  
            "/unit/equip_ex",  
            mutableMapOf("ex_equip_change_unit_list" to listOf(changeUnit))  
        )  
    }  
  
    private fun keyFor(accountId: Int) = "ex_state_$accountId"  
}