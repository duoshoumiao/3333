package com.pcrjjc.app.util.arena

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sin

/**
 * 移植自 arena/old_main.py 的 cut_image / trans_hash / difference_value。
 *
 * 原实现依赖 PIL 的 LANCZOS。Android 没有 PIL，这里用等价的自写 Lanczos3。
 * 注意：指纹库（assets/arena/hashes.json）也是用同一套算法生成的，
 * 两边必须保持一致，否则会丢精度。
 */
object ArenaLanczos {

    private const val A = 3.0
    const val OUT_W = 17
    const val OUT_H = 16

    private fun lanczos(x: Double): Double {
        if (x == 0.0) return 1.0
        val v = abs(x)
        if (v >= A) return 0.0
        val px = PI * v
        return (A * sin(px) * sin(px / A)) / (px * px)
    }

    /** 预计算一维重采样系数：outLen 个输出点，每个对应若干 (输入下标, 权重)。 */
    private class Weights(inLen: Int, outLen: Int) {
        val index: Array<IntArray>
        val weight: Array<DoubleArray>

        init {
            val scale = inLen.toDouble() / outLen
            val support = A * maxOf(1.0, scale)
            index = Array(outLen) { IntArray(0) }
            weight = Array(outLen) { DoubleArray(0) }
            for (i in 0 until outLen) {
                val center = (i + 0.5) * scale - 0.5
                val lo = floor(center - support).toInt()
                val hi = ceil(center + support).toInt()
                // 先收集，再归一化，与 PIL 的抗锯齿思路一致
                var total = 0.0
                val idx = ArrayList<Int>(8)
                val w = ArrayList<Double>(8)
                for (j in lo..hi) {
                    val ww = lanczos((j - center) / maxOf(1.0, scale))
                    if (ww != 0.0) {
                        total += ww
                        idx.add(j.coerceIn(0, inLen - 1))
                        w.add(ww)
                    }
                }
                if (total == 0.0) total = 1.0
                index[i] = idx.toIntArray()
                weight[i] = DoubleArray(w.size) { w[it] / total }
            }
        }
    }

    /**
     * 灰度图的分离式 Lanczos3 重采样。
     */
    fun resizeGray(src: FloatArray, sw: Int, sh: Int, dw: Int, dh: Int): FloatArray {
        if (sw == dw && sh == dh) return src.copyOf()
        val cx = Weights(sw, dw)
        val cy = Weights(sh, dh)

        // 水平
        val tmp = FloatArray(sh * dw)
        for (y in 0 until sh) {
            val rowOff = y * sw
            for (x in 0 until dw) {
                val idx = cx.index[x]
                val w = cx.weight[x]
                var acc = 0.0
                for (k in idx.indices) acc += src[rowOff + idx[k]] * w[k]
                tmp[y * dw + x] = acc.toFloat()
            }
        }
        // 垂直
        val dst = FloatArray(dh * dw)
        for (y in 0 until dh) {
            val idx = cy.index[y]
            val w = cy.weight[y]
            for (x in 0 until dw) {
                var acc = 0.0
                for (k in idx.indices) acc += tmp[idx[k] * dw + x] * w[k]
                dst[y * dw + x] = acc.toFloat()
            }
        }
        return dst
    }

    /**
     * 生成 256bit 指纹（16 行 × 16 次相邻比较），按位打包成 32 字节。
     * 位序：行优先，每字节高位在前 —— 与 build_assets.py 的 hex 编码一致。
     */
    fun fingerprint(gray: FloatArray, w: Int, h: Int): ByteArray {
        val small = resizeGray(gray, w, h, OUT_W, OUT_H)
        val out = ByteArray(32)
        var bit = 0
        for (r in 0 until OUT_H) {
            for (c in 1 until OUT_W) {
                val prev = small[r * OUT_W + c - 1]
                val cur = small[r * OUT_W + c]
                if (prev > cur) {
                    val byteIdx = bit shr 3
                    out[byteIdx] = (out[byteIdx].toInt() or (1 shl (7 - (bit and 7)))).toByte()
                }
                bit++
            }
        }
        return out
    }

    /** 汉明距离（= 原算法「逐位绝对差之和」）。 */
    fun distance(a: ByteArray, b: ByteArray): Int {
        var d = 0
        for (i in a.indices) {
            d += Integer.bitCount((a[i].toInt() xor b[i].toInt()) and 0xFF)
        }
        return d
    }

    /** 把 ARGB 像素区的某一矩形转为灰度浮点数组。 */
    fun toGray(px: IntArray, w: Int, x0: Int, y0: Int, cw: Int, ch: Int): FloatArray {
        val out = FloatArray(cw * ch)
        for (y in 0 until ch) {
            val row = (y0 + y) * w + x0
            for (x in 0 until cw) {
                val p = px[row + x]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                out[y * cw + x] = (r * 299 + g * 587 + b * 114) / 1000f
            }
        }
        return out
    }

    fun toGray(px: IntArray): FloatArray {
        val out = FloatArray(px.size)
        for (i in px.indices) {
            val p = px[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            out[i] = (r * 299 + g * 587 + b * 114) / 1000f
        }
        return out
    }
}
