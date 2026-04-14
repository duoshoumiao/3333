package com.pcrjjc.app.util

enum class Platform(val id: Int, val displayName: String) {
    B_SERVER(0, "B服"),
    QU_SERVER(1, "渠服"),
    TW_SERVER(2, "台服");

    companion object {
        fun fromId(id: Int): Platform = entries.firstOrNull { it.id == id } ?: B_SERVER

        fun fromPrefix(prefix: String): Platform = when (prefix) {
            "渠" -> QU_SERVER
            "台" -> TW_SERVER
            else -> B_SERVER
        }
    }
}

enum class NoticeType(val id: Int) {
    JJC(0),
    PJJC(1),
    ONLINE(2)
}

val TW_SERVER_NAMES = mapOf(
    1 to "美食殿堂",
    2 to "真步真步王国",
    3 to "破晓之星",
    4 to "小小甜心"
)

fun getTwServerName(pcrid: Int): String {
    return TW_SERVER_NAMES[pcrid / 1000000000] ?: "未知服务器"
}
