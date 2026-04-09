package com.pcrjjc.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.pcrjjc.app.data.local.entity.Account
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {

    @Query("SELECT * FROM account ORDER BY id")
    fun getAllAccounts(): Flow<List<Account>>

    @Query("SELECT * FROM account ORDER BY id")
    suspend fun getAllAccountsSync(): List<Account>

    @Query("SELECT * FROM account WHERE id = :id")
    suspend fun getAccountById(id: Int): Account?

    @Query("SELECT * FROM account WHERE platform = :platform")
    suspend fun getAccountsByPlatform(platform: Int): List<Account>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(account: Account): Long

    @Update
    suspend fun update(account: Account)

    @Query("DELETE FROM account WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM account")
    suspend fun deleteAll()
}
