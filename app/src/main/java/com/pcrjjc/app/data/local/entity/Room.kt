package com.pcrjjc.app.data.local.entity

/**
 * 房间信息（来自服务端的API响应，不持久化）
 */
data class Room(
    val roomId: String,        // 服务器返回的房间ID
    val roomName: String,      // 房间名称
    val password: String? = null,  // 房间密码（如果设置了）
    val hostName: String,      // 房主昵称
    val hostQq: String,        // 房主QQ
    val playerCount: Int = 0,  // 当前玩家数量
    val maxPlayers: Int = 8,   // 最大玩家数量
    val players: List<PlayerInfo> = emptyList() // 玩家列表（可能为空，取决于接口）
)

/**
 * 房间内玩家信息
 */
data class PlayerInfo(
    val name: String,
    val qq: String,
    val isHost: Boolean
)
