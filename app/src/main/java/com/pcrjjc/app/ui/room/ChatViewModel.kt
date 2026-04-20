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
    val roomId: String = "",  
    val roomName: String = "",  
    val playerQq: String = "",  
    val messages: List<ChatMessage> = emptyList(),  
    val isLoading: Boolean = false,  
    val error: String? = null,  
    val isSending: Boolean = false  
)  
  
@HiltViewModel  
class ChatViewModel @Inject constructor(  
    private val roomClient: RoomClient,  
    savedStateHandle: SavedStateHandle  
) : ViewModel() {  
  
    private val _uiState = MutableStateFlow(ChatUiState())  
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()  
  
    private var pollingJob: Job? = null  
    private var lastTimestamp: Long = 0  
  
    init {  
        val roomId = savedStateHandle.get<String>("roomId") ?: ""  
        val playerQq = savedStateHandle.get<String>("playerQq") ?: ""  
        val roomName = savedStateHandle.get<String>("roomName") ?: ""  
  
        _uiState.value = _uiState.value.copy(  
            roomId = roomId,  
            playerQq = playerQq,  
            roomName = roomName  
        )  
  
        if (roomId.isNotBlank()) {  
            loadMessages()  
            startPolling()  
        }  
    }  
  
    private fun loadMessages() {  
        viewModelScope.launch {  
            _uiState.value = _uiState.value.copy(isLoading = true)  
            try {  
                val messages = roomClient.getMessages(_uiState.value.roomId)  
                if (messages.isNotEmpty()) {  
                    lastTimestamp = messages.maxOf { it.timestamp }  
                }  
                _uiState.value = _uiState.value.copy(  
                    messages = messages,  
                    isLoading = false,  
                    error = null  
                )  
            } catch (e: Exception) {  
                _uiState.value = _uiState.value.copy(  
                    isLoading = false,  
                    error = e.message ?: "加载消息失败"  
                )  
            }  
        }  
    }  
  
    private fun startPolling() {  
        pollingJob?.cancel()  
        pollingJob = viewModelScope.launch {  
            while (isActive) {  
                delay(3000) // 每3秒轮询一次  
                try {  
                    val newMessages = roomClient.getMessages(  
                        _uiState.value.roomId,  
                        since = lastTimestamp  
                    )  
                    if (newMessages.isNotEmpty()) {  
                        lastTimestamp = newMessages.maxOf { it.timestamp }  
                        val currentMessages = _uiState.value.messages  
                        val existingIds = currentMessages.map { it.id }.toSet()  
                        val filtered = newMessages.filter { it.id !in existingIds }  
                        if (filtered.isNotEmpty()) {  
                            _uiState.value = _uiState.value.copy(  
                                messages = currentMessages + filtered,  
                                error = null  
                            )  
                        }  
                    }  
                } catch (_: Exception) {  
                    // 轮询失败静默忽略，下次重试  
                }  
            }  
        }  
    }  
  
    fun sendMessage(content: String) {  
        if (content.isBlank()) return  
        viewModelScope.launch {  
            _uiState.value = _uiState.value.copy(isSending = true)  
            try {  
                val msg = roomClient.sendMessage(  
                    roomId = _uiState.value.roomId,  
                    senderQq = _uiState.value.playerQq,  
                    senderName = "玩家",  
                    content = content.trim()  
                )  
                lastTimestamp = msg.timestamp  
                _uiState.value = _uiState.value.copy(  
                    messages = _uiState.value.messages + msg,  
                    isSending = false,  
                    error = null  
                )  
            } catch (e: Exception) {  
                _uiState.value = _uiState.value.copy(  
                    isSending = false,  
                    error = e.message ?: "发送失败"  
                )  
            }  
        }  
    }  
  
    fun leaveRoom() {  
        viewModelScope.launch {  
            try {  
                roomClient.leaveRoom(  
                    roomId = _uiState.value.roomId,  
                    playerQq = _uiState.value.playerQq  
                )  
            } catch (_: Exception) {  
                // 离开失败静默忽略  
            }  
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