package com.naaammme.bbspace.infra.grpc

import com.google.protobuf.MessageLite
import com.google.protobuf.Parser
import com.naaammme.bbspace.infra.network.BiliApiException
import com.naaammme.bbspace.core.common.BiliConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * B站 gRPC 客户端
 * 使用 HTTP/1.1 POST 发送 gRPC 请求（B站不使用标准 gRPC over HTTP/2）
 */
@Singleton
class BiliGrpcClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val grpcHeaderBuilder: GrpcHeaderBuilder
) {
    /**
     * 发送 gRPC 请求
     *
     * @param endpoint gRPC 服务路径，如 "bilibili.api.ticket.v1.Ticket/GetTicket"
     * @param requestBytes protobuf 序列化后的请求数据 (request.toByteArray())
     * @param parser protobuf 响应解析器 (Response.parser())
     * @param extraDeviceBin 自定义 device-bin（不传则使用默认）
     * @return 解析后的 protobuf 响应对象
     */
    suspend fun <Resp : MessageLite> call(
        endpoint: String,
        requestBytes: ByteArray,
        parser: Parser<Resp>,
        extraDeviceBin: ByteArray? = null
    ): Resp {
        // 编码 根据 payload 大小自动决定是否 gzip 压缩
        val encodeResult = GrpcFrameCodec.encode(requestBytes)

        // 构建 header编码相关的 header 取决于是否压缩
        val headers = if (extraDeviceBin != null) {
            grpcHeaderBuilder.build(deviceBin = extraDeviceBin, compressed = encodeResult.compressed)
        } else {
            grpcHeaderBuilder.build(compressed = encodeResult.compressed)
        }

        val requestBuilder = Request.Builder()
            .url("${BiliConstants.BASE_URL_APP}/$endpoint")
            .post(encodeResult.frame.toRequestBody(null))

        headers.forEach { (key, value) -> requestBuilder.addHeader(key, value) }

        val response = withContext(Dispatchers.IO) {
            okHttpClient.newCall(requestBuilder.build()).execute()
        }

        if (response.code != 200) {
            throw BiliApiException(response.code, "HTTP ${response.code}: ${response.message}")
        }

        val responseBytes = response.body?.bytes()
            ?: throw BiliApiException(-1, "Empty gRPC response")

        // 解码 检查压缩标志 + 响应头 grpc-encoding
        val grpcEncoding = response.header("grpc-encoding")
        val payload = GrpcFrameCodec.decode(responseBytes, grpcEncoding)
        return parser.parseFrom(payload)
    }
}
