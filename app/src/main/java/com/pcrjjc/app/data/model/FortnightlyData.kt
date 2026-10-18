package com.pcrjjc.app.data.model  
  
import java.text.SimpleDateFormat  
import java.util.Date  
import java.util.Locale  
import java.util.TimeZone  
  
/**  
 * 对应 data.json 中的一条活动记录  
 * JSON 格式: {"开始时间": "2026/4/7 11", "结束时间": "2026/4/18 05", "活动名": "【免费十连】"}  
 */  
data class ActivityEntry(  
    val startTime: String,   // "开始时间"  
    val endTime: String,     // "结束时间"  
    val activityName: String // "活动名"  
)  
  
/**  
 * 分类后的单个子活动  
 */  
data class ClassifiedActivity(  
    val category: String,  
    val subName: String,         // 子活动名（去掉角色ID后的文本）  
    val timeStatus: String,      // 格式化的时间状态  
    val characterIds: List<Int>, // 从活动名中提取的角色 baseId  
    val isEnding: Boolean,       // 即将结束（<2天）  
    val isFuture: Boolean,       // 尚未开始  
    val startTimeSec: Long = 0L, // 开始时间（秒）  
    val endTimeSec: Long = 0L    // 结束时间（秒）  
) {  
    /**  
     * 推送去重用的稳定标识：类别 + 名称 + 角色ID + 开始时间。  
     *  
     * data.json 每次刷新后顺序可能变化，所以不能用下标或哈希；  
     * 名称在分类时已剔除角色 ID，因此必须把 characterIds 一起带上，  
     * 否则【新专1 1322 1277】和【新专1 1139】这类同起始时间的条目会被误判成同一条。  
     */  
    val pushKey: String  
        get() = "$category|$subName|${characterIds.sorted().joinToString(",")}|$startTimeSec"  
  
    /** 开始时间文本，如 "10月18日 11:00" */  
    val startTimeText: String get() = formatDateTime(startTimeSec)  
  
    companion object {  
        private val dateFormat: SimpleDateFormat  
            get() = SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).apply {  
                timeZone = TimeZone.getTimeZone("Asia/Shanghai")  
            }  
  
        fun formatDateTime(seconds: Long): String {  
            if (seconds <= 0L) return ""  
            return dateFormat.format(Date(seconds * 1000))  
        }  
    }  
}  
