package com.pcrjjc.app.util  
  
import android.util.Log  
import java.io.ByteArrayInputStream  
import java.io.DataInputStream  
import java.nio.ByteBuffer  
import java.nio.ByteOrder  
  
/**  
 * Texture2D 提取结果  
 */  
data class Texture2DInfo(  
    val name: String,  
    val width: Int,  
    val height: Int,  
    val textureFormat: Int,  
    val imageData: ByteArray  
)  
  
/**  
 * 简化的 Unity SerializedFile 解析器  
 * 支持提取 Texture2D 和 TextAsset 对象  
 */  
object SerializedFileParser {  
    private const val TAG = "SerializedFileParser"  
  
    fun extractTexture2D(data: ByteArray): Texture2DInfo? {  
        try {  
            return parseSerializedFile(data, targetClassId = 28)  
                as? Texture2DInfo  
        } catch (e: Exception) {  
            Log.w(TAG, "Failed to parse serialized file: ${e.message}")  
            return null  
        }  
    }  
  
    /**  
     * 从序列化文件中提取 TextAsset 的文本内容  
     * TextAsset classId = 49  
     */  
    fun extractTextAsset(data: ByteArray): String? {  
        try {  
            return parseSerializedFile(data, targetClassId = 49)  
                as? String  
        } catch (e: Exception) {  
            Log.w(TAG, "Failed to parse TextAsset: ${e.message}")  
            return null  
        }  
    }  
  
    // ===== 内部数据结构 =====  
  
    private data class TypeInfo(val classId: Int, val scriptTypeIndex: Short)  
  
    private data class ObjectInfo(  
        val pathId: Long, val byteStart: Long,  
        val byteSize: Int, val typeIndex: Int  
    )  
  
    /**  
     * 通用解析入口，根据 targetClassId 返回不同类型的结果  
     * classId=28 -> Texture2DInfo  
     * classId=49 -> String (TextAsset 文本内容)  
     */  
    private fun parseSerializedFile(data: ByteArray, targetClassId: Int): Any? {  
        val buf = ByteBuffer.wrap(data)  
  
        // SerializedFile header (big-endian)  
        buf.order(ByteOrder.BIG_ENDIAN)  
        val metadataSize = buf.int  
        val fileSize = buf.int  
        val version = buf.int  
        val dataOffset = buf.int  
  
        // version >= 9: endianness byte  
        val endianness = if (version >= 9) {  
            val e = buf.get().toInt()  
            buf.get() // reserved  
            buf.get() // reserved  
            buf.get() // reserved  
            e  
        } else 0  
  
        val order = if (endianness == 0)  
            ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN  
        buf.order(order)  
  
        // Unity version string  
        val unityVersion = readNullTermString(buf)  
  
        // Platform  
        val platform = buf.int  
  
        // Type tree  
        val hasTypeTree = if (version >= 13) buf.get().toInt() != 0 else true  
        val typeCount = buf.int  
  
        // 收集类型信息  
        val types = mutableListOf<TypeInfo>()  
  
        for (i in 0 until typeCount) {  
            val classId = if (version >= 17) buf.int else buf.short.toInt()  
            if (version >= 16) buf.get() // isStrippedType  
            if (version >= 17) buf.short // scriptTypeIndex  
            val scriptIdx = if (version >= 17 && (classId == 114 || classId < 0))  
                buf.short else 0.toShort()  
  
            // old type hash  
            if (version >= 13) {  
                if ((version < 16 && classId < 0) ||  
                    (version >= 16 && classId == 114)) {  
                    buf.position(buf.position() + 16) // script hash  
                }  
                buf.position(buf.position() + 16) // type hash  
            }  
  
            // type tree nodes (skip if present)  
            if (hasTypeTree) {  
                val nodeCount = buf.int  
                val stringSize = buf.int  
                // skip nodes: each node is 24 bytes  
                buf.position(buf.position() + nodeCount * 24 + stringSize)  
                if (version >= 21) {  
                    buf.position(buf.position() + 4) // type dependencies  
                }  
            }  
  
            types.add(TypeInfo(classId, scriptIdx))  
        }  
  
        // Object info  
        val objectCount = buf.int  
        val objects = mutableListOf<ObjectInfo>()  
  
        for (i in 0 until objectCount) {  
            if (version >= 14) alignStream(buf, 4)  
            val pathId = if (version >= 14) buf.long  
                else buf.int.toLong()  
            val byteStart = if (version >= 22) buf.long  
                else buf.int.toLong()  
            val byteSize = buf.int  
            val typeIndex = buf.int  
            if (version < 16) {  
                buf.short // classID  
            }  
            if (version < 17) {  
                buf.short // scriptTypeIndex  
            }  
            if (version == 15 || version == 16) {  
                buf.get() // stripped  
            }  
            objects.add(ObjectInfo(pathId, byteStart, byteSize, typeIndex))  
        }  
  
        // 查找目标 classId 的对象  
        for (obj in objects) {  
            val typeIdx = obj.typeIndex  
            if (typeIdx < 0 || typeIdx >= types.size) continue  
            val classId = types[typeIdx].classId  
            if (classId != targetClassId) continue  
  
            val objStart = dataOffset + obj.byteStart.toInt()  
            if (objStart + obj.byteSize > data.size) continue  
  
            val objData = data.copyOfRange(objStart, objStart + obj.byteSize)  
  
            return when (targetClassId) {  
                28 -> readTexture2D(objData, order)  
                49 -> readTextAsset(objData, order)  
                else -> null  
            }  
        }  
  
        return null  
    }  
  
