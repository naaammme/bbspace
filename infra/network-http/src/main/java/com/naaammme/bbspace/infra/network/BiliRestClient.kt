package com.naaammme.bbspace.infra.network

import com.naaammme.bbspace.core.common.BiliConstants
import com.naaammme.bbspace.infra.crypto.AppSigner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * B站 RESTful API 客户端
 * 封装签名、Header 注入、请求执行、响应检查
 */
@Singleton
class BiliRestClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val headerBuilder: BiliHeaderBuilder
) {
    /**
     * 发送签名后的 POST 请求, 检查 code==0
     * @throws BiliApiException 当 API 返回非 0 code 时
     */
    suspend fun postSigned(
        url: String,
        params: Map<String, String>
    ): JSONObject {
        val json = postSignedRaw(url, params)
        val code = json.optInt("code", -1)
        if (code != 0) {
            throw BiliApiException(code, json.optString("message", "Unknown error"))
        }
        return json
    }

    /**
     * 发送 GET 请求（URL 已含完整 query，调用方自行签名），检查 code==0
     */
    suspend fun getUrl(fullUrl: String): JSONObject {
        val headers = headerBuilder.build()
        val requestBuilder = Request.Builder().url(fullUrl).get()
        headers.forEach { (key, value) -> requestBuilder.addHeader(key, value) }
        val response = withContext(Dispatchers.IO) {
            val resp = okHttpClient.newCall(requestBuilder.build()).execute()
            val body = resp.body?.string() ?: throw BiliApiException(-1, "Empty response")
            JSONObject(body)
        }
        val code = response.optInt("code", -1)
        if (code != 0) throw BiliApiException(code, response.optString("message", "Unknown error"))
        return response
    }

    /**
     * 发送签名后的 GET 请求, 检查 code==0
     * 参数拼接在 URL query string 中
     */
    suspend fun getSigned(
        url: String,
        params: Map<String, String>
    ): JSONObject {
        val signedQuery = AppSigner.sign(params)
        val fullUrl = "$url?$signedQuery"

        val headers = headerBuilder.build()

        val requestBuilder = Request.Builder().url(fullUrl).get()
        headers.forEach { (key, value) -> requestBuilder.addHeader(key, value) }

        val response = withContext(Dispatchers.IO) {
            okHttpClient.newCall(requestBuilder.build()).execute()
        }

        val json = withContext(Dispatchers.IO) {
            JSONObject(response.body?.string() ?: throw BiliApiException(-1, "Empty response"))
        }
        val code = json.optInt("code", -1)
        if (code != 0) {
            throw BiliApiException(code, json.optString("message", "Unknown error"))
        }
        return json
    }

    suspend fun postSignedHd(url: String, params: Map<String, String>): JSONObject {
        val json = postSignedRawHd(url, params)
        val code = json.optInt("code", -1)
        if (code != 0) throw BiliApiException(code, json.optString("message", "Unknown error"))
        return json
    }

    suspend fun postSignedRawHd(url: String, params: Map<String, String>): JSONObject {
        val signedBody = AppSigner.sign(params, BiliConstants.APP_KEY_HD, BiliConstants.APP_SEC_HD)
        val requestBody = signedBody.toRequestBody(null)
        val headers = headerBuilder.build()
        val requestBuilder = Request.Builder().url(url).post(requestBody)
        headers.forEach { (key, value) -> requestBuilder.addHeader(key, value) }
        return withContext(Dispatchers.IO) {
            val resp = okHttpClient.newCall(requestBuilder.build()).execute()
            JSONObject(resp.body?.string() ?: throw BiliApiException(-1, "Empty response"))
        }
    }

    suspend fun postSignedSms(url: String, params: Map<String, String>): JSONObject {
        val json = postSignedRawSms(url, params)
        val code = json.optInt("code", -1)
        if (code != 0) throw BiliApiException(code, json.optString("message", "Unknown error"))
        return json
    }

    suspend fun postSignedRawSms(url: String, params: Map<String, String>): JSONObject {
        val signedBody = AppSigner.sign(params, BiliConstants.APP_KEY_SMS, BiliConstants.APP_SEC_SMS)
        val requestBody = signedBody.toRequestBody(null)
        val headers = headerBuilder.build()
        val requestBuilder = Request.Builder().url(url).post(requestBody)
        headers.forEach { (key, value) -> requestBuilder.addHeader(key, value) }
        return withContext(Dispatchers.IO) {
            val resp = okHttpClient.newCall(requestBuilder.build()).execute()
            JSONObject(resp.body?.string() ?: throw BiliApiException(-1, "Empty response"))
        }
    }

    /**
     * 发送签名后的 POST 请求（不检查 code，原样返回 JSON）
     * 用于需要处理特殊 code 的场景（如轮询 86039/86090）
     */
    suspend fun postSignedRaw(
        url: String,
        params: Map<String, String>
    ): JSONObject {
        val signedBody = AppSigner.sign(params)
        val requestBody = signedBody.toRequestBody(null)

        val headers = headerBuilder.build()

        val requestBuilder = Request.Builder().url(url).post(requestBody)
        headers.forEach { (key, value) -> requestBuilder.addHeader(key, value) }

        return withContext(Dispatchers.IO) {
            val resp = okHttpClient.newCall(requestBuilder.build()).execute()
            JSONObject(resp.body?.string() ?: throw BiliApiException(-1, "Empty response"))
        }
    }
}
