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
    val jjcNotice: Boolean = true,  
    val pjjcNotice: Boolean = true,  
    val upNotice: Boolean = false,  
    val onlineNotice: Int = 0  
)  
  
@Entity(tableName = "account")  
data class Account(  
    @PrimaryKey(autoGenerate = true)  
    val id: Int = 0,  
    val viewerId: String,  
    val account: String,  
    val password: String,  
    val platform: Int,  
    val isMaster: Boolean = false       // <-- 新增  
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