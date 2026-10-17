package com.pcrjjc.app.util  
  
import android.content.Context  
import org.json.JSONObject  
import java.math.BigDecimal  
import java.math.RoundingMode  
  
/**  
 * EX装备元数据（assets/ex_meta.json，由 PPPPPP _export_ex_meta.py 导出）  
 * 对应 db.unit_ex_equipment_slot + db.ex_equipment_data  
 *        + db.ex_equipment_sub_status(_group)（彩装词条数值）  
 */  
object ExMeta {  
    data class ExInfo(  
        val name: String, val category: Int, val rarity: Int,  
        val cbFlag: Int, val maxStar: Int,  
        val attrs: Map<String, Pair<Double, Double>>  // 属性 -> (default, max)  
    )  
  
    var unitSlots: Map<Int, List<Int>> = emptyMap()      // unitId -> 3槽类别  
    var starPt: Map<Int, Map<Int, Int>> = emptyMap()     // rarity -> star -> total_point  
    var equips: Map<Int, ExInfo> = emptyMap()  
    // group_id -> (status类型(eParamType值) -> 各档位数值 [value_1..value_5])  
    var subStatus: Map<Int, Map<Int, List<Int>>> = emptyMap()  
    // ex_equipment_id -> group_id（彩装才有词条组）  
    var subStatusGroup: Map<Int, Int> = emptyMap()  
  
    // 基础属性 key -> 中文名（对应 UnitAttribute 字段，custom.py:17-33）  
    private val ATTR_CH = mapOf(  
        "hp" to "HP", "atk" to "物攻", "magic_str" to "魔攻",  
        "def" to "物防", "magic_def" to "魔防",  
        "physical_critical" to "物理暴击", "magic_critical" to "魔法暴击",  
        "wave_hp_recovery" to "HP自回", "wave_energy_recovery" to "TP自回",  
        "dodge" to "回避", "physical_penetrate" to "物理防御贯穿",  
        "magic_penetrate" to "魔法防御贯穿", "life_steal" to "HP吸收",  
        "hp_recovery_rate" to "回复量上升", "energy_recovery_rate" to "TP上升",  
        "energy_reduce_rate" to "TP减耗", "accuracy" to "命中"  
    )  
  
    // sub_status.status -> (UnitAttribute字段名, 中文名)  
    // status 是 eParamType 枚举值（enums.py:724-742）：  
    //   1=HP 2=ATK 3=DEF 4=MAGIC_ATK 5=MAGIC_DEF  
    //   6=PHYSICAL_CRITICAL 7=MAGIC_CRITICAL 8=DODGE 9=LIFE_STEAL  
    //   10=WAVE_HP_RECOVERY 11=WAVE_ENERGY_RECOVERY  
    //   12=PHYSICAL_PENETRATE 13=MAGIC_PENETRATE  
    //   14=ENERGY_RECOVERY_RATE 15=HP_RECOVERY_RATE  
    //   16=ENERGY_REDUCE_RATE 17=ACCURACY  
    private val SUB_STATUS = mapOf(  
        1 to ("hp" to "HP"), 2 to ("atk" to "物攻"),  
        3 to ("def_" to "物防"), 4 to ("magic_str" to "魔攻"),  
        5 to ("magic_def" to "魔防"),  
        6 to ("physical_critical" to "物理暴击"),  
        7 to ("magic_critical" to "魔法暴击"),  
        8 to ("dodge" to "回避"), 9 to ("life_steal" to "HP吸收"),  
        10 to ("wave_hp_recovery" to "HP自回"),  
        11 to ("wave_energy_recovery" to "TP自回"),  
        12 to ("physical_penetrate" to "物理防御贯穿"),  
        13 to ("magic_penetrate" to "魔法防御贯穿"),  
        14 to ("energy_recovery_rate" to "TP上升"),  
        15 to ("hp_recovery_rate" to "回复量上升"),  
        16 to ("energy_reduce_rate" to "TP减耗"),  
        17 to ("accuracy" to "命中")  
    )  
  
    // UnitAttribute.is_present 为 true 的字段（custom.py:75-93）：  
    // 百分比型属性，存储值 = 实际百分比 x 100，显示 "{名}x{值/100:.2f}%"  
    private val PERCENT_ATTRS = setOf(  
        "hp", "atk", "magic_str", "def_", "magic_def",  
        "physical_critical", "magic_critical"  
    )  
  
