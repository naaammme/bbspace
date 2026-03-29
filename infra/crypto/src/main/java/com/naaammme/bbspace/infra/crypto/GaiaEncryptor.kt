package com.naaammme.bbspace.infra.crypto

import android.util.Base64
import java.security.KeyFactory
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Gaia 风控加密器
 * RSA+AES 混合加密
 */
object GaiaEncryptor {

    private const val RSA_ALGORITHM = "RSA/ECB/PKCS1Padding"
    private const val AES_ALGORITHM = "AES/CBC/PKCS5Padding"
    private const val AES_KEY_SIZE = 128

    /**
     * 加密数据
     * @param data 明文数据
     * @param rsaPublicKeyPem RSA 公钥 PEM 格式
     * @return Pair(加密后的 AES 密钥, 加密后的数据)
     */
    fun encrypt(data: ByteArray, rsaPublicKeyPem: String): Pair<ByteArray, ByteArray> {
        val aesKey = generateAesKey()
        val encryptedData = aesEncrypt(data, aesKey)
        val encryptedAesKey = rsaEncrypt(aesKey.encoded, rsaPublicKeyPem)
        return Pair(encryptedAesKey, encryptedData)
    }

    private fun generateAesKey(): SecretKey {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(AES_KEY_SIZE, SecureRandom())
        return keyGen.generateKey()
    }

    private fun aesEncrypt(data: ByteArray, key: SecretKey): ByteArray {
        val cipher = Cipher.getInstance(AES_ALGORITHM)
        val iv = ByteArray(16)
        key.encoded.copyInto(iv, 0, 0, minOf(16, key.encoded.size))
        cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(iv))
        return cipher.doFinal(data)
    }

    private fun rsaEncrypt(data: ByteArray, publicKeyPem: String): ByteArray {
        val publicKeyContent = publicKeyPem
            .replace(Regex("-----BEGIN [^-]+-----"), "")
            .replace(Regex("-----END [^-]+-----"), "")
            .replace("\n", "")
            .replace("\r", "")
            .trim()

        val keyBytes = Base64.decode(publicKeyContent, Base64.DEFAULT)
        val keySpec = X509EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance("RSA")
        val publicKey = keyFactory.generatePublic(keySpec)

        val cipher = Cipher.getInstance(RSA_ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        return cipher.doFinal(data)
    }
}
