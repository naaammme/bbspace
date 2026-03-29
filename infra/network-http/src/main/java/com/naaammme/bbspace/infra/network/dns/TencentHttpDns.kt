package com.naaammme.bbspace.infra.network.dns

import com.naaammme.bbspace.core.common.log.Logger
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * 腾讯 HTTPDNS
 * DES ECB/PKCS5Padding 加解密
 *
 * 响应格式（解密后）: "ip1;ip2;ip3,TTL"
 * 例如: "192.254.90.178;148.153.64.18;148.153.56.163,36"
 * 最后一个逗号后面是 TTL（秒），不是 IP
 */
object TencentHttpDns {
    private const val TAG = "TencentHttpDns"
    private const val SERVER = "119.29.29.29"
    private const val ID = "3092"
    private const val DES_KEY = "LkgBm3xj"
    private const val TIMEOUT_MS = 2000

    /**
     * 通过腾讯 HTTPDNS 解析域名
     * @return DnsResult 包含 IP 列表和 TTL，失败返回 null
     */
    fun resolve(hostname: String): DnsResult? {
        return try {
            val encryptedDomain = desEncrypt(hostname)
            val url = URL("http://$SERVER/d?dn=$encryptedDomain&id=$ID&ttl=1")
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

            if (responseText.isBlank()) return null

            val decrypted = desDecrypt(responseText)
            parseResponse(decrypted)
        } catch (e: Exception) {
            Logger.w(TAG) { "Tencent HTTPDNS resolve failed for $hostname: ${e.message}" }
            null
        }
    }

    private fun desEncrypt(input: String): String {
        val cipher = Cipher.getInstance("DES/ECB/PKCS5Padding")
        val key = SecretKeySpec(DES_KEY.toByteArray(Charsets.UTF_8), "DES")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val encrypted = cipher.doFinal(input.toByteArray(Charsets.UTF_8))
        return bytesToHex(encrypted)
    }

    private fun desDecrypt(input: String): String {
        val cipher = Cipher.getInstance("DES/ECB/PKCS5Padding")
        val key = SecretKeySpec(DES_KEY.toByteArray(Charsets.UTF_8), "DES")
        cipher.init(Cipher.DECRYPT_MODE, key)
        val decrypted = cipher.doFinal(hexToBytes(input.trim()))
        return String(decrypted, Charsets.UTF_8)
    }

    /**
     * 解析格式: "ip1;ip2;ip3,TTL"
     * 逗号前是分号分隔的 IP 列表，逗号后是 TTL 秒数
     */
    private fun parseResponse(response: String): DnsResult? {
        // 先用逗号分离 IP 部分和 TTL
        val commaIndex = response.lastIndexOf(',')
        if (commaIndex < 0) return null

        val ipPart = response.substring(0, commaIndex)
        val ttl = response.substring(commaIndex + 1).trim().toLongOrNull() ?: 60L

        val addresses = ipPart.split(";")
            .filter { it.isNotBlank() }
            .mapNotNull { ip ->
                try {
                    InetAddress.getByName(ip.trim())
                } catch (e: Exception) {
                    null
                }
            }

        if (addresses.isEmpty()) return null
        return DnsResult(addresses, ttl)
    }

    private fun bytesToHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }

    private fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        for (i in 0 until len step 2) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
        }
        return data
    }
}
