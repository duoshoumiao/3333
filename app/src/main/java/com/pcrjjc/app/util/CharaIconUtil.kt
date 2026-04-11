package com.pcrjjc.app.util  
  
import android.content.Context  
  
object CharaIconUtil {  
    private const val BASE_URL = "https://redive.estertion.win/icon/unit/"  
  
    fun getIconUrls(unitId: Int): List<String> {  
        val baseId = unitId / 100  
        return listOf(  
            "${BASE_URL}${baseId}61.webp",  
            "${BASE_URL}${baseId}31.webp",  
            "${BASE_URL}${baseId}11.webp"  
        )  
    }  
  
    fun getIconUrl(unitId: Int, star: Int = 0): String {  
        val baseId = unitId / 100  
        val starSuffix = when {  
            star >= 6 -> 6  
            star >= 3 -> 3  
            star >= 1 -> 1  
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
  
    fun getLocalIconPath(context: Context, unitId: Int): String? {  
        val baseId = unitId / 100  
        return IconStorage.getIconPath(context, baseId, 6)  
            ?: IconStorage.getIconPath(context, baseId, 3)  
            ?: IconStorage.getIconPath(context, baseId, 1)  
    }  
  
    fun getLocalFallbackPath(context: Context, unitId: Int): String? {  
        val baseId = unitId / 100  
        return IconStorage.getIconPath(context, baseId, 3)  
            ?: IconStorage.getIconPath(context, baseId, 1)  
    }  
}