package com.pcrjjc.app.ui.captcha  
  
@Composable  
fun CaptchaWebViewDialog(  
    gt: String,  
    challenge: String,  
    gtUserId: String,  
    onValidateResult: (challenge: String, gtUserId: String, validate: String) -> Unit,  
    onDismiss: () -> Unit  
) {  
    // 构造 URL  
    val url = "https://help.tencentbot.top/geetest/?captcha_type=1&challenge=$challenge&gt=$gt&userid=$gtUserId&gs=1"  
      
    AlertDialog(onDismissRequest = onDismiss, ...) {  
        // 内嵌 AndroidView WebView  
        // WebView 通过 addJavascriptInterface 注入 JS Bridge  
        // 当用户完成验证后，页面会显示 validate 值  
        // 通过 JS Bridge 回调 onValidateResult  
        //   
        // 备选方案：如果 JS Bridge 不方便，可以在 Dialog 底部放一个 TextField  
        // 让用户手动复制粘贴 validate 值，加一个"提交"按钮  
    }  
}