    // ===== Texture2D 读取 (classId = 28) =====  
  
    private fun readTexture2D(  
        data: ByteArray, order: ByteOrder  
    ): Texture2DInfo? {  
        val buf = ByteBuffer.wrap(data).order(order)  
  
        // m_Name (string: length + chars + align)  
        val nameLen = buf.int  
        val nameBytes = ByteArray(nameLen)  
        buf.get(nameBytes)  
        val name = String(nameBytes)  
        alignStream(buf, 4)  
  
        // m_ForcedFallbackFormat  
        buf.int  
        // m_DownscaleFallback  
        buf.int  
        // m_IsAlphaChannelOptional  
        buf.get()  
        alignStream(buf, 4)  
  
        val width = buf.int  
        val height = buf.int  
        // m_CompleteImageSize  
        buf.int  
        // m_TextureFormat  
        val textureFormat = buf.int  
        // m_MipCount  
        buf.int  
        // m_IsReadable  
        buf.get()  
        alignStream(buf, 4)  
        // m_StreamingMipmaps  
        buf.get()  
        alignStream(buf, 4)  
        // m_StreamingMipmapsPriority  
        buf.int  
        // m_ImageCount  
        buf.int  
        // m_TextureDimension  
        buf.int  
        // m_TextureSettings (FilterMode, Aniso, MipBias, WrapMode)  
        buf.int // filterMode  
        buf.int // aniso  
        buf.float // mipBias  
        buf.int // wrapU  
        buf.int // wrapV  
        buf.int // wrapW  
        // m_LightmapFormat  
        buf.int  
        // m_ColorSpace  
        buf.int  
  
        // image data  
        val imageDataSize = buf.int  
        if (imageDataSize > 0 && buf.remaining() >= imageDataSize) {  
            val imageData = ByteArray(imageDataSize)  
            buf.get(imageData)  
            Log.d(TAG, "Texture2D: $name ${width}x${height} fmt=$textureFormat size=$imageDataSize")  
            return Texture2DInfo(name, width, height, textureFormat, imageData)  
        }  
  
        // 如果 imageDataSize == 0，可能数据在 StreamingInfo 中  
        Log.w(TAG, "Texture2D $name has no inline image data")  
        return null  
    }  
  
    // ===== TextAsset 读取 (classId = 49) =====  
  
    private fun readTextAsset(  
        data: ByteArray, order: ByteOrder  
    ): String? {  
        val buf = ByteBuffer.wrap(data).order(order)  
  
        // m_Name (string: length + chars + align)  
        val nameLen = buf.int  
        if (nameLen < 0 || nameLen > data.size) return null  
        val nameBytes = ByteArray(nameLen)  
        buf.get(nameBytes)  
        alignStream(buf, 4)  
  
        // m_Script (byte array: length + bytes)  
        val scriptLen = buf.int  
        if (scriptLen <= 0 || scriptLen > buf.remaining()) return null  
        val scriptBytes = ByteArray(scriptLen)  
        buf.get(scriptBytes)  
  
        val text = String(scriptBytes, Charsets.UTF_8)  
        Log.d(TAG, "TextAsset: ${String(nameBytes)} length=$scriptLen")  
        return text  
    }  
  
    // ===== 工具方法 =====  
  
    private fun readNullTermString(buf: ByteBuffer): String {  
        val sb = StringBuilder()  
        while (buf.hasRemaining()) {  
            val b = buf.get().toInt() and 0xFF  
            if (b == 0) break  
            sb.append(b.toChar())  
        }  
        return sb.toString()  
    }  
  
    private fun alignStream(buf: ByteBuffer, alignment: Int) {  
        val pos = buf.position()  
        val mod = pos % alignment  
        if (mod != 0) {  
            buf.position(pos + (alignment - mod))  
        }  
    }  
}