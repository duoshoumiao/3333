package com.pcrjjc.app.util  
  
import android.graphics.Bitmap  
import android.util.Log  
  
/**  
 * 将 Unity Texture2D 的原始数据解码为 Android Bitmap  
 * 支持格式：RGBA32(4), ETC2_RGBA8(47), ETC2_RGB(34)  
 */  
object TextureDecoder {  
    private const val TAG = "TextureDecoder"  
  
    // Unity TextureFormat 枚举值  
    private const val RGBA32 = 4  
    private const val RGB24 = 3  
    private const val ARGB32 = 5  
    private const val ETC_RGB4 = 34  
    private const val ETC2_RGB = 45  
    private const val ETC2_RGBA8 = 47  
  
    fun decode(tex: Texture2DInfo): Bitmap? {  
        return try {  
            val pixels = when (tex.textureFormat) {  
                RGBA32 -> decodeRGBA32(tex)  
                ARGB32 -> decodeARGB32(tex)  
                RGB24 -> decodeRGB24(tex)  
                ETC2_RGBA8 -> decodeETC2RGBA8(tex)  
                ETC2_RGB, ETC_RGB4 -> decodeETC2RGB(tex)  
                else -> {  
                    Log.w(TAG, "Unsupported format: ${tex.textureFormat}")  
                    null  
                }  
            } ?: return null  
  
            val bitmap = Bitmap.createBitmap(  
                tex.width, tex.height, Bitmap.Config.ARGB_8888  
            )  
            bitmap.setPixels(pixels, 0, tex.width, 0, 0, tex.width, tex.height)  
            // Unity 纹理 Y 轴翻转  
            flipVertically(bitmap)  
        } catch (e: Exception) {  
            Log.e(TAG, "Decode failed: ${e.message}")  
            null  
        }  
    }  
  
    private fun flipVertically(src: Bitmap): Bitmap {  
        val matrix = android.graphics.Matrix()  
        matrix.preScale(1f, -1f)  
        val flipped = Bitmap.createBitmap(  
            src, 0, 0, src.width, src.height, matrix, false  
        )  
        if (flipped != src) src.recycle()  
        return flipped  
    }  
  
    // ===== RGBA32 =====  
    private fun decodeRGBA32(tex: Texture2DInfo): IntArray {  
        val d = tex.imageData  
        val pixels = IntArray(tex.width * tex.height)  
        for (i in pixels.indices) {  
            val off = i * 4  
            if (off + 3 >= d.size) break  
            val r = d[off].toInt() and 0xFF  
            val g = d[off + 1].toInt() and 0xFF  
            val b = d[off + 2].toInt() and 0xFF  
            val a = d[off + 3].toInt() and 0xFF  
            pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b  
        }  
        return pixels  
    }  
  
    // ===== ARGB32 =====  
    private fun decodeARGB32(tex: Texture2DInfo): IntArray {  
        val d = tex.imageData  
        val pixels = IntArray(tex.width * tex.height)  
        for (i in pixels.indices) {  
            val off = i * 4  
            if (off + 3 >= d.size) break  
            val a = d[off].toInt() and 0xFF  
            val r = d[off + 1].toInt() and 0xFF  
            val g = d[off + 2].toInt() and 0xFF  
            val b = d[off + 3].toInt() and 0xFF  
            pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b  
        }  
        return pixels  
    }  
  
    // ===== RGB24 =====  
    private fun decodeRGB24(tex: Texture2DInfo): IntArray {  
        val d = tex.imageData  
        val pixels = IntArray(tex.width * tex.height)  
        for (i in pixels.indices) {  
            val off = i * 3  
            if (off + 2 >= d.size) break  
            val r = d[off].toInt() and 0xFF  
            val g = d[off + 1].toInt() and 0xFF  
            val b = d[off + 2].toInt() and 0xFF  
            pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b  
        }  
        return pixels  
    }  
  
