package com.naaammme.bbspace.infra.crypto

import android.content.Context
import android.content.SharedPreferences
import android.media.MediaDrm
import android.os.Build
import android.provider.Settings
import java.net.NetworkInterface
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.random.Random

/**
 * 设备身份生成器
 * 生成本地 buvid: DRM ID(XU) > MAC(XY) > Android ID(XX) > UUID(XW)
 */
class DeviceIdentity(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("device_identity", Context.MODE_PRIVATE)

    val brand: String = Build.BRAND
    val model: String = Build.MODEL
    val osVer: String = Build.VERSION.RELEASE
    val device: String = Build.DEVICE
    val manufacturer: String = Build.MANUFACTURER
    val buildId: String = Build.DISPLAY
    val buildFingerprint: String = Build.FINGERPRINT

    val androidId: String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: ""

    val mac: String

    val localBuvid: String
    val remoteBuvid: String
        get() = prefs.getString("buvid_remote", "") ?: ""

    val buvid: String
        get() = remoteBuvid.ifEmpty { localBuvid }

    val fp: String

    init {
        mac = prefs.getString("mac", null) ?: getRealMac().also { save("mac", it) }

        localBuvid = prefs.getString("buvid_v2", null) ?: run {
            selectAndEncode(androidId).also { save("buvid_v2", it) }
        }

        val radio = Build.getRadioVersion() ?: ""
        fp = prefs.getString("fp_v2", null)
            ?: generateFp(localBuvid, model, radio).also { save("fp_v2", it) }
    }

    fun isFirstInstall(): Boolean {
        return !prefs.contains("buvid_remote")
    }

    fun updateRemoteBuvid(remote: String) {
        save("buvid_remote", remote)
    }

    /**
     * 按优先级选择设备标识并编码
     * DRM ID(XU) > MAC(XY) > Android ID(XX) > UUID(XW)
     */
    private fun selectAndEncode(realAndroidId: String): String {
        val drmId = getDrmId()
        if (isValidDrmId(drmId)) {
            return encodeBuvid("XU", drmId)
        }

        val realMac = mac.replace(":", "")
        if (realMac.length == 12 && realMac != "020000000000") {
            return encodeBuvid("XY", realMac)
        }

        if (isValidAndroidId(realAndroidId)) {
            return encodeBuvid("XX", realAndroidId)
        }

        val fallbackUuid = UUID.randomUUID().toString().replace("-", "")
        return encodeBuvid("XW", fallbackUuid)
    }

    private fun save(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    private fun getRealMac(): String {
        return runCatching {
            NetworkInterface.getNetworkInterfaces()?.toList()
                ?.firstOrNull { it.name.equals("wlan0", ignoreCase = true) }
                ?.hardwareAddress
                ?.joinToString(":") { "%02x".format(it) }
                ?: ""
        }.getOrDefault("")
    }

    companion object {
        private val FAKE_ANDROID_ID = setOf("0000000000000000")

        private val FAKE_DRM_ID = setOf(
            "430c4b46038ccdae0cd7a8eb3acb21b1",
            "a4d9d444fc12e9221804728d1390c1eb",
            "13a30d2e938b76b3ca4c36c06d397132",
            "810cf75db066c78c272dc8212f33e834",
            "86067f6d303c8b5adc06574fc971474a",
            "823d842f062c51286efb2cb5c35d75ec",
            "7c55cc4d58478caa4ccf651fae7308ad",
            "91982c259e136955eafe65983ee12793",
            "873fc4adc3cbe68d287740016974e4bd",
            "bd427d56cf2530955e0319118151358a",
            "a7460cc1116e3fa31a1fa0ab65bd9924",
            "29d4c7041a58481e8019cdb255eed1fd",
            "e3a4bbf9156f5da153d62f7894491ea6",
            "cf3824e1ae27551cf9232ecf2d31fd5e",
            "a71ab7fd091ae6b67ccf52b2285ad775",
            "2a813c8a20a1a2faadca3b94afe55e14",
            "2839ec148dd5f7ea01897fbc84cba994",
            "26ba3dfcad955f46a01e2b57a12b3c14",
            "b09dada62483de915fa59070e97e8f8a",
            "9c9cff501c08a93d1e9784e09c4af983",
            "363e7afd082f123c682104f617c758be",
            "37d65ff9a58ec91e436e81a2bba3d278",
            "b2b1912983542bb282254d75123838b9",
            "14e53336e91ceaad7c6304b3efc2eae8",
            "d39b56a4b234ad85be68f63ff8cd99ee",
            "43362515de164e3cfdb25e3a6fe7b351",
            "d9157b88ee3423fcbd09e311eb3cb197",
            "aa3c9a0b3d75641cc15e61a05310820a",
            "68dddaa4dc1416f3ea74eee02b387282",
            "a91546c036c9e9ab620f25d389999d6e",
            "5fa7036a11194601d4b359848aa0638b",
            "4a0d32a120544644361228fab8a38bdd",
            "aa2682752d843022b9785e137999304f",
            "4ecca9bf42e4b500bf474e2021910772",
            "dbf872e865d059f90a578486def18dae",
            "850445908dab13e1926c90390a78ddde",
            "9bae0a339d69278e6592802e765542f7",
            "9e7cf3e3a404a3f5494c605d74f8b1ed",
            "b1fcebc53bdd5c67e2f28bdb2127ca65",
            "5566cf477adf2d32b1d06ec181e8e62e",
            "cbed9de2b00475e1d80fd72660b0149c",
            "5732b4eb5ee580faab3715e30809cd83",
            "8db5e1583f85a7d2fc6fe9445e9c1b04",
            "2f84b8c3c93a9c72f0ed67f945b58c3d",
            "eb07e4b4834617aee2db54c4946d3bdc",
            "846f626bef470bb24a53c9ba14b8b2d3",
            "3cc9be8c7469e4ad3b6bdd0e79bf3a10",
            "e7aa0af7128b12c2983014262629eaa9",
            "90a58d59a10f27d2dc313145089bd7ba",
            "1b633ca45b8eb9d2488b94f247771236",
            "aee69a00e648f4282182f8b06351978e",
            "476746f804b27a616de87dbf404ae087",
            "952d7b52e53f89ba50237c87149f713e",
            "46ec3b558778d9710ada730b4c191447",
            "c8f64fa98f28f384c5d8894f3715565f",
            "fc73a059ffe2e96e2c1619f6a6195896",
            "1a853ca5b8ed725e2031f71cce3d9495",
            "8a5cdf9c321e822fcae35adbc1df4629"
        )

        private val WIDEVINE_UUID = UUID(-0x121074568629b532L, -0x5c37d8232ae2de13L)

        fun isValidAndroidId(id: String): Boolean {
            return id.isNotEmpty() && id !in FAKE_ANDROID_ID
        }

        fun isValidDrmId(id: String): Boolean {
            return id.isNotEmpty() && id !in FAKE_DRM_ID
        }

        /**
         * 获取 Widevine DRM ID 的 MD5
         */
        fun getDrmId(): String {
            return runCatching {
                val drm = MediaDrm(WIDEVINE_UUID)
                val deviceId = drm.getPropertyByteArray(MediaDrm.PROPERTY_DEVICE_UNIQUE_ID)
                drm.close()
                md5Upper(deviceId)
            }.getOrDefault("")
        }

        /**
         * 编码 buvid V2
         * md5 = MD5_UPPER(raw)
         * mid3 = md5[2] + md5[12] + md5[22]
         * buvid = prefix + mid3 + md5
         */
        fun encodeBuvid(prefix: String, raw: String): String {
            val md5 = md5Upper(raw.toByteArray())
            val mid3 = "${md5[2]}${md5[12]}${md5[22]}"
            return "$prefix$mid3$md5"
        }

        /**
         * FP 格式: 32位MD5 + 14位时间戳 + 16位随机Hex + 2位校验码
         * TODO: 利用 x/resource/fingerprint 获取远程fp,虽然不知道有啥用....
         */
        fun generateFp(buvid: String, model: String, radio: String = ""): String {
            val fpMd5 = md5Lower("$buvid$model$radio")
            val timestamp =
                SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault()).format(Date())
            val randomHex =
                (0..15).joinToString("") { Random.nextInt(16).toString(16) }
            val fpRaw = "$fpMd5$timestamp$randomHex"

            var veriCode = 0
            val limit = minOf(fpRaw.length - (fpRaw.length % 2), 62)
            for (i in 0 until limit step 2) {
                veriCode += fpRaw.substring(i, i + 2).toInt(16)
            }
            return fpRaw + "%02x".format(veriCode % 256)
        }

        private fun md5Upper(input: ByteArray): String {
            val digest = MessageDigest.getInstance("MD5").digest(input)
            return digest.joinToString("") { "%02X".format(it) }
        }

        private fun md5Lower(input: String): String {
            val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray())
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}
