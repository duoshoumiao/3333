package com.pcrjjc.app.data.local  
  
import androidx.room.Database  
import androidx.room.RoomDatabase  
import androidx.room.migration.Migration  
import androidx.sqlite.db.SupportSQLiteDatabase  
import com.pcrjjc.app.data.local.dao.AccountDao  
import com.pcrjjc.app.data.local.dao.BindDao  
import com.pcrjjc.app.data.local.dao.HistoryDao  
import com.pcrjjc.app.data.local.dao.RankCacheDao  
import com.pcrjjc.app.data.local.dao.ArenaRankingCacheDao
import com.pcrjjc.app.data.local.entity.Account  
import com.pcrjjc.app.data.local.entity.JjcHistory  
import com.pcrjjc.app.data.local.entity.PcrBind  
import com.pcrjjc.app.data.local.entity.RankCache  
import com.pcrjjc.app.data.local.entity.ArenaRankingCache  

@Database(  
    entities = [PcrBind::class, Account::class, JjcHistory::class, RankCache::class, ArenaRankingCache::class],  
    version = 4,  
    exportSchema = true  
) 
abstract class AppDatabase : RoomDatabase() {  
    abstract fun bindDao(): BindDao  
    abstract fun accountDao(): AccountDao
    abstract fun arenaRankingCacheDao(): ArenaRankingCacheDao	
    abstract fun historyDao(): HistoryDao  
    abstract fun rankCacheDao(): RankCacheDao  
  
    companion object {  
        val MIGRATION_2_3 = object : Migration(2, 3) {  
            override fun migrate(db: SupportSQLiteDatabase) {  
                db.execSQL("ALTER TABLE account ADD COLUMN isMaster INTEGER NOT NULL DEFAULT 0")  
            }  
        }  
        val MIGRATION_3_4 = object : Migration(3, 4) {  
            override fun migrate(db: SupportSQLiteDatabase) {  
                db.execSQL("""  
                    CREATE TABLE IF NOT EXISTS arena_ranking_cache (  
                        platform INTEGER NOT NULL,  
                        arenaType INTEGER NOT NULL,  
                        viewerId INTEGER NOT NULL,  
                        rank INTEGER NOT NULL,  
                        userName TEXT NOT NULL,  
                        teamLevel INTEGER NOT NULL,  
                        queryTime INTEGER NOT NULL,  
                        PRIMARY KEY (platform, arenaType, viewerId)  
                    )  
                """.trimIndent())  
            }  
        }
	}  
}