    // ===== ETC2 RGBA8 =====  
    // 每个 4x4 块 = 16 字节 (8 alpha EAC + 8 color ETC2)  
    private fun decodeETC2RGBA8(tex: Texture2DInfo): IntArray {  
        val w = tex.width  
        val h = tex.height  
        val pixels = IntArray(w * h)  
        val d = tex.imageData  
        val bw = (w + 3) / 4  
        val bh = (h + 3) / 4  
        var offset = 0  
  
        for (by in 0 until bh) {  
            for (bx in 0 until bw) {  
                if (offset + 16 > d.size) break  
                // 8 bytes alpha (EAC)  
                val alphas = decodeEACBlock(d, offset)  
                offset += 8  
                // 8 bytes color (ETC2)  
                val colors = decodeETC2Block(d, offset)  
                offset += 8  
  
                // 写入像素  
                for (py in 0 until 4) {  
                    for (px in 0 until 4) {  
                        val x = bx * 4 + px  
                        val y = by * 4 + py  
                        if (x < w && y < h) {  
                            val idx = py * 4 + px  
                            val rgb = colors[idx]  
                            val a = alphas[idx]  
                            pixels[y * w + x] = (a shl 24) or rgb  
                        }  
                    }  
                }  
            }  
        }  
        return pixels  
    }  
  
    // ===== ETC2 RGB =====  
    // 每个 4x4 块 = 8 字节  
    private fun decodeETC2RGB(tex: Texture2DInfo): IntArray {  
        val w = tex.width  
        val h = tex.height  
        val pixels = IntArray(w * h)  
        val d = tex.imageData  
        val bw = (w + 3) / 4  
        val bh = (h + 3) / 4  
        var offset = 0  
  
        for (by in 0 until bh) {  
            for (bx in 0 until bw) {  
                if (offset + 8 > d.size) break  
                val colors = decodeETC2Block(d, offset)  
                offset += 8  
  
                for (py in 0 until 4) {  
                    for (px in 0 until 4) {  
                        val x = bx * 4 + px  
                        val y = by * 4 + py  
                        if (x < w && y < h) {  
                            pixels[y * w + x] =  
                                (0xFF shl 24) or colors[py * 4 + px]  
                        }  
                    }  
                }  
            }  
        }  
        return pixels  
    }  
  
    // ===== EAC Alpha Block (8 bytes -> 16 alpha values) =====  
    private fun decodeEACBlock(data: ByteArray, off: Int): IntArray {  
        val baseCodeword = data[off].toInt() and 0xFF  
        val multiplier = (data[off + 1].toInt() and 0xFF) shr 4  
        val tableIdx = (data[off + 1].toInt() and 0x0F)  
  
        val modTable = EAC_MODIFIER_TABLE[tableIdx]  
  
        // 48 bits of indices (3 bits per pixel, 16 pixels)  
        val bits = ((data[off + 2].toLong() and 0xFF) shl 40) or  
                ((data[off + 3].toLong() and 0xFF) shl 32) or  
                ((data[off + 4].toLong() and 0xFF) shl 24) or  
                ((data[off + 5].toLong() and 0xFF) shl 16) or  
                ((data[off + 6].toLong() and 0xFF) shl 8) or  
                (data[off + 7].toLong() and 0xFF)  
  
        val alphas = IntArray(16)  
        for (i in 0 until 16) {  
            val bitIdx = 45 - i * 3  
            val idx = ((bits shr bitIdx) and 0x7).toInt()  
            var alpha = baseCodeword + modTable[idx] * multiplier  
            alpha = alpha.coerceIn(0, 255)  
            alphas[i] = alpha  
        }  
        return alphas  
    }  
  
