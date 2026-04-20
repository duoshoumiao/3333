package com.pcrjjc.app.ui.room  
  
import androidx.lifecycle.ViewModel  
import androidx.lifecycle.viewModelScope  
import com.pcrjjc.app.data.local.SettingsDataStore  
import com.pcrjjc.app.data.local.entity.Room  
import com.pcrjjc.app.data.remote.RoomClient  
import dagger.hilt.android.lifecycle.HiltViewModel  
import kotlinx.coroutines.flow.MutableStateFlow  
import kotlinx.coroutines.flow.StateFlow  
import kotlinx.coroutines.flow.asStateFlow  
import kotlinx.coroutines.launch  
import javax.inject.Inject  
  
data class RoomUiState(  
    val rooms: List<Room> = emptyList(),  
    val isLoading: Boolean = false,  
    val error: String? = null,  
    val isCreating: Boolean = false,  
    val createdRoom: Room? = null,  
    val joinedRoom: Room? = null,  
    val savedQq: String = "",  
    val savedName: String = "",
    val currentPlayerQq: String? = null,
    val currentPlayerName: String? = null
)  
  
@HiltViewModel  
class RoomViewModel @Inject constructor(  
    private val roomClient: RoomClient,  
    private val settingsDataStore: SettingsDataStore  
) : ViewModel() {  
  
    private val _uiState = MutableStateFlow(RoomUiState())  
    val uiState: StateFlow<RoomUiState> = _uiState.asStateFlow()  
  
    // 房间密码缓存：roomId -> password  
    private val passwordCache = mutableMapOf<String, String>()  
  
    init {  
        refreshRoomList()  
        loadSavedProfile()
    }  
  
    private fun loadSavedProfile() {
        viewModelScope.launch {  
            val qq = settingsDataStore.getUserQq()  
            val name = settingsDataStore.getUserName()
            _uiState.value = _uiState.value.copy(savedQq = qq, savedName = name)
        }  
    }  
  
    fun saveProfile(qq: String, name: String) {
        viewModelScope.launch {  
            settingsDataStore.setUserQq(qq)  
            settingsDataStore.setUserName(name)
            _uiState.value = _uiState.value.copy(savedQq = qq, savedName = name)
        }  
    }  
  
    fun getCachedPassword(roomId: String): String? = passwordCache[roomId]  
  
    fun refreshRoomList() {  
        viewModelScope.launch {  
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)  
            try {  
                val rooms = roomClient.getRoomList()  
                _uiState.value = _uiState.value.copy(  
                    rooms = rooms,  
                    isLoading = false  
                )  
            } catch (e: Exception) {  
                _uiState.value = _uiState.value.copy(  
                    isLoading = false,  
                    error = e.message ?: "获取房间列表失败"  
                )  
            }  
        }  
    }  
  
    fun createRoom(roomName: String, password: String?, hostQq: String, hostName: String) {
        viewModelScope.launch {  
            _uiState.value = _uiState.value.copy(isCreating = true, error = null)  
            try {  
                val room = roomClient.createRoom(  
                    roomName = roomName,  
                    password = password,  
                    hostName = hostName.ifBlank { "房主" },
                    hostQq = hostQq  
                )  
                saveProfile(hostQq, hostName)
                _uiState.value = _uiState.value.copy(  
                    isCreating = false,  
                    createdRoom = room,  
                    currentPlayerQq = hostQq,
                    currentPlayerName = hostName
                )  
                refreshRoomList()  
            } catch (e: Exception) {  
                _uiState.value = _uiState.value.copy(  
                    isCreating = false,  
                    error = e.message ?: "创建房间失败"  
                )  
            }  
        }  
    }  
  
    fun joinRoom(roomId: String, password: String?, playerQq: String, playerName: String) {
        viewModelScope.launch {  
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)  
            try {  
                val room = roomClient.joinRoom(  
                    roomId = roomId,  
                    password = password,  
                    playerName = playerName.ifBlank { "玩家" },
                    playerQq = playerQq  
                )  
                // 加入成功后缓存密码和QQ  
                if (!password.isNullOrBlank()) {  
                    passwordCache[roomId] = password  
                }  
                saveProfile(playerQq, playerName)
                _uiState.value = _uiState.value.copy(  
                    isLoading = false,  
                    joinedRoom = room,  
                    currentPlayerQq = playerQq,
                    currentPlayerName = playerName
                )  
            } catch (e: Exception) {  
                _uiState.value = _uiState.value.copy(  
                    isLoading = false,  
                    error = e.message ?: "加入房间失败"  
                )  
            }  
        }  
    }  
  
    fun clearCreatedRoom() {  
        _uiState.value = _uiState.value.copy(createdRoom = null)  
    }  
  
    fun clearJoinedRoom() {  
        _uiState.value = _uiState.value.copy(joinedRoom = null)  
    }  
  
    fun clearError() {  
        _uiState.value = _uiState.value.copy(error = null)  
    }  
}
