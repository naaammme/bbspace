package com.naaammme.bbspace.infra.network.dns

import com.naaammme.bbspace.core.common.log.Logger

/**
 * Cloudflare DoH
 * JSON 明文，只查询 A
 */
object CloudflareDoh {
    private const val TAG = "CloudflareDoh"
    private const val SERVER = "1.1.1.1"
    private val HEADERS = mapOf("accept" to "application/dns-json")

    fun resolve(hostname: String): DnsResult? {
        val result = resolveByType(hostname, "A")
        if (result == null) {
            Logger.w(TAG) { "Cloudflare DoH resolve failed for $hostname" }
            return null
        }
        return result
    }

    private fun resolveByType(hostname: String, type: String): DnsResult? {
        return try {
            val url = "https://$SERVER/dns-query?name=$hostname&type=$type"
            parseDohDnsResultJson(httpDnsGet(url, headers = HEADERS))
        } catch (e: Exception) {
            Logger.d(TAG) { "Cloudflare DoH type $type failed for $hostname: ${e.message}" }
            null
        }
    }
}
