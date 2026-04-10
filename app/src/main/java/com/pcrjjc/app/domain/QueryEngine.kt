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
  
    data class ArenaRankingPlayer(  
        val viewerId: Long,  
        val rank: Int,  
        val userName: String,  
        val teamLevel: Int  
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
  
    /**  
     * JJC透视：查询竞技场排名前51名玩家  
     * API: /arena/ranking, 参数: limit, page  
     * 响应: ranking 列表，每项含 viewer_id, rank, user_name, team_level  
     */  
    @Suppress("UNCHECKED_CAST")  
    suspend fun queryArenaRanking(client: Any, pages: Int = 3): List<ArenaRankingPlayer> {  
        val allPlayers = mutableListOf<ArenaRankingPlayer>()  
        for (page in 1..pages) {  
            try {  
                val res = when (client) {  
                    is PcrClient -> client.callApi(  
                        "/arena/ranking",  
                        mutableMapOf("limit" to 20, "page" to page)  
                    )  
                    is TwPcrClient -> client.callApi(  
                        "/arena/ranking",  
                        mutableMapOf("limit" to 20, "page" to page)  
                    )  
                    else -> throw IllegalArgumentException("Unknown client type")  
                }  
                val ranking = res["ranking"] as? List<Map<String, Any?>> ?: continue  
                for (item in ranking) {  
                    val viewerId = (item["viewer_id"] as? Number)?.toLong() ?: continue  
                    val rank = (item["rank"] as? Number)?.toInt() ?: continue  
                    if (rank > 51) break  
                    val userName = item["user_name"]?.toString() ?: ""  
                    val teamLevel = (item["team_level"] as? Number)?.toInt() ?: 0  
                    allPlayers.add(ArenaRankingPlayer(viewerId, rank, userName, teamLevel))  
                }  
            } catch (e: Exception) {  
                Log.e(TAG, "queryArenaRanking page $page failed: ${e.message}", e)  
            }  
        }  
        return allPlayers.sortedBy { it.rank }  
    }  
  
    /**  
     * PJJC透视：查询公主竞技场排名前51名玩家  
     * API: /grand_arena/ranking, 参数: limit, page  
     * 响应: ranking 列表，每项含 viewer_id, rank, user_name, team_level  
     */  
    @Suppress("UNCHECKED_CAST")  
    suspend fun queryGrandArenaRanking(client: Any, pages: Int = 3): List<ArenaRankingPlayer> {  
        val allPlayers = mutableListOf<ArenaRankingPlayer>()  
        for (page in 1..pages) {  
            try {  
                val res = when (client) {  
                    is PcrClient -> client.callApi(  
                        "/grand_arena/ranking",  
                        mutableMapOf("limit" to 20, "page" to page)  
                    )  
                    is TwPcrClient -> client.callApi(  
                        "/grand_arena/ranking",  
                        mutableMapOf("limit" to 20, "page" to page)  
                    )  
                    else -> throw IllegalArgumentException("Unknown client type")  
                }  
                val ranking = res["ranking"] as? List<Map<String, Any?>> ?: continue  
                for (item in ranking) {  
                    val viewerId = (item["viewer_id"] as? Number)?.toLong() ?: continue  
                    val rank = (item["rank"] as? Number)?.toInt() ?: continue  
                    if (rank > 51) break  
                    val userName = item["user_name"]?.toString() ?: ""  
                    val teamLevel = (item["team_level"] as? Number)?.toInt() ?: 0  
                    allPlayers.add(ArenaRankingPlayer(viewerId, rank, userName, teamLevel))  
                }  
            } catch (e: Exception) {  
                Log.e(TAG, "queryGrandArenaRanking page $page failed: ${e.message}", e)  
            }  
        }  
        return allPlayers.sortedBy { it.rank }  
    }  
}