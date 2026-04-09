package com.pcrjjc.app.domain  
  
import android.util.Log  
import com.pcrjjc.app.data.local.entity.Account  
import com.pcrjjc.app.data.remote.BiliAuth  
import com.pcrjjc.app.data.remote.PcrClient  
import com.pcrjjc.app.data.remote.TwPcrClient  
import com.pcrjjc.app.util.Platform  
import kotlinx.coroutines.sync.Mutex  
import kotlinx.coroutines.sync.withLock  
import javax.inject.Inject  
import javax.inject.Singleton  
  
/**  
 * 客户端管理器：缓存已登录的 PcrClient / TwPcrClient 实例，避免重复登录。  
 * 只在首次使用或 session 失效时登录。  
 */  
@Singleton  
class ClientManager @Inject constructor() {  
  
    companion object {  
        private const val TAG = "ClientManager"  
    }  
  
    private val clients = mutableMapOf<Int, Any>()  // account.id -> client  
    private val mutex = Mutex()  
  
    /**  
     * 获取已登录的客户端。如果缓存中没有，则创建并登录。  
     * @param account 账号实体  
     * @param forceRelogin 强制重新登录（session 过期时使用）  
     */  
    suspend fun getClient(account: Account, forceRelogin: Boolean = false): Any {  
        return mutex.withLock {  
            val existing = clients[account.id]  
            if (existing != null && !forceRelogin) {  
                Log.d(TAG, "复用已缓存的客户端 accountId=${account.id}")  
                return@withLock existing  
            }  
  
            Log.i(TAG, "创建并登录客户端 accountId=${account.id} platform=${account.platform}")  
            val client = createAndLogin(account)  
            clients[account.id] = client  
            client  
        }  
    }  
  
    /**  
     * 重新登录指定客户端（session 过期时调用）  
     */  
    suspend fun relogin(account: Account): Any {  
        return getClient(account, forceRelogin = true)  
    }  
  
    /**  
     * 清除指定账号的缓存客户端  
     */  
    fun clearClient(accountId: Int) {  
        clients.remove(accountId)  
        Log.d(TAG, "清除客户端缓存 accountId=$accountId")  
    }  
  
    /**  
     * 清除所有缓存  
     */  
    fun clearAll() {  
        clients.clear()  
        Log.d(TAG, "清除所有客户端缓存")  
    }  
  
    private suspend fun createAndLogin(account: Account): Any {  
        return when (account.platform) {  
            Platform.TW_SERVER.id -> {  
                val twPlatform = (account.viewerId.toLong() / 1000000000).toInt()  
                val twClient = TwPcrClient(  
                    account.account,  
                    account.password,  
                    account.viewerId,  
                    twPlatform  
                )  
                twClient.login()  
                twClient  
            }  
            else -> {  
                val biliAuth = BiliAuth(account.account, account.password, account.platform)  
                val pcrClient = PcrClient(biliAuth)  
                pcrClient.login()  
                pcrClient  
            }  
        }  
    }  
}