    // ===== ETC2 Color Block (8 bytes -> 16 RGB values) =====  
    // 返回 IntArray[16]，每个值为 0x00RRGGBB  
    private fun decodeETC2Block(data: ByteArray, off: Int): IntArray {  
        val b0 = data[off].toInt() and 0xFF  
        val b1 = data[off + 1].toInt() and 0xFF  
        val b2 = data[off + 2].toInt() and 0xFF  
        val b3 = data[off + 3].toInt() and 0xFF  
  
        val diffBit = (b3 shr 1) and 1  
        val flipBit = b3 and 1  
  
        // 像素索引 (32 bits)  
        val pixelBits = ((data[off + 4].toLong() and 0xFF) shl 24) or  
                ((data[off + 5].toLong() and 0xFF) shl 16) or  
                ((data[off + 6].toLong() and 0xFF) shl 8) or  
                (data[off + 7].toLong() and 0xFF)  
  
        val colors = IntArray(16)  
  
        if (diffBit == 0) {  
            // Individual mode  
            val r1 = extend4to8((b0 shr 4) and 0xF)  
            val g1 = extend4to8((b1 shr 4) and 0xF)  
            val b1c = extend4to8((b2 shr 4) and 0xF)  
            val r2 = extend4to8(b0 and 0xF)  
            val g2 = extend4to8(b1 and 0xF)  
            val b2c = extend4to8(b2 and 0xF)  
  
            val table1 = ETC1_MODIFIER_TABLE[(b3 shr 5) and 7]  
            val table2 = ETC1_MODIFIER_TABLE[(b3 shr 2) and 7]  
  
            fillETC1SubBlock(  
                colors, pixelBits, flipBit,  
                r1, g1, b1c, table1,  
                r2, g2, b2c, table2  
            )  
        } else {  
            // Check for special modes  
            val r = (b0 shr 3) and 0x1F  
            val dr = signExtend3((b0 and 0x7))  
            val rSum = r + dr  
  
            val g = (b1 shr 3) and 0x1F  
            val dg = signExtend3((b1 and 0x7))  
            val gSum = g + dg  
  
            val b = (b2 shr 3) and 0x1F  
            val db = signExtend3((b2 and 0x7))  
            val bSum = b + db  
  
            if (rSum < 0 || rSum > 31) {  
                // T mode  
                decodeTMode(data, off, colors)  
            } else if (gSum < 0 || gSum > 31) {  
                // H mode  
                decodeHMode(data, off, colors)  
            } else if (bSum < 0 || bSum > 31) {  
                // Planar mode  
                decodePlanarMode(data, off, colors)  
            } else {  
                // Differential mode  
                val r1 = extend5to8(r)  
                val g1 = extend5to8(g)  
                val b1 = extend5to8(b and 0x1F)  
                val r2 = extend5to8(rSum)  
                val g2 = extend5to8(gSum)  
                val b2 = extend5to8(bSum)  
  
                val table1 = ETC1_MODIFIER_TABLE[(b3 shr 5) and 7]  
                val table2 = ETC1_MODIFIER_TABLE[(b3 shr 2) and 7]  
  
                fillETC1SubBlock(  
                    colors, pixelBits, flipBit,  
                    r1, g1, b1, table1,  
                    r2, g2, b2, table2  
                )  
            }  
        }  
        return colors  
    }  
  
    private fun decodeTMode(data: ByteArray, off: Int, out: IntArray) {  
        val b0 = data[off].toInt() and 0xFF  
        val b1 = data[off + 1].toInt() and 0xFF  
        val b2 = data[off + 2].toInt() and 0xFF  
        val b3 = data[off + 3].toInt() and 0xFF  
  
        val r1 = extend4to8(((b0 shr 1) and 0xC) or (b0 and 0x3))  
        val g1 = extend4to8((b1 shr 4) and 0xF)  
        val b1c = extend4to8(((b1 and 0x3) shl 2) or ((b2 shr 6) and 0x3))  
        val r2 = extend4to8((b2 shr 2) and 0xF)  
        val g2 = extend4to8(((b2 and 0x3) shl 2) or ((b3 shr 6) and 0x3))  
        val b2c = extend4to8((b3 shr 2) and 0xF)  
  
        val distIdx = ((b3 shr 1) and 0x6) or (b3 and 1)  
        val dist = T_H_DISTANCES[distIdx]  
  
        val paintColors = arrayOf(  
            intArrayOf(r1, g1, b1c),  
            intArrayOf(clamp255(r2 + dist), clamp255(g2 + dist), clamp255(b2c + dist)),  
            intArrayOf(r2, g2, b2c),  
            intArrayOf(clamp255(r2 - dist), clamp255(g2 - dist), clamp255(b2c - dist))  
        )  
  
        val pixelBits = ((data[off + 4].toLong() and 0xFF) shl 24) or  
                ((data[off + 5].toLong() and 0xFF) shl 16) or  
                ((data[off + 6].toLong() and 0xFF) shl 8) or  
                (data[off + 7].toLong() and 0xFF)  
  
        for (i in 0 until 16) {  
            val msb = ((pixelBits shr (15 - i)) and 1).toInt()  
            val lsb = ((pixelBits shr (31 - i)) and 1).toInt()  
            val idx = (msb shl 1) or lsb  
            val c = paintColors[idx]  
            out[i] = (c[0] shl 16) or (c[1] shl 8) or c[2]  
        }  
    }  
  
