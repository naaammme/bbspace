package com.naaammme.bbspace.infra.network.dns

import com.naaammme.bbspace.core.common.log.Logger

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
    private val SERVERS = listOf(
        "203.119.206.8",
        "203.119.206.9",
        "203.119.204.80",
        "203.119.238.240",
        "203.119.238.194",
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

    private fun resolveByServer(server: String, hostname: String): DnsResult? {
        return try {
            val url = "http://$server/$ACCOUNT_ID/resolve?host=$hostname&query=4,6"
            parseDnsResultJson(httpDnsGet(url))
        } catch (e: Exception) {
            Logger.d(TAG) { "Alibaba HTTPDNS server $server failed for $hostname: ${e.message}" }
            null
        }
    }
}
