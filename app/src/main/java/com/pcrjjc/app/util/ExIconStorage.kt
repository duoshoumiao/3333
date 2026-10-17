package com.pcrjjc.app.util  
  
import android.content.Context  
import android.graphics.Bitmap  
import android.graphics.BitmapFactory  
import java.io.File  
  
/**  
 * EX装备图标本地存储  
 * 保存在 app 内部存储: {filesDir}/icons/ex/icon_icon_extra_equip_{exEquipId}.png  
 */  
object ExIconStorage {  
  
    private const val ICON_DIR = "icons/ex"  
  
    private fun getIconDir(context: Context): File {  
        val dir = File(context.filesDir, ICON_DIR)  
        if (!dir.exists()) dir.mkdirs()  
        return dir  
    }  
  
    private fun getIconFile(context: Context, exEquipId: Int): File {  
        return File(getIconDir(context), "icon_icon_extra_equip_${exEquipId}.png")  
    } 
  
    fun hasIcon(context: Context, exEquipId: Int): Boolean {  
        return getIconFile(context, exEquipId).exists()  
    }  
  
    fun getIconPath(context: Context, exEquipId: Int): String? {  
        val file = getIconFile(context, exEquipId)  
        return if (file.exists()) file.absolutePath else null  
    }  
  
    fun saveBitmap(context: Context, exEquipId: Int, bitmap: Bitmap) {  
        val file = getIconFile(context, exEquipId)  
        file.outputStream().use { out ->  
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)  
        }  
    }  
  
    fun getCachedCount(context: Context): Int {  
        val dir = getIconDir(context)  
        return dir.listFiles()?.count { it.extension == "png" } ?: 0  
    }  
}