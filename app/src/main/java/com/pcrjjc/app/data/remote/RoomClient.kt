package com.pcrjjc.app.data.remote

import com.pcrjjc.app.data.local.entity.Room
import com.pcrjjc.app.data.local.SettingsDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 房间服务器API客户端
 */
@Singleton
class RoomClient @Inject constructor(
    private val settingsDataStore: SettingsDataStore
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * 获取房间服务器基础URL
     */
    private suspend fun getBaseUrl(): String {
        return settingsDataStore.getRoomServerUrl() ?: throw ApiException("请先在设置中配置房间服务器地址", -1)
    }

    /**
     * 获取房间列表
     */
    suspend fun getRoomList(): List List<Room> = withContext(Dispatchers.IO) {
        val baseUrl = getBaseUrl()
        val request = Request.Builder()
            .url("$baseUrl/rooms")
            .get()
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw ApiException("获取房间列表失败: ${response.code}", response.code)
        }

        val body = response.body?.string() ?: throw ApiException("服务器响应为空", -1)
        val jsonArray = JSONArray(body)

        val rooms = mutableListOfOf<Room>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            rooms.add(Room(
                roomId = obj.getString("room_id"),
                roomName = obj.getString("room_name"),
                password = if (obj.has("has_password") && obj.getBoolean("has_password")) "****" else null,
                hostName = obj.getString("host_name"),
                hostQq = obj.getString("host_qq"),
                playerCount = obj.optInt("player_count", 0),
                maxPlayers = obj.optInt("max_players", 8)
            ))
        }
        rooms
    }

    /**
     * 创建房间
     */
    suspend fun createRoom(
        roomName: String,
        password: String?,
        hostName: String,
        hostQq: String
    ): Room = withContext(Dispatchers.IO) {
        val baseUrl = getBaseUrl()

        val json = JSONObject().apply {
            put("room_name", roomName)
            if (!password.isNullOrBlank()) {
                put("password", password)
            }
            put("host_name", hostName)
            put("host_qq", hostQq)
        }

        val request = Request.Builder()
            .url("$baseUrl/rooms")
            .post(json.toString().toRequestBody(jsonMediaType))
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw ApiException("创建房间失败: ${response.code}", response.code)
        }

        val body = response.body?.string() ?: throw ApiException("服务器响应为空", -1)
        val obj = JSONObject(body)

        Room(
            roomId = obj.getString("room_id"),
            roomName = obj.getString("room_name"),
            password = if (obj.has("password")) obj.getString("password") else null,
            hostName = obj.getString("host_name"),
            hostQq = obj.getString("host_qq"),
            playerCount = 1,
            maxPlayers = obj.optInt("max_players", 8)
        )
    }

    /**
     * 加入房间
     */
    suspend fun joinRoom(
        roomId: String,
        password: String?,
        playerName: String,
        playerQq: String
    ): Room = withContext(Dispatchers.IO) {
        val baseUrl = getBaseUrl()

        val json = JSONObject().apply {
            put("room_id", roomId)
            if (!password.isNullOrBlank()) {
                put("password", password)
            }
            put("player_name", playerName)
            put("player_qq", playerQq)
        }

        val request = Request.Builder()
            .url("$baseUrl/rooms/join")
            .post(json.toString().toRequestBody(jsonMediaType))
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            val errorBody = response.body?.string()
            val errorMsg = try {
                JSONObject(errorBody ?: "").optString("error", "加入房间失败")
            } catch (e: Exception) {
                "加入房间失败: ${response.code}"
            }
            throw ApiException(errorMsg, response.code)
        }

        val body = response.body?.string() ?: throw ApiException("服务器响应为空", -1)
        val obj = JSONObject(body)

        Room(
            roomId = obj.getString("room_id"),
            roomName = obj.getString("room_name"),
            password = null,
            hostName = obj.getString("host_name"),
            hostQq = obj.getString("host_qq"),
            playerCount = obj.optInt("player_count", 0),
            maxPlayers = obj.optInt("max_players", 8)
        )
    }

    /**
     * 离开房间
     */
    suspend fun leaveRoom(roomId: String, playerQq: String) = withContext(Dispatchers.IO) {
        val baseUrl = getBaseUrl()

        val json = JSONObject().apply {
            put("room_id", roomId)
            put("player_qq", playerQq)
        }

        val request = Request.Builder()
            .url("$baseUrl/rooms/leave")
            .post(json.toString().toRequestBody(jsonMediaType))
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw ApiException("离开房间失败: ${response.code}", response.code)
        }
    }
}