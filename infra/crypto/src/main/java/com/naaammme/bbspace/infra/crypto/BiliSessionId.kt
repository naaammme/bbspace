package com.naaammme.bbspace.infra.crypto

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import java.util.UUID
import kotlin.random.Random

object BiliSessionId {
    private val secureRandom = SecureRandom()

    fun header(): String {
        val bytes = ByteArray(4)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun view(buvid: String): String {
        val raw = buildString {
            append(buvid)
            append(System.currentTimeMillis())
            append(Random.nextInt(1_000_000))
        }
        return MessageDigest.getInstance("SHA-1")
            .digest(raw.toByteArray())
            .toHex()
    }

    fun polarisAction(): String {
        val value = UUID.randomUUID().toString().hashCode()
        return String.format(Locale.ROOT, "%08X", value)
    }

    private fun ByteArray.toHex(): String {
        val out = CharArray(size * 2)
        forEachIndexed { idx, byte ->
            val value = byte.toInt() and 0xff
            out[idx * 2] = HEX[value ushr 4]
            out[idx * 2 + 1] = HEX[value and 0x0f]
        }
        return String(out)
    }

    private val HEX = "0123456789abcdef".toCharArray()
}