    @Synchronized  
    fun load(context: Context) {  
        if (equips.isNotEmpty()) return  
        try {  
            val obj = JSONObject(  
                context.assets.open("ex_meta.json").bufferedReader().readText()  
            )  
            val us = mutableMapOf<Int, List<Int>>()  
            obj.getJSONObject("unit_slots").keys().forEach { k ->  
                val a = obj.getJSONObject("unit_slots").getJSONArray(k)  
                us[k.toInt()] = List(a.length()) { a.getInt(it) }  
            }  
            unitSlots = us  
            val sp = mutableMapOf<Int, Map<Int, Int>>()  
            obj.getJSONObject("star_pt").keys().forEach { r ->  
                val o = obj.getJSONObject("star_pt").getJSONObject(r)  
                val m = mutableMapOf<Int, Int>()  
                o.keys().forEach { s -> m[s.toInt()] = o.getInt(s) }  
                sp[r.toInt()] = m  
            }  
            starPt = sp  
            val eq = mutableMapOf<Int, ExInfo>()  
            obj.getJSONObject("equips").keys().forEach { k ->  
                val o = obj.getJSONObject("equips").getJSONObject(k)  
                val attrs = mutableMapOf<String, Pair<Double, Double>>()  
                if (o.has("attrs")) {  
                    val ao = o.getJSONObject("attrs")  
                    ao.keys().forEach { a ->  
                        val arr = ao.getJSONArray(a)  
                        attrs[a] = arr.getDouble(0) to arr.getDouble(1)  
                    }  
                }  
                eq[k.toInt()] = ExInfo(  
                    o.optString("name"), o.getInt("category"), o.getInt("rarity"),  
                    o.optInt("cb", 0), o.optInt("max_star", 0), attrs  
                )  
            }  
            equips = eq  
            // 词条数值表：group_id -> status -> [value_1..value_5]  
            val ss = mutableMapOf<Int, Map<Int, List<Int>>>()  
            if (obj.has("sub_status")) {  
                obj.getJSONObject("sub_status").keys().forEach { gid ->  
                    val go = obj.getJSONObject("sub_status").getJSONObject(gid)  
                    val gm = mutableMapOf<Int, List<Int>>()  
                    go.keys().forEach { st ->  
                        val arr = go.getJSONArray(st)  
                        gm[st.toInt()] = List(arr.length()) { arr.getInt(it) }  
                    }  
                    ss[gid.toInt()] = gm  
                }  
            }  
            subStatus = ss  
            // ex_equipment_id -> group_id  
            val sg = mutableMapOf<Int, Int>()  
            if (obj.has("sub_status_group")) {  
                obj.getJSONObject("sub_status_group").keys().forEach { k ->  
                    sg[k.toInt()] = obj.getJSONObject("sub_status_group").getInt(k)  
                }  
            }  
            subStatusGroup = sg  
        } catch (e: Exception) {  
            e.printStackTrace()  
        }  
    }  
  
    /** 角色第 slotNum 槽(1-3)的类别 */  
    fun slotCategory(unitId: Int, slotNum: Int): Int? =  
        unitSlots[unitId]?.getOrNull(slotNum - 1)  
  
    /** 对应 db.get_ex_equip_star_from_pt */  
    fun starFromPt(rarity: Int, pt: Int): Int {  
        val m = starPt[rarity] ?: return 0  
        return m.filter { it.value <= pt }.keys.maxOrNull() ?: 0  
    }  
  
    /**  
     * 对应 ExEquipmentDatum.get_unit_attribute：min + (max-min)*star/maxStar，  
     * 显示按 ROUND_HALF_UP 取整（对齐 UnitAttribute.round()）  
     */  
    fun attrStr(exEquipId: Int, star: Int): String {  
        val info = equips[exEquipId] ?: return ""  
        val parts = mutableListOf<String>()  
        for ((k, ch) in ATTR_CH) {  
            val (d, m) = info.attrs[k] ?: continue  
            val v = if (info.maxStar > 0) d + (m - d) * star / info.maxStar else d  
            val r = BigDecimal(v).setScale(0, RoundingMode.HALF_UP).toInt()  
            if (r != 0) parts += "$ch+$r"  
        }  
        return parts.joinToString("/")  
    }  
  
    /**  
     * 副词条展示串，对齐 PPPPPP database.py get_ex_equip_sub_status_str（2117-2132行）：  
     * - 数值 = ex_equipment_sub_status[group][status].value_{step}，不是 step 本身  
     * - 同类型词条数值先累加  
     * - 百分比型属性显示 "名xN.NN%"（值/100），其余显示 "名xN"  
     * @param subStatus 每项 Pair(status=eParamType值, step=档位)  
     */  
    fun subStatusStr(exEquipId: Int, subStatus: List<Pair<Int, Int>>): String {  
        if (subStatus.isEmpty()) return ""  
        val group = subStatusGroup.get(exEquipId) ?: return ""  
        val table = subStatus.get(group) ?: return ""  
        // Counter 累加同类型词条（对齐 database.py:2114）  
        val sums = java.util.TreeMap<Int, Int>()  
        for (pair in subStatus) {  
            val st = pair.first  
            val step = pair.second  
            val values = table.get(st)  
            val v = if (values != null && step >= 1 && step <= values.size)  
                values.get(step - 1) else 0  
            sums.put(st, (sums.get(st) ?: 0) + v)  
        }  
        val parts = mutableListOf<String>()  
        for (entry in sums.entries) {  
            val st = entry.key  
            val total = entry.value  
            val pair = SUB_STATUS.get(st)  
            val field = pair?.first ?: "?"  
            val name = pair?.second ?: "未知属性$st"  
            if (PERCENT_ATTRS.contains(field)) {  
                parts.add("${name}x${"%.2f".format(total / 100.0)}%")  
            } else {  
                parts.add("${name}x$total")  
            }  
        }  
        return parts.joinToString("/", prefix = " 词条:")  
    }  
}