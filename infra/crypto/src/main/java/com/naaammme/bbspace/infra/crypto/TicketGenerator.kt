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
import androidx.core.content.edit
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.GZIPInputStream
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
class TicketGenerator(
    context: Context,
    private val deviceIdentity: DeviceIdentity
) {
    companion object {
        private const val TAG = "TicketGenerator"
        private const val REFRESH_THRESHOLD_MS = 1800_000L
        private const val MAX_RETRY = 4
        private const val TICKET_ENDPOINT = "bilibili.api.ticket.v1.Ticket/GetTicket"
    }

    private data class TicketResp(
        val ticket: String,
        val createdAt: Long,
        val ttl: Long
    )

    private val prefs = context.getSharedPreferences("ticket_prefs", Context.MODE_PRIVATE)
    private val refreshMutex = Mutex()
    private val client = OkHttpClient()
    private val bgScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val deviceInfoCollector = DeviceInfoCollector(context, deviceIdentity)
    private val backgroundRefreshing = AtomicBoolean(false)

    suspend fun getOrRefresh(mid: Long = 0, accessKey: String = ""): String {
        val now = System.currentTimeMillis()
        val ticket = cachedTicket()
        val expireAt = expireAtMs()
        if (ticket.isNotEmpty() && expireAt - now >= REFRESH_THRESHOLD_MS) return ticket
        return refresh(mid, accessKey) ?: cachedTicket().takeIf { it.isNotEmpty() && expireAtMs() > System.currentTimeMillis() }.orEmpty()
    }

    fun getTicketForHeader(mid: Long = 0, accessKey: String = ""): String {
        val now = System.currentTimeMillis()
        val ticket = cachedTicket()
        val expireAt = expireAtMs()
        if (expireAt == 0L || expireAt - now < REFRESH_THRESHOLD_MS) {
            refreshInBackground(mid, accessKey)
        }
        return if (ticket.isNotEmpty() && expireAt > now) ticket else ""
    }

    fun getTicketInfo(): Map<String, String> {
        return mapOf(
            "ticket" to cachedTicket(),
            "expireAt" to expireAtMs().toString()
        )
    }

    fun clearCachedTicket() {
        prefs.edit {
            remove("ticket")
            remove("ticket_expire_at_ms")
        }
    }

    fun buildAndroidDeviceInfo(mid: Long = 0): AndroidDeviceInfoOuterClass.AndroidDeviceInfo {
        return deviceInfoCollector.collect(mid)
    }

    private fun cachedTicket(): String = prefs.getString("ticket", "") ?: ""

    private fun expireAtMs(): Long = prefs.getLong("ticket_expire_at_ms", 0)

    private fun refreshInBackground(mid: Long, accessKey: String) {
        if (!backgroundRefreshing.compareAndSet(false, true)) return
        bgScope.launch {
            try {
                refresh(mid, accessKey)
                Logger.d(TAG) { "后台刷新 ticket 完成" }
            } catch (e: Exception) {
                Logger.w(TAG) { "后台刷新 ticket 失败: ${e.message}" }
            } finally {
                backgroundRefreshing.set(false)
            }
        }
    }

    private suspend fun refresh(mid: Long, accessKey: String = ""): String? {
        return refreshMutex.withLock {
            val now = System.currentTimeMillis()
            val cached = cachedTicket()
            val expireAt = expireAtMs()
            if (cached.isNotEmpty() && expireAt - now >= REFRESH_THRESHOLD_MS) return@withLock cached

            Logger.d(TAG) { "Refreshing ticket..." }
            var lastError: Exception? = null

            for (attempt in 1..MAX_RETRY) {
                try {
                    val ticket = fetchFromServer(mid, accessKey)
                    saveTicket(ticket.ticket, ticket.createdAt, ticket.ttl)
                    Logger.d(TAG) { "Ticket refreshed, ttl=${ticket.ttl}s" }
                    return@withLock ticket.ticket
                } catch (e: Exception) {
                    lastError = e
                    if (attempt == MAX_RETRY) break
                    val delayMs = minOf(attempt * 1000L, 15_000L)
                    Logger.w(TAG) { "Attempt $attempt failed: ${e.message}, retry in ${delayMs}ms" }
                    delay(delayMs)
                }
            }

            Logger.e(TAG, lastError) { "All $MAX_RETRY attempts failed" }
            null
        }
    }

    private suspend fun fetchFromServer(mid: Long, accessKey: String = ""): TicketResp {
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
        val oldTicket = cachedTicket()

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
            .addHeader("x-bili-aurora-eid", if (mid > 0) AuroraEidGenerator.generate(mid) else "")
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
        response.use {
            if (it.code != 200) {
                throw Exception("HTTP ${it.code}: ${it.message}")
            }

            val responseBytes = it.body?.bytes() ?: throw Exception("Empty response")
            val grpcEncoding = it.header("grpc-encoding")
            val payload = decodeGrpcFrame(responseBytes, grpcEncoding)
            val ticketResponse = TicketOuterClass.GetTicketResponse.parseFrom(payload)

            return TicketResp(
                ticket = ticketResponse.ticket,
                createdAt = ticketResponse.createdAt,
                ttl = ticketResponse.ttl
            )
        }
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

    private fun saveTicket(ticket: String, createdAtSeconds: Long, ttlSeconds: Long) {
        val createdAtMs = if (createdAtSeconds > 0) createdAtSeconds * 1000 else System.currentTimeMillis()
        val expireAtMs = createdAtMs + ttlSeconds * 1000
        prefs.edit {
            putString("ticket", ticket)
            putLong("ticket_expire_at_ms", expireAtMs)
        }
    }
}
