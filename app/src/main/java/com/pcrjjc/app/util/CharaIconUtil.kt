package com.pcrjjc.app.util  
  
object CharaIconUtil {  
    private const val BASE_URL = "https://redive.estertion.win/icon/unit/"  
  
    /**  
     * 根据6位 unit ID 生成图标 URL 列表（优先级：61 > 31 > 11）  
     * unit ID 格式如 100101，前4位为角色基础ID  
     */  
    fun getIconUrls(unitId: Int): List<String> {  
        val baseId = unitId / 100  // 100101 -> 1001  
        return listOf(  
            "${BASE_URL}${baseId}61.webp",  
            "${BASE_URL}${baseId}31.webp",  
            "${BASE_URL}${baseId}11.webp"  
        )  
    }  
  
    /**  
     * 根据 unit ID 和星级生成最佳匹配 URL  
     * star: 原始星级 1-6  
     */  
    fun getIconUrl(unitId: Int, star: Int = 0): String {  
        val baseId = unitId / 100  
        val starSuffix = when {  
            star >= 6 -> 6  
            star >= 3 -> 3  
            star >= 1 -> 1  
            else -> 3  // 默认用3星  
        }  
        return "${BASE_URL}${baseId}${starSuffix}1.webp"  
    }  
  
    /**  
     * 获取优先使用的图标 URL（优先61，其次31）  
     */  
    fun getPriorityIconUrl(unitId: Int): String {  
        val baseId = unitId / 100  
        return "${BASE_URL}${baseId}61.webp"  // 优先6星  
    }  
  
    fun getFallbackIconUrl(unitId: Int): String {  
        val baseId = unitId / 100  
        return "${BASE_URL}${baseId}31.webp"  // 回退3星  
    }  
}