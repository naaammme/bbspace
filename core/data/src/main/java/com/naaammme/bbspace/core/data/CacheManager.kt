package com.naaammme.bbspace.core.data

import android.content.Context
import com.naaammme.bbspace.infra.crypto.RegionCodeCache
import com.naaammme.bbspace.core.common.log.Logger
import com.naaammme.bbspace.infra.coldstart.ColdStartClient
import com.naaammme.bbspace.infra.crypto.DeviceIdentity
import com.naaammme.bbspace.infra.crypto.GuestIdGenerator
import com.naaammme.bbspace.infra.crypto.TicketGenerator
import com.naaammme.bbspace.infra.network.dns.BiliDns
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CacheManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val ticketGenerator: TicketGenerator,
    private val guestIdGenerator: GuestIdGenerator,
    private val regionCodeCache: RegionCodeCache,
    private val biliDns: BiliDns,
    private val coldStartClient: ColdStartClient,
    private val deviceIdentity: DeviceIdentity
) {
    companion object {
        private const val TAG = "CacheManager"
    }

    // ── 会话信息（转发到 GuestIdGenerator）──

    val guestId: String get() = guestIdGenerator.getCachedGuestId()
    val sessionId: String get() = guestIdGenerator.getCachedSessionId()
    val loginSessionId: String get() = guestIdGenerator.getCachedLoginSessionId()

    fun generateSessionId(): String = guestIdGenerator.generateNewSessionId()
    fun generateLoginSessionId(): String = guestIdGenerator.generateNewLoginSessionId()

    fun saveSession(guestId: String, sessionId: String, loginSessionId: String) {
        guestIdGenerator.saveSession(guestId, sessionId, loginSessionId)
    }

    fun clearSession() {
        guestIdGenerator.clearCache()
        Logger.d(TAG) { "会话信息已清除" }
    }

    // ── 设备信息 ──

    fun getDeviceInfo(): Map<String, String> = mapOf(
        "brand" to deviceIdentity.brand,
        "model" to deviceIdentity.model,
        "osVer" to deviceIdentity.osVer,
        "device" to deviceIdentity.device,
        "manufacturer" to deviceIdentity.manufacturer,
        "buildId" to deviceIdentity.buildId,
        "buildFingerprint" to deviceIdentity.buildFingerprint,
        "buvid" to deviceIdentity.buvid,
        "fp" to deviceIdentity.fp,
        "mac" to deviceIdentity.mac,
        "androidId" to deviceIdentity.androidId,
        "drmId" to DeviceIdentity.getDrmId()
    )

    // 缓存清理

    fun clearTicketCache() {
        ticketGenerator.clearCache()
        Logger.d(TAG) { "Ticket 缓存已清除" }
    }

    fun clearGuestCache() {
        guestIdGenerator.clearCache()
        Logger.d(TAG) { "Guest 缓存已清除" }
    }

    fun clearRegionCache() {
        regionCodeCache.clear()
        Logger.d(TAG) { "地区码缓存已清除" }
    }

    fun clearDnsCache() {
        biliDns.clearCache()
        Logger.d(TAG) { "DNS 缓存已清除" }
    }

    fun clearColdStartCache() {
        coldStartClient.clearCache()
        Logger.d(TAG) { "冷启动缓存已清除" }
    }

    fun clearImageCache() {
        val imageLoader = coil3.SingletonImageLoader.get(context)
        imageLoader.memoryCache?.clear()
        imageLoader.diskCache?.clear()
        Logger.d(TAG) { "图片缓存已清除" }
    }

    fun clearAllCache() {
        clearSession()
        clearTicketCache()
        clearGuestCache()
        clearRegionCache()
        clearDnsCache()
        clearColdStartCache()
        clearImageCache()
        Logger.d(TAG) { "所有缓存已清除" }
    }

    fun getCachedTicket(): String = ticketGenerator.getCachedTicket()

    fun getRegionCode(): String = regionCodeCache.get()
}
