package com.naaammme.bbspace

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.naaammme.bbspace.core.common.log.Logger
import com.naaammme.bbspace.infra.coldstart.ColdStartClient
import com.naaammme.bbspace.core.data.AppSettings
import com.naaammme.bbspace.core.data.CacheManager
import com.naaammme.bbspace.infra.grpc.GaiaReporter
import com.naaammme.bbspace.infra.crypto.BuvidFetcher
import com.naaammme.bbspace.infra.crypto.GuestIdGenerator
import com.naaammme.bbspace.infra.crypto.TicketGenerator
import com.naaammme.bbspace.infra.network.dns.BiliDns
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppInitializer @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val coldStartClient: ColdStartClient,
    private val ticketGenerator: TicketGenerator,
    private val buvidFetcher: BuvidFetcher,
    private val guestIdGenerator: GuestIdGenerator,
    private val biliDns: BiliDns,
    private val okHttpClient: OkHttpClient,
    private val gaiaReporter: GaiaReporter,
    private val appSettings: AppSettings,
    private val cacheManager: CacheManager
) {
    companion object {
        private const val TAG = "AppInitializer"
    }

    private val initScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var initialized = false

    fun initialize() {
        // DNS 预取，不依赖其他初始化，立即后台执行
        biliDns.prefetch()
        warmupImageConnections()
        registerNetworkCallback()

        initScope.launch {
            try {
                Logger.d(TAG) { "开始初始化应用..." }

                appSettings.themeConfig.first()

                val ticket = ticketGenerator.getValidTicket() ?: ticketGenerator.getCachedTicket()
                Logger.d(TAG) { "Ticket 初始化完成: ${if (ticket.isEmpty()) "空" else "已缓存"}" }

                val remoteBuvid = buvidFetcher.fetchAndUpdate()
                Logger.d(TAG) { "远程 buvid: ${remoteBuvid ?: "获取失败，使用本地 buvid"}" }

                val coldStartData = coldStartClient.getColdStartData()
                Logger.d(TAG) { "冷启动数据获取完成: IP=${coldStartData.ipInfo?.addr}, 免流规则=${coldStartData.freeFlowRules != null}" }

                val guestId = guestIdGenerator.getOrGenerateGuestId(ticket)
                Logger.d(TAG) { "GuestId 初始化完成: $guestId" }

                if (cacheManager.sessionId.isEmpty()) {
                    val sessionId = cacheManager.generateSessionId()
                    cacheManager.saveSession(guestId, sessionId, "")
                    Logger.d(TAG) { "SessionId 初始化完成: $sessionId" }
                }

                if (!appSettings.blockGaia.first()) {
                    gaiaReporter.reportIfNeeded()
                } else {
                    Logger.d(TAG) { "Gaia 上报已被用户禁用，跳过" }
                }

                initialized = true
                Logger.d(TAG) { "应用初始化完成" }
            } catch (e: Exception) {
                Logger.e(TAG, e) { "应用初始化失败" }
            }
        }
    }

    // 预建 TCP+TLS 连接到图片 CDN，避免首批图片等待握手
    private fun warmupImageConnections() {
        val hosts = listOf("i0.hdslb.com", "i1.hdslb.com", "i2.hdslb.com")
        initScope.launch {
            hosts.map { host ->
                async {
                    val req = Request.Builder()
                        .url("https://$host")
                        .head()
                        .build()
                    runCatching { okHttpClient.newCall(req).execute().close() }
                    Logger.d(TAG) { "连接预热完成: $host" }
                }
            }.forEach { it.await() }
        }
    }

    private fun registerNetworkCallback() {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (!initialized) return
                Logger.d(TAG) { "网络切换，清除缓存并重新获取" }
                cacheManager.clearDnsCache()
                cacheManager.clearColdStartCache()
                biliDns.prefetch()
                initScope.launch {
                    coldStartClient.getColdStartData()
                }
            }
        })
    }
}
