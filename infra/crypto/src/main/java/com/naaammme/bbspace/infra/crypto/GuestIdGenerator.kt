package com.naaammme.bbspace.infra.crypto

import android.content.Context
import android.util.Base64
import com.naaammme.bbspace.core.common.BiliConstants
import com.naaammme.bbspace.core.common.UserAgentBuilder
import com.naaammme.bbspace.core.common.log.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

/**
 * B站 GuestId 生成器
 * 这里顺带复用 passport 侧 dt 加密逻辑，但职责仍然以 guestid 流程为主
 */
class GuestIdGenerator(
    private val context: Context,
    private val deviceIdentity: DeviceIdentity
) {
    companion object {
        private const val TAG = "GuestIdGenerator"
        private const val GET_KEY_ENDPOINT = "/x/passport-login/web/key"
        private const val GUEST_ID_ENDPOINT = "/x/passport-user/guest/reg"
        private const val MAX_ERROR_BODY_LOG_BYTES = 4_096L
        private const val AES_LETTER = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    }

    private var rsaPublicKey: String? = null
    @Volatile
    private var loginDeviceMetaCache: CachedLoginDeviceMeta? = null
    private val deviceInfoCollector = DeviceInfoCollector(context, deviceIdentity)

    private data class CachedLoginDeviceMeta(
        val sessionId: String,
        val payload: Pair<String, String>
    )

    suspend fun generateLoginDtAndDeviceMeta(loginSessionId: String): Pair<String, String>? {
        loginDeviceMetaCache?.takeIf { it.sessionId == loginSessionId }?.let { return it.payload }

        return withContext(Dispatchers.IO) {
            loginDeviceMetaCache?.takeIf { it.sessionId == loginSessionId }?.payload
                ?: generateDtAndEncryptedPayload(deviceInfoCollector.buildLoginDeviceMetaJson())
                    ?.also { loginDeviceMetaCache = CachedLoginDeviceMeta(loginSessionId, it) }
        }
    }

    suspend fun getGuestId(ticket: String = ""): String? {
        return try {
            Logger.d(TAG) { "开始获取 guestid" }

            val ts = (System.currentTimeMillis() / 1000).toString()
            val deviceInfoJson = deviceInfoCollector.buildGuestDeviceInfoJson()
            Logger.d(TAG) { "设备信息: $deviceInfoJson" }
            val payload = generateDtAndEncryptedPayload(deviceInfoJson) ?: run {
                Logger.e(TAG) { "生成 guest device_info 失败" }
                return null
            }
            val (dtValue, encryptedDeviceInfo) = payload
            Logger.d(TAG) { "dt (RSA Base64): ${dtValue.take(50)}..." }
            Logger.d(TAG) { "device_info (Hex): ${encryptedDeviceInfo.take(50)}..." }

            val params = mapOf(
                "build" to BiliConstants.BUILD_STR,
                "buvid" to deviceIdentity.buvid,
                "c_locale" to "zh-Hans_CN",
                "channel" to BiliConstants.CHANNEL,
                "device_info" to encryptedDeviceInfo,
                "disable_rcmd" to "0",
                "dt" to dtValue,
                "local_id" to deviceIdentity.buvid,
                "mobi_app" to BiliConstants.MOBI_APP,
                "platform" to BiliConstants.PLATFORM,
                "s_locale" to "zh-Hans_CN",
                "statistics" to BiliConstants.STATISTICS_JSON,
                "ts" to ts
            )

            val signedBody = AppSigner.sign(params)
            Logger.d(TAG) { "签名后的参数: ${signedBody.take(200)}..." }

            val requestBody = signedBody.toRequestBody(
                "application/x-www-form-urlencoded; charset=utf-8".toMediaTypeOrNull()
            )

            val url = "${BiliConstants.BASE_URL_PASSPORT}$GUEST_ID_ENDPOINT"
            val client = okhttp3.OkHttpClient()
            val request = okhttp3.Request.Builder()
                .url(url)
                .post(requestBody)
                .addHeader("fp_local", deviceIdentity.fp)
                .addHeader("fp_remote", deviceIdentity.fp)
                .addHeader("session_id", BiliSessionId.header())
                .addHeader("buvid", deviceIdentity.buvid)
                .addHeader("env", BiliConstants.ENV)
                .addHeader("app-key", BiliConstants.APP_KEY_NAME)
                .addHeader("user-agent", UserAgentBuilder.buildRestfulUserAgent(deviceIdentity.model, deviceIdentity.osVer))
                .addHeader("x-bili-trace-id", TraceIdGenerator.generate())
                .addHeader("x-bili-aurora-eid", "")
                .addHeader("x-bili-mid", "")
                .addHeader("x-bili-aurora-zone", "")
                .addHeader("x-bili-gaia-vtoken", "")
                .addHeader("x-bili-ticket", ticket)
                .addHeader("content-type", "application/x-www-form-urlencoded; charset=utf-8")
                .addHeader("accept-encoding", "gzip")
                .build()

            withContext(Dispatchers.IO) {
                val response = client.newCall(request).execute()
                Logger.d(TAG) { "服务器响应状态: ${response.code}" }

                if (response.code == 200) {
                    val json = JSONObject(response.body?.string() ?: "")
                    Logger.d(TAG) { "服务器响应: $json" }

                    if (json.getInt("code") == 0) {
                        val guestId = json.getJSONObject("data").getLong("guest_id").toString()
                        Logger.d(TAG) { "成功获取 guestid: $guestId" }
                        guestId
                    } else {
                        val code = json.optInt("code", -1)
                        val msg = json.optString("message")
                        Logger.e(TAG) { "API 返回错误: code=$code, message=$msg" }
                        null
                    }
                } else {
                    Logger.e(TAG) { "HTTP 错误: ${response.code}" }
                    if (Logger.isDebug) {
                        val bodyText = response.peekBody(MAX_ERROR_BODY_LOG_BYTES).string()
                        Logger.e(TAG) { "响应内容: $bodyText" }
                    }
                    null
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, e) { "获取 guestid 异常" }
            null
        }
    }
    
    /**
     * 获取或生成 guestid（永久缓存）
     * guestId 应该永久保存，除非重新安装应用
     */
    suspend fun getOrGenerateGuestId(ticket: String = ""): String {
        val prefs = context.getSharedPreferences("guest_info", Context.MODE_PRIVATE)
        val cached = prefs.getString("guest_id", null)

        if (!cached.isNullOrEmpty()) {
            Logger.d(TAG) { "使用缓存的 guestid: $cached" }
            return cached
        }

        val guestId = getGuestId(ticket)
        if (guestId != null) {
            prefs.edit()
                .putString("guest_id", guestId)
                .putLong("cache_time", System.currentTimeMillis())
                .apply()
            Logger.d(TAG) { "获取并缓存新的 guestid: $guestId" }
            return guestId
        }

        val fallback = System.currentTimeMillis().toString()
        Logger.w(TAG) { "获取 guestid 失败，使用备用值: $fallback" }
        return fallback
    }

    fun clearSession() {
        context.getSharedPreferences("guest_info", Context.MODE_PRIVATE).edit()
            .remove("session_id")
            .remove("login_session_id")
            .apply()
        loginDeviceMetaCache = null
    }

    fun getCachedGuestId(): String {
        return context.getSharedPreferences("guest_info", Context.MODE_PRIVATE)
            .getString("guest_id", "") ?: ""
    }

    fun getCachedSessionId(): String {
        return context.getSharedPreferences("guest_info", Context.MODE_PRIVATE)
            .getString("session_id", "") ?: ""
    }

    fun getCachedLoginSessionId(): String {
        return context.getSharedPreferences("guest_info", Context.MODE_PRIVATE)
            .getString("login_session_id", "") ?: ""
    }

    fun saveSession(guestId: String, sessionId: String, loginSessionId: String = "") {
        context.getSharedPreferences("guest_info", Context.MODE_PRIVATE).edit().apply {
            putString("guest_id", guestId)
            putString("session_id", sessionId)
            if (loginSessionId.isNotEmpty()) {
                putString("login_session_id", loginSessionId)
            }
            apply()
        }
    }

    fun generateNewSessionId(): String {
        return BiliSessionId.header()
    }

    fun generateNewLoginSessionId(): String {
        return java.util.UUID.randomUUID().toString().replace("-", "")
    }

    private suspend fun generateDtAndEncryptedPayload(payloadJson: String): Pair<String, String>? {
        return try {
            if (rsaPublicKey == null && !getRsaKey()) {
                Logger.e(TAG) { "无法获取 RSA 公钥" }
                return null
            }

            val (aesKey, encryptedPayload) = aesEncrypt(payloadJson.toByteArray(Charsets.UTF_8))
            val dtValue = rsaEncrypt(aesKey, rsaPublicKey!!) ?: run {
                Logger.e(TAG) { "RSA 加密失败" }
                return null
            }

            dtValue to encryptedPayload
        } catch (e: Exception) {
            Logger.e(TAG, e) { "生成 dt 和设备载荷失败" }
            null
        }
    }

    private suspend fun getRsaKey(): Boolean {
        return try {
            val url = "${BiliConstants.BASE_URL_PASSPORT}$GET_KEY_ENDPOINT"
            val client = okhttp3.OkHttpClient()
            val request = okhttp3.Request.Builder()
                .url(url)
                .get()
                .build()

            withContext(Dispatchers.IO) {
                val response = client.newCall(request).execute()
                if (response.code == 200) {
                    val json = JSONObject(response.body?.string() ?: "")
                    if (json.getInt("code") == 0) {
                        val data = json.getJSONObject("data")
                        val key = data.getString("key")
                        rsaPublicKey = key
                            .replace("-----BEGIN PUBLIC KEY-----\n", "")
                            .replace("\n-----END PUBLIC KEY-----\n", "")
                            .replace("\n", "")
                        Logger.d(TAG) { "成功获取 RSA 公钥" }
                        true
                    } else {
                        val msg = json.optString("message")
                        Logger.e(TAG) { "获取 RSA 密钥失败: $msg" }
                        false
                    }
                } else {
                    Logger.e(TAG) { "HTTP 错误: ${response.code}" }
                    false
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, e) { "获取 RSA 密钥异常" }
            false
        }
    }

    private fun aesEncrypt(plainBytes: ByteArray): Pair<String, String> {
        val key = generateRandomKey(16)
        val keyBytes = key.toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val secretKey = SecretKeySpec(keyBytes, "AES")
        val ivSpec = IvParameterSpec(keyBytes)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec)
        val encrypted = cipher.doFinal(plainBytes)
        return key to encrypted.joinToString("") { "%02X".format(it) }
    }

    private fun rsaEncrypt(plainText: String, publicKeyStr: String): String? {
        return try {
            val publicKeyBytes = Base64.decode(publicKeyStr, Base64.NO_WRAP)
            val keySpec = X509EncodedKeySpec(publicKeyBytes)
            val keyFactory = KeyFactory.getInstance("RSA")
            val publicKey = keyFactory.generatePublic(keySpec)
            val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
            cipher.init(Cipher.ENCRYPT_MODE, publicKey)
            Base64.encodeToString(cipher.doFinal(plainText.toByteArray(Charsets.UTF_8)), Base64.DEFAULT)
        } catch (e: Exception) {
            Logger.e(TAG, e) { "RSA 加密失败" }
            null
        }
    }

    private fun generateRandomKey(length: Int = 16): String {
        return (1..length)
            .map { AES_LETTER[Random.nextInt(AES_LETTER.length)] }
            .joinToString("")
    }
}
