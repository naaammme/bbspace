package com.naaammme.bbspace.infra.network

import android.util.Base64
import com.naaammme.bbspace.core.common.AuthProvider
import com.naaammme.bbspace.core.common.BiliConstants
import com.naaammme.bbspace.core.common.UserAgentBuilder
import com.naaammme.bbspace.infra.crypto.RegionCodeCache
import com.naaammme.bbspace.infra.crypto.AuroraEidGenerator
import com.naaammme.bbspace.infra.crypto.DeviceIdentity
import com.naaammme.bbspace.infra.crypto.GuestIdGenerator
import com.naaammme.bbspace.infra.crypto.LegalRegionCache
import com.naaammme.bbspace.infra.crypto.TicketGenerator
import com.naaammme.bbspace.infra.crypto.TraceIdGenerator
import javax.inject.Inject
import javax.inject.Singleton
/**
 * RESTful 请求公共 Header 构建器
 * 按照官方顺序构建
 * 登录态从 AuthProvider 自动读取
 */
@Singleton
class BiliHeaderBuilder @Inject constructor(
    private val authProvider: AuthProvider,
    private val deviceIdentity: DeviceIdentity,
    private val metadataBuilder: BiliMetadataBuilder,
    private val regionCodeCache: RegionCodeCache,
    private val legalRegionCache: LegalRegionCache,
    private val ticketGenerator: TicketGenerator,
    private val guestIdGenerator: GuestIdGenerator
) {
    fun build(): Map<String, String> {
        val mid = authProvider.mid
        return buildMap {
            put("accept", "*/*")
            put("app-key", BiliConstants.MOBI_APP)
            put("bili-http-engine", "ignet")
            put("buvid", deviceIdentity.buvid)
            put("content-type", "application/x-www-form-urlencoded; charset=utf-8")
            put("env", BiliConstants.ENV)
            put("fp_local", deviceIdentity.fp)
            put("fp_remote", deviceIdentity.fp)
            val guestId = guestIdGenerator.getCachedGuestId()
            if (guestId.isNotEmpty()) {
                put("guestid", guestId)
            }
            val sessionId = guestIdGenerator.getCachedSessionId()
            if (sessionId.isNotEmpty()) {
                put("session_id", sessionId)
            }
            put("user-agent", UserAgentBuilder.buildRestfulUserAgent(deviceIdentity.model, deviceIdentity.osVer))
            if (mid > 0) {
                put("x-bili-aurora-eid", AuroraEidGenerator.generate(mid))
            }
            put("x-bili-locale-bin", Base64.encodeToString(metadataBuilder.buildLocale(), Base64.NO_WRAP or Base64.NO_PADDING))
            put("x-bili-metadata-ip-region", regionCodeCache.get())
            val legalRegion = legalRegionCache.get()
            if (mid > 0 && legalRegion.isNotEmpty()) {
                put("x-bili-metadata-legal-region", legalRegion)
            }
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
