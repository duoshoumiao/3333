package com.pcrjjc.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 房间实体
 */
@Entity(tableName = "rooms")
data class Room(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val roomId: String,        // 服务器返回的房间ID
    val roomName: String,      // 房间名称
    val password: String? = null,  // 房间密码（如果设置了）
    val hostName: String,      // 房主名称
    val hostQq: String,        // 房主QQ
    val playerCount: Int = 0,  // 当前玩家数量
    val maxPlayers: Int = 8,   // 最大玩家数量
    val createdAt: Long = System.currentTimeMillis()
)