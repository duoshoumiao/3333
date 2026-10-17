package com.pcrjjc.app.util  
  
import android.content.Context  
import org.json.JSONObject  
  
/**  
 * EX装备元数据（assets/ex_meta.json，由 PPPPPP _export_ex_meta.py 导出）  
 * 对应 db.unit_ex_equipment_slot + db.ex_equipment_data  
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
  
    private val ATTR_CH = mapOf(  
        "hp" to "HP", "atk" to "物攻", "magic_str" to "魔攻",  
        "def" to "物防", "magic_def" to "魔防",  
        "physical_critical" to "物理暴击", "magic_critical" to "魔法暴击",  
        "wave_hp_recovery" to "HP自回", "wave_energy_recovery" to "TP自回",  
        "dodge" to "回避", "physical_penetrate" to "物穿",  
        "magic_penetrate" to "魔穿", "life_steal" to "HP吸收",  
        "hp_recovery_rate" to "回复上升", "energy_recovery_rate" to "TP上升",  
        "energy_reduce_rate" to "TP减耗", "accuracy" to "命中"  
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
  
    /** 对应 ExEquipmentDatum.get_unit_attribute：min + (max-min)*star/maxStar */  
    fun attrStr(exEquipId: Int, star: Int): String {  
        val info = equips[exEquipId] ?: return ""  
        val parts = mutableListOf<String>()  
        for ((k, ch) in ATTR_CH) {  
            val (d, m) = info.attrs[k] ?: continue  
            val v = if (info.maxStar > 0) d + (m - d) * star / info.maxStar else d  
            if (v != 0.0) parts += "$ch${v.toInt()}"  
        }  
        return parts.joinToString("/")  
    }  
}