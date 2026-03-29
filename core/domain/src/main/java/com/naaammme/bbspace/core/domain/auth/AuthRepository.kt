package com.naaammme.bbspace.core.domain.auth

import com.naaammme.bbspace.core.model.HdAccessGrant
import com.naaammme.bbspace.core.model.LoginCredential
import com.naaammme.bbspace.core.model.QrCodeData
import com.naaammme.bbspace.core.model.SmsCodeResult
import com.naaammme.bbspace.core.model.User
import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    var guestMode: Boolean
    val currentMidFlow: StateFlow<Long>
    suspend fun getQrCode(): Result<QrCodeData>
    suspend fun pollQrCode(authCode: String): Result<Pair<Int, HdAccessGrant?>>
    suspend fun logout(credential: LoginCredential): Result<Unit>
    suspend fun fetchMyInfo(credential: LoginCredential): Result<User>
    suspend fun fetchMineInfo(credential: LoginCredential): Result<User>
    fun saveHdAccessKey(mid: Long, key: String, expiresIn: Long)
    fun getHdAccessKeyForCurrent(): String
    fun hasHdAccessKeyForCurrent(): Boolean
    fun clearHdAccessKey()
    fun saveCredential(credential: LoginCredential)
    fun getSavedCredential(): LoginCredential?
    fun clearCredential()
    fun getAllAccounts(): List<LoginCredential>
    fun switchAccount(mid: Long): LoginCredential?
    fun removeAccount(mid: Long)
    fun getUserInfo(): User?
    fun getAllUserInfos(): Map<Long, User>

    suspend fun sendSmsCode(
        tel: String, cid: Int,
        geeValidate: String = "", geeSeccode: String = "",
        geeChallenge: String = "", recaptchaToken: String = ""
    ): Result<SmsCodeResult>

    suspend fun loginBySms(
        tel: String, cid: Int, code: String, captchaKey: String
    ): Result<LoginCredential>
}
