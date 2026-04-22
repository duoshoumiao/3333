package com.pcrjjc.app.util  
  
import java.util.Calendar  
import java.util.TimeZone  
  
/**  
 * 会战工具函数，移植自 zi-dong-bao-dao/util/tools.py 和 clanbattle/base.py  
 */  
  
// 阶段字典：字母 <-> 数字 双向映射  
val stageDictLetterToNum = mapOf("A" to 1, "B" to 2, "C" to 3, "D" to 4)  
val stageDictNumToLetter = mapOf(1 to "A", 2 to "B", 3 to "C", 4 to "D")  
  
// 分数倍率  
val rateScore = mapOf(  
    "A" to listOf(1.6, 1.6, 1.8, 1.9, 2.0),  
    "B" to listOf(1.6, 1.6, 1.8, 1.9, 2.0),  
    "C" to listOf(2.0, 2.0, 2.1, 2.1, 2.2),  
    "D" to listOf(4.5, 4.5, 4.7, 4.8, 5.0)  
)  
  
/**  
 * 周目 -> 阶段字母  
 * 移植自 zi-dong-bao-dao/util/tools.py:60-67  
 */  
fun lap2stage(lapNum: Int): String {  
    return when {  
        lapNum in 0..6 -> "B"  
        lapNum in 7..22 -> "C"  
        else -> "D"  
    }  
}  
  
/**  
 * 阶段字母 -> 阶段数字  
 */  
fun stageToNum(stage: String): Int = stageDictLetterToNum[stage] ?: 1  
  
/**  
 * 大数字格式化：12000000 -> "1200万"  
 * 移植自 zi-dong-bao-dao/clanbattle/base.py:43-46  
 */  
fun formatBigNum(num: Long): String {  
    return if (num > 10000) "${num / 10000}万" else num.toString()  
}  
  
/**  
 * 百分比格式化  
 * 移植自 zi-dong-bao-dao/clanbattle/base.py:48-51  
 */  
fun formatPercent(num: Double): String {  
    return if (num < 0.00005) "血皮" else "%.2f%%".format(num * 100)  
}  
  
/**  
 * 时间格式化（秒 -> X小时X分钟X秒）  
 * 移植自 zi-dong-bao-dao/clanbattle/base.py:32-41  
 */  
fun formatTime(seconds: Long): String {  
    val sec = seconds.toInt()  
    val sb = StringBuilder()  
    val hour = sec / 3600  
    val minute = sec % 3600 / 60  
    val second = sec % 60  
    if (hour > 0) sb.append("${hour}小时")  
    if (minute > 0) sb.append("${minute}分钟")  
    if (second > 0) sb.append("${second}秒")  
    return sb.toString().ifEmpty { "0秒" }  
}  
  
/**  
 * PCR 日期：以每天凌晨5点为分界线  
 * 移植自 zi-dong-bao-dao/clanbattle/sql.py:8-13  
 */  
fun pcrDateMillis(timestampMillis: Long): Long {  
    val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai")).apply {  
        timeInMillis = timestampMillis  
        if (get(Calendar.HOUR_OF_DAY) < 5) {  
            add(Calendar.DAY_OF_MONTH, -1)  
        }  
        set(Calendar.HOUR_OF_DAY, 5)  
        set(Calendar.MINUTE, 0)  
        set(Calendar.SECOND, 0)  
        set(Calendar.MILLISECOND, 0)  
    }  
    return cal.timeInMillis  
}  
  
/**  
 * 从 item_list 中查找指定 id 的 stock  
 * 移植自 zi-dong-bao-dao/clanbattle/base.py:19-24  
 */  
@Suppress("UNCHECKED_CAST")  
fun findItem(itemList: List<Any?>, id: Int): Int {  
    for (item in itemList) {  
        val map = item as? Map<String, Any?> ?: continue  
        val itemId = (map["id"] as? Number)?.toInt() ?: continue  
        if (itemId == id) {  
            return (map["stock"] as? Number)?.toInt() ?: 0  
        }  
    }  
    return 0  
}