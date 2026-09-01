package com.pcrjjc.app.util  
  
import android.content.Context  
import android.graphics.Bitmap  
import android.graphics.BitmapFactory  
import kotlinx.coroutines.flow.MutableStateFlow  
import kotlinx.coroutines.flow.StateFlow  
import java.io.File  
  
/**  
 * 自定义顶栏背景图存储  
 * 保存路径: {filesDir}/topbar/topbar_bg.png  
 */  
object TopBarBgStorage {  
  
    private const val DIR = "topbar"  
    private const val FILE_NAME = "topbar_bg.png"  
  
    // 目标输出规格：宽高比与 ImageTopAppBar 显示比例一致（全宽 × TopAppBar 64dp 高）  
    // 取常见屏宽 1080，TopAppBar 高约按比例 -> 1080 x 200，可按需微调  
    const val TARGET_WIDTH = 1080  
    const val TARGET_HEIGHT = 200  
  
    // 版本号，保存/清除时自增，供 ImageTopAppBar collect 后刷新  
    private val _version = MutableStateFlow(0)  
    val version: StateFlow<Int> = _version  
  
    private fun getDir(context: Context): File {  
        val dir = File(context.filesDir, DIR)  
        if (!dir.exists()) dir.mkdirs()  
        return dir  
    }  
  
    fun getFile(context: Context): File = File(getDir(context), FILE_NAME)  
  
    fun hasCustom(context: Context): Boolean = getFile(context).exists()  
  
    fun save(context: Context, bitmap: Bitmap) {  
        getFile(context).outputStream().use { out ->  
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)  
        }  
        _version.value += 1  
    }  
  
    fun load(context: Context): Bitmap? {  
        val file = getFile(context)  
        if (!file.exists()) return null  
        return BitmapFactory.decodeFile(file.absolutePath)  
    }  
  
    fun clear(context: Context) {  
        val file = getFile(context)  
        if (file.exists()) file.delete()  
        _version.value += 1  
    }  
}