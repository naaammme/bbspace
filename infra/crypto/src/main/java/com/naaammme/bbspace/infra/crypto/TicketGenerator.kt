package com.naaammme.bbspace.infra.crypto

import android.content.Context
import android.util.Base64
import bilibili.api.ticket.v1.TicketOuterClass
import bilibili.metadata.MetadataOuterClass
import bilibili.metadata.device.DeviceOuterClass
import bilibili.metadata.fawkes.Fawkes
import bilibili.metadata.locale.LocaleOuterClass
import bilibili.metadata.network.NetworkOuterClass
import com.google.protobuf.ByteString
import com.naaammme.bbspace.core.common.BiliConstants
import com.naaammme.bbspace.core.common.UserAgentBuilder
import com.naaammme.bbspace.core.common.log.Logger
import datacenter.hakase.protobuf.AndroidDeviceInfoOuterClass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayInputStream
import java.util.UUID
import java.util.zip.GZIPInputStream
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

class TicketGenerator(
    private val context: Context,
    private val deviceIdentity: DeviceIdentity
) {
    companion object {
        private const val TAG = "TicketGenerator"
        private const val REFRESH_THRESHOLD_MS = 1800_000L
        private const val MAX_RETRY = 4
        private const val TICKET_ENDPOINT = "bilibili.api.ticket.v1.Ticket/GetTicket"
    }

    private val prefs = context.getSharedPreferences("ticket_prefs", Context.MODE_PRIVATE)
    private val refreshMutex = Mutex()
    private val client = OkHttpClient()
    private val bgScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun getValidTicket(mid: Long = 0, accessKey: String = ""): String {
        if (!needsRefresh()) return getCachedTicket()
        return refresh(mid, accessKey) ?: getCachedTicket()
    }

    fun getTicketWithPassiveRefresh(mid: Long = 0, accessKey: String = ""): String {
        val ticket = getCachedTicket()
        if (needsRefresh()) {
            bgScope.launch {
                try {
                    refresh(mid, accessKey)
                    Logger.d(TAG) { "后台刷新 ticket 完成" }
                } catch (e: Exception) {
                    Logger.w(TAG) { "后台刷新 ticket 失败: ${e.message}" }
                }
            }
        }
        return ticket
    }

    fun getCachedTicket(): String {
        return prefs.getString("ticket", "") ?: ""
    }

    fun buildAndroidDeviceInfo(mid: Long = 0): AndroidDeviceInfoOuterClass.AndroidDeviceInfo {
        val fts = System.currentTimeMillis() / 1000 - (30 * 24 * 3600)
        val sensorMsg = AndroidDeviceInfoOuterClass.SensorInfo.newBuilder().apply {
            name = "accelerometer"
            vendor = "invensense"
            version = 1
            type = 1
            maxRange = 156.96f
            resolution = 0.0048f
            power = 0.25f
            minDelay = 5000
        }.build()

        return AndroidDeviceInfoOuterClass.AndroidDeviceInfo.newBuilder().apply {
            sdkver = "0.2.4"
            appId = BiliConstants.APP_ID.toString()
            appVersion = BiliConstants.VERSION
            appVersionCode = BiliConstants.BUILD_STR
            if (mid > 0) {
                this.mid = mid.toString()
            }
            chid = BiliConstants.CHANNEL
            this.fts = fts
            buvidLocal = deviceIdentity.fp
            first = 0
            proc = "tv.danmaku.bili"
            net = ""
            band = ""

            osver = deviceIdentity.osVer
            t = System.currentTimeMillis()
            cpuCount = Runtime.getRuntime().availableProcessors()
            model = deviceIdentity.model
            brand = deviceIdentity.brand
            screen = "1080,2340,${Random.nextInt(400, 600)}"
            cpuModel = ""
            btmac = ""
            boot = Random.nextLong(100000, 948576)
            emu = "000"
            oid = "46000"
            network = "WIFI"
            mem = Runtime.getRuntime().maxMemory()
            sensor = "[\"accelerometer\", \"gyroscope\", \"magnetometer\"]"
            cpuFreq = 2450000
            cpuVendor = "ARM"
            sim = ""
            brightness = Random.nextInt(50, 200)

            putProps(
                "ro.build.date.utc",
                (System.currentTimeMillis() / 1000 - Random.nextInt(86400 * 30, 86400 * 365)).toString()
            )
            putProps("ro.product.device", deviceIdentity.device)
            putProps("ro.serialno", (0..7).joinToString("") { Random.nextInt(16).toString(16) })
            putProps("ro.build.fingerprint", deviceIdentity.buildFingerprint)
            putProps("ro.product.manufacturer", deviceIdentity.manufacturer)
            putProps("ro.build.display.id", deviceIdentity.buildId)

            wifimac = ""
            mac = deviceIdentity.mac
            adid = deviceIdentity.androidId
            os = BiliConstants.PLATFORM
            imei = ""
            cell = ""
            imsi = ""
            iccid = ""
            camcnt = 0
            campx = ""
            totalSpace = Random.nextLong(10_000_000_000L, 100_000_000_000L)
            axposed = "false"
            maps = ""
            files = "/data/user/0/tv.danmaku.bili/files"
            virtual = "0"
            virtualproc = "[]"
            gadid = ""
            glimit = ""
            apps = "[]"
            guid = ""
            uid = Random.nextInt(10000, 10053).toString()
            root = 0
            camzoom = ""
            camlight = ""
            oaid = ""
            udid = deviceIdentity.androidId
            vaid = ""
            aaid = ""

            androidapp20 = "[]"
            androidappcnt = 0
            androidsysapp20 = "[]"
            battery = Random.nextInt(30, 100)
            batteryState = "BATTERY_STATUS_DISCHARGING"
            bssid = ""
            buildId = deviceIdentity.buildId
            countryIso = "CN"
            freeMemory = Random.nextLong(1_000_000_000L, 10_000_000_000L)
            fstorage = Random.nextLong(10_000_000_000L, 100_000_000_000L).toString()
            kernelVersion = "4.14.117"
            languages = "zh"
            ssid = ""
            systemvolume = 0
            wifimaclist = ""
            memory = Runtime.getRuntime().maxMemory()
            strBattery = battery.toString()
            isRoot = false
            strBrightness = brightness.toString()
            strAppId = BiliConstants.APP_ID.toString()
            ip = ""
            userAgent = ""
            lightIntensity = "%.3f".format(Random.nextDouble(50.0, 600.0))

            addDeviceAngle(Random.nextDouble(-180.0, 180.0).toFloat())
            addDeviceAngle(Random.nextDouble(-180.0, 180.0).toFloat())
            addDeviceAngle(Random.nextDouble(-180.0, 180.0).toFloat())

            gpsSensor = 1
            speedSensor = 1
            linearSpeedSensor = 1
            gyroscopeSensor = 1
            biometric = 1
            addBiometrics("touchid")
            lastDumpTs = System.currentTimeMillis() - Random.nextLong(3600000, 86400000)
            location = ""
            country = ""
            city = ""
            dataActivityState = 0
            dataConnectState = 0
            dataNetworkType = 0
            voiceNetworkType = 0
            voiceServiceState = 0
            usbConnected = 0
            adbEnabled = 0
            uiVersion = "14.0.0"
            addSensorsInfo(sensorMsg)

            drmid = DeviceIdentity.getDrmId()
            batteryPresent = true
            batteryTechnology = "Li-ion"
            batteryTemperature = Random.nextInt(320, 330)
            batteryVoltage = Random.nextInt(3800, 4200)
            batteryPlugged = 0
            batteryHealth = 2
        }.build()
    }

    private fun needsRefresh(): Boolean {
        val expireAt = prefs.getLong("ticket_expire_at_ms", 0)
        return expireAt == 0L || expireAt - System.currentTimeMillis() < REFRESH_THRESHOLD_MS
    }

    private suspend fun refresh(mid: Long, accessKey: String = ""): String? {
        if (refreshMutex.isLocked) {
            refreshMutex.withLock { }
            return getCachedTicket()
        }

        return refreshMutex.withLock {
            if (!needsRefresh()) return@withLock getCachedTicket()

            Logger.d(TAG) { "Refreshing ticket..." }
            var lastError: Exception? = null

            for (attempt in 1..MAX_RETRY) {
                try {
                    val (ticket, ttl) = fetchFromServer(mid, accessKey)
                    saveTicket(ticket, ttl)
                    Logger.d(TAG) { "Ticket refreshed, ttl=${ttl}s" }
                    return@withLock ticket
                } catch (e: Exception) {
                    lastError = e
                    val delayMs = minOf(attempt * 1000L, 15_000L)
                    Logger.w(TAG) { "Attempt $attempt failed: ${e.message}, retry in ${delayMs}ms" }
                    delay(delayMs)
                }
            }

            Logger.e(TAG, lastError) { "All $MAX_RETRY attempts failed" }
            null
        }
    }

    private suspend fun fetchFromServer(mid: Long, accessKey: String = ""): Pair<String, Long> {
        val fingerprintBytes = buildAndroidDeviceInfo(mid).toByteArray()
        val deviceBytes = buildDeviceProtobuf()

        val hmac = Mac.getInstance("HmacSHA256")
        hmac.init(SecretKeySpec(BiliConstants.HMAC_KEY.toByteArray(), "HmacSHA256"))
        hmac.update(deviceBytes)
        hmac.update("x-exbadbasket".toByteArray())
        hmac.update(ByteArray(0))
        hmac.update("x-fingerprint".toByteArray())
        hmac.update(fingerprintBytes)
        val sign = hmac.doFinal()

        val request = TicketOuterClass.GetTicketRequest.newBuilder().apply {
            keyId = BiliConstants.TICKET_KEY_ID
            token = ""
            putContext("x-fingerprint", ByteString.copyFrom(fingerprintBytes))
            putContext("x-exbadbasket", ByteString.EMPTY)
            this.sign = ByteString.copyFrom(sign)
        }.build()

        val grpcFrame = buildGrpcFrame(request.toByteArray())
        val metadataBytes = buildMetadataProtobuf(accessKey)
        val networkBytes = buildNetworkProtobuf()
        val localeBytes = buildLocaleProtobuf()
        val fawkesBytes = buildFawkesProtobuf()
        val oldTicket = getCachedTicket()

        val httpRequest = Request.Builder()
            .url("${BiliConstants.BASE_URL_APP}/$TICKET_ENDPOINT")
            .post(grpcFrame.toRequestBody(null))
            .addHeader("content-type", "application/grpc")
            .addHeader("accept-encoding", "gzip")
            .addHeader(
                "user-agent",
                UserAgentBuilder.buildGrpcUserAgent(deviceIdentity.model, deviceIdentity.osVer)
            )
            .addHeader("x-bili-metadata-bin", Base64.encodeToString(metadataBytes, Base64.NO_WRAP or Base64.NO_PADDING))
            .addHeader("x-bili-device-bin", Base64.encodeToString(deviceBytes, Base64.NO_WRAP or Base64.NO_PADDING))
            .addHeader("x-bili-network-bin", Base64.encodeToString(networkBytes, Base64.NO_WRAP or Base64.NO_PADDING))
            .addHeader("x-bili-locale-bin", Base64.encodeToString(localeBytes, Base64.NO_WRAP or Base64.NO_PADDING))
            .addHeader("x-bili-fawkes-req-bin", Base64.encodeToString(fawkesBytes, Base64.NO_WRAP or Base64.NO_PADDING))
            .addHeader("x-bili-restriction-bin", "")
            .addHeader("x-bili-exps-bin", "")
            .addHeader("x-bili-trace-id", TraceIdGenerator.generate())
            .addHeader("x-bili-aurora-eid", if (mid > 0) AuroraEidGenerator.generate(mid) ?: "" else "")
            .addHeader("x-bili-mid", if (mid > 0) mid.toString() else "")
            .addHeader("x-bili-aurora-zone", "")
            .addHeader("x-bili-gaia-vtoken", "")
            .addHeader("x-bili-ticket", oldTicket)
            .addHeader("buvid", deviceIdentity.buvid)
            .addHeader("env", BiliConstants.ENV)
            .addHeader("app-key", BiliConstants.APP_KEY_NAME)
            .apply {
                if (accessKey.isNotEmpty()) {
                    addHeader("authorization", "identify_v1 $accessKey")
                }
            }
            .build()

        val response = withContext(Dispatchers.IO) {
            client.newCall(httpRequest).execute()
        }

        if (response.code != 200) {
            throw Exception("HTTP ${response.code}: ${response.message}")
        }

        val responseBytes = response.body?.bytes() ?: throw Exception("Empty response")
        val grpcEncoding = response.header("grpc-encoding")
        val payload = decodeGrpcFrame(responseBytes, grpcEncoding)
        val ticketResponse = TicketOuterClass.GetTicketResponse.parseFrom(payload)

        return ticketResponse.ticket to ticketResponse.ttl
    }

    private fun buildGrpcFrame(protobufBytes: ByteArray): ByteArray {
        val frame = ByteArray(5 + protobufBytes.size)
        frame[0] = 0x00
        frame[1] = ((protobufBytes.size shr 24) and 0xFF).toByte()
        frame[2] = ((protobufBytes.size shr 16) and 0xFF).toByte()
        frame[3] = ((protobufBytes.size shr 8) and 0xFF).toByte()
        frame[4] = (protobufBytes.size and 0xFF).toByte()
        System.arraycopy(protobufBytes, 0, frame, 5, protobufBytes.size)
        return frame
    }

    private fun decodeGrpcFrame(responseBytes: ByteArray, grpcEncoding: String?): ByteArray {
        if (responseBytes.size < 5) return ByteArray(0)
        val compressionFlag = responseBytes[0].toInt()
        val payload = responseBytes.copyOfRange(5, responseBytes.size)
        return if (compressionFlag != 0 && (grpcEncoding == "gzip" || compressionFlag == 1)) {
            GZIPInputStream(ByteArrayInputStream(payload)).use { it.readBytes() }
        } else {
            payload
        }
    }

    private fun buildDeviceProtobuf(): ByteArray {
        val fts = System.currentTimeMillis() / 1000 - (30 * 24 * 3600)
        return DeviceOuterClass.Device.newBuilder().apply {
            appId = BiliConstants.APP_ID
            build = BiliConstants.BUILD
            buvid = deviceIdentity.buvid
            mobiApp = BiliConstants.MOBI_APP
            platform = BiliConstants.PLATFORM
            device = ""
            channel = BiliConstants.CHANNEL
            brand = deviceIdentity.brand
            model = deviceIdentity.model
            osver = deviceIdentity.osVer
            fpLocal = deviceIdentity.fp
            fpRemote = deviceIdentity.fp
            versionName = BiliConstants.VERSION
            fp = deviceIdentity.fp
            this.fts = fts
        }.build().toByteArray()
    }

    private fun buildMetadataProtobuf(accessKey: String = ""): ByteArray {
        return MetadataOuterClass.Metadata.newBuilder().apply {
            this.accessKey = accessKey
            mobiApp = BiliConstants.MOBI_APP
            device = ""
            build = BiliConstants.BUILD
            channel = BiliConstants.CHANNEL
            buvid = deviceIdentity.buvid
            platform = BiliConstants.PLATFORM
        }.build().toByteArray()
    }

    private fun buildNetworkProtobuf(): ByteArray {
        return NetworkOuterClass.Network.newBuilder().apply {
            type = NetworkOuterClass.NetworkType.WIFI
            tf = NetworkOuterClass.TFType.TF_UNKNOWN
            oid = "46000"
        }.build().toByteArray()
    }

    private fun buildLocaleProtobuf(): ByteArray {
        val cLocale = LocaleOuterClass.LocaleIds.newBuilder()
            .setLanguage("zh")
            .setRegion("CN")
            .build()
        val sLocale = LocaleOuterClass.LocaleIds.newBuilder()
            .setLanguage("zh")
            .setRegion("CN")
            .build()

        return LocaleOuterClass.Locale.newBuilder().apply {
            this.cLocale = cLocale
            this.sLocale = sLocale
            timezone = ""
        }.build().toByteArray()
    }

    private fun buildFawkesProtobuf(): ByteArray {
        val sessionId = UUID.randomUUID().toString().replace("-", "").substring(0, 8)
        return Fawkes.FawkesReq.newBuilder().apply {
            appkey = BiliConstants.APP_KEY_NAME
            env = BiliConstants.ENV
            this.sessionId = sessionId
        }.build().toByteArray()
    }

    private fun saveTicket(ticket: String, ttlSeconds: Long) {
        val expireAtMs = System.currentTimeMillis() + ttlSeconds * 1000
        prefs.edit()
            .putString("ticket", ticket)
            .putLong("ticket_expire_at_ms", expireAtMs)
            .apply()
    }

    fun clearCache() {
        prefs.edit().clear().apply()
    }
}
