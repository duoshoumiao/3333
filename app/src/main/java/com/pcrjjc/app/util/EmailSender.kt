package com.pcrjjc.app.util  
  
import android.util.Log  
import kotlinx.coroutines.Dispatchers  
import kotlinx.coroutines.withContext  
import java.util.Properties  
import javax.mail.*  
import javax.mail.internet.InternetAddress  
import javax.mail.internet.MimeMessage  
  
object EmailSender {  
    private const val TAG = "EmailSender"  
  
    /**  
     * 通过 SMTP 发送邮件（自己给自己发）  
     * @param email 邮箱地址（同时作为发件人和收件人）  
     * @param authCode 邮箱授权码（SMTP密码）  
     * @param subject 邮件标题  
     * @param body 邮件正文  
     */  
    suspend fun sendEmail(  
        email: String,  
        authCode: String,  
        subject: String,  
        body: String  
    ): Result<Unit> = withContext(Dispatchers.IO) {  
        try {  
            val host = getSmtpHost(email)  
            val port = getSmtpPort(email)  
  
            val props = Properties().apply {  
                put("mail.smtp.auth", "true")  
                put("mail.smtp.starttls.enable", "true")  
                put("mail.smtp.host", host)  
                put("mail.smtp.port", port)  
                put("mail.smtp.ssl.enable", "true")  
                put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory")  
                put("mail.smtp.socketFactory.port", port)  
                put("mail.smtp.connectiontimeout", "10000")  
                put("mail.smtp.timeout", "10000")  
            }  
  
            val session = Session.getInstance(props, object : Authenticator() {  
                override fun getPasswordAuthentication(): PasswordAuthentication {  
                    return PasswordAuthentication(email, authCode)  
                }  
            })  
  
            val message = MimeMessage(session).apply {  
                setFrom(InternetAddress(email))  
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(email))  
                setSubject(subject)  
                setText(body)  
            }  
  
            Transport.send(message)  
            Log.i(TAG, "Email sent successfully to $email")  
            Result.success(Unit)  
        } catch (e: Exception) {  
            Log.e(TAG, "Failed to send email: ${e.message}", e)  
            Result.failure(e)  
        }  
    }  
  
    private fun getSmtpHost(email: String): String {  
        return when {  
            email.endsWith("@qq.com") -> "smtp.qq.com"  
            email.endsWith("@163.com") -> "smtp.163.com"  
            email.endsWith("@126.com") -> "smtp.126.com"  
            email.endsWith("@gmail.com") -> "smtp.gmail.com"  
            email.endsWith("@outlook.com") || email.endsWith("@hotmail.com") -> "smtp.office365.com"  
            email.endsWith("@foxmail.com") -> "smtp.qq.com"  
            email.endsWith("@yeah.net") -> "smtp.yeah.net"  
            else -> "smtp.${email.substringAfter("@")}"  
        }  
    }  
  
    private fun getSmtpPort(email: String): String {  
        return when {  
            email.endsWith("@gmail.com") -> "465"  
            email.endsWith("@outlook.com") || email.endsWith("@hotmail.com") -> "587"  
            else -> "465"  
        }  
    }  
}