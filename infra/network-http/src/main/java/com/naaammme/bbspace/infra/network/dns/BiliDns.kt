package com.naaammme.bbspace.infra.network.dns

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.telephony.TelephonyManager
import com.naaammme.bbspace.core.common.log.Logger
import okhttp3.Dns
import java.net.Inet4Address
import java.net.InetAddress
import java.net.Inet6Address
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * B站 HTTPDNS 加速
 *
 * B站业务域名
 *   B站运营商 HTTPDNS → 阿里 HTTPDNS → 腾讯 HTTPDNS → 系统 DNS
 *
 * 视频 CDN 域名
 *   阿里 HTTPDNS → 系统 DNS
 *
 * 其他域名
 *   系统 DNS
 */
class BiliDns(
    context: Context
) : Dns {
    companion object {
        private const val TAG = "BiliDns"
        private const val FALLBACK_TTL_MS = 60_000L // 兜底 60 秒
        private const val MIN_HTTPDNS_TTL_MS = 120_000L // 业务域名本地最小 TTL，降低热域名刷新频率
        private const val NO_NETWORK_HANDLE = -1L

        // 启动时主动预热的高频业务域名
        private val PREFETCH_DOMAINS = setOf(
            "app.bilibili.com",
            "api.bilibili.com",
            "api.live.bilibili.com",
        )

        // 视频 CDN 域名后缀
        private val VIDEO_CDN_SUFFIXES = listOf(
            ".bilivideo.com",
            ".bilivideo.cn",
            ".akamaized.net",
        )

        // 业务接口 / 图片静态资源域名后缀
        private val HTTPDNS_SUFFIXES = listOf(
            ".bilibili.com",
            ".bilibili.cn",
            ".hdslb.com",
            ".hdslb.net",
        )
    }

    private enum class DomainType { HTTPDNS, VIDEO_CDN, OTHER }

    private data class CacheEntry(
        val addresses: List<InetAddress>,
        val expireAt: Long
    )

    // 当前网络句柄、运营商码和 IPv6 能力,懒解析避免构造时在主线程做 IPC
    private data class NetworkState(
        val networkHandle: Long,
        val operatorCode: String?,
        val hasIpv6: Boolean,
    )

    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val refreshing = ConcurrentHashMap.newKeySet<String>()
    private val refreshExecutor: ExecutorService = Executors.newFixedThreadPool(3) { r ->
        Thread(r, "bili-dns-refresh").apply { isDaemon = true }
    }
    private val appContext = context.applicationContext
    private val connectivityManager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            refreshExecutor.execute {
                handleNetworkChanged(network)
            }
        }
    }

    @Volatile
    private var started = false

    @Volatile
    private var networkChanged: (() -> Unit)? = null

    @Volatile
    private var networkState: NetworkState? = null

    /**
     * 启动 DNS 预热和网络监听
     */
    @SuppressLint("MissingPermission")
    fun start(onNetworkChanged: (() -> Unit)? = null) {
        networkChanged = onNetworkChanged
        if (started) return
        started = true
        prefetch()
        connectivityManager?.registerDefaultNetworkCallback(networkCallback)
    }

    // 懒解析,首次访问时在调用线程完成 IPC
    @SuppressLint("MissingPermission")
    private fun currentNetworkState(): NetworkState {
        networkState?.let { return it }
        val resolved = buildNetworkState(connectivityManager?.activeNetwork, connectivityManager)
        networkState = resolved
        return resolved
    }

    /**
     * 手动清空缓存
     */
    fun clearCache() {
        cache.clear()
        refreshing.clear()
        Logger.d(TAG) { "DNS cache cleared" }
    }

    private fun handleNetworkChanged(network: Network) {
        val newNetworkHandle = network.networkHandle
        if (newNetworkHandle == networkState?.networkHandle) return
        // 同步切状态:立即更新 handle + 清缓存,确保切网后首批请求不命中旧缓存
        // 运营商码和 IPv6 能力先用保守值,后台异步补齐,避免在回调线程做网络 IPC
        networkState = NetworkState(newNetworkHandle, operatorCode = null, hasIpv6 = false)
        cache.clear()
        refreshing.clear()
        Logger.d(TAG) { "Network changed, handle=$newNetworkHandle, cache cleared, network state pending" }
        // 异步补网络状态并预热,带 handle 校验,网络再变则旧任务直接丢弃,避免状态回退
        if (networkState?.networkHandle != newNetworkHandle) return
        val resolved = buildNetworkState(network)
        if (networkState?.networkHandle != newNetworkHandle) return
        networkState = resolved
        prefetch(resolved)
        networkChanged?.invoke()
    }

    /**
     * 预取 API 域名 DNS，启动时后台调用
     *
     * 整体在 refreshExecutor 上执行,确保 currentNetworkState() 的
     * ConnectivityManager/TelephonyManager IPC 不落到主线程
     * (start 在主线程调用本方法)
     */
    private fun prefetch() {
        refreshExecutor.execute {
            prefetch(currentNetworkState())
        }
    }

    private fun prefetch(state: NetworkState) {
        Logger.d(TAG) { "DNS prefetch start operator=${state.operatorCode}, ipv6=${state.hasIpv6}" }
        for (host in PREFETCH_DOMAINS) {
            refreshExecutor.execute {
                try {
                    if (cache.containsKey(host)) return@execute
                    val result = queryHttpDns(host, state.operatorCode)
                    if (result != null) {
                        cacheResult(host, result.addresses, result.ttlSeconds, state.networkHandle)
                        Logger.d(TAG) { "Prefetched $host: ${orderAddresses(result.addresses, state.hasIpv6)}" }
                    }
                } catch (e: Exception) {
                    Logger.w(TAG) { "Prefetch failed for $host: ${e.message}" }
                }
            }
        }
    }

    override fun lookup(hostname: String): List<InetAddress> {
        return when (classifyDomain(hostname)) {
            DomainType.HTTPDNS -> lookupHttpDns(hostname)
            DomainType.VIDEO_CDN -> lookupVideoCdn(hostname)
            DomainType.OTHER -> Dns.SYSTEM.lookup(hostname)
        }
    }

    /**
     * B站业务域名：Bili → 阿里 → 腾讯 → 系统 DNS
     */
    private fun lookupHttpDns(hostname: String): List<InetAddress> {
        val state = currentNetworkState()
        cachedOrNull(hostname, state.hasIpv6)?.let { return it }

        queryHttpDns(hostname, state.operatorCode)?.let { result ->
            val ordered = orderAddresses(result.addresses, state.hasIpv6)
            Logger.d(TAG) { "HTTPDNS resolved $hostname: $ordered (ttl=${result.ttlSeconds}s)" }
            cacheResult(hostname, result.addresses, result.ttlSeconds, state.networkHandle)
            return ordered
        }

        Logger.d(TAG) { "Falling back to system DNS for $hostname" }
        return Dns.SYSTEM.lookup(hostname).distinct()
    }

    /**
     * 视频 CDN 域名：阿里 → 系统 DNS
     */
    private fun lookupVideoCdn(hostname: String): List<InetAddress> {
        val state = currentNetworkState()
        cachedOrNull(hostname, state.hasIpv6)?.let { return it }

        queryVideoHttpDns(hostname)?.let { result ->
            val ordered = orderAddresses(result.addresses, state.hasIpv6)
            Logger.d(TAG) { "HTTPDNS resolved video CDN $hostname: $ordered (ttl=${result.ttlSeconds}s)" }
            cacheResult(hostname, result.addresses, result.ttlSeconds, state.networkHandle)
            return ordered
        }

        Logger.d(TAG) { "Falling back to system DNS for video CDN $hostname" }
        return Dns.SYSTEM.lookup(hostname).distinct()
    }

    private fun classifyDomain(hostname: String): DomainType {
        if (HTTPDNS_SUFFIXES.any { hostname.endsWith(it) }) return DomainType.HTTPDNS
        if (VIDEO_CDN_SUFFIXES.any { hostname.endsWith(it) }) return DomainType.VIDEO_CDN
        return DomainType.OTHER
    }

    private fun cachedOrNull(hostname: String, hasIpv6: Boolean): List<InetAddress>? {
        val entry = cache[hostname] ?: return null
        val now = System.currentTimeMillis()
        val ordered = orderAddresses(entry.addresses, hasIpv6)
        if (now < entry.expireAt) {
            Logger.d(TAG) { "Cache hit for $hostname: $ordered" }
            return ordered
        }
        // 过期了，返回旧值，后台异步刷新
        scheduleRefresh(hostname)
        Logger.d(TAG) { "Cache stale for $hostname, returning old and refreshing async" }
        return ordered
    }

    private fun scheduleRefresh(hostname: String) {
        if (!refreshing.add(hostname)) return // 已在刷新中
        val state = currentNetworkState()
        refreshExecutor.execute {
            try {
                val result = when (classifyDomain(hostname)) {
                    DomainType.HTTPDNS -> queryHttpDns(hostname, state.operatorCode)
                    DomainType.VIDEO_CDN -> queryVideoHttpDns(hostname)
                    DomainType.OTHER -> null
                }
                if (result != null) {
                    cacheResult(hostname, result.addresses, result.ttlSeconds, state.networkHandle)
                    Logger.d(TAG) { "Async refresh done for $hostname: ${orderAddresses(result.addresses, state.hasIpv6)}" }
                } else if (state.networkHandle == currentNetworkState().networkHandle) {
                    // 刷新失败且网络未变,顺延 TTL 保留旧值,避免反复同步阻塞
                    val old = cache[hostname]
                    if (old != null) {
                        cache[hostname] = old.copy(expireAt = System.currentTimeMillis() + FALLBACK_TTL_MS)
                    }
                }
            } catch (e: Exception) {
                Logger.w(TAG) { "Async refresh failed for $hostname: ${e.message}" }
            } finally {
                refreshing.remove(hostname)
            }
        }
    }

    private fun cacheResult(
        hostname: String,
        addresses: List<InetAddress>,
        ttlSeconds: Long,
        networkHandle: Long
    ) {
        if (networkHandle != currentNetworkState().networkHandle) return
        val remoteTtlMs = if (ttlSeconds > 0) ttlSeconds * 1000 else FALLBACK_TTL_MS
        val ttlMs = when (classifyDomain(hostname)) {
            DomainType.HTTPDNS -> maxOf(remoteTtlMs, MIN_HTTPDNS_TTL_MS)
            DomainType.VIDEO_CDN,
            DomainType.OTHER -> remoteTtlMs
        }
        cache[hostname] = CacheEntry(
            addresses = addresses,
            expireAt = System.currentTimeMillis() + ttlMs
        )
    }

    private fun orderAddresses(addresses: List<InetAddress>, hasIpv6: Boolean): List<InetAddress> {
        if (hasIpv6) return addresses
        val ipv4 = addresses.filterIsInstance<Inet4Address>()
        if (ipv4.isEmpty() || ipv4.size == addresses.size) return addresses
        return ipv4 + addresses.filterNot { it is Inet4Address }
    }

    private fun queryHttpDns(hostname: String, operatorCode: String?): DnsResult? {
        return BiliHttpDns.resolve(hostname, operatorCode)
            ?: AlibabaHttpDns.resolve(hostname)
            ?: TencentHttpDns.resolve(hostname)
    }

    private fun queryVideoHttpDns(hostname: String): DnsResult? {
        return AlibabaHttpDns.resolve(hostname)
    }

    @SuppressLint("MissingPermission")
    private fun buildNetworkState(
        network: Network?,
        cm: ConnectivityManager? = connectivityManager
    ): NetworkState {
        if (network == null || cm == null) {
            return NetworkState(NO_NETWORK_HANDLE, operatorCode = null, hasIpv6 = false)
        }
        return NetworkState(
            networkHandle = network.networkHandle,
            operatorCode = readOperatorCode(cm, network),
            hasIpv6 = detectIpv6Capability(cm, network)
        )
    }

    // 只在蜂窝网络下取运营商码(mcc+mnc), WiFi 和无 SIM 返回 null, ISP 识别交给 BiliHttpDns
    @SuppressLint("MissingPermission")
    private fun readOperatorCode(cm: ConnectivityManager, network: Network): String? {
        val capabilities = cm.getNetworkCapabilities(network)
        if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) != true) return null

        val tm = appContext.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        return tm?.networkOperator?.takeIf { it.isNotBlank() }
            ?: tm?.simOperator?.takeIf { it.isNotBlank() }
    }

    @SuppressLint("MissingPermission")
    // 当前网络存在非链路本地 IPv6 地址时认为可优先保留 AAAA 顺序
    private fun detectIpv6Capability(cm: ConnectivityManager, network: Network): Boolean {
        val linkProperties = cm.getLinkProperties(network) ?: return false
        return linkProperties.linkAddresses.any { linkAddress ->
            val address = linkAddress.address
            address is Inet6Address &&
                !address.isAnyLocalAddress &&
                !address.isLoopbackAddress &&
                !address.isLinkLocalAddress
        }
    }
}
