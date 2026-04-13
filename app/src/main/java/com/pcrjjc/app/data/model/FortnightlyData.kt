package com.pcrjjc.app.data.model  
  
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
    val isFuture: Boolean        // 尚未开始  
)