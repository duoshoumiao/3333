package com.pcrjjc.app.ui.bind

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pcrjjc.app.data.local.dao.BindDao
import com.pcrjjc.app.data.local.entity.PcrBind
import com.pcrjjc.app.util.Platform
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BindUiState(
    val uid: String = "",
    val nickname: String = "",
    val selectedPlatform: Platform = Platform.B_SERVER,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isSuccess: Boolean = false
)

@HiltViewModel
class BindViewModel @Inject constructor(
    private val bindDao: BindDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(BindUiState())
    val uiState: StateFlow<BindUiState> = _uiState

    fun updateUid(uid: String) {
        _uiState.value = _uiState.value.copy(uid = uid, errorMessage = null)
    }

    fun updateNickname(nickname: String) {
        _uiState.value = _uiState.value.copy(nickname = nickname)
    }

    fun updatePlatform(platform: Platform) {
        _uiState.value = _uiState.value.copy(selectedPlatform = platform)
    }

    fun bind() {
        val state = _uiState.value
        val uid = state.uid.trim()

        // Validate UID length
        val validLen = if (state.selectedPlatform == Platform.TW_SERVER) 10 else 13
        if (uid.length != validLen) {
            _uiState.value = state.copy(errorMessage = "UID应为${validLen}位数字")
            return
        }

        if (!uid.all { it.isDigit() }) {
            _uiState.value = state.copy(errorMessage = "UID应为纯数字")
            return
        }

        if (state.nickname.length > 12) {
            _uiState.value = state.copy(errorMessage = "昵称不能超过12个字")
            return
        }

        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true)

            val pcrid = uid.toLong()
            val existing = bindDao.getBindByPcrid(pcrid, state.selectedPlatform.id)
            if (existing != null) {
                _uiState.value = state.copy(
                    isLoading = false,
                    errorMessage = "该UID已经绑定过了"
                )
                return@launch
            }

            val count = bindDao.getBindCount(state.selectedPlatform.id)
            if (count >= 999) {
                _uiState.value = state.copy(
                    isLoading = false,
                    errorMessage = "绑定数量已达上限"
                )
                return@launch
            }

            val bind = PcrBind(
                pcrid = pcrid,
                platform = state.selectedPlatform.id,
                name = state.nickname.ifEmpty { null }
            )
            bindDao.insert(bind)

            _uiState.value = state.copy(
                isLoading = false,
                isSuccess = true
            )
        }
    }
}
