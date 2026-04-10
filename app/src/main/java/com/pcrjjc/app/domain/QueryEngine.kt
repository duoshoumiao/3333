package com.pcrjjc.app.domain  
  
import android.util.Log  
import com.pcrjjc.app.data.local.entity.Account  
import com.pcrjjc.app.data.local.entity.PcrBind  
import com.pcrjjc.app.data.remote.ApiException  
import com.pcrjjc.app.data.remote.PcrClient  
import com.pcrjjc.app.data.remote.TwPcrClient  
  
class QueryEngine {  
  
    companion object {  
        private const val TAG = "QueryEngine"  
    }  
  
    data class QueryTask(  
        val bind: PcrBind,  
        val priority: Int = 10  
    )  
  
    data class QueryResult(  
        val bind: PcrBind,  
        val userInfo: Map<String, Any?>,  
        val fullResponse: Map<String, Any?>  
    )  
  
    @Suppress("UNCHECKED_CAST")  
    suspend fun queryProfile(  
        client: Any,  
        bind: PcrBind,  
        clientManager: ClientManager? = null,  
        account: Account? = null  
    ): QueryResult? {  
        return try {  
            val res = when (client) {  
                is PcrClient -> {  
                    client.callApi(  
                        "/profile/get_profile",  
                        mutableMapOf("target_viewer_id" to bind.pcrid)  
                    )  
                }  
                is TwPcrClient -> {  
                    client.callApi(  
                        "/profile/get_profile",  
                        mutableMapOf("target_viewer_id" to bind.pcrid)  
                    )  
                }  
                else -> throw IllegalArgumentException("Unknown client type")  
            }  
  
            val userInfo = res["user_info"] as? Map<String, Any?>  
            if (userInfo == null) {  
                // 会话过期，重新登录  
                val retryClient = if (clientManager != null && account != null) {  
                    clientManager.relogin(account)  
                } else {  
                    when (client) {  
                        is PcrClient -> { client.login(); client }  
                        is TwPcrClient -> { client.login(); client }  
                        else -> return null  
                    }  
                }  
                val retryRes = when (retryClient) {  
                    is PcrClient -> retryClient.callApi(  
                        "/profile/get_profile",  
                        mutableMapOf("target_viewer_id" to bind.pcrid)  
                    )  
                    is TwPcrClient -> retryClient.callApi(  
                        "/profile/get_profile",  
                        mutableMapOf("target_viewer_id" to bind.pcrid)  
                    )  
                    else -> return null  
                }  
                val retryUserInfo = retryRes["user_info"] as? Map<String, Any?> ?: return null  
                QueryResult(bind, retryUserInfo, retryRes)  
            } else {  
                QueryResult(bind, userInfo, res)  
            }  
        } catch (e: ApiException) {  
            Log.e(TAG, "Query failed for ${bind.pcrid}: ${e.message}")  
            null  
        } catch (e: Exception) {  
            Log.e(TAG, "Query failed for ${bind.pcrid}: ${e.message}", e)  
            null  
        }  
    }  
  
    suspend fun queryAll(  
		binds: List<PcrBind>,  
		client: Any,  
		clientManager: ClientManager? = null,  
		account: Account? = null,  
		onResult: suspend (QueryResult) -> Unit  
	) {  
		for (bind in binds) {  
			val result = queryProfile(client, bind, clientManager, account)  
			if (result != null) {  
				onResult(result)  
			}  
		}  
	}  
}