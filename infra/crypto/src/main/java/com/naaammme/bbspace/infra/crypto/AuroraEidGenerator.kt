package com.naaammme.bbspace.infra.crypto

import android.util.Base64

/**
 * 生成 x-bili-aurora-eid
 * 算法：将 UID 字符串与密钥进行异或操作后 Base64 编码
 *
 * 从 APK 逆向得到的原始实现
 */
object AuroraEidGenerator {

    private const val XOR_KEY = "ad1va46a7lza"

    /**
     * 根据 UID 生成 aurora-eid
     * @param uid 用户 ID，<= 0 时返回空字符串
     * @return Base64 编码的 eid（无 padding），未登录时返回 ""
     */
    fun generate(uid: Long): String {
        if (uid <= 0) {
            return ""
        }

        val midStr = uid.toString()
        val bArr = ByteArray(midStr.length)

        for (i in midStr.indices) {
            bArr[i] = (midStr[i].code xor XOR_KEY[i % 12].code).toByte()
        }

        return Base64.encodeToString(bArr, Base64.NO_WRAP or Base64.NO_PADDING)
    }
}
