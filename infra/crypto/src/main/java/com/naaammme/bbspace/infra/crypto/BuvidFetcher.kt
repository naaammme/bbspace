package com.naaammme.bbspace.infra.crypto

import android.util.Base64
import com.naaammme.bbspace.core.common.BiliConstants
import com.naaammme.bbspace.core.common.log.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * 远程 buvid 获取器
 * 调用 /x/polymer/buvid/get 接口，用服务端返回的高权限 buvid 替换本地生成的
 */
class BuvidFetcher(
    private val okHttpClient: OkHttpClient,
    private val deviceIdentity: DeviceIdentity
) {
    companion object {
        private const val TAG = "BuvidFetcher"
        private const val ENDPOINT = "/x/polymer/buvid/get"
    }

    /**
     * 获取远程 buvid 并更新本地存储
     * 已有远程 buvid 时直接返回缓存值
     */
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
            put("fawkesAppKey", BiliConstants.MOBI_APP)
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
            .addHeader("app-key", BiliConstants.MOBI_APP)
            .addHeader("bili-http-engine", "ignet")
            .addHeader("buvid", localBuvid)
            .addHeader("content-type", "application/x-www-form-urlencoded; charset=utf-8")
            .addHeader("env", BiliConstants.ENV)
            .addHeader("fp_local", deviceIdentity.fp)
            .addHeader("session_id", genSessionId())
            .addHeader("user-agent", "Mozilla/5.0 BiliDroid/${BiliConstants.VERSION} (bbcallen@gmail.com) os/android model/${deviceIdentity.model} mobi_app/${BiliConstants.MOBI_APP} build/${BiliConstants.BUILD_STR} channel/${BiliConstants.CHANNEL} innerVer/${BiliConstants.BUILD_STR} osVer/${deviceIdentity.osVer} network/2")
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

            Logger.i(TAG) { "远程 buvid: $remote (匹配: ${data.optString("match_device")}, 类型: ${data.optString("device_type")})" }
            deviceIdentity.updateRemoteBuvid(remote)
            remote
        }.getOrElse { e ->
            Logger.e(TAG, e) { "远程 buvid 请求异常" }
            null
        }
    }

    private fun buildLocaleProtobuf(): ByteArray {
        return bilibili.metadata.locale.locale {
            cLocale = bilibili.metadata.locale.localeIds {
                language = "zh"
                region = "CN"
            }
            sLocale = bilibili.metadata.locale.localeIds {
                language = "zh"
                region = "CN"
            }
            timezone = ""
        }.toByteArray()
    }

    private fun genSessionId(): String {
        val bytes = ByteArray(4)
        kotlin.random.Random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
