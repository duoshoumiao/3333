package com.pcrjjc.app.domain  
  
import androidx.compose.ui.graphics.Color  
  
/**  
 * 移植自 huodong.py 的活动分类逻辑 (line 892-929) 和时间格式化 (line 683-751)  
 */  
object ActivityClassifier {  
  
    // 分类颜色映射 — 移植自 huodong.py line 892-904  
    val categoryColors: Map<String, Color> = mapOf(  
        "庆典活动" to Color(255f / 255, 215f / 255, 0f / 255),  
        "剧情活动" to Color(100f / 255, 200f / 255, 255f / 255),  
        "卡池" to Color(255f / 255, 100f / 255, 100f / 255),  
        "露娜塔" to Color(200f / 255, 100f / 255, 255f / 255),  
        "sp地下城" to Color(150f / 255, 100f / 255, 200f / 255),  
        "深渊讨伐战" to Color(100f / 255, 100f / 255, 200f / 255),  
        "免费十连" to Color(100f / 255, 255f / 255, 100f / 255),  
        "公会战" to Color(255f / 255, 150f / 255, 50f / 255),  
        "新开专" to Color(150f / 255, 200f / 255, 100f / 255),  
        "斗技场" to Color(200f / 255, 150f / 255, 100f / 255),  
        "其他活动" to Color(150f / 255, 150f / 255, 150f / 255)  
    )  
  
    // 分类顺序  
    val categoryOrder: List<String> = listOf(  
        "庆典活动", "剧情活动", "卡池", "露娜塔", "sp地下城",  
        "深渊讨伐战", "免费十连", "公会战", "新开专", "斗技场", "其他活动"  
    )  
  
    /**  
     * 活动分类 — 移植自 huodong.py line 907-929  
     */  
    fun classify(activityName: String): String {  
        return when {  
            "N" in activityName || "H" in activityName ||  
                "VH" in activityName || "庆典" in activityName -> "庆典活动"  
            "剧情活动" in activityName || "角色活动" in activityName ||  
                "复刻剧情活动" in activityName -> "剧情活动"  
            "up" in activityName || "卡池" in activityName -> "卡池"  
            "露娜塔" in activityName -> "露娜塔"  
            "免费十连" in activityName -> "免费十连"  
            "公会战" in activityName -> "公会战"  
            "sp地下城" in activityName -> "sp地下城"  
            "深渊讨伐战" in activityName || "深渊" in activityName -> "深渊讨伐战"  
            "新专1" in activityName || "新专2" in activityName -> "新开专"  
            "斗技场" in activityName -> "斗技场"  
            else -> "其他活动"  
        }  
    }  
  
    /**  
     * 从子活动名中提取角色 baseId — 移植自 huodong.py line 1154  
     * 返回 (清理后的文本, 角色ID列表)  
     */  
    fun extractCharacterIds(text: String): Pair<String, List<Int>> {  
        val regex = Regex("\\d{4,6}")  
        val ids = regex.findAll(text).mapNotNull { it.value.toIntOrNull() }.toList()  
        var cleaned = text  
        // 按长度倒序替换，避免短ID被长ID包含时的冲突 — 移植自 huodong.py line 82  
        val sortedMatches = regex.findAll(text).sortedByDescending { it.value.length }  
        for (match in sortedMatches) {  
            cleaned = cleaned.replace(match.value, "")  
        }  
        // 清理多余空格  
        cleaned = cleaned.replace(Regex("\\s+"), " ").trim()  
        return cleaned to ids  
    }  
  
    /**  
     * 格式化活动时间状态 — 移植自 huodong.py line 683-751  
     */  
    fun formatActivityStatus(  
        startTimeSec: Long,  
        endTimeSec: Long,  
        currentTimeSec: Long  
    ): Triple<String, Boolean, Boolean> {  
        val duration = endTimeSec - startTimeSec  
        val durationDays = (duration / (24 * 3600)).toInt()  
        val durationHours = ((duration % (24 * 3600)) / 3600).toInt()  
        val durationStr = if (durationHours > 0) "${durationDays}天${durationHours}小时" else "${durationDays}天"  
  
        val startDay = java.util.Calendar.getInstance().apply {  
            timeInMillis = startTimeSec * 1000  
        }.get(java.util.Calendar.DAY_OF_MONTH)  
  
        return when {  
            currentTimeSec < startTimeSec -> {  
                val delta = startTimeSec - currentTimeSec  
                val timeStr = formatCountdown(delta)  
                Triple(  
                    "开始倒计时: ${timeStr}（${startDay}号开始,持续${durationStr}）",  
                    false,  // isEnding  
                    true    // isFuture  
                )  
            }  
            else -> {  
                val delta = endTimeSec - currentTimeSec  
                if (delta > 0) {  
                    val timeStr = formatCountdown(delta)  
                    val isEnding = delta < 2 * 24 * 3600  
                    val suffix = if (isEnding) "（即将结束）" else ""  
                    Triple(  
                        "剩余时间: ${timeStr}${suffix}",  
                        isEnding,  
                        false  
                    )  
                } else {  
                    Triple("已结束（持续${durationStr}）", false, false)  
                }  
            }  
        }  
    }  
  
    /**  
     * 格式化倒计时 — 移植自 huodong.py line 715-751  
     */  
    private fun formatCountdown(seconds: Long): String {  
        val totalHours = seconds / 3600  
        return when {  
            totalHours >= 24 -> {  
                val days = totalHours / 24  
                val remainingHours = totalHours % 24  
                val minutes = (seconds % 3600) / 60  
                "${days}天${remainingHours}时${minutes}分"  
            }  
            totalHours > 0 -> {  
                val minutes = (seconds % 3600) / 60  
                "${totalHours}时${minutes}分"  
            }  
            else -> {  
                val minutes = (seconds % 3600) / 60  
                "${minutes}分"  
            }  
        }  
    }  
}