package com.pcrjjc.app.util  
  
/**  
 * 公主骑士经验 → RANK 计算  
 * 移植自 pcrjjc2 的 img/rank_parse.py  
 */  
object KnightRankCalculator {  
  
    fun calculateRank(experience: Int): Int {  
        var exp = experience.toLong()  
  
        // 等级1到125，每级 53235 经验  
        val base1 = 53235L  
        if (exp < base1 * 125) {  
            return (exp / base1 + 1).toInt()  
        }  
  
        // 等级126到201，每级 53236 经验  
        exp -= base1 * 124  
        val base2 = 53236L  
        if (exp < base2 * 76) {  
            return (exp / base2 + 125).toInt()  
        }  
  
        // 等级202及以上  
        exp -= base2 * 76  
        var level = 201  
        var nextExperience = 111076L  
  
        // 增量规则表  
        val increments: List<Triple<IntRange, Int, Int>> = listOf(  
            Triple(202..216, 505, 0),  
            Triple(217..250, 500, 0),  
            Triple(251..251, 476, 0),  
            Triple(252..255, 474, 0),  
            Triple(256..256, 475, 0),  
            Triple(257..288, 476, 0),  
            Triple(289..289, 475, 0),  
            Triple(290..293, 476, 0),  
            Triple(294..295, 475, 0),  
            Triple(296..296, 477, 0),  
            Triple(297..298, 476, 0),  
            Triple(299..299, -22335, 0),  
            Triple(300..301, 471, 0)  
        )  
  
        while (exp >= nextExperience) {  
            exp -= nextExperience  
            level++  
            val increment = increments.firstOrNull { level in it.first }?.second  
            if (increment == null) {  
                // 超出计算范围，返回当前等级  
                return level  
            }  
            nextExperience += increment  
        }  
        return level  
    }  
}