package com.pcrjjc.app.domain  
  
import android.util.Log  
import com.pcrjjc.app.data.local.entity.PcrBind  
import com.pcrjjc.app.data.remote.ApiException  
import com.pcrjjc.app.data.remote.PcrClient  
import com.pcrjjc.app.data.remote.TwPcrClient  
import kotlinx.coroutines.channels.Channel  
import kotlinx.coroutines.coroutineScope  
import kotlinx.coroutines.launch  
  
class QueryEngine {  
  
    companion object {  
        private const val TAG = "QueryEngine"  
    }  
  
    data class QueryTask(  
        val bind: PcrBind,  
        val priority: Int = 10  
    )  
  
    data class QueryResult(  
        val bind: PcrBind,  
        val userInfo: Map<String, Any?>,  
        val fullResponse: Map<String, Any?>  
    )  
  
    @Suppress("UNCHECKED_CAST")  
    suspend fun queryProfile(  
        client: Any,  
        bind: PcrBind  
    ): QueryResult? {  
        return try {  
            val res = when (client) {