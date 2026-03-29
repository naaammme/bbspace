package com.naaammme.bbspace.core.model

sealed class SmsLoginState {
    data object Idle : SmsLoginState()
    data object SendingSms : SmsLoginState()
    data class SmsSent(val captchaKey: String) : SmsLoginState()
    data class NeedGeetest(
        val gt: String,
        val challenge: String,
        val token: String
    ) : SmsLoginState()
    data object Logging : SmsLoginState()
    data class Success(val credential: LoginCredential) : SmsLoginState()
    data class Error(val msg: String) : SmsLoginState()
}

data class GeetestResult(
    val validate: String,
    val seccode: String,
    val challenge: String
)

data class SmsCodeResult(
    val captchaKey: String = "",
    val needGeetest: Boolean = false,
    val geeGt: String = "",
    val geeChallenge: String = "",
    val recaptchaToken: String = ""
)
