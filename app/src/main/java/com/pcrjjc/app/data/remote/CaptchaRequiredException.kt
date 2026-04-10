package com.pcrjjc.app.data.remote  
  
class CaptchaRequiredException(  
    val gt: String,  
    val challenge: String,  
    val gtUserId: String  
) : Exception("需要手动过码")