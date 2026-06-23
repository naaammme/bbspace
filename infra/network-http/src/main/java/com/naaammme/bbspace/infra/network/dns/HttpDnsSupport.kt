package com.naaammme.bbspace.infra.network.dns

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL

// 2s 超时的 HTTPDNS GET, 失败抛异常由调用方记录
internal fun httpDnsGet(url: String, timeoutMs: Int = 2000): String {
    var conn: HttpURLConnection? = null
    try {
        conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            requestMethod = "GET"
        }
        return BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
    } finally {
        conn?.disconnect()
    }
}

// 解析 {dns:[{ttl,ips:[...]}]} 明文响应, 阿里和 B 站自建 HTTPDNS 共用
internal fun parseDnsResultJson(response: String): DnsResult? {
    val dnsArray = JSONObject(response).optJSONArray("dns") ?: return null
    val addresses = mutableListOf<InetAddress>()
    var minTtl = Long.MAX_VALUE
    for (i in 0 until dnsArray.length()) {
        val entry = dnsArray.getJSONObject(i)
        val ttl = entry.optLong("ttl", 60L)
        if (ttl < minTtl) minTtl = ttl
        val ips = entry.optJSONArray("ips") ?: continue
        for (j in 0 until ips.length()) {
            try {
                addresses.add(InetAddress.getByName(ips.getString(j)))
            } catch (_: Exception) {
            }
        }
    }
    val distinct = addresses.distinct()
    if (distinct.isEmpty()) return null
    val ttl = if (minTtl == Long.MAX_VALUE) 60L else minTtl
    return DnsResult(distinct, ttl)
}
