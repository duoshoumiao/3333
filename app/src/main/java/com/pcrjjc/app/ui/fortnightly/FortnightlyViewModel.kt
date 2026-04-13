package com.pcrjjc.app.ui.fortnightly  
  
import androidx.lifecycle.ViewModel  
import androidx.lifecycle.viewModelScope  
import com.pcrjjc.app.data.model.ClassifiedActivity  
import com.pcrjjc.app.data.repository.FortnightlyRepository  
import dagger.hilt.android.lifecycle.HiltViewModel  
import kotlinx.coroutines.flow.MutableStateFlow  
import kotlinx.coroutines.flow.StateFlow  
import kotlinx.coroutines.flow.asStateFlow  
import kotlinx.coroutines.launch  
import javax.inject.Inject  
  
data class FortnightlyUiState(  
    val isLoading: Boolean = true,  
    val isUpdating: Boolean = false,  
    val activities: Map<String, List<ClassifiedActivity>> = emptyMap(),  
    val message: String? = null,       // Snackbar 消息  
    val errorMessage: String? = null  
)  
  
@HiltViewModel  
class FortnightlyViewModel @Inject constructor(  
    private val repository: FortnightlyRepository  
) : ViewModel() {  
  
    private val _uiState = MutableStateFlow(FortnightlyUiState())  
    val uiState: StateFlow<FortnightlyUiState> = _uiState.asStateFlow()  
  
    init {  
        // 如果本地没有数据，先从 GitHub 拉取  
        if (!repository.hasLocalData()) {  
            updateData(showMessage = false)  
        } else {  
            loadActivities()  
        }  
    }  
  
    fun loadActivities() {  
        viewModelScope.launch {  
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)  
            try {  
                val classified = repository.loadClassifiedActivities()  
                _uiState.value = _uiState.value.copy(  
                    isLoading = false,  
                    activities = classified  
                )  
            } catch (e: Exception) {  
                _uiState.value = _uiState.value.copy(  
                    isLoading = false,  
                    errorMessage = "加载失败: ${e.message}"  
                )  
            }  
        }  
    }  
  
    fun updateData(showMessage: Boolean = true) {  
        viewModelScope.launch {  
            _uiState.value = _uiState.value.copy(isUpdating = true, message = null)  
            val (hasUpdate, msg) = repository.updateFromGitHub()  
            _uiState.value = _uiState.value.copy(  
                isUpdating = false,  
                message = if (showMessage) msg else null  
            )  
            // 更新后重新加载  
            loadActivities()  
        }  
    }  
  
    fun clearMessage() {  
        _uiState.value = _uiState.value.copy(message = null)  
    }  
}