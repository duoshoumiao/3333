package com.pcrjjc.app.util  
  
import java.security.MessageDigest  
import android.util.Base64  
  
/**  
 * 移植自 HoshinoBot-V1/pcrdapi/pcrdapi.py 的签名算法。  
 * 用于生成 pcrdfans.com API 的 _sign 参数。  
 */  
object PcrdApiSigner {  
  
    private const val INT_MASK = 0xFFFFFFFFL  
    private const val N_HASH = 29  
  
    private val HEADER = byteArrayOf(  
        0x01, 0x04, 0x04, 0x77, 0x05, 0x02, 0x04, 0x0e,  
        0x00, 0x01, 0x0f, 0x70, 0x77, 0x70, 0x00, 0x0e,  
        0x72, 0x77, 0x07, 0x74, 0x74, 0x0e, 0x77, 0x07,  
        0x75, 0x70, 0x03, 0x72, 0x75, 0x73, 0x0e, 0x72,  
        0x36, 0x36, 0x36, 0x36, 0x36, 0x36, 0x36, 0x36,  
        0x36, 0x36, 0x36, 0x36, 0x36, 0x36, 0x36, 0x36,  
        0x36, 0x36, 0x36, 0x36, 0x36, 0x36, 0x36, 0x36,  
        0x36, 0x36, 0x36, 0x36, 0x36, 0x36, 0x36, 0x36  
    )  
  
    private val TAIL = "12566690671543B46BC9F74D39582E54".toByteArray(Charsets.US_ASCII)  
  
    private val HEADER2 = byteArrayOf(  
        0x6b, 0x6e, 0x6e, 0x1d, 0x6f, 0x68, 0x6e, 0x64,  
        0x6a, 0x6b, 0x65, 0x1a, 0x1d, 0x1a, 0x6a, 0x64,  
        0x18, 0x1d, 0x6d, 0x1e, 0x1e, 0x64, 0x1d, 0x6d,  
        0x1f, 0x1a, 0x69, 0x18, 0x1f, 0x19, 0x64, 0x18,  
        0x5c, 0x5c, 0x5c, 0x5c, 0x5c, 0x5c, 0x5c, 0x5c,  
        0x5c, 0x5c, 0x5c, 0x5c, 0x5c, 0x5c, 0x5c, 0x5c,  
        0x5c, 0x5c, 0x5c, 0x5c, 0x5c, 0x5c, 0x5c, 0x5c,  
        0x5c, 0x5c, 0x5c, 0x5c, 0x5c, 0x5c, 0x5c, 0x5c  
    )  
  
    private val TAIL2 = "5D60EFA990727AE5500CAB2CF9BB5F6F".toByteArray(Charsets.US_ASCII)  
  
    private val TABLES = arrayOf(  
        "巴噼叮噜啰铃拉唎啵切咧啪嘭哔卟蹦",  
        "啪巴拉铃切噜哔啵啰卟唎嘭叮噼蹦咧",  
        "唎啪噼嘭蹦哔拉咧啵卟啰噜巴铃切叮",  
        "拉卟噼咧唎叮噜巴切嘭啪哔蹦铃啵啰"  
    )  
  
    private const val HEADER3 = "切噜~"  
  
    /**  
     * 生成 API 签名。  
     * @param text JSON 字符串（不含 _sign 字段）  
     * @param nonce 随机字符串  
     * @return "切噜~" 开头的签名字符串  
     */  
    fun sign(text: String, nonce: String): String {  
        val toHash = (text + nonce).toByteArray(Charsets.UTF_8)  
  
        // 双重 SHA256  
        val md1 = MessageDigest.getInstance("SHA-256")  
        md1.update(HEADER)  
        md1.update(toHash)  
        md1.update(TAIL)  
        var digest = md1.digest()  
  
        val md2 = MessageDigest.getInstance("SHA-256")  
        md2.update(HEADER2)  
        md2.update(digest)  
        digest = md2.digest()  
  
        // base64 编码 + TAIL2  
        val b64Bytes = Base64.encode(digest, Base64.NO_WRAP)  
        val b64 = b64Bytes + TAIL2  
  
        // 状态机  
        val s = longArrayOf(0x6295C58DL, 0x62B82175L, 0x07BB0142L, 0x6C62272EL)  
  
        for (byte in b64) {  
            val bs = byte.toLong() and 0xFFL  
            val t = bs xor s[0]  
            val t2 = s[1]  
            s[0] = (315L * t) and INT_MASK  
            s[1] = (315L * t2 + (s[0] shr 16)) and INT_MASK  
            s[2] = (315L * s[2] + (s[1] shr 16) + (t shl 24)) and INT_MASK  
            s[3] = (315L * s[3] + (s[2] shr 16) + (t2 shl 24)) and INT_MASK  
        }  
  
        val tFinal = if (s[2] and 1L != 0L) {  
            (s[0] or (s[1] shl 8)) and INT_MASK  
        } else {  
            (s[2] or (s[3] shl 8)) and INT_MASK  
        }  
        val offset = tFinal shr 2  
        val tableId = (offset % 3).toInt()  
  
        // 生成 idxs  
        var t = 0L  
        var i = 0  
        val idxs = mutableListOf<Int>()  
        val combined = toHash + TAIL  
  
        for (byte in combined) {  
            val x = byte.toLong() and 0xFFL  
            if (i == 0) t = 0L  
            t = (t shl 1) or (x and 1L)  
            val idx = (x and 0xFEL) - 1L  
            i += 1  
            if (i == 4) {  
                i = 0  
                val v = if (t != 0L) t else idx  
                idxs.add(floorMod((v + offset), 16).toInt())  
            } else {  
                idxs.add(floorMod((idx + offset), 16).toInt())  
            }  
        }  
  
        val n = idxs.size  
        val res = arrayOfNulls<Char>(N_HASH)  
        val step = n / N_HASH  
        val table = TABLES[tableId]  
  
        for (j in 0..N_HASH / 2) {  
            res[j] = table[idxs[(step * j) % n]]  
            res[N_HASH - 1 - j] = table[idxs[((n - 1 - step * j) % n + n) % n]]  
        }  
  
        return HEADER3 + res.joinToString("")  
    }  
  
    /** Python 风格的 floorMod，保证结果非负 */  
    private fun floorMod(a: Long, b: Int): Long {  
        val r = a % b  
        return if (r < 0) r + b else r  
    }  
}