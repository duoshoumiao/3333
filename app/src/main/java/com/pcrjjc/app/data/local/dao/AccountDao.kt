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
  
    // ---- 新增：非主人号（用于轮询监控） ----  
    @Query("SELECT * FROM account WHERE isMaster = 0 ORDER BY id")  
    fun getNonMasterAccounts(): Flow<List<Account>>  
  
    @Query("SELECT * FROM account WHERE isMaster = 0 ORDER BY id")  
    suspend fun getNonMasterAccountsSync(): List<Account>  
  
    // ---- 新增：主人号 ----  
    @Query("SELECT * FROM account WHERE isMaster = 1 ORDER BY id")  
    fun getMasterAccounts(): Flow<List<Account>>  
  
    @Query("SELECT * FROM account WHERE isMaster = 1 AND platform = :platform")  
    suspend fun getMasterAccountsByPlatform(platform: Int): List<Account>  
  
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