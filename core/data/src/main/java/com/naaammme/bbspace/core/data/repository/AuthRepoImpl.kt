package com.naaammme.bbspace.core.data.repository

import com.naaammme.bbspace.core.common.log.Logger
import com.naaammme.bbspace.core.data.AuthStore
import com.naaammme.bbspace.core.data.CacheManager
import com.naaammme.bbspace.core.model.Cookie
import com.naaammme.bbspace.core.model.HdAccessGrant
import com.naaammme.bbspace.core.model.LoginCredential
import com.naaammme.bbspace.core.model.QrCodeData
import com.naaammme.bbspace.core.model.SmsCodeResult
import com.naaammme.bbspace.core.model.User
import java.security.MessageDigest
import com.naaammme.bbspace.core.domain.auth.AuthRepository
import com.naaammme.bbspace.infra.crypto.DeviceIdentity
import com.naaammme.bbspace.infra.crypto.GuestIdGenerator
import com.naaammme.bbspace.core.common.BiliConstants
import com.naaammme.bbspace.infra.network.BiliRestClient
import com.naaammme.bbspace.infra.network.BiliRestParamBuilder
import com.naaammme.bbspace.infra.network.BiliRestProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepoImpl @Inject constructor(
    private val restClient: BiliRestClient,
    private val authStore: AuthStore,
    private val guestIdGenerator: GuestIdGenerator,
    private val deviceIdentity: DeviceIdentity,
    private val restParamBuilder: BiliRestParamBuilder,
    private val cacheManager: CacheManager
) : AuthRepository {

    private val _currentMidFlow = MutableStateFlow(authStore.mid)
    override val currentMidFlow: StateFlow<Long> = _currentMidFlow.asStateFlow()

    companion object {
        private const val TAG = "AuthRepoImpl"

        private const val QR_AUTH_CODE_ENDPOINT = "/x/passport-tv-login/qrcode/auth_code"
        private const val QR_POLL_ENDPOINT = "/x/passport-tv-login/qrcode/poll"
        private const val REVOKE_ENDPOINT = "/x/passport-login/revoke"
        private const val MY_INFO_ENDPOINT = "/x/v2/account/myinfo"
        private const val MINE_IPAD_ENDPOINT = "/x/v2/account/mine"

        private const val SMS_SEND_ENDPOINT = "/x/passport-login/sms/send"
        private const val SMS_LOGIN_ENDPOINT = "/x/passport-login/login/sms"
        private const val PRE_CAPTURE_ENDPOINT = "/x/safecenter/captcha/pre"
    }

    override var guestMode: Boolean
        get() = authStore.guestMode
        set(value) {
            authStore.guestMode = value
            _currentMidFlow.value = if (value) 0L else authStore.mid
        }

    override suspend fun getQrCode(): Result<QrCodeData> = runCatching {
        val guestId = cacheManager.guestId
        val sessionId = cacheManager.sessionId

        val loginSessionId = cacheManager.generateLoginSessionId()
        cacheManager.saveSession(guestId, sessionId, loginSessionId)

        val ts = System.currentTimeMillis() / 1000
        val json = restClient.postSigned(
            url = "${BiliConstants.BASE_URL_PASSPORT}$QR_AUTH_CODE_ENDPOINT",
            params = restParamBuilder.passport(BiliRestProfile.HD, ts) + mapOf(
                "app_id" to "",
                "code" to "",
                "device_tourist_id" to guestId,
                "extend" to "",
                "gourl" to "",
                "login_session_id" to loginSessionId,
                "spm_id" to "from_spmid"
            ),
            profile = BiliRestProfile.HD
        )

        val data = json.getJSONObject("data")
        QrCodeData(url = data.getString("url"), authCode = data.getString("auth_code"))
    }

    override suspend fun pollQrCode(authCode: String): Result<Pair<Int, HdAccessGrant?>> = runCatching {
        val ts = System.currentTimeMillis() / 1000

        val deviceInfoJson = org.json.JSONObject().apply {
            put("DeviceType", "Android")
            put("Buvid", deviceIdentity.buvid)
            put("fts", (System.currentTimeMillis() / 1000 - 30 * 24 * 3600).toString())
            put("BuildHost", "android-build")
            put("BuildDisplay", deviceIdentity.buildId)
            put("BuildFingerprint", deviceIdentity.buildFingerprint)
            put("BuildBrand", deviceIdentity.brand)
            if (deviceIdentity.mac.isNotEmpty()) put("MAC", deviceIdentity.mac)
            if (deviceIdentity.androidId.isNotEmpty()) put("AndroidID", deviceIdentity.androidId)
        }.toString()

        val (dt, _) = guestIdGenerator.generateDtAndDeviceInfo(deviceInfoJson)
            ?: throw Exception("生成 dt 和 device_info 失败")

        val json = restClient.postSignedRaw(
            url = "${BiliConstants.BASE_URL_PASSPORT}$QR_POLL_ENDPOINT",
            params = restParamBuilder.passport(BiliRestProfile.HD, ts) + mapOf(
                "auth_code" to authCode,
                "device_tourist_id" to cacheManager.guestId,
                "dt" to dt,
                "extend" to "",
                "login_session_id" to cacheManager.loginSessionId,
                "spm_id" to "from_spmid"
            ),
            profile = BiliRestProfile.HD
        )

        val code = json.getInt("code")
        Logger.d(TAG) { "Poll response code: $code" }

        when (code) {
            86039 -> 0 to null
            86090 -> 1 to null
            0 -> {
                val data = json.getJSONObject("data")
                val mid = data.getLong("mid")
                val accessToken = data.getString("access_token")
                val expiresIn = data.optLong("expires_in", 0)

                Logger.d(TAG) { "扫码登录成功, 提取 HD key: mid=$mid" }

                2 to HdAccessGrant(
                    mid = mid,
                    accessKey = accessToken,
                    expiresIn = expiresIn
                )
            }
            else -> throw Exception(json.optString("message", "Unknown error (code=$code)"))
        }
    }

    override suspend fun logout(credential: LoginCredential): Result<Unit> = runCatching {
        val ts = System.currentTimeMillis() / 1000

        restClient.postSigned(
            url = "${BiliConstants.BASE_URL_PASSPORT}$REVOKE_ENDPOINT",
            params = restParamBuilder.passport(BiliRestProfile.APP, ts, credential.accessToken) + mapOf(
                "from_access_key" to credential.accessToken,
                "mid" to credential.mid.toString(),
                "revoke_type" to "2"
            ),
            profile = BiliRestProfile.APP
        )

        Logger.d(TAG) { "退出登录成功" }

        authStore.clearCredential()
        authStore.clearUserInfo()
        cacheManager.clearSession()
        cacheManager.clearTicketCache()
        cacheManager.clearGuestCache()
    }

    override fun saveHdAccessKey(mid: Long, key: String, expiresIn: Long) =
        authStore.saveHdAccessKey(mid, key, expiresIn)
    override fun getHdAccessKeyForCurrent(): String = authStore.getHdAccessKeyForCurrent()
    override fun hasHdAccessKeyForCurrent(): Boolean = authStore.hasHdAccessKeyForCurrent()
    override fun clearHdAccessKey() = authStore.clearHdAccessKey()
    override fun saveCredential(credential: LoginCredential) {
        authStore.saveCredential(credential)
        _currentMidFlow.value = credential.mid
    }
    override fun getSavedCredential(): LoginCredential? = authStore.getSavedCredential()
    override fun clearCredential() = authStore.clearCredential()
    override fun getAllAccounts(): List<LoginCredential> = authStore.getAllAccounts()
    override fun switchAccount(mid: Long): LoginCredential? {
        val result = authStore.switchAccount(mid)
        _currentMidFlow.value = mid
        return result
    }
    override fun removeAccount(mid: Long) = authStore.removeAccount(mid)
    override fun getUserInfo(): User? = authStore.getUserInfo()
    override fun getAllUserInfos(): Map<Long, User> = authStore.getAllUserInfos()

    override suspend fun fetchMyInfo(credential: LoginCredential): Result<User> = runCatching {
        val ts = System.currentTimeMillis() / 1000
        val json = restClient.getSigned(
            url = "${BiliConstants.BASE_URL_APP}$MY_INFO_ENDPOINT",
            params = restParamBuilder.app(BiliRestProfile.APP, ts, credential.accessToken) + mapOf(
                "buvid" to deviceIdentity.buvid,
                "local_id" to deviceIdentity.buvid
            ),
            profile = BiliRestProfile.APP
        )
        val data = json.getJSONObject("data")
        val vip = data.optJSONObject("vip")
        val official = data.optJSONObject("official")
        val user = User(
            mid = data.getLong("mid"),
            name = data.getString("name"),
            avatar = data.getString("face"),
            sign = data.optString("sign"),
            level = data.optInt("level"),
            coins = data.optDouble("coins"),
            sex = data.optInt("sex"),
            birthday = data.optString("birthday"),
            vipType = vip?.optInt("type") ?: 0,
            vipStatus = vip?.optInt("status") ?: 0,
            emailVerified = data.optInt("email_status") == 1,
            phoneVerified = data.optInt("tel_status") == 1,
            officialRole = official?.optInt("role") ?: 0,
            silence = data.optInt("silence") == 1
        )
        authStore.saveUserInfo(user)
        Logger.d(TAG) { "获取用户信息成功: mid=${user.mid} name=${user.name}" }
        user
    }

    override suspend fun fetchMineInfo(credential: LoginCredential): Result<User> = runCatching {
        val ts = System.currentTimeMillis() / 1000
        val json = restClient.getSigned(
            url = "${BiliConstants.BASE_URL_APP}$MINE_IPAD_ENDPOINT",
            params = restParamBuilder.app(BiliRestProfile.APP, ts, credential.accessToken),
            profile = BiliRestProfile.APP
        )
        val data = json.getJSONObject("data")
        val vip = data.optJSONObject("vip")
        val user = User(
            mid = data.getLong("mid"),
            name = data.getString("name"),
            avatar = data.getString("face"),
            level = data.optInt("level"),
            coins = data.optDouble("coin"),
            vipType = vip?.optInt("type") ?: 0,
            vipStatus = vip?.optInt("status") ?: 0,
            silence = data.optInt("silence") == 1,
            dynamic = data.optInt("dynamic"),
            following = data.optInt("following"),
            follower = data.optInt("follower")
        )
        Logger.d(TAG) { "获取 mine/ipad 成功: mid=${user.mid} following=${user.following} follower=${user.follower}" }
        user
    }

    // ── 短信登录 ──

    override suspend fun sendSmsCode(
        tel: String, cid: Int,
        geeValidate: String, geeSeccode: String,
        geeChallenge: String, recaptchaToken: String
    ): Result<SmsCodeResult> = runCatching {
        val ts = System.currentTimeMillis() / 1000
        val loginSessionId = generateLoginSessionId()

        val params = restParamBuilder.passport(BiliRestProfile.SMS, ts) + buildMap {
            put("cid", cid.toString())
            put("tel", tel)
            put("login_session_id", loginSessionId)
            if (geeValidate.isNotEmpty()) put("gee_validate", geeValidate)
            if (geeSeccode.isNotEmpty()) put("gee_seccode", geeSeccode)
            if (geeChallenge.isNotEmpty()) put("gee_challenge", geeChallenge)
            if (recaptchaToken.isNotEmpty()) put("recaptcha_token", recaptchaToken)
        }

        val json = restClient.postSignedRaw(
            url = "${BiliConstants.BASE_URL_PASSPORT}$SMS_SEND_ENDPOINT",
            params = params,
            profile = BiliRestProfile.SMS
        )

        val code = json.optInt("code", -1)
        val data = json.optJSONObject("data")

        when {
            // 成功且无极验
            code == 0 && data?.optString("recaptcha_url", "").isNullOrEmpty() -> {
                SmsCodeResult(captchaKey = data?.optString("captcha_key", "") ?: "")
            }
            // 成功但需要极验 (recaptcha_url 非空)
            code == 0 && !data?.optString("recaptcha_url", "").isNullOrEmpty() -> {
                val url = data!!.getString("recaptcha_url")
                val uri = android.net.Uri.parse(url)
                SmsCodeResult(
                    needGeetest = true,
                    geeGt = uri.getQueryParameter("gee_gt") ?: "",
                    geeChallenge = uri.getQueryParameter("gee_challenge") ?: "",
                    recaptchaToken = uri.getQueryParameter("recaptcha_token") ?: ""
                )
            }
            // 86214: 极验参数在 data 中
            code == 86214 -> {
                SmsCodeResult(
                    needGeetest = true,
                    geeGt = data?.optString("gee_gt", "") ?: "",
                    geeChallenge = data?.optString("gee_challenge", "") ?: "",
                    recaptchaToken = data?.optString("recaptcha_token", "") ?: ""
                )
            }
            // 2400: 需要先 preCapture
            code == 2400 -> {
                preCapture()
            }
            else -> {
                throw Exception(json.optString("message", "发送验证码失败 (code=$code)"))
            }
        }
    }

    private suspend fun preCapture(): SmsCodeResult {
        val ts = System.currentTimeMillis() / 1000
        val json = restClient.postSigned(
            url = "${BiliConstants.BASE_URL_PASSPORT}$PRE_CAPTURE_ENDPOINT",
            params = restParamBuilder.passport(BiliRestProfile.SMS, ts),
            profile = BiliRestProfile.SMS
        )
        val data = json.getJSONObject("data")
        return SmsCodeResult(
            needGeetest = true,
            geeGt = data.optString("gee_gt", ""),
            geeChallenge = data.optString("gee_challenge", ""),
            recaptchaToken = data.optString("recaptcha_token", "")
        )
    }

    override suspend fun loginBySms(
        tel: String, cid: Int, code: String, captchaKey: String
    ): Result<LoginCredential> = runCatching {
        val ts = System.currentTimeMillis() / 1000

        val deviceInfoJson = org.json.JSONObject().apply {
            put("DeviceType", "Android")
            put("Buvid", deviceIdentity.buvid)
            put("fts", (System.currentTimeMillis() / 1000 - 30 * 24 * 3600).toString())
            put("BuildHost", "android-build")
            put("BuildDisplay", deviceIdentity.buildId)
            put("BuildFingerprint", deviceIdentity.buildFingerprint)
            put("BuildBrand", deviceIdentity.brand)
            if (deviceIdentity.mac.isNotEmpty()) put("MAC", deviceIdentity.mac)
            if (deviceIdentity.androidId.isNotEmpty()) put("AndroidID", deviceIdentity.androidId)
        }.toString()

        val (dt, _) = guestIdGenerator.generateDtAndDeviceInfo(deviceInfoJson)
            ?: throw Exception("生成 dt 失败")

        val params = restParamBuilder.passport(BiliRestProfile.SMS, ts) + mapOf(
            "captcha_key" to captchaKey,
            "cid" to cid.toString(),
            "code" to code,
            "device_tourist_id" to cacheManager.guestId,
            "dt" to dt,
            "from_pv" to "main.my-information.my-login.0.click",
            "from_url" to "bilibili://user_center/mine",
            "login_session_id" to cacheManager.loginSessionId,
            "tel" to tel
        )

        val json = restClient.postSigned(
            url = "${BiliConstants.BASE_URL_PASSPORT}$SMS_LOGIN_ENDPOINT",
            params = params,
            profile = BiliRestProfile.SMS
        )

        val data = json.getJSONObject("data")
        val tokenInfo = data.getJSONObject("token_info")
        val mid = tokenInfo.getLong("mid")
        val accessToken = tokenInfo.getString("access_token")
        val refreshToken = tokenInfo.getString("refresh_token")
        val expiresIn = tokenInfo.optLong("expires_in", 0)

        val cookies = mutableListOf<Cookie>()
        val cookieInfo = data.optJSONObject("cookie_info")
        val cookiesArray = cookieInfo?.optJSONArray("cookies")
        if (cookiesArray != null) {
            for (i in 0 until cookiesArray.length()) {
                val c = cookiesArray.getJSONObject(i)
                cookies.add(Cookie(
                    name = c.getString("name"),
                    value = c.getString("value"),
                    httpOnly = c.optInt("http_only", 0) == 1,
                    expires = c.optLong("expires", 0),
                    secure = c.optInt("secure", 0) == 1
                ))
            }
        }

        Logger.d(TAG) { "短信登录成功: mid=$mid" }

        LoginCredential(
            mid = mid,
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresIn = expiresIn,
            cookies = cookies
        )
    }

    private fun generateLoginSessionId(): String {
        val buvid = deviceIdentity.buvid
        val tsMs = System.currentTimeMillis().toString()
        val input = buvid + tsMs
        val md = MessageDigest.getInstance("MD5")
        return md.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
