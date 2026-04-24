package com.naaammme.bbspace.infra.network

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
 *
 * 按显式传入的 profile 选择 appkey 和 appsec
 * 对参数做签名并按 GET/POST 的协议形态发送
 * 统一注入 Header 并检查 code
 *
 * 公参不在这里隐式补，调用方先通过参数构建器准备好业务参数
 */
@Singleton
class BiliRestClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val headerBuilder: BiliHeaderBuilder
) {
    /**
     * 发送签名后的 POST 请求并要求 code == 0
     *
     * POST 参数会被签名后直接写入请求体
     */
    suspend fun postSigned(
        url: String,
        params: Map<String, String>,
        profile: BiliRestProfile = BiliRestProfile.APP
    ): JSONObject {
        return requireSuccess(postSignedRaw(url, params, profile))
    }

    /**
     * 发送签名后的 POST 请求并读取指定响应头
     */
    suspend fun postSignedReadHeader(
        url: String,
        params: Map<String, String>,
        headerName: String,
        profile: BiliRestProfile = BiliRestProfile.APP
    ): Pair<JSONObject, String> {
        val signedBody = AppSigner.sign(params, profile.appKey, profile.appSec)
        val requestBody = signedBody.toRequestBody(null)
        return withContext(Dispatchers.IO) {
            val resp = okHttpClient.newCall(
                Request.Builder().url(url).post(requestBody).withHeaders().build()
            ).execute()
            val json = JSONObject(resp.body?.string() ?: throw BiliApiException(-1, "Empty response"))
            requireSuccess(json) to resp.header(headerName).orEmpty()
        }
    }

    /**
     * 发送签名后的 GET 请求并要求 code == 0
     *
     * GET 参数会被签名后直接拼进 URL query。
     */
    suspend fun getSigned(
        url: String,
        params: Map<String, String>,
        profile: BiliRestProfile = BiliRestProfile.APP
    ): JSONObject {
        val signedQuery = AppSigner.sign(params, profile.appKey, profile.appSec)
        val fullUrl = "$url?$signedQuery"
        return requireSuccess(executeJson(Request.Builder().url(fullUrl).get().withHeaders().build()))
    }

    /**
     * 发送签名后的 POST 请求但不检查业务 code
     *
     * 用于二维码轮询这类需要调用方自行处理特殊返回码的接口
     */
    suspend fun postSignedRaw(
        url: String,
        params: Map<String, String>,
        profile: BiliRestProfile = BiliRestProfile.APP
    ): JSONObject {
        val signedBody = AppSigner.sign(params, profile.appKey, profile.appSec)
        val requestBody = signedBody.toRequestBody(null)
        return executeJson(Request.Builder().url(url).post(requestBody).withHeaders().build())
    }

    /**
     * 检查 B站标准 JSON 响应的 code 字段
     */
    private fun requireSuccess(json: JSONObject): JSONObject {
        val code = json.optInt("code", -1)
        if (code != 0) {
            throw BiliApiException(code, json.optString("message", "Unknown error"))
        }
        return json
    }

    /**
     * 在 IO 线程执行请求并解析 JSON 响应体
     */
    private suspend fun executeJson(request: Request): JSONObject {
        return withContext(Dispatchers.IO) {
            val resp = okHttpClient.newCall(request).execute()
            JSONObject(resp.body?.string() ?: throw BiliApiException(-1, "Empty response"))
        }
    }

    /**
     * 统一附加 RESTful 请求 Header
     */
    private fun Request.Builder.withHeaders(): Request.Builder {
        headerBuilder.build().forEach { (key, value) -> addHeader(key, value) }
        return this
    }
}
