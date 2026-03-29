package com.naaammme.bbspace.infra.crypto

import java.util.Random

/**
 * 生成 x-bili-trace-id
 * 格式：{32位hex}:{后16位hex}:0:0
 *
 * 算法：从 APK 逆向得到的原始实现
 * - 前 13 字节随机
 * - 后 3 字节（index 13-15）嵌入时间戳的高 3 字节（跳过最低字节）
 */
object TraceIdGenerator {

    /**
     * 生成 32 位 hex 字符串
     * 前 13 字节随机，后 3 字节嵌入时间戳（跳过最低字节）
     */
    private fun generateTraceBase(): String {
        val bArr = ByteArray(16)
        Random().nextBytes(bArr)

        var ts = (System.currentTimeMillis() / 1000).toInt()

        // 从 index 15 倒着到 13，嵌入时间戳的高 3 字节
        for (i in 15 downTo 13) {
            ts = ts shr 8  // 先右移 8 位
            bArr[i] = ts.toByte()  // 再赋值
        }
        // 结果：
        // bArr[13] = (原始ts >> 24) & 0xFF  (最高字节)
        // bArr[14] = (原始ts >> 16) & 0xFF
        // bArr[15] = (原始ts >> 8) & 0xFF   (跳过了最低字节)

        return bArr.toHexString()
    }

    /**
     * 生成完整的 trace-id
     * 格式: {32位hex}:{后16位hex}:0:0
     */
    fun generate(): String {
        val hex32 = generateTraceBase()
        return "$hex32:${hex32.substring(16)}:0:0"
    }

    // 字节数组转 hex 字符串
    private fun ByteArray.toHexString(): String {
        return joinToString("") { "%02x".format(it) }
    }
}
