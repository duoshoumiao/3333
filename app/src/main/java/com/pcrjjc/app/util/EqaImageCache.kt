package com.pcrjjc.app.util  
  
import android.content.Context  
import java.io.File  
import java.security.MessageDigest  
  
/**  
 * EQA 问答图片本地缓存  
 * 图片保存在: {filesDir}/eqa_images/{questionHash}/{answerIdx}_{segIdx}.jpg  
 * 同名问答会覆盖（先清空该问题的目录再重新下载）  
 */  
object EqaImageCache {  
  
    private const val CACHE_DIR = "eqa_images"  
  
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
  
    /** 获取本地图片路径（如果存在） */  
    fun getImagePath(context: Context, question: String, answerIdx: Int, segIdx: Int): String? {  
        val dir = getQuestionDir(context, question)  
        val file = File(dir, "${answerIdx}_${segIdx}.jpg")  
        return if (file.exists()) file.absolutePath else null  
    }  
}