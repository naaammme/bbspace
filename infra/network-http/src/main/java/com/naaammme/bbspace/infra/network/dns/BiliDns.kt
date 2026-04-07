package com.naaammme.bbspace.infra.network.dns

import com.naaammme.bbspace.core.common.log.Logger
import okhttp3.Dns
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * B站 HTTPDNS 加速
 *
 * API / 图片域名
 *   腾讯 HTTPDNS → 阿里 HTTPDNS → 系统 DNS
 *
 * 视频 CDN 域名
 *   阿里 HTTPDNS → 系统 DNS（视频 CDN 只用阿里）
 */
class BiliDns : Dns {
    companion object {
        private const val TAG = "BiliDns"
        private const val FALLBACK_TTL_MS = 60_000L // 兜底 60 秒
        /// DNS白名单
        /// API + 图片/静态资源走腾讯+阿里(
        private val BILI_DOMAINS = setOf(
            "app.bilibili.com",
            "passport.bilibili.com",
            "api.bilibili.com",
            "cm.bilibili.com",
            "api.live.bilibili.com",
            "i0.hdslb.com",
            "i1.hdslb.com",
            "i2.hdslb.com",
            "s1.hdslb.com",
        )

        /// 视频 CDN 域名后缀只走阿里(
        private val VIDEO_CDN_SUFFIXES = listOf(
            ".bilivideo.com",
            ".bilivideo.cn",
            ".akamaized.net",
        )
    }

    private enum class DomainType { API, VIDEO_CDN, OTHER }

    private data class CacheEntry(
        val addresses: List<InetAddress>,
        val expireAt: Long
    )

    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val refreshing = ConcurrentHashMap.newKeySet<String>()
    private val refreshExecutor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "bili-dns-refresh").apply { isDaemon = true }
    }

    /**
     * 清空缓存，网络切换时调用
     */
    fun clearCache() {
        cache.clear()
        Logger.d(TAG) { "DNS cache cleared" }
    }

    /**
     * 预取 API 域名 DNS，启动时后台调用
     */
    fun prefetch() {
        refreshExecutor.execute {
            Logger.d(TAG) { "DNS prefetch start" }
            for (host in BILI_DOMAINS) {
                if (cache.containsKey(host)) continue
                try {
                    val result = TencentHttpDns.resolve(host)
                        ?: AlibabaHttpDns.resolve(host)
                    if (result != null) {
                        cacheResult(host, result.addresses, result.ttlSeconds)
                        Logger.d(TAG) { "Prefetched $host: ${result.addresses}" }
                    }
                } catch (e: Exception) {
                    Logger.w(TAG) { "Prefetch failed for $host: ${e.message}" }
                }
            }
            Logger.d(TAG) { "DNS prefetch done" }
        }
    }

    override fun lookup(hostname: String): List<InetAddress> {
        return when (classifyDomain(hostname)) {
            DomainType.API -> lookupApi(hostname)
            DomainType.VIDEO_CDN -> lookupVideoCdn(hostname)
            DomainType.OTHER -> Dns.SYSTEM.lookup(hostname)
        }
    }

    /**
     * API / 图片域名：腾讯 → 阿里 → 系统 DNS
     */
    private fun lookupApi(hostname: String): List<InetAddress> {
        cachedOrNull(hostname)?.let { return preferIpv4(it) }

        TencentHttpDns.resolve(hostname)?.let { result ->
            Logger.d(TAG) { "Tencent resolved $hostname: ${result.addresses} (ttl=${result.ttlSeconds}s)" }
            cacheResult(hostname, result.addresses, result.ttlSeconds)
            return result.addresses
        }

        AlibabaHttpDns.resolve(hostname)?.let { result ->
            Logger.d(TAG) { "Alibaba resolved $hostname: ${result.addresses} (ttl=${result.ttlSeconds}s)" }
            cacheResult(hostname, result.addresses, result.ttlSeconds)
            return result.addresses
        }

        Logger.d(TAG) { "Falling back to system DNS for $hostname" }
        return Dns.SYSTEM.lookup(hostname)
    }
    /**
     * 视频 CDN 域名：只走阿里 → 系统 DNS
     * 取第一个 IP 即可（阿里已按最优排序）
     */
    private fun lookupVideoCdn(hostname: String): List<InetAddress> {
        cachedOrNull(hostname)?.let { return it } // 缓存

        AlibabaHttpDns.resolve(hostname)?.let { result ->
            val addresses = preferIpv4(result.addresses)
            Logger.d(TAG) { "Alibaba resolved video CDN $hostname: $addresses (ttl=${result.ttlSeconds}s)" }
            cacheResult(hostname, addresses, result.ttlSeconds)
            return addresses
        }

        Logger.d(TAG) { "Falling back to system DNS for video CDN $hostname" }
        return preferIpv4(Dns.SYSTEM.lookup(hostname))
    }

    private fun classifyDomain(hostname: String): DomainType {
        if (hostname in BILI_DOMAINS) return DomainType.API
        if (VIDEO_CDN_SUFFIXES.any { hostname.endsWith(it) }) return DomainType.VIDEO_CDN
        return DomainType.OTHER
    }

    private fun cachedOrNull(hostname: String): List<InetAddress>? {
        val entry = cache[hostname] ?: return null
        val now = System.currentTimeMillis()
        if (now < entry.expireAt) {
            Logger.d(TAG) { "Cache hit for $hostname: ${entry.addresses}" }
            return entry.addresses
        }
        // 过期了，返回旧值，后台异步刷新
        scheduleRefresh(hostname)
        Logger.d(TAG) { "Cache stale for $hostname, returning old and refreshing async" }
        return entry.addresses
    }

    private fun scheduleRefresh(hostname: String) {
        if (!refreshing.add(hostname)) return // 已在刷新中
        refreshExecutor.execute {
            try {
                val result = when (classifyDomain(hostname)) {
                    DomainType.API -> TencentHttpDns.resolve(hostname)
                        ?: AlibabaHttpDns.resolve(hostname)
                    DomainType.VIDEO_CDN -> AlibabaHttpDns.resolve(hostname)?.let { dns ->
                        dns.copy(addresses = preferIpv4(dns.addresses))
                    }
                    DomainType.OTHER -> null
                }
                if (result != null) {
                    cacheResult(hostname, result.addresses, result.ttlSeconds)
                    Logger.d(TAG) { "Async refresh done for $hostname: ${result.addresses}" }
                } else {
                    cache.remove(hostname)
                }
            } catch (e: Exception) {
                Logger.w(TAG) { "Async refresh failed for $hostname: ${e.message}" }
            } finally {
                refreshing.remove(hostname)
            }
        }
    }

    private fun cacheResult(hostname: String, addresses: List<InetAddress>, ttlSeconds: Long) {
        val ttlMs = if (ttlSeconds > 0) ttlSeconds * 1000 else FALLBACK_TTL_MS
        cache[hostname] = CacheEntry(
            addresses = addresses,
            expireAt = System.currentTimeMillis() + ttlMs
        )
    }

    private fun preferIpv4(addresses: List<InetAddress>): List<InetAddress> {
        val ipv4 = addresses.filterIsInstance<Inet4Address>()
        val other = addresses.filterNot { it is Inet4Address }
        return (ipv4 + other).distinct()
    }
}
