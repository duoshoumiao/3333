package com.pcrjjc.app.di    
  
import android.content.Context    
import androidx.room.Room    
import androidx.room.migration.Migration    
import androidx.sqlite.db.SupportSQLiteDatabase    
import com.pcrjjc.app.data.local.AppDatabase  
import com.pcrjjc.app.data.local.SettingsDataStore  
import com.pcrjjc.app.data.local.dao.AccountDao  
import com.pcrjjc.app.data.remote.RoomClient  
import com.pcrjjc.app.domain.ClientManager  
import com.pcrjjc.app.domain.UpdateChecker  
import com.pcrjjc.app.data.local.dao.BindDao    
import com.pcrjjc.app.data.local.dao.HistoryDao    
import com.pcrjjc.app.data.local.dao.RankCacheDao  
import com.pcrjjc.app.data.local.dao.ArenaRankingCacheDao  
import dagger.Module    
import dagger.Provides    
import dagger.hilt.InstallIn    
import dagger.hilt.android.qualifiers.ApplicationContext    
import dagger.hilt.components.SingletonComponent    
import okhttp3.OkHttpClient    
import java.util.concurrent.TimeUnit    
import javax.inject.Singleton    
  
@Module    
@InstallIn(SingletonComponent::class)    
object AppModule {    
  
    private val MIGRATION_2_3 = object : Migration(2, 3) {    
        override fun migrate(db: SupportSQLiteDatabase) {    
            db.execSQL("ALTER TABLE account ADD COLUMN isMaster INTEGER NOT NULL DEFAULT 0")    
            db.execSQL("ALTER TABLE pcr_bind ADD COLUMN arenaType INTEGER NOT NULL DEFAULT 0")    
        }    
    }    
  
    private val MIGRATION_3_4 = object : Migration(3, 4) {  
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
	
	@Provides    
    @Singleton    
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {    
        return Room.databaseBuilder(    
            context,    
            AppDatabase::class.java,    
            "pcrjjc.db"    
        )    
        .addMigrations(MIGRATION_2_3, MIGRATION_3_4)    
        .build()    
    }     
  
    @Provides    
    fun provideBindDao(database: AppDatabase): BindDao = database.bindDao()    
  
    @Provides    
    fun provideAccountDao(database: AppDatabase): AccountDao = database.accountDao()    
  
    @Provides    
    fun provideHistoryDao(database: AppDatabase): HistoryDao = database.historyDao()    
  
    @Provides    
    fun provideRankCacheDao(database: AppDatabase): RankCacheDao = database.rankCacheDao()    
  
    @Provides  
    fun provideArenaRankingCacheDao(database: AppDatabase): ArenaRankingCacheDao = database.arenaRankingCacheDao()
	
	@Provides    
    @Singleton    
    fun provideOkHttpClient(): OkHttpClient {    
        return OkHttpClient.Builder()    
            .connectTimeout(20, TimeUnit.SECONDS)    
            .readTimeout(20, TimeUnit.SECONDS)    
            .writeTimeout(20, TimeUnit.SECONDS)    
            .build()    
    }    
  
    @Provides  
    @Singleton  
    fun provideSettingsDataStore(@ApplicationContext context: Context): SettingsDataStore {  
        return SettingsDataStore(context)  
    }  
  
    @Provides  
    @Singleton  
    fun provideUpdateChecker(@ApplicationContext context: Context): UpdateChecker {  
        return UpdateChecker(context)  
    }  
  
    @Provides  
    @Singleton  
    fun provideRoomClient(settingsDataStore: SettingsDataStore): RoomClient {  
        return RoomClient(settingsDataStore)  
    }  
  
    // 新增：ClientManager 单例提供  
    @Provides  
    @Singleton  
    fun provideClientManager(): ClientManager {  
        return ClientManager()  
    }  
}