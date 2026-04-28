package com.naaammme.bbspace.infra.crypto

import android.util.Base64
import bilibili.metadata.locale.LocaleOuterClass
import com.naaammme.bbspace.core.common.BiliConstants
import com.naaammme.bbspace.core.common.UserAgentBuilder
import com.naaammme.bbspace.core.common.log.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class BuvidFetcher(
    private val okHttpClient: OkHttpClient,
    private val deviceIdentity: DeviceIdentity
) {
    companion object {
        private const val TAG = "BuvidFetcher"
        private const val ENDPOINT = "/x/polymer/buvid/get"
    }

    suspend fun fetchAndUpdate(): String? {
        val cached = deviceIdentity.remoteBuvid
        if (cached.isNotEmpty()) return cached
        return doFetch()
    }

    private suspend fun doFetch(): String? = withContext(Dispatchers.IO) {
        val localBuvid = deviceIdentity.localBuvid
        val isFirst = deviceIdentity.isFirstInstall()

        val params = buildMap {
            put("androidId", deviceIdentity.androidId)
            put("brand", deviceIdentity.brand)
            put("build", BiliConstants.BUILD_STR)
            put("buvid", localBuvid)
            put("channel", BiliConstants.CHANNEL)
            put("drmId", DeviceIdentity.getDrmId())
            put("fawkesAppKey", BiliConstants.APP_KEY_NAME)
            put("first", if (isFirst) "1" else "0")
            put("firstStart", if (isFirst) "1" else "0")
            put("imei", "")
            put("internalVersionCode", BiliConstants.BUILD_STR)
            put("mac", deviceIdentity.mac)
            put("model", deviceIdentity.model)
            put("neuronAppId", "1")
            put("neuronPlatformId", "3")
            put("oaid", "")
            put("ts", (System.currentTimeMillis() / 1000).toString())
            put("versionCode", BiliConstants.BUILD_STR)
            put("versionName", BiliConstants.VERSION)
        }

        val signedBody = AppSigner.sign(params)
        val localeBytes = buildLocaleProtobuf()

        val request = Request.Builder()
            .url("${BiliConstants.BASE_URL_APP}$ENDPOINT")
            .post(signedBody.toRequestBody())
            .addHeader("accept", "*/*")
            .addHeader("app-key", BiliConstants.APP_KEY_NAME)
            .addHeader("bili-http-engine", "ignet")
            .addHeader("buvid", localBuvid)
            .addHeader("content-type", "application/x-www-form-urlencoded; charset=utf-8")
            .addHeader("env", BiliConstants.ENV)
            .addHeader("fp_local", deviceIdentity.fp)
            .addHeader("session_id", BiliSessionId.header())
            .addHeader("user-agent", UserAgentBuilder.buildRestfulUserAgent(deviceIdentity.model, deviceIdentity.osVer))
            .addHeader("x-bili-locale-bin", Base64.encodeToString(localeBytes, Base64.NO_WRAP or Base64.NO_PADDING))
            .addHeader("x-bili-redirect", "1")
            .addHeader("x-bili-trace-id", TraceIdGenerator.generate())
            .build()

        runCatching {
            val response = okHttpClient.newCall(request).execute()
            val body = response.body?.string() ?: return@runCatching null

            val json = JSONObject(body)
            if (json.optInt("code") != 0) {
                Logger.w(TAG) { "远程 buvid 获取失败: ${json.optString("message")}" }
                return@runCatching null
            }

            val data = json.optJSONObject("data") ?: return@runCatching null
            val remote = data.optString("buvid")
            if (remote.isEmpty()) {
                Logger.w(TAG) { "远程 buvid 为空" }
                return@runCatching null
            }

            Logger.i(TAG) { "远程 buvid: $remote 匹配: ${data.optString("match_device")} 类型: ${data.optString("device_type")}" }
            deviceIdentity.updateRemoteBuvid(remote)
            remote
        }.getOrElse { e ->
            Logger.e(TAG, e) { "远程 buvid 请求异常" }
            null
        }
    }

    private fun buildLocaleProtobuf(): ByteArray {
        val cLocale = LocaleOuterClass.LocaleIds.newBuilder()
            .setLanguage("zh")
            .setRegion("CN")
            .build()
        val sLocale = LocaleOuterClass.LocaleIds.newBuilder()
            .setLanguage("zh")
            .setRegion("CN")
            .build()

        return LocaleOuterClass.Locale.newBuilder().apply {
            this.cLocale = cLocale
            this.sLocale = sLocale
            timezone = ""
        }.build().toByteArray()
    }

}
