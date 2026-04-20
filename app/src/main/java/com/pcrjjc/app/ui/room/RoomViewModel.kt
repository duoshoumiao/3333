package com.pcrjjc.app.ui.room

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val joinedRoom: Room? = null
)

@HiltViewModel
class RoomViewModel @Inject constructor(
    private val roomClient: RoomClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(RoomUiState())
    val uiState: StateFlow<RoomUiState> = _uiState.asStateFlow()

    init {
        refreshRoomList()
    }

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

    fun createRoom(roomName: String, password: String?, hostQq: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCreating = true, error = null)
            try {
                // hostName暂时使用QQ号代替，实际可以从QQ昵称获取
                val room = roomClient.createRoom(
                    roomName = roomName,
                    password = password,
                    hostName = "房主",
                    hostQq = hostQq
                )
                _uiState.value = _uiState.value.copy(
                    isCreating = false,
                    createdRoom = room
                )
                // 刷新房间列表
                refreshRoomList()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isCreating = false,
                    error = e.message ?: "创建房间失败"
                )
            }
        }
    }

    fun joinRoom(roomId: String, password: String?, playerQq: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val room = roomClient.joinRoom(
                    roomId = roomId,
                    password = password,
                    playerName = "玩家",
                    playerQq = playerQq
                )
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    joinedRoom = room
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