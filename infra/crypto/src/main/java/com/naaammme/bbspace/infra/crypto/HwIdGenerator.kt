package com.naaammme.bbspace.infra.crypto

import android.os.Build
import android.util.Base64
import java.io.File

class HwIdGenerator(
    private val deviceIdentity: DeviceIdentity
) {
    fun build(): String? {
        val raw = buildRaw() ?: return fallbackRaw()
        return encode(raw)
    }

    private fun buildRaw(): String? {
        val wifiMac = normalizeHex(deviceIdentity.mac)
        val btAddr = normalizeHex(readBtAddr())
        val idA = readIdA()
        val idB = readIdB()
        val raw = "$wifiMac|$btAddr|$idA|$idB"
        return raw.takeIf { it.length >= MIN_RAW_LEN }
    }

    private fun fallbackRaw(): String? {
        val androidId = deviceIdentity.androidId.takeIf(String::isNotBlank) ?: return null
        val model = Build.MODEL
            .orEmpty()
            .filterNot(Char::isWhitespace)
            .takeIf(String::isNotBlank)
            ?: return null
        return encode("$androidId@$model")
    }

    private fun normalizeHex(raw: String): String {
        return raw.lowercase().filter { it.isDigit() || it in 'a'..'f' }
    }

    private fun readBtAddr(): String {
        return readText("/sys/class/bluetooth/hci0/address")
            ?: readSystemProp("persist.service.bdroid.bdaddr")
            ?: ""
    }

    private fun readIdA(): String {
        val root = File("/sys/bus/mmc/devices")
        val device = root.listFiles()
            ?.firstOrNull { File(it, "block/mmcblk0").exists() }
            ?: return ""
        val serial = readText(File(device, "serial").path).orEmpty()
        val name = readText(File(device, "name").path).orEmpty()
        if (serial.isBlank() || name.isBlank()) return ""
        val raw = "$serial@$name"
        return raw.removePrefix("0x")
    }

    private fun readIdB(): String {
        val iSerial = readText("/sys/class/android_usb/android0/iSerial")
        if (isValidIdB(iSerial)) return iSerial.orEmpty()
        return ID_B_PROPS.asSequence()
            .mapNotNull(::readSystemProp)
            .firstOrNull(::isValidIdB)
            .orEmpty()
    }

    private fun isValidIdB(raw: String?): Boolean {
        val value = raw?.trim().orEmpty()
        if (value.isBlank()) return false
        return value.any { it != value[0] }
    }

    private fun readText(path: String): String? {
        return runCatching {
            File(path).takeIf(File::exists)?.readText()?.trim()
        }.getOrNull()?.takeIf(String::isNotBlank)
    }

    private fun readSystemProp(name: String): String? {
        return runCatching {
            val cls = Class.forName("android.os.SystemProperties")
            val method = cls.getMethod("get", String::class.java)
            (method.invoke(null, name) as? String)?.trim()
        }.getOrNull()?.takeIf(String::isNotBlank)
    }

    private fun encode(raw: String): String {
        val bytes = raw.toByteArray(Charsets.UTF_8)
        if (bytes.isEmpty()) return ""
        bytes[0] = (bytes[0].toInt() xor (bytes.size and 0xFF)).toByte()
        for (i in 1 until bytes.size) {
            bytes[i] = (bytes[i - 1].toInt() xor bytes[i].toInt()).toByte()
        }
        return Base64.encodeToString(
            bytes,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
    }

    private companion object {
        const val MIN_RAW_LEN = 4
        val ID_B_PROPS = listOf(
            "ro.serialno",
            "ro.boot.serialno",
            "gsm.device.sn",
            "gsm.baseband.imei",
            "gsm.sim.imei",
            "persist.radio.device.imei",
            "ro.aliyun.clouduuid",
            "ril.barcode"
        )
    }
}
