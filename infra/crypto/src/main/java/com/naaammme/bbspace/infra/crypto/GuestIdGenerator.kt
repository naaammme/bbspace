package com.naaammme.bbspace.infra.crypto

import android.content.Context
import android.util.Base64
import com.naaammme.bbspace.core.common.log.Logger
import com.naaammme.bbspace.core.common.BiliConstants
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.KeyFactory
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

/**
 * B站 GuestId 生成器
 * 实现完整的 AES + RSA 加密流程
 */
class GuestIdGenerator(
    private val context: Context,
    private val deviceIdentity: DeviceIdentity
) {
    companion object {
        private const val TAG = "GuestIdGenerator"

        // 端点路径（就近定义，base URL 统一在 BiliConstants）
        private const val GET_KEY_ENDPOINT = "/x/passport-login/web/key"
        private const val GUEST_ID_ENDPOINT = "/x/passport-user/guest/reg"

        // AES 加密使用的字符集
        private const val AES_LETTER = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    }

    private var rsaPublicKey: String? = null
    private var keyHash: String? = null

    /**
     * 生成随机密钥
     */
    private fun generateRandomKey(length: Int = 16): String {
        return (1..length)
            .map { AES_LETTER[Random.nextInt(AES_LETTER.length)] }
            .joinToString("")
    }

    /**
     * 生成 session_id
     * 对应 DefaultApps 静态初始化块:
     * byte[] bArr = new byte[4];
     * new Random().nextBytes(bArr);
     * f102791m = ByteString.of(bArr, 0, 4).hex();
     *
     * @return 8 位十六进制字符串（小写）
     */
    private fun generateSessionId(): String {
        // 生成 4 个随机字节
        val randomBytes = ByteArray(4)
        SecureRandom().nextBytes(randomBytes)

        // 转换为十六进制字符串（小写）
        return randomBytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * 字节数组转十六进制大写字符串
     */
    private fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02X".format(it) }
    }

    /**
     * AES 加密
     * 使用 AES/CBC/PKCS5Padding 模式
     * IV 和 Key 相同
     *
     * @return Pair<密钥, 加密后的十六进制字符串>
     */
    private fun aesEncrypt(plainBytes: ByteArray): Pair<String, String> {
        // 生成 16 位随机密钥
        val key = generateRandomKey(16)
        val keyBytes = key.toByteArray(Charsets.UTF_8)

        // AES/CBC/PKCS5Padding 加密
        // IV 和 Key 相同
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val secretKey = SecretKeySpec(keyBytes, "AES")
        val ivSpec = IvParameterSpec(keyBytes)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec)

        val encrypted = cipher.doFinal(plainBytes)
        val hexStr = bytesToHex(encrypted) // 转换为十六进制大写字符串

        return Pair(key, hexStr)
    }

    /**
     * RSA 加密
     * 使用 RSA/ECB/PKCS1Padding 模式
     *
     * @param plainText 要加密的明文
     * @param publicKeyStr Base64 编码的公钥（不含 PEM 头尾）
     * @return Base64 编码的加密结果
     */
    private fun rsaEncrypt(plainText: String, publicKeyStr: String): String? {
        try {
            // 解码公钥
            val publicKeyBytes = Base64.decode(publicKeyStr, Base64.NO_WRAP)

            // 导入公钥
            val keySpec = X509EncodedKeySpec(publicKeyBytes)
            val keyFactory = KeyFactory.getInstance("RSA")
            val publicKey = keyFactory.generatePublic(keySpec)

            // RSA/ECB/PKCS1Padding 加密
            val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
            cipher.init(Cipher.ENCRYPT_MODE, publicKey)
            val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

            // Base64 编码flag=0 即 DEFAULT，会自动加换行符
            return Base64.encodeToString(encrypted, Base64.DEFAULT)
        } catch (e: Exception) {
            Logger.e(TAG, e) { "RSA 加密失败" }
            return null
        }
    }

    /**
     * 获取 RSA 公钥
     */
    private suspend fun getRsaKey(): Boolean {
        return try {
            val url = "${BiliConstants.BASE_URL_PASSPORT}$GET_KEY_ENDPOINT"
            val client = okhttp3.OkHttpClient()
            val request = okhttp3.Request.Builder()
                .url(url)
                .get()
                .build()

            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val response = client.newCall(request).execute()
                if (response.code == 200) {
                    val json = JSONObject(response.body?.string() ?: "")
                    if (json.getInt("code") == 0) {
                        val data = json.getJSONObject("data")
                        // 获取公钥，去除 PEM 头尾
                        val key = data.getString("key")
                        rsaPublicKey = key
                            .replace("-----BEGIN PUBLIC KEY-----\n", "")
                            .replace("\n-----END PUBLIC KEY-----\n", "")
                            .replace("\n", "")
                        keyHash = data.getString("hash")
                        Logger.d(TAG) { "成功获取 RSA 公钥" }
                        true
                    } else {
                        Logger.e(TAG) { "获取 RSA 密钥失败: ${json.getString("message")}" }
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

    /**
     * 生成设备信息参数
     */
    private fun generateDeviceInfo(appFirstRunTime: String): JSONObject {
        return JSONObject().apply {
            put("DeviceType", "Android")
            put("Buvid", deviceIdentity.buvid)
            put("fts", appFirstRunTime)
            put("BuildHost", "android-build")
            put("BuildDisplay", deviceIdentity.buildId)
            put("BuildFingerprint", deviceIdentity.buildFingerprint)
            put("BuildBrand", deviceIdentity.brand)

            if (deviceIdentity.mac.isNotEmpty()) {
                put("MAC", deviceIdentity.mac)
            }
            if (deviceIdentity.androidId.isNotEmpty()) {
                put("AndroidID", deviceIdentity.androidId)
            }
        }
    }

    /**
     * 生成 dt 和 device_info 参数对
     * 用于 qrcode/poll 和 guest/reg 接口
     *
     * @param deviceInfoJson 设备信息 JSON 字符串
     * @return Pair<dt, device_info>
     */
    suspend fun generateDtAndDeviceInfo(deviceInfoJson: String): Pair<String, String>? {
        return try {
            if (rsaPublicKey == null) {
                if (!getRsaKey()) {
                    Logger.e(TAG) { "无法获取 RSA 公钥" }
                    return null
                }
            }

            val (aesKey, encryptedDeviceInfo) = aesEncrypt(deviceInfoJson.toByteArray(Charsets.UTF_8))
            val dtValue = rsaEncrypt(aesKey, rsaPublicKey!!)

            if (dtValue == null) {
                Logger.e(TAG) { "RSA 加密失败" }
                return null
            }

            Pair(dtValue, encryptedDeviceInfo)
        } catch (e: Exception) {
            Logger.e(TAG, e) { "生成 dt 和 device_info 失败" }
            null
        }
    }

    /**
     * 获取 guestid - 完整加密实现
     *
     * @return guestid (13位数字字符串) 或 null
     */
    suspend fun getGuestId(ticket: String = ""): String? {
        return try {
            Logger.d(TAG) { "开始获取 guestid" }

            if (rsaPublicKey == null) {
                if (!getRsaKey()) {
                    Logger.e(TAG) { "无法获取 RSA 公钥" }
                    return null
                }
            }

            val appFirstRunTime = System.currentTimeMillis().toString()
            val deviceInfo = generateDeviceInfo(appFirstRunTime)

            val deviceInfoJson = deviceInfo.toString()
            Logger.d(TAG) { "设备信息: $deviceInfoJson" }

            val ts = (System.currentTimeMillis() / 1000).toString()

            val (aesKey, encryptedDeviceInfo) = aesEncrypt(deviceInfoJson.toByteArray(Charsets.UTF_8))
            Logger.d(TAG) { "AES Key: $aesKey" }
            Logger.d(TAG) { "Encrypted device_info (Hex): ${encryptedDeviceInfo.take(50)}..." }

            val dtValue = rsaEncrypt(aesKey, rsaPublicKey!!)
            if (dtValue == null) {
                Logger.e(TAG) { "RSA 加密失败" }
                return null
            }
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
                .addHeader("session_id", generateSessionId())
                .addHeader("buvid", deviceIdentity.buvid)
                .addHeader("env", BiliConstants.ENV)
                .addHeader("app-key", BiliConstants.MOBI_APP)
                .addHeader("user-agent", "Mozilla/5.0 BiliDroid/${BiliConstants.VERSION} (bbcallen@gmail.com) os/${BiliConstants.PLATFORM} model/${deviceIdentity.model} mobi_app/${BiliConstants.MOBI_APP} build/${BiliConstants.BUILD_STR} channel/${BiliConstants.CHANNEL} innerVer/${BiliConstants.BUILD_STR} osVer/${deviceIdentity.osVer} network/2")
                .addHeader("x-bili-trace-id", TraceIdGenerator.generate())
                .addHeader("x-bili-aurora-eid", "")
                .addHeader("x-bili-mid", "")
                .addHeader("x-bili-aurora-zone", "")
                .addHeader("x-bili-gaia-vtoken", "")
                .addHeader("x-bili-ticket", ticket)
                .addHeader("content-type", "application/x-www-form-urlencoded; charset=utf-8")
                .addHeader("accept-encoding", "gzip")
                .build()

            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
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
                        Logger.e(TAG) { "API 返回错误: code=${json.getInt("code")}, message=${json.getString("message")}" }
                        null
                    }
                } else {
                    Logger.e(TAG) { "HTTP 错误: ${response.code}" }
                    Logger.e(TAG) { "响应内容: ${response.body?.string()}" }
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

    fun clearCache() {
        context.getSharedPreferences("guest_info", Context.MODE_PRIVATE)
            .edit().clear().apply()
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
        return generateSessionId()
    }

    fun generateNewLoginSessionId(): String {
        return java.util.UUID.randomUUID().toString().replace("-", "")
    }
}
