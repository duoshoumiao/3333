package com.pcrjjc.app.ui.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pcrjjc.app.data.local.dao.HistoryDao
import com.pcrjjc.app.data.local.entity.JjcHistory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    historyDao: HistoryDao
) : ViewModel() {

    private val pcrid: Long = savedStateHandle["pcrid"] ?: 0L
    private val platform: Int = savedStateHandle["platform"] ?: -1

    val histories: StateFlow<List<JjcHistory>> = (if (pcrid > 0 && platform >= 0) {
        historyDao.getHistoryByPcrid(platform, pcrid)
    } else if (platform >= 0) {
        historyDao.getHistoryByPlatform(platform)
    } else {
        historyDao.getHistoryByPlatform(0)
    }).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
