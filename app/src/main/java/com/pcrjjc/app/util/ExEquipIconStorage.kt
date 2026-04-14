package com.pcrjjc.app.util  
  
import android.content.Context  
import android.graphics.Bitmap  
import android.graphics.BitmapFactory  
import java.io.File  
  
/**  
 * EX装备图标本地缓存管理  
 * 图标保存在 app 内部存储: {filesDir}/icons/ex_equip/{equipId}.png  
 */  
object ExEquipIconStorage {  
  
    private const val ICON_DIR = "icons/ex_equip"  
  
    private fun getIconDir(context: Context): File {  
        val dir = File(context.filesDir, ICON_DIR)  
        if (!dir.exists()) dir.mkdirs()  
        return dir  
    }  
  
    private fun getIconFile(context: Context, equipId: Int): File {  
        return File(getIconDir(context), "${equipId}.png")  
    }  
  
    fun hasIcon(context: Context, equipId: Int): Boolean {  
        return getIconFile(context, equipId).exists()  
    }  
  
    fun getIconPath(context: Context, equipId: Int): String? {  
        val file = getIconFile(context, equipId)  
        return if (file.exists()) file.absolutePath else null  
    }  
  
    fun saveBytes(context: Context, equipId: Int, data: ByteArray) {  
        val file = getIconFile(context, equipId)  
        file.writeBytes(data)  
    }  
  
    fun saveBitmap(context: Context, equipId: Int, bitmap: Bitmap) {  
        val file = getIconFile(context, equipId)  
        file.outputStream().use { out ->  
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)  
        }  
    }  
  
    fun loadBitmap(context: Context, equipId: Int): Bitmap? {  
        val file = getIconFile(context, equipId)  
        if (!file.exists()) return null  
        return BitmapFactory.decodeFile(file.absolutePath)  
    }  
  
    /**  
     * 获取已缓存的EX装备图标数量  
     */  
    fun getCachedCount(context: Context): Int {  
        val dir = getIconDir(context)  
        return dir.listFiles()?.size ?: 0  
    }  
}