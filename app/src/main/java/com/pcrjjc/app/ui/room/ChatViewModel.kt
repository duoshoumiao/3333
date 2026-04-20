package com.pcrjjc.app.ui.room  
  
import androidx.lifecycle.SavedStateHandle  
import androidx.lifecycle.ViewModel  
import androidx.lifecycle.viewModelScope  
import com.pcrjjc.app.data.local.entity.ChatMessage  
import com.pcrjjc.app.data.remote.RoomClient  
import dagger.hilt.android.lifecycle.HiltViewModel  
import kotlinx.coroutines.Job  
import kotlinx.coroutines.delay  
import kotlinx.coroutines.flow.MutableStateFlow  
import kotlinx.coroutines.flow.StateFlow  
import kotlinx.coroutines.flow.asStateFlow  
import kotlinx.coroutines.isActive  
import kotlinx.coroutines.launch  
import javax.inject.Inject  
  
data class ChatUiState(  
    val messages: List<ChatMessage> = emptyList(),  
    val isLoading: Boolean = false,  
    val isSending: Boolean = false,  
    val error: String? = null,  
    val roomId: String = "",  
    val playerQq: String = "",  
    val roomName: String = ""  
)  
  
@HiltViewModel  
class ChatViewModel @Inject constructor(  
    savedStateHandle: SavedStateHandle,  
    private val roomClient: RoomClient  
) : ViewModel() {  
  
    private val roomId: String = savedStateHandle["roomId"] ?: ""  
    private val playerQq: String = savedStateHandle["playerQq"] ?: ""  
    private val roomName: String = savedStateHandle["roomName"] ?: ""  
  
    private val _uiState = MutableStateFlow(  
        ChatUiState(  
            roomId = roomId,  
            playerQq = playerQq,  
            roomName = roomName  
        )  
    )  
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()  
  
    private var pollingJob: Job? = null  
    private var lastTimestamp: Long = 0  
  
    init {  
        startPolling()  
    }  
  
    private fun startPolling() {  
        pollingJob?.cancel()  
        pollingJob = viewModelScope.launch {  
            while (isActive) {  
                try {  
                    val newMessages = roomClient.getMessages(roomId, lastTimestamp)  
                    if (newMessages.isNotEmpty()) {  
                        lastTimestamp = newMessages.maxOf { it.timestamp }  
                        val currentMessages = _uiState.value.messages.toMutableList()  
                        val existingIds = currentMessages.map { it.id }.toSet()  
                        val uniqueNew = newMessages.filter { it.id !in existingIds }  
                        currentMessages.addAll(uniqueNew)  
                        currentMessages.sortBy { it.timestamp }  
                        _uiState.value = _uiState.value.copy(  
                            messages = currentMessages,  
                            isLoading = false,  
                            error = null  
                        )  
                    }  
                } catch (e: Exception) {  
                    _uiState.value = _uiState.value.copy(  
                        error = e.message ?: "获取消息失败"  
                    )  
                }  
                delay(3000) // 每3秒轮询一次  
            }  
        }  
    }  
  
    fun sendMessage(content: String) {  
        if (content.isBlank()) return  
        viewModelScope.launch {  
            _uiState.value = _uiState.value.copy(isSending = true)  
            try {  
                roomClient.sendMessage(  
                    roomId = roomId,  
                    senderQq = playerQq,  
                    senderName = "玩家",  
                    content = content  
                )  
                _uiState.value = _uiState.value.copy(isSending = false)  
                // 立即拉取新消息  
                try {  
                    val newMessages = roomClient.getMessages(roomId, lastTimestamp)  
                    if (newMessages.isNotEmpty()) {  
                        lastTimestamp = newMessages.maxOf { it.timestamp }  
                        val currentMessages = _uiState.value.messages.toMutableList()  
                        val existingIds = currentMessages.map { it.id }.toSet()  
                        val uniqueNew = newMessages.filter { it.id !in existingIds }  
                        currentMessages.addAll(uniqueNew)  
                        currentMessages.sortBy { it.timestamp }  
                        _uiState.value = _uiState.value.copy(messages = currentMessages)  
                    }  
                } catch (_: Exception) { }  
            } catch (e: Exception) {  
                _uiState.value = _uiState.value.copy(  
                    isSending = false,  
                    error = e.message ?: "发送消息失败"  
                )  
            }  
        }  
    }  
  
    fun leaveRoom() {  
        viewModelScope.launch {  
            try {  
                roomClient.leaveRoom(roomId, playerQq)  
            } catch (_: Exception) { }  
        }  
    }  
  
    fun clearError() {  
        _uiState.value = _uiState.value.copy(error = null)  
    }  
  
    override fun onCleared() {  
        super.onCleared()  
        pollingJob?.cancel()  
    }  
}