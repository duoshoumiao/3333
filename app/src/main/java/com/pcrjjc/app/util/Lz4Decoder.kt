package com.pcrjjc.app.util  
  
import net.jpountz.lz4.LZ4Factory  
  
object Lz4Decoder {  
    private val factory = LZ4Factory.fastestInstance()  
  
    fun decode(compressed: ByteArray, uncompressedSize: Int): ByteArray {  
        val decompressor = factory.safeDecompressor()  
        val output = ByteArray(uncompressedSize)  
        decompressor.decompress(  
            compressed, 0, compressed.size,  
            output, 0, uncompressedSize  
        )  
        return output  
    }  
}