package com.pcrjjc.app.util  
  
import android.util.Log  
import java.io.ByteArrayInputStream  
import java.io.ByteArrayOutputStream  
import java.io.DataInputStream  
import java.nio.ByteBuffer  
import java.nio.ByteOrder  
  
/**  
 * UnityFS bundle 中的一个文件条目  
 */  
data class BundleFileEntry(  
    val name: String,  
    val data: ByteArray  
)  
  
/**  
 * 解析 UnityFS 格式的 asset bundle  
 *  
 * UnityFS 格式：  
 * - Header: magic("UnityFS\0"), version(int32BE), unityVer(string),  
 *           generatorVer(string), fileSize(int64BE),  
 *           compressedBlockInfoSize(int32BE),  
 *           uncompressedBlockInfoSize(int32BE), flags(int32BE)  
 * - Block info (可能被压缩)  
 * - Data blocks  
 */  
object UnityBundleParser {  
  
    private const val TAG = "UnityBundleParser"  
    private const val MAGIC = "UnityFS"  
  
    // 压缩类型  
    private const val COMPRESSION_NONE = 0  
    private const val COMPRESSION_LZMA = 1  
    private const val COMPRESSION_LZ4 = 2  
    private const val COMPRESSION_LZ4HC = 3  
  
    fun parse(data: ByteArray): List<BundleFileEntry> {  
        val stream = DataInputStream(ByteArrayInputStream(data))  
  
        // 1. 读取 magic  
        val magicBytes = ByteArray(8)  
        stream.readFully(magicBytes)  
        val magic = String(magicBytes).trimEnd('\u0000')  
        if (magic != MAGIC) {  
            throw Exception("Not a UnityFS bundle: magic='$magic'")  
        }  
  
        // 2. 读取 header  
        val formatVersion = stream.readInt() // big-endian  
        val unityVersion = readNullTermString(stream)  
        val generatorVersion = readNullTermString(stream)  
        val fileSize = stream.readLong()  
        val compressedBlockInfoSize = stream.readInt()  
        val uncompressedBlockInfoSize = stream.readInt()  
        val flags = stream.readInt()  
  
        Log.d(TAG, "UnityFS v$formatVersion, unity=$unityVersion, " +  
                "gen=$generatorVersion, size=$fileSize")  
  
        val compressionType = flags and 0x3F  
  
        // 3. 读取 block info（可能在文件末尾）  
        val blockInfoAtEnd = (flags and 0x80) != 0  
        val blockInfoData: ByteArray  
  
        if (blockInfoAtEnd) {  
            // block info 在文件末尾  
            val offset = data.size - compressedBlockInfoSize  
            val compressed = data.copyOfRange(offset, data.size)  
            blockInfoData = decompressBlockInfo(  
                compressed, compressionType,  
                uncompressedBlockInfoSize  
            )  
        } else {  
            // block info 紧跟 header  
            val compressed = ByteArray(compressedBlockInfoSize)  
            stream.readFully(compressed)  
            blockInfoData = decompressBlockInfo(  
                compressed, compressionType,  
                uncompressedBlockInfoSize  
            )  
        }  
  
        // 4. 解析 block info  
        val blockInfoStream = DataInputStream(  
            ByteArrayInputStream(blockInfoData)  
        )  
        // 跳过 16 字节 data hash  
        blockInfoStream.skipBytes(16)  
  
        val blockCount = blockInfoStream.readInt()  
        data class BlockInfo(  
            val uncompressedSize: Int,  
            val compressedSize: Int,  
            val flags: Short  
        )  
  
        val blocks = mutableListOf<BlockInfo>()  
        for (i in 0 until blockCount) {  
            blocks.add(  
                BlockInfo(  
                    uncompressedSize = blockInfoStream.readInt(),  
                    compressedSize = blockInfoStream.readInt(),  
                    flags = blockInfoStream.readShort()  
                )  
            )  
        }  
  
        val directoryCount = blockInfoStream.readInt()  
        data class DirectoryEntry(  
            val offset: Long,  
            val size: Long,  
            val status: Int,  
            val name: String  
        )  
  
        val directories = mutableListOf<DirectoryEntry>()  
        for (i in 0 until directoryCount) {  
            directories.add(  
                DirectoryEntry(  
                    offset = blockInfoStream.readLong(),  
                    size = blockInfoStream.readLong(),  
                    status = blockInfoStream.readInt(),  
                    name = readNullTermString(blockInfoStream)  
                )  
            )  
        }  
  
        // 5. 计算数据起始偏移  
        val dataOffset = if (blockInfoAtEnd) {  
            // header 大小 = 当前 stream 位置  
            val headerSize = data.size.toLong() -  
                    compressedBlockInfoSize -  
                    blocks.sumOf { it.compressedSize.toLong() }  
            // 简化：从 header 结束后开始  
            findDataStart(data, blocks)  
        } else {  
            // header + compressed block info  
            8L + 4 + unityVersion.length + 1 +  
                    generatorVersion.length + 1 +  
                    8 + 4 + 4 + 4 + compressedBlockInfoSize  
        }  
  
        // 6. 解压所有数据块并拼接  
        val allData = ByteArrayOutputStream()  
        var currentOffset = dataOffset.toInt()  
  
        for (block in blocks) {  
            val blockCompression = block.flags.toInt() and 0x3F  
            val compressedData = data.copyOfRange(  
                currentOffset,  
                currentOffset + block.compressedSize  
            )  
  
            val decompressed = when (blockCompression) {  
                COMPRESSION_NONE -> compressedData  
                COMPRESSION_LZ4, COMPRESSION_LZ4HC -> {  
                    Lz4Decoder.decode(  
                        compressedData,  
                        block.uncompressedSize  
                    )  
                }  
                COMPRESSION_LZMA -> {  
                    throw Exception("LZMA compression not supported")  
                }  
                else -> throw Exception(  
                    "Unknown compression: $blockCompression"  
                )  
            }  
  
            allData.write(decompressed)  
            currentOffset += block.compressedSize  
        }  
  
        val fullData = allData.toByteArray()  
  
        // 7. 根据 directory 信息提取文件  
        val files = mutableListOf<BundleFileEntry>()  
        for (dir in directories) {  
            val start = dir.offset.toInt()  
            val end = start + dir.size.toInt()  
            if (end <= fullData.size) {  
                files.add(  
                    BundleFileEntry(  
                        dir.name,  
                        fullData.copyOfRange(start, end)  
                    )  
                )  
            }  
        }  
  
        Log.d(TAG, "Extracted ${files.size} files from bundle")  
        return files  
    }  
  
