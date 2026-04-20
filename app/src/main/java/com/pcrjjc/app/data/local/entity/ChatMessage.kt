package com.pcrjjc.app.data.local.entity  
  
data class ChatMessage(  
    val id: String,  
    val roomId: String,  
    val senderName: String,  
    val senderQq: String,  
    val content: String,  
    val timestamp: Long  
)