package com.naaammme.bbspace.infra.network.dns

import com.naaammme.bbspace.core.common.log.Logger
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.URL

/**
 * 阿里 HTTPDNS
 * JSON 明文，支持 IPv4+IPv6
 *
 * 响应格式:
 * {
 *   "dns": [
 *     {"host": "...", "ips": ["1.2.3.4"], "type": 1,  "ttl": 51},
 *     {"host": "...", "ips": ["2409:..."], "type": 28, "ttl": 51}
 *   ]
 * }
 * type=1 是 IPv4 (A 记录)，type=28 是 IPv6 (AAAA 记录)
 */
object AlibabaHttpDns {
    private const val TAG = "AlibabaHttpDns"
    private const val ACCOUNT_ID = "191607"
    private const val TIMEOUT_MS = 2000
    private val SERVERS = listOf(
        "203.107.1.65",
        "203.107.1.34",
        "203.107.1.66",
        "203.107.1.33"
    )

    /**
     * 通过阿里 HTTPDNS 解析域名
     * @return DnsResult 包含 IP 列表和 TTL，失败返回 null
     */
    fun resolve(hostname: String): DnsResult? {
        for (server in SERVERS) {
            resolveByServer(server, hostname)?.let { return it }
        }
        Logger.w(TAG) { "Alibaba HTTPDNS resolve failed for $hostname" }
        return null
    }

    private fun resolveByServer(
        server: String,
        hostname: String
    ): DnsResult? {
        return try {
            val url = URL("http://$server/$ACCOUNT_ID/resolve?host=$hostname&query=4,6")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                requestMethod = "GET"
            }

            val responseText = try {
                BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
            } finally {
                conn.disconnect()
            }

            parseResponse(responseText)
        } catch (e: Exception) {
            Logger.d(TAG) { "Alibaba HTTPDNS server $server failed for $hostname: ${e.message}" }
            null
        }
    }

    private fun parseResponse(response: String): DnsResult? {
        val json = JSONObject(response)
        val dnsArray = json.optJSONArray("dns") ?: return null

        val ipv4 = mutableListOf<InetAddress>()
        val ipv6 = mutableListOf<InetAddress>()
        var minTtl = Long.MAX_VALUE

        for (i in 0 until dnsArray.length()) {
            val entry = dnsArray.getJSONObject(i)
            val ttl = entry.optLong("ttl", 60L)
            if (ttl < minTtl) minTtl = ttl

            val ips = entry.optJSONArray("ips") ?: continue
            val type = entry.optInt("type", 0)
            for (j in 0 until ips.length()) {
                try {
                    val address = InetAddress.getByName(ips.getString(j))
                    when {
                        type == 1 || address is Inet4Address -> ipv4.add(address)
                        else -> ipv6.add(address)
                    }
                } catch (_: Exception) {}
            }
        }

        val addresses = (ipv4 + ipv6).distinct()
        if (addresses.isEmpty()) return null
        val ttl = if (minTtl == Long.MAX_VALUE) 60L else minTtl
        return DnsResult(addresses, ttl)
    }
}
