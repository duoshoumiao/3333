package com.pcrjjc.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 签名器移植验证。
 * 期望值由参考实现 pcrdapi/pcrdapi.py 生成（见 arena_local_port/verify/sign_vectors.json）。
 */
class PcrdApiSignerTest {

    private data class Vector(val text: String, val nonce: String, val sign: String)

    private val vectors = listOf(
        Vector(
            "{\"def\":[101201,103401,104501,105201,113401],\"language\":0," +
                "\"nonce\":\"abc123\",\"page\":1,\"region\":2,\"sort\":1,\"ts\":1700000000}",
            "abc123",
            "切噜~巴啵拉嘭拉咧拉切拉叮拉蹦啰啵嘭蹦啰拉啵噜拉啵啵啵咧嘭嘭卟咧"
        ),
        Vector(
            "{\"def\":[110001,100801,101101,102601,108901],\"language\":0," +
                "\"nonce\":\"0000000000000000\",\"page\":1,\"region\":1,\"sort\":2,\"ts\":1755000000}",
            "0000000000000000",
            "切噜~噼噼唎蹦唎唎唎噜哔噼铃唎唎唎噜铃切噼切唎噜唎唎切唎切蹦蹦啪"
        ),
        Vector(
            "{\"def\":[101201,103401,104501,105201,113401],\"language\":0," +
                "\"nonce\":\"zzzzzzzzzzzzzzzz\",\"page\":1,\"region\":2,\"sort\":1,\"ts\":1760000000}",
            "zzzzzzzzzzzzzzzz",
            "切噜~咧咧巴啰啰巴巴嘭拉咧卟咧咧咧嘭卟叮咧叮巴嘭咧咧叮巴叮啵啵啰"
        ),
        Vector(
            "{\"def\":[100101,100201,100301,100401,100501],\"language\":0," +
                "\"nonce\":\"1a2b3c4d5e6f7a8b\",\"page\":1,\"region\":3,\"sort\":1,\"ts\":1800000000}",
            "1a2b3c4d5e6f7a8b",
            "切噜~唎唎哔蹦哔哔哔切噜唎啪哔噼噜切啪蹦唎蹦哔切蹦噜蹦哔蹦铃铃噼"
        ),
        Vector(
            "{\"def\":[120001,120101,120201,120301,120401],\"language\":0," +
                "\"nonce\":\"zzzz0000zzzz0000\",\"page\":1,\"region\":4,\"sort\":2,\"ts\":1900000000}",
            "zzzz0000zzzz0000",
            "切噜~拉拉嘭嘭嘭卟嘭啵叮拉咧拉嘭嘭啵咧卟拉卟嘭啵嘭拉卟嘭卟啰啰巴"
        ),
    )

    @Test
    fun matchesReferenceImplementation() {
        for (v in vectors) {
            val got = PcrdApiSigner.sign(v.text, v.nonce)
            assertEquals("签名与参考实现不一致 (nonce=${v.nonce})", v.sign, got)
        }
    }

    @Test
    fun isDeterministicAndFixedLength() {
        val v = vectors[0]
        val a = PcrdApiSigner.sign(v.text, v.nonce)
        val b = PcrdApiSigner.sign(v.text, v.nonce)
        assertEquals("同一输入应得到同一签名", a, b)
        assertEquals("签名长度应为 3+29 个字符", 32, a.length)
    }
}
