package com.pcrjjc.app.data.local.dao  
  
import androidx.room.Dao  
import androidx.room.Insert  
import androidx.room.OnConflictStrategy  
import androidx.room.Query  
import com.pcrjjc.app.data.local.entity.RankCache  
import kotlinx.coroutines.flow.Flow  
  
@Dao  
interface RankCacheDao {  
    @Query("SELECT * FROM rank_cache WHERE pcrid = :pcrid AND platform = :platform LIMIT 1")  
    suspend fun get(pcrid: Long, platform: Int): RankCache?  
    @Query("SELECT * FROM rank_cache")  
    suspend fun getAll(): List<RankCache>  
    @Query("SELECT * FROM rank_cache")  
    fun getAllFlow(): Flow<List<RankCache>>  
    @Insert(onConflict = OnConflictStrategy.REPLACE)  
    suspend fun upsert(cache: RankCache)  
    @Query("DELETE FROM rank_cache")  
    suspend fun deleteAll()  
}