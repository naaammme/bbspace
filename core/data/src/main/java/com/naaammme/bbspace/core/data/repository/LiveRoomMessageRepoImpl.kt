package com.naaammme.bbspace.core.data.repository

import android.os.SystemClock
import com.naaammme.bbspace.core.common.AuthProvider
import com.naaammme.bbspace.core.common.BiliConstants
import com.naaammme.bbspace.core.common.log.Logger
import com.naaammme.bbspace.core.domain.live.LiveRoomMessageRepository
import com.naaammme.bbspace.core.model.LiveRoomMedal
import com.naaammme.bbspace.core.model.LiveRoomMessage
import com.naaammme.bbspace.core.model.LiveRoomPanelState
import com.naaammme.bbspace.core.model.LiveRoomSessionState
import com.naaammme.bbspace.core.model.LiveRoomSessionStatus
import com.naaammme.bbspace.core.model.LiveRoomUser
import com.naaammme.bbspace.core.model.merge
import com.naaammme.bbspace.infra.crypto.DeviceIdentity
import com.naaammme.bbspace.infra.crypto.HwIdGenerator
import com.naaammme.bbspace.infra.network.BiliRestClient
import com.naaammme.bbspace.infra.network.BiliRestParamBuilder
import com.naaammme.bbspace.infra.network.BiliRestProfile
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.ArrayDeque
import java.util.HashSet
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.InflaterInputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.brotli.dec.BrotliInputStream
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class LiveRoomMessageRepoImpl @Inject constructor(
    private val restClient: BiliRestClient,
    private val restParamBuilder: BiliRestParamBuilder,
    private val authProvider: AuthProvider,
    private val deviceIdentity: DeviceIdentity,
    private val hwIdGenerator: HwIdGenerator
) : LiveRoomMessageRepository {

    override fun observeRoomSession(roomId: Long): Flow<LiveRoomSessionState> = flow {
        if (roomId <= 0L) {
            emit(
                LiveRoomSessionState(
                    roomId = roomId,
                    status = LiveRoomSessionStatus.Closed,
                    lastError = "直播间 roomId 无效"
                )
            )
            return@flow
        }

        coroutineScope {
            val localIdGen = AtomicLong(0L)
            val ackDeduper = AckDeduper()
            val stateLock = Any()
            val stateFlow = MutableStateFlow(LiveRoomSessionState(roomId = roomId))
            var retryCount = 0

            fun pushState(transform: (LiveRoomSessionState) -> LiveRoomSessionState) {
                synchronized(stateLock) {
                    stateFlow.value = transform(stateFlow.value)
                }
            }

            suspend fun runLoop(scope: CoroutineScope) {
                while (scope.isActive) {
                    val danmuInfo = try {
                        pushState {
                            it.copy(
                                status = if (retryCount == 0) {
                                    LiveRoomSessionStatus.Connecting
                                } else {
                                    LiveRoomSessionStatus.Reconnecting
                                },
                                retryCount = retryCount,
                                lastError = null
                            )
                        }
                        fetchDanmuInfo(roomId)
                    } catch (t: Throwable) {
                        if (t is CancellationException) throw t
                        val msg = t.message ?: "获取直播弹幕配置失败"
                        Logger.e(TAG, t) { "fetch danmu info failed roomId=$roomId msg=$msg" }
                        pushState {
                            it.copy(
                                status = LiveRoomSessionStatus.Reconnecting,
                                retryCount = retryCount,
                                lastError = msg
                            )
                        }
                        retryCount += 1
                        delay(nextRetryDelayMs(retryCount))
                        continue
                    }

                    try {
                        connectAndConsume(
                            roomId = roomId,
                            info = danmuInfo,
                            localIdGen = localIdGen,
                            ackDeduper = ackDeduper,
                            emitState = ::pushState
                        )
                        if (!scope.isActive) return
                        retryCount = 0
                    } catch (t: Throwable) {
                        if (t is CancellationException) throw t
                        val msg = t.message ?: "直播消息连接断开"
                        Logger.w(TAG) { "live room session break roomId=$roomId msg=$msg retry=$retryCount" }
                        pushState {
                            it.copy(
                                status = LiveRoomSessionStatus.Reconnecting,
                                retryCount = retryCount,
                                lastError = msg
                            )
                        }
                        retryCount += 1
                        delay(nextRetryDelayMs(retryCount))
                    }
                }
            }

            val sessionJob = launch(Dispatchers.IO) { runLoop(this) }
            try {
                emitAll(stateFlow)
            } finally {
                sessionJob.cancel()
            }
        }
    }

    private suspend fun fetchDanmuInfo(roomId: Long): DanmuInfo {
        val ts = System.currentTimeMillis() / 1000L
        val json = restClient.getSigned(
            url = "${BiliConstants.BASE_URL_LIVE_API}$DANMU_INFO_ENDPOINT",
            params = restParamBuilder.app(BiliRestProfile.APP, ts, authProvider.accessToken) + buildMap {
                put("actionKey", "appkey")
                put("device", BiliConstants.PLATFORM)
                put("free_type", "0")
                put("is_anchor", "0")
                put("room_id", roomId.toString())
                put("version", BiliConstants.VERSION)
            },
            profile = BiliRestProfile.APP
        )
        val data = json.optJSONObject("data")
            ?: throw IllegalStateException("直播弹幕配置缺少 data")
        val token = data.optString("token")
            .takeIf(String::isNotBlank)
            ?: throw IllegalStateException("直播弹幕配置缺少 token")
        val hosts = parseHosts(data.optJSONArray("ip_list"))
        if (hosts.isEmpty()) throw IllegalStateException("直播弹幕配置缺少 ip_list")
        return DanmuInfo(token = token, hosts = hosts)
    }

    private suspend fun connectAndConsume(
        roomId: Long,
        info: DanmuInfo,
        localIdGen: AtomicLong,
        ackDeduper: AckDeduper,
        emitState: ((LiveRoomSessionState) -> LiveRoomSessionState) -> Unit
    ) = withContext(Dispatchers.IO) {
        var lastError: Throwable? = null
        for (index in info.hosts.indices) {
            val host = info.hosts[index]
            val queueId = UUID.randomUUID().toString()
            val socket = Socket()
            val writeLock = Any()
            try {
                ackDeduper.clear()
                emitState {
                    it.copy(
                        status = LiveRoomSessionStatus.Connecting,
                        queueId = queueId,
                        lastConnectAtMs = System.currentTimeMillis(),
                        lastError = null
                    )
                }
                socket.tcpNoDelay = true
                socket.keepAlive = true
                socket.connect(InetSocketAddress(host.host, host.port), CONNECT_TIMEOUT_MS)
                socket.soTimeout = AUTH_TIMEOUT_MS
                socket.getOutputStream().use { output ->
                    DataInputStream(socket.getInputStream()).use { input ->
                        emitState { it.copy(status = LiveRoomSessionStatus.Authorizing) }
                        sendPacket(
                            output = output,
                            writeLock = writeLock,
                            op = OP_AUTH,
                            payload = buildAuthPayload(roomId, info.token, queueId).toByteArray()
                        )
                        consumeSocket(
                            socket = socket,
                            input = input,
                            output = output,
                            writeLock = writeLock,
                            queueId = queueId,
                            localIdGen = localIdGen,
                            ackDeduper = ackDeduper,
                            emitState = emitState
                        )
                    }
                }
                return@withContext
            } catch (t: Throwable) {
                lastError = t
                socket.closeQuietly()
                Logger.w(TAG) {
                    "live socket host failed roomId=$roomId host=${host.host}:${host.port} msg=${t.message}"
                }
            } finally {
                socket.closeQuietly()
            }
        }
        throw lastError ?: IllegalStateException("直播消息连接失败")
    }

    private suspend fun consumeSocket(
        socket: Socket,
        input: DataInputStream,
        output: OutputStream,
        writeLock: Any,
        queueId: String,
        localIdGen: AtomicLong,
        ackDeduper: AckDeduper,
        emitState: ((LiveRoomSessionState) -> LiveRoomSessionState) -> Unit
    ) = withContext(Dispatchers.IO) {
        var authOk = false
        var heartbeatJob: Job? = null

        try {
            while (isActive) {
                val packet = readPacket(input)
                handlePacket(
                    packet = packet,
                    output = output,
                    writeLock = writeLock,
                    localIdGen = localIdGen,
                    ackDeduper = ackDeduper,
                    emitState = emitState,
                    onAuthSuccess = {
                        authOk = true
                        socket.soTimeout = READ_TIMEOUT_MS
                        if (heartbeatJob == null) {
                            heartbeatJob = launch {
                                while (isActive) {
                                    delay(HEARTBEAT_INTERVAL_MS)
                                    sendPacket(output, writeLock, OP_HEARTBEAT, EMPTY_BYTES)
                                    emitState {
                                        it.copy(lastHeartbeatAtMs = System.currentTimeMillis())
                                    }
                                }
                            }
                        }
                        emitState {
                            it.copy(
                                status = LiveRoomSessionStatus.Running,
                                queueId = queueId,
                                lastError = null
                            )
                        }
                    },
                    onHeartbeat = { popular ->
                        emitState { it.copy(popularCount = popular) }
                    },
                    onMessage = { msg ->
                        emitState {
                            val next = appendMessage(it.messages, msg)
                            it.copy(messages = next, latestMessage = msg)
                        }
                    }
                )
            }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            if (!authOk && t !is EOFException) {
                throw IllegalStateException("直播消息认证失败: ${t.message}", t)
            }
            throw t
        } finally {
            heartbeatJob?.cancel()
        }
    }

    private fun handlePacket(
        packet: LivePacket,
        output: OutputStream,
        writeLock: Any,
        localIdGen: AtomicLong,
        ackDeduper: AckDeduper,
        emitState: ((LiveRoomSessionState) -> LiveRoomSessionState) -> Unit,
        onAuthSuccess: () -> Unit,
        onHeartbeat: (Long) -> Unit,
        onMessage: (LiveRoomMessage) -> Unit
    ) = when (packet.op) {
        OP_AUTH_REPLY -> {
            val json = parseJson(packet.payload)
            if (json.optInt("code", -1) != 0) {
                throw IllegalStateException("直播消息认证返回 code=${json.optInt("code", -1)}")
            }
            onAuthSuccess()
        }

        OP_HEARTBEAT_REPLY -> {
            onHeartbeat(parsePopularCount(packet.payload))
        }

        OP_MESSAGE -> {
            unpackBusinessPayload(packet).forEach { json ->
                parseAckPayload(json)?.let { ack ->
                    sendAck(
                        output = output,
                        writeLock = writeLock,
                        msgId = ack.msgId,
                        cmd = ack.cmd,
                        msgType = ack.msgType
                    )
                }
                if (ackDeduper.shouldDrop(json)) return@forEach
                parseDanmuMessage(json, localIdGen.incrementAndGet())?.let { msg ->
                    onMessage(msg)
                    return@forEach
                }
                parsePanelPatch(json)?.let { patch ->
                    emitState {
                        it.copy(panel = it.panel.merge(patch))
                    }
                }
            }
        }

        else -> Unit
    }

    private fun unpackBusinessPayload(packet: LivePacket): List<JSONObject> {
        return when (packet.version) {
            VERSION_PLAIN -> parseJsonOrNull(packet.payload)?.let(::listOf)
                ?: unpackNestedPackets(packet.payload)
            VERSION_ZLIB -> unpackNestedPackets(inflate(packet.payload))
            VERSION_BROTLI -> unpackNestedPackets(decodeBrotli(packet.payload))
            else -> emptyList()
        }
    }

    private fun unpackNestedPackets(bytes: ByteArray): List<JSONObject> {
        if (bytes.isEmpty()) return emptyList()
        val packets = mutableListOf<JSONObject>()
        var offset = 0
        while (offset + HEADER_SIZE <= bytes.size) {
            val packetLen = readInt32(bytes, offset)
            if (packetLen !in HEADER_SIZE..MAX_PACKET_SIZE || offset + packetLen > bytes.size) break
            val headerLen = readInt16(bytes, offset + 4)
            if (headerLen !in HEADER_SIZE..packetLen) break
            val version = readInt16(bytes, offset + 6)
            val op = readInt32(bytes, offset + 8)
            val payload = bytes.copyOfRange(offset + headerLen, offset + packetLen)
            if (op == OP_MESSAGE) {
                val nested = LivePacket(version, op, payload)
                packets += unpackBusinessPayload(nested)
            } else if (op == OP_AUTH_REPLY) {
                parseJsonOrNull(payload)?.let(packets::add)
            }
            offset += packetLen
        }
        return packets
    }

    private fun parseDanmuMessage(
        json: JSONObject,
        localId: Long
    ): LiveRoomMessage? {
        val rawCmd = json.optString("cmd")
        val cmd = rawCmd.substringBefore(':')
        if (cmd != CMD_DANMU && cmd != CMD_DANMU_MIRROR) return null
        val info = json.optJSONArray("info") ?: return null
        val meta = info.optJSONArray(0)
        val content = info.optString(1).takeIf(String::isNotBlank) ?: return null
        val extObj = meta?.optJSONObject(15)
        val extra = extObj?.optString("extra")
        val userObj = extObj?.optJSONObject("user")
        val baseObj = userObj?.optJSONObject("base")
        val medalObj = userObj?.optJSONObject("medal")
        val user = userObj?.let {
            LiveRoomUser(
                uid = it.optLong("uid"),
                name = baseObj?.optString("name").orEmpty(),
                avatar = baseObj?.optString("face")?.takeIf(String::isNotBlank),
                nameColor = baseObj?.optString("name_color_str")?.takeIf(String::isNotBlank)
            )
        }?.takeIf { it.uid > 0L || it.name.isNotBlank() }
        val medal = medalObj?.let {
            LiveRoomMedal(
                name = it.optString("name"),
                level = it.optInt("level"),
                colorStart = it.optString("v2_medal_color_start").takeIf(String::isNotBlank),
                colorEnd = it.optString("v2_medal_color_end").takeIf(String::isNotBlank),
                colorBorder = it.optString("v2_medal_color_border").takeIf(String::isNotBlank)
            )
        }?.takeIf { it.name.isNotBlank() || it.level > 0 }
        return LiveRoomMessage(
            localId = localId,
            msgId = json.optString("msg_id").takeIf(String::isNotBlank),
            cmd = cmd,
            title = if (cmd == CMD_DANMU_MIRROR) "镜像弹幕" else null,
            content = content,
            user = user,
            medal = medal,
            mode = meta?.optInt(1) ?: 1,
            fontSize = meta?.optInt(2) ?: 25,
            color = meta?.optInt(3) ?: 0xFFFFFF,
            sendTimeMs = json.optLong("send_time").takeIf { it > 0L }
                ?: meta?.optLong(4)?.takeIf { it > 0L }
                ?: 0L,
            isMirror = cmd == CMD_DANMU_MIRROR,
            isAck = json.optBoolean("p_is_ack"),
            msgType = json.optInt("p_msg_type").takeIf { it > 0 || json.has("p_msg_type") },
            extra = extra?.takeIf(String::isNotBlank)
        )
    }

    private fun parsePanelPatch(
        json: JSONObject
    ): LiveRoomPanelState? {
        val rawCmd = json.optString("cmd").takeIf(String::isNotBlank) ?: return null
        val cmd = rawCmd.substringBefore(':')
        return when (cmd) {
            CMD_WATCHED_CHANGE -> {
                val data = json.optJSONObject("data") ?: return null
                val content = data.optString("text_large")
                    .ifBlank { data.optString("text_small") }
                    .ifBlank { data.optString("num") }
                    .ifBlank { return null }
                LiveRoomPanelState(
                    watchedText = content
                )
            }

            CMD_ONLINE_RANK_COUNT -> {
                val data = json.optJSONObject("data") ?: return null
                val countText = data.optString("count_text")
                val content = countText
                    .ifBlank { data.optString("count") }
                    .takeIf(String::isNotBlank)
                    ?.let { "在线榜 $it" }
                    ?: return null
                LiveRoomPanelState(
                    onlineRankText = content
                )
            }

            CMD_RANK_CHANGED -> {
                val data = json.optJSONObject("data") ?: return null
                val rankName = data.optString("rank_name_by_type")
                    .ifBlank { data.optString("on_rank_name_by_type") }
                    .ifBlank { "排名变化" }
                val rank = data.optInt("rank")
                val content = if (rank > 0) {
                    "$rankName 第 $rank 名"
                } else {
                    rankName
                }
                LiveRoomPanelState(
                    rankChangedText = content
                )
            }

            else -> null
        }
    }

    private fun parseAckPayload(json: JSONObject): AckPayload? {
        if (!json.optBoolean("p_is_ack")) return null
        val msgId = json.optString("msg_id").takeIf(String::isNotBlank) ?: return null
        val cmd = json.optString("cmd").takeIf(String::isNotBlank) ?: return null
        val msgType = json.optInt("p_msg_type").takeIf { it > 0 || json.has("p_msg_type") }
            ?: return null
        return AckPayload(
            msgId = msgId,
            cmd = cmd,
            msgType = msgType
        )
    }

    private fun appendMessage(
        messages: List<LiveRoomMessage>,
        msg: LiveRoomMessage
    ): List<LiveRoomMessage> {
        if (messages.isEmpty()) return listOf(msg)
        val next = if (messages.size >= MAX_MESSAGE_COUNT) {
            ArrayList<LiveRoomMessage>(MAX_MESSAGE_COUNT).apply {
                addAll(messages.subList(1, messages.size))
                add(msg)
            }
        } else {
            ArrayList<LiveRoomMessage>(messages.size + 1).apply {
                addAll(messages)
                add(msg)
            }
        }
        return next
    }

    private fun buildAuthPayload(
        roomId: Long,
        token: String,
        queueId: String
    ): String {
        return JSONObject().apply {
            put("app_id", BiliConstants.APP_ID)
            put("brand", deviceIdentity.brand)
            put("build", BiliConstants.BUILD)
            put("buvid", deviceIdentity.buvid)
            put("channel", BiliConstants.CHANNEL)
            put("clientver", "${BiliConstants.VERSION}.${BiliConstants.BUILD}")
            put("device", BiliConstants.PLATFORM)
            put("fp_local", deviceIdentity.fp)
            put("fp_remote", deviceIdentity.fp)
            put("group", "")
            hwIdGenerator.build()?.takeIf(String::isNotBlank)?.let { put("hwid", it) }
            put("key", token)
            put("mobi_app", BiliConstants.MOBI_APP)
            put("model", deviceIdentity.model)
            put("osver", deviceIdentity.osVer)
            put("platform", BiliConstants.PLATFORM)
            put("protover", 3)
            put("queue_uuid", queueId)
            put("roomid", roomId)
            put("scene", "room")
            put("support_ack", true)
            put("uid", authProvider.mid)
            put("version_name", BiliConstants.VERSION)
        }.toString()
    }

    private fun sendAck(
        output: OutputStream,
        writeLock: Any,
        msgId: String,
        cmd: String,
        msgType: Int
    ) {
        val payload = JSONObject().apply {
            put("msg_id", msgId)
            put("cmd", cmd)
            put("p_msg_type", msgType)
        }.toString().toByteArray()
        sendPacket(output, writeLock, OP_ACK, payload)
    }

    private fun sendPacket(
        output: OutputStream,
        writeLock: Any,
        op: Int,
        payload: ByteArray
    ) {
        val buf = ByteBuffer.allocate(HEADER_SIZE + payload.size)
            .order(ByteOrder.BIG_ENDIAN)
        buf.putInt(HEADER_SIZE + payload.size)
        buf.putShort(HEADER_SIZE.toShort())
        buf.putShort(VERSION_PLAIN.toShort())
        buf.putInt(op)
        buf.putInt(0)
        buf.put(payload)
        synchronized(writeLock) {
            output.write(buf.array())
            output.flush()
        }
    }

    private fun readPacket(input: DataInputStream): LivePacket {
        val header = ByteArray(HEADER_SIZE)
        input.readFully(header)
        val packetLen = readInt32(header, 0)
        val headerLen = readInt16(header, 4)
        val version = readInt16(header, 6)
        val op = readInt32(header, 8)
        if (packetLen !in HEADER_SIZE..MAX_PACKET_SIZE) {
            throw IllegalStateException("直播消息包长度非法 packetLen=$packetLen")
        }
        if (headerLen !in HEADER_SIZE..packetLen) {
            throw IllegalStateException("直播消息包头长度非法 headerLen=$headerLen packetLen=$packetLen")
        }
        val extraHeaderLen = headerLen - HEADER_SIZE
        if (extraHeaderLen > 0) {
            skipFully(input, extraHeaderLen)
        }
        val bodyLen = packetLen - headerLen
        val payload = ByteArray(bodyLen)
        input.readFully(payload)
        return LivePacket(
            version = version,
            op = op,
            payload = payload
        )
    }

    private fun parsePopularCount(payload: ByteArray): Long {
        if (payload.size < 4) return 0L
        return readUInt32(payload)
    }

    private fun inflate(bytes: ByteArray): ByteArray {
        return readAllBytes(InflaterInputStream(bytes.inputStream()))
    }

    private fun decodeBrotli(bytes: ByteArray): ByteArray {
        return readAllBytes(BrotliInputStream(bytes.inputStream()))
    }

    private fun readAllBytes(input: InputStream): ByteArray {
        input.use { stream ->
            val out = ByteArrayOutputStream()
            val buf = ByteArray(IO_BUFFER_SIZE)
            var total = 0
            while (true) {
                val read = stream.read(buf)
                if (read <= 0) break
                total += read
                if (total > MAX_DECOMPRESSED_SIZE) {
                    throw IllegalStateException("直播消息包体超过限制")
                }
                out.write(buf, 0, read)
            }
            return out.toByteArray()
        }
    }

    private fun parseJson(payload: ByteArray): JSONObject {
        return parseJsonOrNull(payload)
            ?: throw IllegalStateException("直播消息 JSON 非法")
    }

    private fun parseJsonOrNull(payload: ByteArray): JSONObject? {
        val text = payload.toString(Charsets.UTF_8).trim()
        if (text.isBlank()) return null
        return runCatching { JSONObject(text) }.getOrNull()
    }

    private fun parseHosts(arr: JSONArray?): List<HostPort> {
        if (arr == null || arr.length() == 0) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val host = item.optString("host")
                val port = item.optInt("port")
                if (host.isBlank() || port <= 0) continue
                add(HostPort(host, port))
            }
        }
    }

    private fun nextRetryDelayMs(retryCount: Int): Long {
        val step = min(retryCount, MAX_RETRY_STEP)
        return RETRY_BASE_DELAY_MS * step
    }

    private fun AckDeduper.shouldDrop(json: JSONObject): Boolean {
        val msgType = json.optInt("p_msg_type")
        if (msgType != ACK_DEDUP_MSG_TYPE) return false
        val msgId = json.optString("msg_id").takeIf(String::isNotBlank) ?: return false
        val now = SystemClock.uptimeMillis()
        trimExpired(now)
        if (!msgIds.add(msgId)) return true
        entries.addLast(AckCacheEntry(msgId, now))
        while (entries.size > ACK_CACHE_MAX_COUNT) {
            msgIds.remove(entries.removeFirst().msgId)
        }
        return false
    }

    private fun AckDeduper.trimExpired(nowMs: Long) {
        while (entries.isNotEmpty() && nowMs - entries.first().atMs >= ACK_CACHE_WINDOW_MS) {
            msgIds.remove(entries.removeFirst().msgId)
        }
    }

    private fun AckDeduper.clear() {
        entries.clear()
        msgIds.clear()
    }

    private fun readInt32(bytes: ByteArray, offset: Int): Int {
        return ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)
    }

    private fun readInt16(bytes: ByteArray, offset: Int): Int {
        return ((bytes[offset].toInt() and 0xFF) shl 8) or
            (bytes[offset + 1].toInt() and 0xFF)
    }

    private fun readUInt32(bytes: ByteArray): Long {
        return readInt32(bytes, 0).toLong() and 0xFFFFFFFFL
    }

    private fun skipFully(input: DataInputStream, count: Int) {
        var remain = count
        while (remain > 0) {
            val skipped = input.skipBytes(remain)
            if (skipped <= 0) throw EOFException("直播消息头部不完整")
            remain -= skipped
        }
    }

    private fun Socket.closeQuietly() {
        runCatching { close() }
    }

    private data class DanmuInfo(
        val token: String,
        val hosts: List<HostPort>
    )

    private data class HostPort(
        val host: String,
        val port: Int
    )

    private class LivePacket(
        val version: Int,
        val op: Int,
        val payload: ByteArray
    )

    private data class AckPayload(
        val msgId: String,
        val cmd: String,
        val msgType: Int
    )

    private data class AckCacheEntry(
        val msgId: String,
        val atMs: Long
    )

    private class AckDeduper {
        val entries = ArrayDeque<AckCacheEntry>()
        val msgIds = HashSet<String>()
    }

    private companion object {
        const val TAG = "LiveRoomMsg"
        const val DANMU_INFO_ENDPOINT = "/xlive/app-room/v1/index/getDanmuInfo"
        const val HEADER_SIZE = 16
        // 先把单包和解压后的包体做限制,避免恶意数据导致 OOM,后续如果发现合理的包体大小可以再调整
        const val MAX_PACKET_SIZE = 2 * 1024 * 1024
        const val MAX_DECOMPRESSED_SIZE = 2 * 1024 * 1024
        const val VERSION_PLAIN = 0
        const val VERSION_ZLIB = 2
        const val VERSION_BROTLI = 3
        const val OP_HEARTBEAT = 2
        const val OP_HEARTBEAT_REPLY = 3
        const val OP_MESSAGE = 5
        const val OP_AUTH = 7
        const val OP_AUTH_REPLY = 8
        const val OP_ACK = 24
        const val HEARTBEAT_INTERVAL_MS = 30_000L
        const val CONNECT_TIMEOUT_MS = 3_000
        const val AUTH_TIMEOUT_MS = 6_000
        const val READ_TIMEOUT_MS = 65_000
        const val RETRY_BASE_DELAY_MS = 2_000L
        const val MAX_RETRY_STEP = 5
        const val MAX_MESSAGE_COUNT = 200
        const val ACK_DEDUP_MSG_TYPE = 1
        const val ACK_CACHE_MAX_COUNT = 2_000
        const val ACK_CACHE_WINDOW_MS = 30_000L
        const val IO_BUFFER_SIZE = 8 * 1024
        const val CMD_DANMU = "DANMU_MSG"
        const val CMD_DANMU_MIRROR = "DANMU_MSG_MIRROR"
        const val CMD_WATCHED_CHANGE = "WATCHED_CHANGE"
        const val CMD_ONLINE_RANK_COUNT = "ONLINE_RANK_COUNT"
        const val CMD_RANK_CHANGED = "RANK_CHANGED_V2"
        val EMPTY_BYTES = ByteArray(0)
    }
}
