package com.naaammme.bbspace.infra.crypto

import java.security.MessageDigest

/**
 * Wbi 签名计算器
 * 用于 B 站 Web 接口的签名验证
 */
object WbiSigner {
    /**
     * 计算 Wbi 签名
     * @param params 请求参数
     * @param imgKey 图片密钥
     * @param subKey 子密钥
     * @return 签名后的参数 Map
     */
    fun sign(params: Map<String, String>, imgKey: String, subKey: String): Map<String, String> {
        val mixinKey = getMixinKey(imgKey + subKey)
        val sortedParams = params.toSortedMap()
        val query = sortedParams.entries.joinToString("&") { "${it.key}=${it.value}" }
        val wbiSign = md5(query + mixinKey)

        return sortedParams.toMutableMap().apply {
            put("w_rid", wbiSign)
        }
    }

    private fun getMixinKey(orig: String): String {
        val mixinKeyEncTab = intArrayOf(
            46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35, 27, 43, 5, 49,
            33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13, 37, 48, 7, 16, 24, 55, 40,
            61, 26, 17, 0, 1, 60, 51, 30, 4, 22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11,
            36, 20, 34, 44, 52
        )
        return mixinKeyEncTab.take(32)
            .map { orig.getOrNull(it) ?: ' ' }
            .joinToString("")
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
