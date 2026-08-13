package com.pcrjjc.app.util  
  
import android.content.Context  
import java.io.File  
import java.security.MessageDigest  
import org.json.JSONObject
  
/**  
 * EQA 问答图片本地缓存  
 * 图片保存在: {filesDir}/eqa_images/{questionHash}/{answerIdx}_{segIdx}.jpg  
 * 同名问答会覆盖（先清空该问题的目录再重新下载）  
 */  
object EqaImageCache {  
  
    private const val CACHE_DIR = "eqa_images"  
	private const val DATA_DIR = "eqa_cache"  
    private const val QUESTIONS_FILE = "questions.json"  
    private const val VERSION_FILE = "version.txt"
  
    private fun getCacheDir(context: Context): File {  
        val dir = File(context.filesDir, CACHE_DIR)  
        if (!dir.exists()) dir.mkdirs()  
        return dir  
    }  
  
    /** 用问题文本的 MD5 作为目录名，避免特殊字符 */  
    private fun questionHash(question: String): String {  
        val md = MessageDigest.getInstance("MD5")  
        val digest = md.digest(question.toByteArray(Charsets.UTF_8))  
        return digest.joinToString("") { "%02x".format(it) }  
    }  
  
    private fun getQuestionDir(context: Context, question: String): File {  
        val dir = File(getCacheDir(context), questionHash(question))  
        if (!dir.exists()) dir.mkdirs()  
        return dir  
    }  
  
    /** 清空某个问题的所有缓存图片（用于覆盖前清理） */  
    fun clearQuestion(context: Context, question: String) {  
        val dir = getQuestionDir(context, question)  
        dir.listFiles()?.forEach { it.delete() }  
    }  
  
    /** 保存图片字节到本地，返回本地文件路径 */  
    fun saveImage(context: Context, question: String, answerIdx: Int, segIdx: Int, bytes: ByteArray): String {  
        val dir = getQuestionDir(context, question)  
        val file = File(dir, "${answerIdx}_${segIdx}.jpg")  
        file.writeBytes(bytes)  
        return file.absolutePath  
    }
    
    // ---------- 问答文本数据缓存 ----------  
  
    private fun getDataDir(context: Context): File {  
        val dir = File(context.filesDir, DATA_DIR)  
        if (!dir.exists()) dir.mkdirs()  
        return dir  
    }  
  
    /** 保存问题列表 JSON */  
    fun saveQuestions(context: Context, json: String) {  
        File(getDataDir(context), QUESTIONS_FILE).writeText(json)  
    }  
  
    /** 读取问题列表 JSON，无缓存返回 null */  
    fun getCachedQuestions(context: Context): String? {  
        val f = File(getDataDir(context), QUESTIONS_FILE)  
        return if (f.exists()) f.readText() else null  
    }  
  
    /** 保存某个问题的回答 JSON（图片已替换为本地 file:// 路径） */  
    fun saveAnswer(context: Context, question: String, json: String) {  
        File(getDataDir(context), "${questionHash(question)}.json").writeText(json)  
    }  
  
    /** 读取某个问题的回答 JSON，无缓存返回 null */  
    fun getCachedAnswer(context: Context, question: String): String? {  
        val f = File(getDataDir(context), "${questionHash(question)}.json")  
        return if (f.exists()) f.readText() else null  
    }  
  
    /** 记录/读取已下载缓存对应的服务端版本号 */  
    fun saveCacheVersion(context: Context, version: String) {  
        File(getDataDir(context), VERSION_FILE).writeText(version)  
    }  
  
    fun getCacheVersion(context: Context): String? {  
        val f = File(getDataDir(context), VERSION_FILE)  
        return if (f.exists()) f.readText() else null  
    }  
  
    /** 是否已有本地缓存 */  
    fun hasCache(context: Context): Boolean {  
        return File(getDataDir(context), QUESTIONS_FILE).exists()  
    }  
  
    /** 清空全部缓存（问答文本 + 图片），用于一键下载前移除旧缓存 */  
    fun clearAll(context: Context) {  
        getDataDir(context).deleteRecursively()  
        getCacheDir(context).deleteRecursively()  
    }	
  
    /** 获取本地图片路径（如果存在） */  
    fun getImagePath(context: Context, question: String, answerIdx: Int, segIdx: Int): String? {  
        val dir = getQuestionDir(context, question)  
        val file = File(dir, "${answerIdx}_${segIdx}.jpg")  
        return if (file.exists()) file.absolutePath else null  
    }  
}