package com.pcrjjc.app.data.local.entity  
  
import androidx.room.Entity  
import androidx.room.PrimaryKey  
  
@Entity(tableName = "pcr_bind")  
data class PcrBind(  
    @PrimaryKey(autoGenerate = true)  
    val id: Int = 0,  
    val pcrid: Long,  
    val platform: Int,  
    val name: String? = null,  
    val jjcNotice: Boolean = false,  
    val pjjcNotice: Boolean = false,  
    val upNotice: Boolean = false,  
    val onlineNotice: Int = 0,  
    val arenaType: Int = 0          // 0=手动绑定, 1=JJC透视绑定, 2=PJJC透视绑定  
)  
  
@Entity(tableName = "account")  
data class Account(  
    @PrimaryKey(autoGenerate = true)  
    val id: Int = 0,  
    val viewerId: String,  
    val account: String,  
    val password: String,  
    val platform: Int,  
    val isMaster: Boolean = false   // true=我的账号(仅透视), false=监控号  
)
  
@Entity(tableName = "jjc_history")  
data class JjcHistory(  
    @PrimaryKey(autoGenerate = true)  
    val id: Int = 0,  
    val pcrid: Long,  
    val name: String,  
    val platform: Int,  
    val date: Long,  
    val item: Int,  
    val before: Int,  
    val after: Int  
)  
  
/**  
 * Rank cache entity - persists last known ranks so that  
 * RankMonitor can detect changes across Worker/Service restarts.  
 */  
@Entity(  
    tableName = "rank_cache",  
    primaryKeys = ["pcrid", "platform"]  
)  
data class RankCache(  
    val pcrid: Long,  
    val platform: Int,  
    val arenaRank: Int,  
    val grandArenaRank: Int,  
    val lastLoginTime: Int  
)

@Entity(  
    tableName = "arena_ranking_cache",  
    primaryKeys = ["platform", "arenaType", "viewerId"]  
)  
data class ArenaRankingCache(  
    val platform: Int,  
    val arenaType: Int,       // 1=JJC, 2=PJJC  
    val viewerId: Long,  
    val rank: Int,  
    val userName: String,  
    val teamLevel: Int,  
    val queryTime: Long       // 查询时间戳  
)