package com.pcrjjc.app.util  
  
import android.content.Context  
import android.graphics.Bitmap  
import android.graphics.BitmapFactory  
import java.io.File  
  
/**  
 * 本地图标文件存储管理  
 * 图标保存在 app 内部存储: {filesDir}/icons/unit/{baseId}_{star}.png  
 */  
object IconStorage {  
  
    private const val ICON_DIR = "icons/unit"  
    private const val DEFAULT_STAR = 6  
  
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
  
    fun getIcon(context: Context, baseId: Int, star: Int = DEFAULT_STAR): Bitmap? {  
        return loadBitmap(context, baseId, star)  
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
}
