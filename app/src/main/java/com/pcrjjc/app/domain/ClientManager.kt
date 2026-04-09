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
  
@Singleton  
class ClientManager @Inject constructor() {  
  
    companion object {  
        private const val TAG = "ClientManager"  
    }  
  
    private val clients = mutableMapOf<Int, Any>()  
    private val mutex = Mutex()  
  
    suspend fun getClient(account: Account): Any {  
        return mutex.withLock {  
            val existing = clients[account.id]  
            if (existing != null) {  
                // Check if TwPcrClient needs re-login  
                if (existing is TwPcrClient && existing.shouldLogin) {  
                    Log.i(TAG, "TwPcrClient for account ${account.id} needs re-login")  
                    clients.remove(account.id)  
                } else {  
                    Log.d(TAG, "Reusing cached client for account ${account.id}")  
                    return@withLock existing  
                }  
            }  
  
            Log.i(TAG, "Creating new client for account ${account.id}, platform ${account.platform}")  
            val client = createAndLogin(account)  
            clients[account.id] = client  
            client  
        }  
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
                val biliAuth = BiliAuth(  
                    account.account,  
                    account.password,  
                    account.platform  
                )  
                val pcrClient = PcrClient(biliAuth)  
                pcrClient.login()  
                pcrClient  
            }  
        }  
    }  
  
    suspend fun clearClient(accountId: Int) {  
        mutex.withLock {  
            clients.remove(accountId)  
            Log.i(TAG, "Cleared cached client for account $accountId")  
        }  
    }  
  
    suspend fun clearAll() {  
        mutex.withLock {  
            clients.clear()  
            Log.i(TAG, "All clients cleared")  
        }  
    }  
}