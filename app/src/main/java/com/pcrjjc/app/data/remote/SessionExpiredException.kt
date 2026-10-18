package com.pcrjjc.app.data.remote

/**
 * 会话失效（被顶下线 / 登录过期）。
 *
 * 由 [PcrClient] 在服务器返回会话失效响应时抛出；ClientManager 会据此
 * 用缓存 token 自动重登一次并重试原请求，重登失败才把错误抛给界面。
 */
class SessionExpiredException(
    message: String = "登录状态已失效，可能已在其他设备登录"
) : Exception(message)
