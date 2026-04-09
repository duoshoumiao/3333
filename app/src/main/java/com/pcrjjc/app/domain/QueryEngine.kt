package com.pcrjjc.app.domain

import android.util.Log
import com.pcrjjc.app.data.local.entity.PcrBind
import com.pcrjjc.app.data.remote.ApiException
import com.pcrjjc.app.data.remote.PcrClient
import com.pcrjjc.app.data.remote.TwPcrClient
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Query engine corresponding to pcrjjc2/query.py
 * Uses Kotlin Channel for task scheduling
 */
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

    /**
     * Query profile for a single bind using the appropriate client
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun queryProfile(
        client: Any,
        bind: PcrBind
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
                // Try re-login and retry
                when (client) {
                    is PcrClient -> client.login()
                    is TwPcrClient -> client.login()
                }
                val retryRes = when (client) {
                    is PcrClient -> client.callApi(
                        mutableMapOf("target_viewer_id" to bind.pcrid) 
                    )
                    is TwPcrClient -> client.callApi(
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

    /**
     * Query all binds for a platform
     */
    suspend fun queryAll(
        binds: List<PcrBind>,
        client: Any,
        onResult: suspend (QueryResult) -> Unit
    ) {
        coroutineScope {
            for (bind in binds) {
                launch {
                    val result = queryProfile(client, bind)
                    if (result != null) {
                        onResult(result)
                    }
                }
            }
        }
    }
}
