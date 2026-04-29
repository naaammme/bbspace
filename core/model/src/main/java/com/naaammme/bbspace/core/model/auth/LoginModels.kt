package com.naaammme.bbspace.core.model

/**
 * 二维码登录数据
 */
data class QrCodeData(
    val url: String,          // 二维码 URL
    val authCode: String      // 授权码
)

/**
 * 扫码绑定的 HD 鉴权 key
 */
data class HdAccessGrant(
    val mid: Long,
    val accessKey: String,
    val expiresIn: Long
)

/**
 * 登录凭证
 */
data class LoginCredential(
    val mid: Long,                    // 用户 ID
    val accessToken: String,          // 访问令牌
    val refreshToken: String,         // 刷新令牌
    val expiresIn: Long,              // 过期时间（秒）
    val cookies: List<Cookie>         // Cookie 列表
)

/**
 * Cookie 数据
 */
data class Cookie(
    val name: String,
    val value: String,
    val httpOnly: Boolean,
    val expires: Long,
    val secure: Boolean
)

/**
 * 登录状态
 */
sealed class LoginState {
    data object Idle : LoginState()
    data object Loading : LoginState()
    data class QrCodeReady(val qrCodeData: QrCodeData) : LoginState()
    data object Scanned : LoginState()
    data class QrSuccess(val grant: HdAccessGrant) : LoginState()
    data class Error(val message: String) : LoginState()
}
