package com.pcrjjc.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pcrjjc.app.data.local.entity.JjcHistory
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {

    @Query("SELECT * FROM jjc_history WHERE platform = :platform AND item != 2 ORDER BY date DESC LIMIT 50")
    fun getHistoryByPlatform(platform: Int): Flow<List<JjcHistory>>

    @Query("SELECT * FROM jjc_history WHERE platform = :platform AND pcrid = :pcrid AND item != 2 ORDER BY date DESC LIMIT 50")
    fun getHistoryByPcrid(platform: Int, pcrid: Long): Flow<List<JjcHistory>>

    @Query("SELECT * FROM jjc_history WHERE platform = :platform AND pcrid = :pcrid AND item != 2 ORDER BY date DESC LIMIT 50")
    suspend fun getHistoryByPcridSync(platform: Int, pcrid: Long): List<JjcHistory>

    @Query("""
        SELECT COUNT(*) FROM jjc_history 
        WHERE pcrid = :pcrid AND platform = :platform 
        AND item = :item AND before > after
        AND date > :startTime AND date < :endTime
    """)
    suspend fun getUpCount(pcrid: Long, platform: Int, item: Int, startTime: Long, endTime: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: JjcHistory): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(histories: List<JjcHistory>)

    @Query("DELETE FROM jjc_history WHERE platform = :platform")
    suspend fun deleteByPlatform(platform: Int)

    @Query("DELETE FROM jjc_history")
    suspend fun deleteAll()
}
