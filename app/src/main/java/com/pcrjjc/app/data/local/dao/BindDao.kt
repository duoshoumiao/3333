package com.pcrjjc.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.pcrjjc.app.data.local.entity.PcrBind
import kotlinx.coroutines.flow.Flow

@Dao
interface BindDao {

    @Query("SELECT * FROM pcr_bind WHERE platform = :platform ORDER BY id")
    fun getBindsByPlatform(platform: Int): Flow<List<PcrBind>>

    @Query("SELECT * FROM pcr_bind WHERE platform = :platform ORDER BY id")
    suspend fun getBindsByPlatformSync(platform: Int): List<PcrBind>

    @Query("SELECT * FROM pcr_bind ORDER BY id")
    fun getAllBinds(): Flow<List<PcrBind>>

    @Query("SELECT * FROM pcr_bind ORDER BY id")
    suspend fun getAllBindsSync(): List<PcrBind>

    @Query("SELECT * FROM pcr_bind WHERE id = :id")
    suspend fun getBindById(id: Int): PcrBind?

    @Query("SELECT * FROM pcr_bind WHERE pcrid = :pcrid AND platform = :platform LIMIT 1")
    suspend fun getBindByPcrid(pcrid: Long, platform: Int): PcrBind?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bind: PcrBind): Long

    @Update
    suspend fun update(bind: PcrBind)

    @Query("SELECT * FROM pcr_bind WHERE pcrid = :pcrid AND platform = :platform AND arenaType = :arenaType LIMIT 1")  
	suspend fun getBindByPcridAndType(pcrid: Long, platform: Int, arenaType: Int): PcrBind?
	
	@Query("DELETE FROM pcr_bind WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM pcr_bind WHERE pcrid = :pcrid AND platform = :platform")
    suspend fun deleteByPcrid(pcrid: Long, platform: Int)

    @Query("DELETE FROM pcr_bind WHERE platform = :platform")
    suspend fun deleteByPlatform(platform: Int)

    @Query("DELETE FROM pcr_bind")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM pcr_bind WHERE platform = :platform")
    suspend fun getBindCount(platform: Int): Int
}
