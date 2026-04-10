package com.pcrjjc.app.ui.home  
  
import androidx.lifecycle.ViewModel  
import androidx.lifecycle.viewModelScope  
import com.pcrjjc.app.data.local.dao.BindDao  
import com.pcrjjc.app.data.local.dao.RankCacheDao  
import com.pcrjjc.app.data.local.entity.PcrBind  
import com.pcrjjc.app.data.local.entity.RankCache  
import dagger.hilt.android.lifecycle.HiltViewModel  
import kotlinx.coroutines.flow.SharingStarted  
import kotlinx.coroutines.flow.StateFlow  
import kotlinx.coroutines.flow.map  
import kotlinx.coroutines.flow.stateIn  
import kotlinx.coroutines.launch  
import javax.inject.Inject  
  
@HiltViewModel  
class HomeViewModel @Inject constructor(  
    private val bindDao: BindDao,  
    private val rankCacheDao: RankCacheDao  
) : ViewModel() {  
  
    val binds: StateFlow<List<PcrBind>> = bindDao.getAllBinds()  
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())  
  
    val rankCacheMap: StateFlow<Map<Pair<Long, Int>, RankCache>> = rankCacheDao.getAllFlow()  
        .map { list -> list.associateBy { Pair(it.pcrid, it.platform) } }  
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())  
  
    fun deleteBind(bind: PcrBind) {  
        viewModelScope.launch { bindDao.deleteById(bind.id) }  
    }  
}