    private fun decompressBlockInfo(  
        compressed: ByteArray,  
        compressionType: Int,  
        uncompressedSize: Int  
    ): ByteArray {  
        return when (compressionType) {  
            COMPRESSION_NONE -> compressed  
            COMPRESSION_LZ4, COMPRESSION_LZ4HC -> {  
                Lz4Decoder.decode(compressed, uncompressedSize)  
            }  
            COMPRESSION_LZMA -> {  
                throw Exception("LZMA not supported for block info")  
            }  
            else -> throw Exception(  
                "Unknown block info compression: $compressionType"  
            )  
        }  
    }  
  
    /**  
     * 启发式查找数据块起始位置  
     */  
    private fun findDataStart(  
        data: ByteArray,  
        blocks: List<Any>  
    ): Long {  
        // 对于 blockInfoAtEnd 的情况，数据紧跟 header  
        // header 结束位置 = magic(8) + version(4) + strings + sizes  
        val stream = DataInputStream(ByteArrayInputStream(data))  
        stream.skipBytes(8) // magic  
        stream.readInt() // version  
        readNullTermString(stream) // unity version  
        readNullTermString(stream) // generator version  
        stream.readLong() // file size  
        stream.readInt() // compressed block info size  
        stream.readInt() // uncompressed block info size  
        stream.readInt() // flags  
  
        // 对齐到 16 字节边界（某些版本需要）  
        val pos = 8 + 4 + // magic + version 已知  
                data.size // 这个方法不太准确  
  
        // 更简单的方法：header 固定部分后就是数据  
        // 重新计算  
        var offset = 8 // magic "UnityFS\0"  
        offset += 4 // format version  
        // 跳过两个 null-terminated string  
        while (offset < data.size && data[offset] != 0.toByte()) offset++  
        offset++ // null terminator  
        while (offset < data.size && data[offset] != 0.toByte()) offset++  
        offset++ // null terminator  
        offset += 8 + 4 + 4 + 4 // fileSize + compressed + uncompressed + flags  
  
        return offset.toLong()  
    }  
  
    private fun readNullTermString(stream: DataInputStream): String {  
        val sb = StringBuilder()  
        while (true) {  
            val b = stream.readByte().toInt()  
            if (b == 0) break  
            sb.append(b.toChar())  
        }  
        return sb.toString()  
    }  
}