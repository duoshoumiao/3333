package com.pcrjjc.app.util  
  
import android.content.Context  
import android.graphics.Bitmap  
import android.graphics.BitmapFactory  
import java.io.File  
import org.json.JSONArray
  
/**  
 * 本地图标文件存储管理  
 * 图标保存在 app 内部存储: {filesDir}/icons/unit/{baseId}_{star}.png  
 */  
object IconStorage {  
  
    private const val ICON_DIR = "icons/unit"  
  
    private fun getIconDir(context: Context): File {  
        val dir = File(context.filesDir, ICON_DIR)  
        if (!dir.exists()) dir.mkdirs()  
        return dir  
    }  
  
    private fun getIconFile(context: Context, baseId: Int, star: Int): File {  
        return File(getIconDir(context), "${baseId}_${star}.png")  
    }  
  
    fun hasIcon(context: Context, baseId: Int, star: Int): Boolean {  
        return getIconFile(context, baseId, star).exists()  
    }  
  
    fun getIconPath(context: Context, baseId: Int, star: Int): String? {  
        val file = getIconFile(context, baseId, star)  
        return if (file.exists()) file.absolutePath else null  
    }  
  
    fun saveBitmap(context: Context, baseId: Int, star: Int, bitmap: Bitmap) {  
        val file = getIconFile(context, baseId, star)  
        file.outputStream().use { out ->  
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)  
        }  
    }  
  
    fun loadBitmap(context: Context, baseId: Int, star: Int): Bitmap? {  
        val file = getIconFile(context, baseId, star)  
        if (!file.exists()) return null  
        return BitmapFactory.decodeFile(file.absolutePath)  
    }  
  
    /**  
     * 获取已缓存的图标数量  
     */  
    fun getCachedCount(context: Context): Int {  
        val dir = getIconDir(context)  
        return dir.listFiles()?.size ?: 0  
    }
    private const val KNOWN_CHARAS_FILE = "avatar_charas.json"  
  
    /**  
     * 加载已下载过的角色ID清单（avatar_charas.json）  
     */  
    fun loadKnownCharaIds(context: Context): MutableSet<Int> {  
        val file = File(context.filesDir, KNOWN_CHARAS_FILE)  
        if (!file.exists()) return mutableSetOf()  
        return try {  
            val arr = JSONArray(file.readText())  
            MutableSet(arr.length()) { i -> arr.getInt(i) }  
        } catch (e: Exception) {  
            mutableSetOf()  
        }  
    }  
  
    /**  
     * 保存已下载过的角色ID清单  
     */  
    fun saveKnownCharaIds(context: Context, ids: Set<Int>) {  
        try {  
            val arr = JSONArray()  
            ids.forEach { arr.put(it) }  
            File(context.filesDir, KNOWN_CHARAS_FILE).writeText(arr.toString())  
        } catch (e: Exception) {  
            // 清单写入失败不影响本次下载结果  
        }  
    }	
}