    private fun decodeHMode(data: ByteArray, off: Int, out: IntArray) {  
        val b0 = data[off].toInt() and 0xFF  
        val b1 = data[off + 1].toInt() and 0xFF  
        val b2 = data[off + 2].toInt() and 0xFF  
        val b3 = data[off + 3].toInt() and 0xFF  
  
        val r1 = extend4to8((b0 shr 3) and 0xF)  
        val g1 = extend4to8(((b0 and 0x7) shl 1) or ((b1 shr 4) and 1))  
        val b1c = extend4to8(((b1 and 0x8) shr 1) or ((b1 and 0x3) shl 1) or ((b2 shr 7) and 1))  
        val r2 = extend4to8((b2 shr 3) and 0xF)  
        val g2 = extend4to8(((b2 and 0x7) shl 1) or ((b3 shr 7) and 1))  
        val b2c = extend4to8((b3 shr 3) and 0xF)  
  
        val baseVal = ((r1 shl 16) or (g1 shl 8) or b1c) >= ((r2 shl 16) or (g2 shl 8) or b2c)  
        val distBit = (b3 shr 2) and 1  
        val distIdx = ((b3 and 0x4) shr 1) or (if (baseVal) 1 else 0) or (distBit shl 2)  
        val dist = T_H_DISTANCES[distIdx.coerceIn(0, 7)]  
  
        val paintColors = arrayOf(  
            intArrayOf(clamp255(r1 + dist), clamp255(g1 + dist), clamp255(b1c + dist)),  
            intArrayOf(clamp255(r1 - dist), clamp255(g1 - dist), clamp255(b1c - dist)),  
            intArrayOf(clamp255(r2 + dist), clamp255(g2 + dist), clamp255(b2c + dist)),  
            intArrayOf(clamp255(r2 - dist), clamp255(g2 - dist), clamp255(b2c - dist))  
        )  
  
        val pixelBits = ((data[off + 4].toLong() and 0xFF) shl 24) or  
                ((data[off + 5].toLong() and 0xFF) shl 16) or  
                ((data[off + 6].toLong() and 0xFF) shl 8) or  
                (data[off + 7].toLong() and 0xFF)  
  
        for (i in 0 until 16) {  
            val msb = ((pixelBits shr (15 - i)) and 1).toInt()  
            val lsb = ((pixelBits shr (31 - i)) and 1).toInt()  
            val idx = (msb shl 1) or lsb  
            val c = paintColors[idx]  
            out[i] = (c[0] shl 16) or (c[1] shl 8) or c[2]  
        }  
    }  
  
    private fun decodePlanarMode(data: ByteArray, off: Int, out: IntArray) {  
        val b0 = data[off].toInt() and 0xFF  
        val b1 = data[off + 1].toInt() and 0xFF  
        val b2 = data[off + 2].toInt() and 0xFF  
        val b3 = data[off + 3].toInt() and 0xFF  
        val b4 = data[off + 4].toInt() and 0xFF  
        val b5 = data[off + 5].toInt() and 0xFF  
        val b6 = data[off + 6].toInt() and 0xFF  
        val b7 = data[off + 7].toInt() and 0xFF  
  
        val ro = extend6to8((b0 shr 1) and 0x3F)  
        val go = extend7to8(((b0 and 1) shl 6) or ((b1 shr 1) and 0x3F))  
        val bo = extend6to8(((b1 and 1) shl 5) or ((b2 shr 3) and 0x18) or (b2 and 0x3) or ((b3 shr 1) and 0x4))  
        val rh = extend6to8(((b3 and 1) shl 5) or ((b4 shr 3) and 0x1F))  
        val gh = extend7to8(((b4 and 0x7) shl 4) or ((b5 shr 4) and 0xF))  
        val bh = extend6to8(((b5 and 0xF) shl 2) or ((b6 shr 6) and 0x3))  
        val rv = extend6to8((b6 and 0x3F))  
        val gv = extend7to8((b7 shr 1) and 0x7F)  
        val bv = extend6to8(((b7 and 1) shl 5) or 0) // simplified  
  
        for (y in 0 until 4) {  
            for (x in 0 until 4) {  
                val r = clamp255((x * (rh - ro) + y * (rv - ro) + 4 * ro + 2) shr 2)  
                val g = clamp255((x * (gh - go) + y * (gv - go) + 4 * go + 2) shr 2)  
                val b = clamp255((x * (bh - bo) + y * (bv - bo) + 4 * bo + 2) shr 2)  
                out[y * 4 + x] = (r shl 16) or (g shl 8) or b  
            }  
        }  
    }  
  
