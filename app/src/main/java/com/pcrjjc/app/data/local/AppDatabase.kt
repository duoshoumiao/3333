package com.pcrjjc.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.pcrjjc.app.data.local.dao.AccountDao
import com.pcrjjc.app.data.local.dao.BindDao
import com.pcrjjc.app.data.local.dao.HistoryDao
import com.pcrjjc.app.data.local.entity.Account
import com.pcrjjc.app.data.local.entity.JjcHistory
import com.pcrjjc.app.data.local.entity.PcrBind

@Database(
    entities = [PcrBind::class, Account::class, JjcHistory::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bindDao(): BindDao
    abstract fun accountDao(): AccountDao
    abstract fun historyDao(): HistoryDao
}
