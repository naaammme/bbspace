package com.naaammme.bbspace.infra.network.dns

import com.naaammme.bbspace.core.common.log.Logger
import java.security.MessageDigest

/**
 * 官方 native bili_single_isp_services 的 Java 版实现, 来自 HttpDnsResolver
 *
 * bili provider 请求格式:
 *   http://<server>/resolve?host=<hostname>&sign=<md5>&query=4,6
 * sign = MD5(host + "*-Bili-dns^*-Http")
 *
 * ISP 语义的唯一 owner: 运营商码(mcc+mnc, 如 46001)到服务器池的映射在此完成,
 * 外部只需传运营商码, 不需要知道 ct/cu/cm 这些内部标识
 */
object BiliHttpDns {
    private const val TAG = "BiliHttpDns"
    private const val ISP_CT = "ct"
    private const val ISP_CU = "cu"
    private const val ISP_CM = "cm"
    private const val SIGN_SUFFIX = "*-Bili-dns^*-Http"

    private val DEFAULT_SERVERS = listOf(
        "116.63.10.135",
        "117.185.17.235",
    )

    private val CT_SERVERS = listOf(
        "122.9.13.79",
        "180.163.55.134",
    )

    private val CU_SERVERS = listOf(
        "116.63.10.31",
        "112.65.200.25",
    )

    private val CM_SERVERS = listOf(
        "117.185.18.212",
        "117.185.17.235",
    )

    fun resolve(hostname: String, operatorCode: String?): DnsResult? {
        val sign = computeSign(hostname)
        val servers = when (ispForOperator(operatorCode)) {
            ISP_CT -> CT_SERVERS
            ISP_CU -> CU_SERVERS
            ISP_CM -> CM_SERVERS
            else -> DEFAULT_SERVERS
        }
        for (server in servers) {
            resolveByServer(server, hostname, sign)?.let { return it }
        }
        Logger.d(TAG) { "Bili HTTPDNS resolve failed for $hostname on operator=$operatorCode" }
        return null
    }

    // 运营商码(mcc+mnc) → ISP 池标识, 非 Cellular 或未知返回 null 走默认池
    private fun ispForOperator(operator: String?): String? = when {
        operator == null -> null
        operator.startsWith("46003") || operator.startsWith("46005") || operator.startsWith("46011") -> ISP_CT
        operator.startsWith("46001") || operator.startsWith("46006") || operator.startsWith("46009") -> ISP_CU
        operator.startsWith("46000") || operator.startsWith("46002") || operator.startsWith("46004") ||
            operator.startsWith("46007") || operator.startsWith("46008") || operator.startsWith("46012") -> ISP_CM
        else -> null
    }

    private fun resolveByServer(server: String, hostname: String, sign: String): DnsResult? {
        return try {
            val url = "http://$server/resolve?host=$hostname&sign=$sign&query=4,6"
            parseDnsResultJson(httpDnsGet(url))
        } catch (e: Exception) {
            Logger.d(TAG) { "Bili HTTPDNS server $server failed for $hostname: ${e.message}" }
            null
        }
    }

    private fun computeSign(hostname: String): String {
        val digest = MessageDigest.getInstance("MD5")
        val bytes = digest.digest((hostname + SIGN_SUFFIX).toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
