package com.naaammme.bbspace.infra.grpc

import android.util.Base64
import com.naaammme.bbspace.core.common.AuthProvider
import com.naaammme.bbspace.core.common.BiliConstants
import com.naaammme.bbspace.core.common.UserAgentBuilder
import com.naaammme.bbspace.infra.crypto.RegionCodeCache
import com.naaammme.bbspace.infra.crypto.AuroraEidGenerator
import com.naaammme.bbspace.infra.crypto.DeviceIdentity
import com.naaammme.bbspace.infra.crypto.TicketGenerator
import com.naaammme.bbspace.infra.crypto.TraceIdGenerator
import com.naaammme.bbspace.infra.network.BiliMetadataBuilder
import javax.inject.Inject
import javax.inject.Singleton
/**
 * gRPC 请求 Header 构建器
 * 按照官方顺序构建,自动 Base64 编码所有 -bin 后缀的 header
 * 登录态从 AuthProvider 自动读取
 */
@Singleton
class GrpcHeaderBuilder @Inject constructor(
    private val authProvider: AuthProvider,
    private val deviceIdentity: DeviceIdentity,
    private val metadataBuilder: BiliMetadataBuilder,
    private val regionCodeCache: RegionCodeCache,
    private val ticketGenerator: TicketGenerator
) {
    fun build(
        deviceBin: ByteArray = metadataBuilder.buildDevice(),
        compressed: Boolean = false
    ): Map<String, String> {
        val mid = authProvider.mid
        val accessKey = authProvider.accessToken
        return buildMap {
            put("accept", "*/*")
            put("accept-encoding", "gzip, deflate, br")
            put("app-key", BiliConstants.MOBI_APP)
            if (accessKey.isNotEmpty()) {
                put("authorization", "identify_v1 $accessKey")
            }
            put("bili-http-engine", "ignet")
            put("buvid", deviceIdentity.buvid)
            put("content-type", "application/grpc")
            put("env", BiliConstants.ENV)
            if (compressed) {
                put("grpc-accept-encoding", "identity, gzip")
                put("grpc-encoding", "gzip")
            }
            put("user-agent", UserAgentBuilder.buildGrpcUserAgent(deviceIdentity.model, deviceIdentity.osVer))
            if (mid > 0) {
                val auroraEid = AuroraEidGenerator.generate(mid)
                if (auroraEid.isNotEmpty()) {
                    put("x-bili-aurora-eid", auroraEid)
                }
            }
            put("x-bili-device-bin", Base64.encodeToString(deviceBin, Base64.NO_WRAP or Base64.NO_PADDING))
            put("x-bili-fawkes-req-bin", Base64.encodeToString(metadataBuilder.buildFawkes(), Base64.NO_WRAP or Base64.NO_PADDING))
            put("x-bili-locale-bin", Base64.encodeToString(metadataBuilder.buildLocale(), Base64.NO_WRAP or Base64.NO_PADDING))
            put("x-bili-metadata-ip-region", regionCodeCache.get())
            put("x-bili-metadata-bin", Base64.encodeToString(metadataBuilder.buildMetadata(accessKey), Base64.NO_WRAP or Base64.NO_PADDING))
            if (mid > 0) {
                put("x-bili-mid", mid.toString())
            }
            put("x-bili-network-bin", Base64.encodeToString(metadataBuilder.buildNetwork(), Base64.NO_WRAP or Base64.NO_PADDING))
            val ticket = ticketGenerator.getCachedTicket()
            if (ticket.isNotEmpty()) {
                put("x-bili-ticket", ticket)
            }
            put("x-bili-trace-id", TraceIdGenerator.generate())
        }
    }
}
