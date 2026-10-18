package com.pcrjjc.app.ui.fortnightly  

import androidx.lifecycle.ViewModel  
import androidx.lifecycle.viewModelScope  
import com.pcrjjc.app.data.local.SettingsDataStore  
import com.pcrjjc.app.data.model.ClassifiedActivity  
import com.pcrjjc.app.data.repository.FortnightlyRepository  
import com.pcrjjc.app.domain.ActivityClassifier  
import com.pcrjjc.app.domain.FortnightlyPushManager  
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
    val errorMessage: String? = null,  
    /** 已订阅的活动类别 */  
    val subscribedCategories: Set<String> = emptySet(),  
    /** 新活动推送总开关 */  
    val pushEnabled: Boolean = true,  
    /** 最近一次推送检查时间（毫秒） */  
    val lastCheckAt: Long = 0L  
) {  
    /** 订阅数量，用于标题栏角标 */  
    val subscriptionCount: Int get() = subscribedCategories.size  
}  

@HiltViewModel  
class FortnightlyViewModel @Inject constructor(  
    private val repository: FortnightlyRepository,  
    private val settingsDataStore: SettingsDataStore,  
    private val pushManager: FortnightlyPushManager  
) : ViewModel() {  

    private val _uiState = MutableStateFlow(FortnightlyUiState())  
    val uiState: StateFlow<FortnightlyUiState> = _uiState.asStateFlow()  

    init {  
        // 订阅状态实时同步  
        viewModelScope.launch {  
            settingsDataStore.fortnightlySubscriptionsFlow().collect { subscribed ->  
                _uiState.value = _uiState.value.copy(subscribedCategories = subscribed)  
            }  
        }  
        viewModelScope.launch {  
            _uiState.value = _uiState.value.copy(  
                pushEnabled = settingsDataStore.isFortnightlyPushEnabled(),  
                lastCheckAt = settingsDataStore.getFortnightlyLastCheckAt()  
            )  
        }  

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
            val (_, msg) = repository.updateFromGitHub()  
            _uiState.value = _uiState.value.copy(  
                isUpdating = false,  
                message = if (showMessage) msg else null  
            )  
            // 更新后重新加载  
            loadActivities()  
        }  
    }  

    /**  
     * 订阅 / 取消订阅某个类别。  
     * 新订阅时会为该类别建立推送基线，避免立刻收到一堆历史活动。  
     */  
    fun toggleSubscription(category: String) {  
        viewModelScope.launch {  
            val nowSubscribed = settingsDataStore.toggleFortnightlySubscription(category)  
            if (nowSubscribed) {  
                pushManager.seedCategoryBaseline(category)  
                _uiState.value = _uiState.value.copy(  
                    message = "已订阅【$category】，新活动开始时将推送提醒"  
                )  
            } else {  
                _uiState.value = _uiState.value.copy(  
                    message = "已取消订阅【$category】"  
                )  
            }  
        }  
    }  

    /** 一键订阅当前列表里的全部类别 */  
    fun subscribeAll() {  
        viewModelScope.launch {  
            val all = ActivityClassifier.categoryOrder.toSet()  
            val current = settingsDataStore.getFortnightlySubscriptions()  
            val added = all - current  
            settingsDataStore.setFortnightlySubscriptions(current + all)  
            for (category in added) {  
                pushManager.seedCategoryBaseline(category)  
            }  
            _uiState.value = _uiState.value.copy(  
                message = if (added.isEmpty()) "已订阅全部类别" else "已订阅全部 ${all.size} 个类别"  
            )  
        }  
    }  

    /** 清空全部订阅 */  
    fun clearSubscriptions() {  
        viewModelScope.launch {  
            settingsDataStore.setFortnightlySubscriptions(emptySet())  
            _uiState.value = _uiState.value.copy(message = "已取消全部订阅")  
        }  
    }  

    fun setPushEnabled(enabled: Boolean) {  
        viewModelScope.launch {  
            settingsDataStore.setFortnightlyPushEnabled(enabled)  
            _uiState.value = _uiState.value.copy(  
                pushEnabled = enabled,  
                message = if (enabled) "已开启新活动推送" else "已关闭新活动推送"  
            )  
        }  
    }  

    /** 手动触发一次推送检查（用户主动拉取，强制联网） */  
    fun checkNow() {  
        viewModelScope.launch {  
            _uiState.value = _uiState.value.copy(isUpdating = true, message = null)  
            val result = pushManager.checkAndPush(refresh = true, forceRefresh = true)  
            _uiState.value = _uiState.value.copy(  
                isUpdating = false,  
                message = result.message,  
                lastCheckAt = settingsDataStore.getFortnightlyLastCheckAt()  
            )  
            loadActivities()  
        }  
    }  

    fun clearMessage() {  
        _uiState.value = _uiState.value.copy(message = null)  
    }  
}  
