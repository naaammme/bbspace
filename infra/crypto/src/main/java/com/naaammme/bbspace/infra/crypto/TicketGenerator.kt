package com.naaammme.bbspace.infra.crypto

import android.content.Context
import android.util.Base64
import com.naaammme.bbspace.core.common.log.Logger
import com.naaammme.bbspace.core.common.BiliConstants
import datacenter.hakase.protobuf.AndroidDeviceInfoOuterClass
import datacenter.hakase.protobuf.androidDeviceInfo
import datacenter.hakase.protobuf.sensorInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

/**
 * Ticket 生成器
 * 职责：构建 AndroidDeviceInfo + gRPC 请求 + 本地缓存 + 过期检查
 *
 *
 * 逻辑：
 * getValidTicket() 唯一入口
 * 本地有未过期 ticket → 直接返回
 * 过期或不存在 → gRPC 请求刷新 → 缓存 → 返回
 * 距过期不足 30 分钟视为需刷新
 */
class TicketGenerator(
    private val context: Context,
    private val deviceIdentity: DeviceIdentity
) {
    companion object {
        private const val TAG = "TicketGenerator"
        private const val REFRESH_THRESHOLD_MS = 1800_000L // 30 分钟
        private const val MAX_RETRY = 4

        // 端点路径（就近定义，base URL 统一在 BiliConstants）
        private const val TICKET_ENDPOINT = "bilibili.api.ticket.v1.Ticket/GetTicket"
    }

    private val prefs = context.getSharedPreferences("ticket_prefs", Context.MODE_PRIVATE)
    private val refreshMutex = Mutex()
    private val client = OkHttpClient()
    private val backgroundScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
    )

    // 公开接口

    /**
     * 获取有效 ticket（主动刷新）
     * 用于应用初始化时，如果即将过期会阻塞等待刷新完成
     */
    suspend fun getValidTicket(mid: Long = 0, accessKey: String = ""): String {
        if (!needsRefresh()) return getCachedTicket()
        return refresh(mid, accessKey) ?: getCachedTicket()
    }

    /**
     * 获取 ticket 并触发被动刷新（推荐用于请求时）
     * 立即返回缓存的 ticket，如果即将过期则在后台异步刷新
     */
    fun getTicketWithPassiveRefresh(mid: Long = 0, accessKey: String = ""): String {
        val ticket = getCachedTicket()

        // 如果即将过期，触发后台异步刷新
        if (needsRefresh()) {
            backgroundScope.launch {
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

    /**
     * 读取缓存 ticket（不检查过期）
     */
    fun getCachedTicket(): String {
        return prefs.getString("ticket", "") ?: ""
    }

    /**
     * 构建 AndroidDeviceInfo protobuf（也用于 pollQrCode 的 device_meta）
     */
    fun buildAndroidDeviceInfo(mid: Long = 0): AndroidDeviceInfoOuterClass.AndroidDeviceInfo {
        val fts = System.currentTimeMillis() / 1000 - (30 * 24 * 3600)

        return androidDeviceInfo {
            // 应用信息
            sdkver = "0.2.4"
            appId = BiliConstants.APP_ID.toString()
            appVersion = BiliConstants.VERSION
            appVersionCode = BiliConstants.BUILD_STR
            this.mid = if (mid > 0) mid.toString() else ""
            chid = BiliConstants.CHANNEL
            this.fts = fts
            buvidLocal = deviceIdentity.fp
            first = 0
            proc = "tv.danmaku.bili"
            net = ""
            band = ""

            // 设备信息
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
            sensor = """["accelerometer", "gyroscope", "magnetometer"]"""
            cpuFreq = 2450000
            cpuVendor = "ARM"
            sim = ""
            brightness = Random.nextInt(50, 200)

            // props（真实设备属性）
            props.put("ro.build.date.utc", (System.currentTimeMillis() / 1000 - Random.nextInt(86400 * 30, 86400 * 365)).toString())
            props.put("ro.product.device", deviceIdentity.device)
            props.put("ro.serialno", (0..7).joinToString("") { Random.nextInt(16).toString(16) })
            props.put("ro.build.fingerprint", deviceIdentity.buildFingerprint)
            props.put("ro.product.manufacturer", deviceIdentity.manufacturer)
            props.put("ro.build.display.id", deviceIdentity.buildId)

            // ── 设备标识 ──
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

            // ── device_meta 相关字段 ──
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

            // 传感器
            deviceAngle.add(Random.nextDouble(-180.0, 180.0).toFloat())
            deviceAngle.add(Random.nextDouble(-180.0, 180.0).toFloat())
            deviceAngle.add(Random.nextDouble(-180.0, 180.0).toFloat())

            gpsSensor = 1
            speedSensor = 1
            linearSpeedSensor = 1
            gyroscopeSensor = 1
            biometric = 1
            biometrics.add("touchid")
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

            sensorsInfo.add(sensorInfo {
                name = "accelerometer"
                vendor = "invensense"
                version = 1
                type = 1
                maxRange = 156.96f
                resolution = 0.0048f
                power = 0.25f
                minDelay = 5000
            })

            // 电池详情
            drmid = DeviceIdentity.getDrmId()
            batteryPresent = true
            batteryTechnology = "Li-ion"
            batteryTemperature = Random.nextInt(320, 330)
            batteryVoltage = Random.nextInt(3800, 4200)
            batteryPlugged = 0
            batteryHealth = 2
        }
    }

    // 缓存与刷新（内部）


    private fun needsRefresh(): Boolean {
        val expireAt = prefs.getLong("ticket_expire_at_ms", 0)
        return expireAt == 0L || expireAt - System.currentTimeMillis() < REFRESH_THRESHOLD_MS
    }

    private suspend fun refresh(mid: Long, accessKey: String = ""): String? {
        if (refreshMutex.isLocked) {
            refreshMutex.withLock { /* 等前一个刷完 */ }
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
                    val delay = minOf(attempt * 1000L, 15_000L)
                    Logger.w(TAG) { "Attempt $attempt failed: ${e.message}, retry in ${delay}ms" }
                    kotlinx.coroutines.delay(delay)
                }
            }

            Logger.e(TAG, lastError) { "All $MAX_RETRY attempts failed" }
            null
        }
    }

    // gRPC 请求（内部，直接用 OkHttp，跟 GuestIdGenerator 同模式）


    private suspend fun fetchFromServer(mid: Long, accessKey: String = ""): Pair<String, Long> {
        val fingerprintBytes = buildAndroidDeviceInfo(mid).toByteArray()
        val deviceBytes = buildDeviceProtobuf()

        // HMAC 签名
        val hmac = Mac.getInstance("HmacSHA256")
        hmac.init(SecretKeySpec(BiliConstants.HMAC_KEY.toByteArray(), "HmacSHA256"))
        hmac.update(deviceBytes)
        hmac.update("x-exbadbasket".toByteArray())
        hmac.update(ByteArray(0))
        hmac.update("x-fingerprint".toByteArray())
        hmac.update(fingerprintBytes)
        val sign = hmac.doFinal()

        // 构建 GetTicketRequest protobuf
        val request = bilibili.api.ticket.v1.getTicketRequest {
            keyId = BiliConstants.TICKET_KEY_ID
            token = ""
            context.put("x-fingerprint", com.google.protobuf.ByteString.copyFrom(fingerprintBytes))
            context.put("x-exbadbasket", com.google.protobuf.ByteString.EMPTY)
            this.sign = com.google.protobuf.ByteString.copyFrom(sign)
        }

        // gRPC frame: 5字节头 + protobuf
        val requestBytes = request.toByteArray()
        val grpcFrame = buildGrpcFrame(requestBytes)

        // 构建 gRPC 请求头
        val metadataBytes = buildMetadataProtobuf(accessKey)
        val networkBytes = buildNetworkProtobuf()
        val localeBytes = buildLocaleProtobuf()
        val fawkesBytes = buildFawkesProtobuf()

        // 获取缓存的旧 ticket（如果有）
        val oldTicket = getCachedTicket()

        val httpRequest = Request.Builder()
            .url("${BiliConstants.BASE_URL_APP}/$TICKET_ENDPOINT")
            .post(grpcFrame.toRequestBody(null))
            .addHeader("content-type", "application/grpc")
            .addHeader("accept-encoding", "gzip")
            .addHeader("user-agent", "Dalvik/2.1.0 (Linux; U; Android ${deviceIdentity.osVer}; ${deviceIdentity.model} Build/PQ3A.190605.07021633) ${BiliConstants.VERSION} os/android model/${deviceIdentity.model} mobi_app/${BiliConstants.MOBI_APP} build/${BiliConstants.BUILD_STR} channel/${BiliConstants.CHANNEL} innerVer/${BiliConstants.BUILD_STR} osVer/${deviceIdentity.osVer} network/2")
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
            .addHeader("app-key", BiliConstants.MOBI_APP)
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

        // 解析 gRPC 响应：跳过5字节头，检查压缩
        val grpcEncoding = response.header("grpc-encoding")
        val payload = decodeGrpcFrame(responseBytes, grpcEncoding)
        val ticketResponse = bilibili.api.ticket.v1.TicketOuterClass.GetTicketResponse.parseFrom(payload)

        return ticketResponse.ticket to ticketResponse.ttl
    }

    // gRPC frame 编解码


    private fun buildGrpcFrame(protobufBytes: ByteArray): ByteArray {
        val frame = ByteArray(5 + protobufBytes.size)
        frame[0] = 0x00 // 不压缩
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

    // 构建 gRPC metadata protobuf（内部复制，避免跨模块依赖）

    private fun buildDeviceProtobuf(): ByteArray {
        val fts = System.currentTimeMillis() / 1000 - (30 * 24 * 3600)
        return bilibili.metadata.device.device {
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
        }.toByteArray()
    }

    private fun buildMetadataProtobuf(accessKey: String = ""): ByteArray {
        return bilibili.metadata.metadata {
            this.accessKey = accessKey
            mobiApp = BiliConstants.MOBI_APP
            device = ""
            build = BiliConstants.BUILD
            channel = BiliConstants.CHANNEL
            buvid = deviceIdentity.buvid
            platform = BiliConstants.PLATFORM
        }.toByteArray()
    }

    private fun buildNetworkProtobuf(): ByteArray {
        return bilibili.metadata.network.network {
            type = bilibili.metadata.network.NetworkOuterClass.NetworkType.WIFI
            tf = bilibili.metadata.network.NetworkOuterClass.TFType.TF_UNKNOWN
            oid = "46000"
        }.toByteArray()
    }

    private fun buildLocaleProtobuf(): ByteArray {
        return bilibili.metadata.locale.locale {
            cLocale = bilibili.metadata.locale.localeIds {
                language = "zh"
                region = "CN"
            }
            sLocale = bilibili.metadata.locale.localeIds {
                language = "zh"
                region = "CN"
            }
            // simCode = ""
            timezone = ""
        }.toByteArray()
    }

    private fun buildFawkesProtobuf(): ByteArray {
        val sessionId = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8)
        return bilibili.metadata.fawkes.fawkesReq {
            appkey = BiliConstants.MOBI_APP
            env = BiliConstants.ENV
            this.sessionId = sessionId
        }.toByteArray()
    }


    // 持久化
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
