package com.pcrjjc.app.di  
  
import android.content.Context  
import androidx.room.Room  
import androidx.room.migration.Migration  
import androidx.sqlite.db.SupportSQLiteDatabase  
import com.pcrjjc.app.data.local.AppDatabase  
import com.pcrjjc.app.data.local.SettingsDataStore  
import com.pcrjjc.app.data.local.dao.AccountDao  
import com.pcrjjc.app.data.local.dao.BindDao  
import com.pcrjjc.app.data.local.dao.HistoryDao  
import com.pcrjjc.app.data.local.dao.RankCacheDao  
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
  
    private val MIGRATION_1_2 = object : Migration(1, 2) {  
        override fun migrate(db: SupportSQLiteDatabase) {  
            db.execSQL("""  
                CREATE TABLE IF NOT EXISTS `rank_cache` (  
                    `pcrid` INTEGER NOT NULL,  
                    `platform` INTEGER NOT NULL,  
                    `arenaRank` INTEGER NOT NULL,  
                    `grandArenaRank` INTEGER NOT NULL,  
                    `lastLoginTime` INTEGER NOT NULL,  
                    PRIMARY KEY(`pcrid`, `platform`)  
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
            .addMigrations(MIGRATION_1_2)  
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
    @Singleton  
    fun provideSettingsDataStore(@ApplicationContext context: Context): SettingsDataStore {  
        return SettingsDataStore(context)  
    }  
  
    @Provides  
    @Singleton  
    fun provideOkHttpClient(): OkHttpClient {  
        return OkHttpClient.Builder()  
            .connectTimeout(20, TimeUnit.SECONDS)  
            .readTimeout(20, TimeUnit.SECONDS)  
            .writeTimeout(20, TimeUnit.SECONDS)  
            .build()  
    }  
}