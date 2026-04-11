package com.pcrjjc.app.util  
  
import android.content.Context  
  
object CharaIconUtil {  
    private const val BASE_URL = "https://redive.estertion.win/icon/unit/"  
  
    /**  
     * 根据6位 unit ID 生成图标 URL 列表（优先级：61 > 31）  
     * unit ID 格式如 100101，前4位为角色基础ID  
     */  
    fun getIconUrls(unitId: Int): List<String> {  
        val baseId = unitId / 100  
        return listOf(  
            "${BASE_URL}${baseId}61.webp",  
            "${BASE_URL}${baseId}31.webp"  
        )  
    }  
  
    fun getIconUrl(unitId: Int, star: Int = 0): String {  
        val baseId = unitId / 100  
        val starSuffix = when {  
            star >= 6 -> 6  
            else -> 3  
        }  
        return "${BASE_URL}${baseId}${starSuffix}1.webp"  
    }  
  
    fun getPriorityIconUrl(unitId: Int): String {  
        val baseId = unitId / 100  
        return "${BASE_URL}${baseId}61.webp"  
    }  
  
    fun getFallbackIconUrl(unitId: Int): String {  
        val baseId = unitId / 100  
        return "${BASE_URL}${baseId}31.webp"  
    }  
  
    // ===== 本地图标支持 =====  
  
    /**  
     * 获取本地图标路径（优先6星 > 3星），无本地缓存返回 null  
     */  
    fun getLocalIconPath(context: Context, unitId: Int): String? {  
        val baseId = unitId / 100  
        return IconStorage.getIconPath(context, baseId, 6)  
            ?: IconStorage.getIconPath(context, baseId, 3)  
    }  
  
    /**  
     * 获取本地回退图标路径（3星），无本地缓存返回 null  
     */  
    fun getLocalFallbackPath(context: Context, unitId: Int): String? {  
        val baseId = unitId / 100  
        return IconStorage.getIconPath(context, baseId, 3)  
    }  
}