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
  
    /**  
     * 通过 /profile/get_profile 获取玩家名称  
     */  
    @Suppress("UNCHECKED_CAST")  
    private suspend fun getUserName(client: Any, viewerId: Long): String {  
        return try {  
            val res = when (client) {  
                is PcrClient -> client.callApi(  
                    "/profile/get_profile",  
                    mutableMapOf("target_viewer_id" to viewerId)  
                )  
                is TwPcrClient -> client.callApi(  
                    "/profile/get_profile",  
                    mutableMapOf("target_viewer_id" to viewerId)  
                )  
                else -> return "未知"  
            }  
            val userInfo = res["user_info"] as? Map<String, Any?>  
            userInfo?.get("user_name")?.toString() ?: "未知"  
        } catch (e: Exception) {  
            Log.e(TAG, "getUserName failed for $viewerId: ${e.message}")  
            "未知"  
        }  
    }  
  
    /**  
     * JJC透视：查询竞技场排名前51名玩家  
     * 排名API不返回user_name，需要逐个调用get_profile获取  
     */  
    @Suppress("UNCHECKED_CAST")  
    suspend fun queryArenaRanking(client: Any, pages: Int = 3): List<ArenaRankingPlayer> {  
        val allPlayers = mutableListOf<ArenaRankingPlayer>()  
        // 第一步：获取排名列表（viewer_id, rank, team_level）  
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
                    val teamLevel = (item["team_level"] as? Number)?.toInt() ?: 0  
                    // 先用空名称占位  
                    allPlayers.add(ArenaRankingPlayer(viewerId, rank, "", teamLevel))  
                }  
            } catch (e: Exception) {  
                Log.e(TAG, "queryArenaRanking page $page failed: ${e.message}", e)  
            }  
        }  
        // 第二步：逐个获取用户名称  
        val result = allPlayers.map { player ->  
            val userName = getUserName(client, player.viewerId)  
            player.copy(userName = userName)  
        }  
        return result.sortedBy { it.rank }  
    }  
  
    /**  
     * PJJC透视：查询公主竞技场排名前51名玩家  
     * 排名API不返回user_name，需要逐个调用get_profile获取  
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
                    val teamLevel = (item["team_level"] as? Number)?.toInt() ?: 0  
                    allPlayers.add(ArenaRankingPlayer(viewerId, rank, "", teamLevel))  
                }  
            } catch (e: Exception) {  
                Log.e(TAG, "queryGrandArenaRanking page $page failed: ${e.message}", e)  
            }  
        }  
        val result = allPlayers.map { player ->  
            val userName = getUserName(client, player.viewerId)  
            player.copy(userName = userName)  
        }  
        return result.sortedBy { it.rank }  
    }  
	
	// ==================== 黎明界（Labyrinth）====================  
  
    /** 黎明界地图格子 */  
    data class LabyrinthBlock(  
        val blockId: Int,  
        val area: Int,  
        val column: Int,  
        val row: Int,  
        val blockType: Int,  
        val questId: Int,  
        val nextBlockIdList: List<Int>  
    )  
  
    /** labyrinth/top 结果：已有开局的 enter_id + 已解锁最高难度 */  
    data class LabyrinthTopResult(  
        val enterId: Long,  
        val maxUnlockedDifficulty: Int  
    )  
  
    /** labyrinth/enter 结果：本次开局 enter_id + 地图 */  
    data class LabyrinthEnterResult(  
        val enterId: Long,  
        val blocks: List<LabyrinthBlock>  
    )  
  
    @Suppress("UNCHECKED_CAST")  
    private suspend fun callLabyrinth(  
        client: Any,  
        api: String,  
        params: MutableMap<String, Any?>  
    ): Map<String, Any?> {  
        return when (client) {  
            is PcrClient -> client.callApi(api, params)  
            is TwPcrClient -> client.callApi(api, params)  
            else -> throw IllegalArgumentException("Unknown client type")  
        }  
    }  
  
    /** 调 /labyrinth/top：读取已有开局 enter_id 和已解锁最高难度 */  
    @Suppress("UNCHECKED_CAST")  
    suspend fun labyrinthTop(  
        client: Any,  
        clientManager: ClientManager? = null,  
        account: Account? = null  
    ): LabyrinthTopResult {  
        var res = callLabyrinth(client, "/labyrinth/top", mutableMapOf())  
  
        // 会话失效检测：顶号/过期后响应缺少关键字段（既无 enter_id 也无 guild_cleared_difficulty_list）  
        val looksExpired = !res.containsKey("enter_id") &&  
            !res.containsKey("guild_cleared_difficulty_list")  
        if (looksExpired && clientManager != null && account != null) {  
            Log.w(TAG, "labyrinthTop session expired, relogin and retry")  
            val retryClient = clientManager.relogin(account)  
            res = callLabyrinth(retryClient, "/labyrinth/top", mutableMapOf())  
        }  
  
        val enterId = (res["enter_id"] as? Number)?.toLong() ?: 0L  
        // guild_cleared_difficulty_list: [{guild_id, difficulty}, ...]  
        val clearedList = res["guild_cleared_difficulty_list"] as? List<Map<String, Any?>>  
        val cleared = clearedList  
            ?.mapNotNull { (it["difficulty"] as? Number)?.toInt() }  
            ?.filter { it > 0 }  
            ?: emptyList()  
        val maxUnlocked = if (cleared.isEmpty()) 1 else minOf(cleared.max() + 1, 5)  
        return LabyrinthTopResult(enterId, maxUnlocked)  
    }
  
    /** 调 /labyrinth/enter：进入某公会某难度，返回地图 */  
    @Suppress("UNCHECKED_CAST")  
    suspend fun labyrinthEnter(client: Any, guildId: Int, difficulty: Int): LabyrinthEnterResult {  
        val res = callLabyrinth(  
            client,  
            "/labyrinth/enter",  
            mutableMapOf("guild_id" to guildId, "difficulty" to difficulty)  
        )  
        val enterId = (res["enter_id"] as? Number)?.toLong() ?: 0L  
        val mapList = res["map_list"] as? List<Map<String, Any?>> ?: emptyList()  
        val blocks = mapList.mapNotNull { item ->  
            val blockId = (item["block_id"] as? Number)?.toInt() ?: return@mapNotNull null  
            val area = (item["area"] as? Number)?.toInt() ?: return@mapNotNull null  
            val column = (item["column"] as? Number)?.toInt() ?: return@mapNotNull null  
            val row = (item["row"] as? Number)?.toInt() ?: 0  
            val blockType = (item["block_type"] as? Number)?.toInt() ?: 0  
            val questId = (item["quest_id"] as? Number)?.toInt() ?: 0  
            val nextList = (item["next_block_id_list"] as? List<*>)  
                ?.mapNotNull { (it as? Number)?.toInt() } ?: emptyList()  
            LabyrinthBlock(blockId, area, column, row, blockType, questId, nextList)  
        }  
        return LabyrinthEnterResult(enterId, blocks)  
    }  
  
    /** 调 /labyrinth/retire：撤退当前开局 */  
    suspend fun labyrinthRetire(client: Any, enterId: Long) {  
        callLabyrinth(client, "/labyrinth/retire", mutableMapOf("enter_id" to enterId))  
    }
  
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
                val retryViewerId = (retryUserInfo["viewer_id"] as? Number)?.toLong()  
                if (retryViewerId != null && retryViewerId != bind.pcrid) {  
                    Log.e(TAG, "viewer_id mismatch after relogin: requested ${bind.pcrid}, got $retryViewerId")  
                    return null  
                }  
                QueryResult(bind, retryUserInfo, retryRes)  
            } else {  
                val returnedViewerId = (userInfo["viewer_id"] as? Number)?.toLong()  
                if (returnedViewerId != null && returnedViewerId != bind.pcrid) {  
                    Log.e(TAG, "viewer_id mismatch: requested ${bind.pcrid}, got $returnedViewerId")  
                    return null  
                }  
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
        account: Account? = null
    ): List<QueryResult> {
        val results = mutableListOf<QueryResult>()
        for (bind in binds) {
            val result = queryProfile(client, bind, clientManager, account)
            if (result != null) {
                results.add(result)
            }
        }
        return results
    }
}