    private fun fillETC1SubBlock(  
        out: IntArray, pixelBits: Long, flipBit: Int,  
        r1: Int, g1: Int, b1: Int, table1: IntArray,  
        r2: Int, g2: Int, b2: Int, table2: IntArray  
    ) {  
        for (i in 0 until 16) {  
            val x = i % 4  
            val y = i / 4  
            val isSecond = if (flipBit == 1) y >= 2 else x >= 2  
  
            val r: Int; val g: Int; val b: Int; val table: IntArray  
            if (!isSecond) { r = r1; g = g1; b = b1; table = table1 }  
            else { r = r2; g = g2; b = b2; table = table2 }  
  
            val msb = ((pixelBits shr (15 - i)) and 1).toInt()  
            val lsb = ((pixelBits shr (31 - i)) and 1).toInt()  
            val idx = (msb shl 1) or lsb  
            val mod = table[idx]  
  
            out[i] = (clamp255(r + mod) shl 16) or  
                    (clamp255(g + mod) shl 8) or  
                    clamp255(b + mod)  
        }  
    }  
  
    private fun extend4to8(v: Int): Int = (v shl 4) or v  
    private fun extend5to8(v: Int): Int = (v shl 3) or (v shr 2)  
    private fun extend6to8(v: Int): Int = (v shl 2) or (v shr 4)  
    private fun extend7to8(v: Int): Int = (v shl 1) or (v shr 6)  
    private fun clamp255(v: Int): Int = v.coerceIn(0, 255)  
    private fun signExtend3(v: Int): Int = if (v >= 4) v - 8 else v  
  
    private val ETC1_MODIFIER_TABLE = arrayOf(  
        intArrayOf(2, 8, -2, -8),  
        intArrayOf(5, 17, -5, -17),  
        intArrayOf(9, 29, -9, -29),  
        intArrayOf(13, 42, -13, -42),  
        intArrayOf(18, 60, -18, -60),  
        intArrayOf(24, 80, -24, -80),  
        intArrayOf(33, 106, -33, -106),  
        intArrayOf(47, 183, -47, -183)  
    )  
  
    private val EAC_MODIFIER_TABLE = arrayOf(  
        intArrayOf(-3, -6, -9, -15, 2, 5, 8, 14),  
        intArrayOf(-3, -7, -10, -13, 2, 6, 9, 12),  
        intArrayOf(-2, -5, -8, -13, 1, 4, 7, 12),  
        intArrayOf(-2, -4, -6, -13, 1, 3, 5, 12),  
        intArrayOf(-3, -6, -8, -12, 2, 5, 7, 11),  
        intArrayOf(-3, -7, -9, -11, 2, 6, 8, 10),  
        intArrayOf(-4, -7, -8, -11, 3, 6, 7, 10),  
        intArrayOf(-3, -5, -8, -11, 2, 4, 7, 10),  
        intArrayOf(-2, -6, -8, -10, 1, 5, 7, 9),  
        intArrayOf(-2, -5, -8, -10, 1, 4, 7, 9),  
        intArrayOf(-2, -4, -8, -10, 1, 3, 7, 9),  
        intArrayOf(-2, -5, -7, -10, 1, 4, 6, 9),  
        intArrayOf(-3, -4, -7, -10, 2, 3, 6, 9),  
        intArrayOf(-1, -2, -3, -10, 0, 1, 2, 9),  
        intArrayOf(-4, -6, -8, -9, 3, 5, 7, 8),  
        intArrayOf(-3, -5, -7, -9, 2, 4, 6, 8)  
    )  
  
    private val T_H_DISTANCES = intArrayOf(3, 6, 11, 16, 23, 32, 41, 64)  
}