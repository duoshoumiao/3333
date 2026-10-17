package com.pcrjjc.app.ui.whois

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pcrjjc.app.util.CharaProfile
import com.pcrjjc.app.util.CharaProfileStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class WhoIsUiState(
    val query: String = "",
    val isLoading: Boolean = true,
    val hasSearched: Boolean = false,
    val exactMatch: Boolean = false,
    val results: List<CharaProfile> = emptyList()
)

@HiltViewModel
class WhoIsViewModel @Inject constructor(
    private val app: Application
) : ViewModel() {

    private val _uiState = MutableStateFlow(WhoIsUiState())
    val uiState: StateFlow<WhoIsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                CharaProfileStore.ensureLoaded(app)
            }
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    fun onQueryChange(value: String) {
        _uiState.value = if (value.isBlank()) {
            _uiState.value.copy(
                query = value,
                hasSearched = false,
                exactMatch = false,
                results = emptyList()
            )
        } else {
            _uiState.value.copy(query = value)
        }
    }

    fun search() {
        val query = _uiState.value.query.trim()
        if (query.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                hasSearched = false,
                exactMatch = false,
                results = emptyList()
            )
            return
        }
        if (_uiState.value.isLoading) return

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                CharaProfileStore.ensureLoaded(app)
                CharaProfileStore.search(query)
            }
            _uiState.value = _uiState.value.copy(
                hasSearched = true,
                exactMatch = result.exactMatch,
                results = result.matches
            )
        }
    }

    fun clear() {
        _uiState.value = _uiState.value.copy(
            query = "",
            hasSearched = false,
            exactMatch = false,
            results = emptyList()
        )
    }
}
