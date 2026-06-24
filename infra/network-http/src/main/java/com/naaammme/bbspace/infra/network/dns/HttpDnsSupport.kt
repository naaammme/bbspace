package com.naaammme.bbspace.infra.network.dns

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL

// 2s 超时的 HTTPDNS/DoH GET, 失败抛异常由调用方记录
internal fun httpDnsGet(
    url: String,
    timeoutMs: Int = 1000,
    headers: Map<String, String> = emptyMap()
): String {
    var conn: HttpURLConnection? = null
    try {
        conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            requestMethod = "GET"
            headers.forEach { (name, value) ->
                setRequestProperty(name, value)
            }
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

// 解析 DoH JSON 响应，只收 A/AAAA 记录并取最小 TTL
internal fun parseDohDnsResultJson(response: String): DnsResult? {
    val json = JSONObject(response)
    if (json.optInt("Status", -1) != 0) return null
    val answers = json.optJSONArray("Answer") ?: return null
    val addresses = mutableListOf<InetAddress>()
    var minTtl = Long.MAX_VALUE
    for (i in 0 until answers.length()) {
        val entry = answers.getJSONObject(i)
        when (entry.optInt("type")) {
            1, 28 -> {
                val ttl = entry.optLong("TTL", 60L)
                if (ttl < minTtl) minTtl = ttl
                val ip = entry.optString("data")
                if (ip.isBlank()) continue
                try {
                    addresses.add(InetAddress.getByName(ip))
                } catch (_: Exception) {
                }
            }
        }
    }
    val distinct = addresses.distinct()
    if (distinct.isEmpty()) return null
    val ttl = if (minTtl == Long.MAX_VALUE) 60L else minTtl
    return DnsResult(distinct, ttl)
}
