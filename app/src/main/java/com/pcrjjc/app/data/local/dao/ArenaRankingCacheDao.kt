package com.pcrjjc.app.data.local.dao  
  
import androidx.room.Dao  
import androidx.room.Insert  
import androidx.room.OnConflictStrategy  
import androidx.room.Query  
import com.pcrjjc.app.data.local.entity.ArenaRankingCache  
  
@Dao  
interface ArenaRankingCacheDao {  
  
    @Query("SELECT * FROM arena_ranking_cache WHERE platform = :platform AND arenaType = :arenaType ORDER BY rank ASC")  
    suspend fun getByPlatformAndType(platform: Int, arenaType: Int): List<ArenaRankingCache>  
  
    @Insert(onConflict = OnConflictStrategy.REPLACE)  
    suspend fun upsertAll(caches: List<ArenaRankingCache>)  
  
    @Query("DELETE FROM arena_ranking_cache WHERE platform = :platform AND arenaType = :arenaType")  
    suspend fun deleteByPlatformAndType(platform: Int, arenaType: Int)  
}