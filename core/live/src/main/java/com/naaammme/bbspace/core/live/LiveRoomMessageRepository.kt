package com.naaammme.bbspace.core.live

import android.os.SystemClock
import com.naaammme.bbspace.core.common.AuthProvider
import com.naaammme.bbspace.core.common.BiliConstants
import com.naaammme.bbspace.core.common.log.Logger
import com.naaammme.bbspace.core.auth.AuthRepository
import com.naaammme.bbspace.core.published.PublishedRecordRepository
import com.naaammme.bbspace.core.model.LiveRoomMedal
import com.naaammme.bbspace.core.model.LiveRoomMessage
import com.naaammme.bbspace.core.model.LiveRoomPanelState
import com.naaammme.bbspace.core.model.LiveRoomSessionState
import com.naaammme.bbspace.core.model.LiveRoomUser
import com.naaammme.bbspace.core.model.PUBLISHED_RECORD_KIND_LIVE_DANMAKU
import com.naaammme.bbspace.core.model.PublishedRecord
import com.naaammme.bbspace.core.model.PublishedRecordKeyTool
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
class LiveRoomMessageRepository @Inject constructor(
    private val restClient: BiliRestClient,
    private val restParamBuilder: BiliRestParamBuilder,
    private val authRepository: AuthRepository,
    private val authProvider: AuthProvider,
    private val publishedRecordRepo: PublishedRecordRepository,
    private val deviceIdentity: DeviceIdentity,
    private val hwIdGenerator: HwIdGenerator
) {

    fun observeRoomSession(roomId: Long): Flow<LiveRoomSessionState> = flow {
        if (roomId <= 0L) {
            emit(
                LiveRoomSessionState(
                    roomId = roomId,
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
                        pushState { it.copy(lastError = null) }
                        fetchDanmuInfo(roomId)
                    } catch (t: Throwable) {
                        if (t is CancellationException) throw t
                        val msg = t.message ?: "获取直播弹幕配置失败"
                        Logger.e(TAG, t) { "fetch danmu info failed roomId=$roomId msg=$msg" }
                        pushState { it.copy(lastError = msg) }
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
                        pushState { it.copy(lastError = msg) }
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

    suspend fun sendDanmaku(
        roomId: Long,
        content: String,
        jumpFrom: Int
    ) {
        require(roomId > 0L) { "直播间 roomId 无效" }
        val msg = content.trim()
        require(msg.isNotEmpty()) { "弹幕内容不能为空" }
        val accessToken = authProvider.accessToken.takeIf(String::isNotBlank)
            ?: throw IllegalStateException("请先登录后再发送弹幕")
        val mid = authProvider.mid.takeIf { it > 0L }
            ?: throw IllegalStateException("当前账号信息无效")
        val sentAtMs = System.currentTimeMillis()
        val ts = System.currentTimeMillis() / 1000L
        val dataExtend = buildBasicDataExtend()
        val liveStatistics = buildBasicLiveStatistics(
            roomId = roomId,
            jumpFrom = jumpFrom,
            dataExtend = dataExtend
        )
        restClient.postSigned(
            url = "${BiliConstants.BASE_URL_LIVE_API}$SEND_DANMAKU_ENDPOINT",
            params = restParamBuilder.app(BiliRestProfile.APP, ts, accessToken) + buildMap {
                put("actionKey", "appkey")
                put("av_id", UNKNOWN_EXT_VALUE)
                put("bubble", "0")
                put("bussiness_extend", DEFAULT_BUSSINESS_EXTEND)
                put("cid", roomId.toString())
                put("color", DEFAULT_DANMAKU_COLOR.toString())
                put("data_extend", dataExtend)
                put("device", BiliConstants.PLATFORM)
                put("dm_type", "0")
                put("flow_extend", DEFAULT_FLOW_EXTEND)
                put("fontsize", DEFAULT_DANMAKU_FONT_SIZE.toString())
                put("jumpfrom", jumpFrom.toString())
                put("jumpfrom_extend", UNKNOWN_EXT_VALUE)
                put("launch_id", DEFAULT_LAUNCH_ID)
                put("live_statistics", liveStatistics)
                put("live_status", DEFAULT_LIVE_STATUS)
                put("mid", mid.toString())
                put("mode", DEFAULT_DANMAKU_MODE.toString())
                put("msg", msg)
                put("msg_type", "0")
                put("playTime", DEFAULT_PLAY_TIME)
                put("pool", "0")
                put("reply_attr", "0")
                put("reply_mid", "0")
                put("reply_type", "0")
                put("reply_uname", "")
                put("rnd", randomDanmakuSeed())
                put("room_type", "0")
                put("screen_status", DEFAULT_SCREEN_STATUS)
                put("session_id", UNKNOWN_EXT_VALUE)
                put("type", "json")
            },
            profile = BiliRestProfile.APP
        )
        runCatching {
            publishedRecordRepo.save(
                buildLiveDanmakuRecord(
                    roomId = roomId,
                    senderMid = mid,
                    content = msg,
                    sentAtMs = sentAtMs
                )
            )
        }.onFailure { err ->
            Logger.e(TAG, err) { "save live danmaku record failed roomId=$roomId" }
        }
    }

    private fun buildLiveDanmakuRecord(
        roomId: Long,
        senderMid: Long,
        content: String,
        sentAtMs: Long
    ): PublishedRecord {
        val itemId = sentAtMs
        val user = authRepository.getUserInfo()
        return PublishedRecord(
            key = PublishedRecordKeyTool.liveDanmaku(
                roomId = roomId,
                senderMid = senderMid,
                itemId = itemId
            ),
            kind = PUBLISHED_RECORD_KIND_LIVE_DANMAKU,
            itemId = itemId,
            targetId = roomId,
            targetType = 0L,
            senderMid = senderMid,
            senderName = user?.name?.ifBlank { null } ?: "用户$senderMid",
            senderAvatar = user?.avatar.orEmpty(),
            content = content,
            ctime = sentAtMs / 1000L
        )
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
                emitState { it.copy(lastError = null) }
                socket.tcpNoDelay = true
                socket.keepAlive = true
                socket.connect(InetSocketAddress(host.host, host.port), CONNECT_TIMEOUT_MS)
                socket.soTimeout = AUTH_TIMEOUT_MS
                socket.getOutputStream().use { output ->
                    DataInputStream(socket.getInputStream()).use { input ->
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
                                }
                            }
                        }
                        emitState { it.copy(lastError = null) }
                    },
                    onHeartbeat = { popular ->
                        emitState { it.copy(popularCount = popular) }
                    },
                    onMessage = { msg ->
                        emitState {
                            val next = appendMessage(it.messages, msg)
                            it.copy(messages = next)
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

    private fun buildBasicDataExtend(): String {
        return JSONObject().apply {
            put("from_launch_id", DEFAULT_LAUNCH_ID)
            put("from_session_id", UNKNOWN_EXT_VALUE)
            put("live_key", UNKNOWN_EXT_VALUE)
            put("sub_session_key", UNKNOWN_EXT_VALUE)
        }.toString()
    }

    private fun buildBasicLiveStatistics(
        roomId: Long,
        jumpFrom: Int,
        dataExtend: String
    ): String {
        return JSONObject().apply {
            put("buvid", deviceIdentity.buvid.ifBlank { UNKNOWN_EXT_VALUE })
            put("session_id", UNKNOWN_EXT_VALUE)
            put("launch_id", DEFAULT_LAUNCH_ID)
            put("jumpfrom", jumpFrom.toString())
            put("jumpfrom_extend", UNKNOWN_EXT_VALUE)
            put("screen_status", DEFAULT_SCREEN_STATUS)
            put("live_status", DEFAULT_LIVE_STATUS)
            put("av_id", UNKNOWN_EXT_VALUE)
            put("flow_extend", DEFAULT_FLOW_EXTEND)
            put("bussiness_extend", DEFAULT_BUSSINESS_EXTEND)
            put("data_extend", dataExtend)
            put("spm_id", UNKNOWN_EXT_VALUE)
            put("up_id", UNKNOWN_EXT_VALUE)
            put("room_id", roomId.toString())
            put("parent_area_id", UNKNOWN_EXT_VALUE)
            put("area_id", UNKNOWN_EXT_VALUE)
            put("simple_id", UNKNOWN_EXT_VALUE)
            put("room_category", "0")
            put("official_channel", UNKNOWN_EXT_VALUE)
            put("if_dual_screen", "0")
            put("subscreen_scale", "0")
            put("dual_screen_model", "0")
            put("gift_method", UNKNOWN_EXT_VALUE)
            put("gift_position", UNKNOWN_EXT_VALUE)
            put("gift_subname", UNKNOWN_EXT_VALUE)
        }.toString()
    }

    private fun randomDanmakuSeed(): String {
        return (UUID.randomUUID().mostSignificantBits.toInt()).toString()
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
        const val SEND_DANMAKU_ENDPOINT = "/xlive/app-room/v1/dM/sendmsg"
        const val DEFAULT_DANMAKU_MODE = 1
        const val DEFAULT_DANMAKU_FONT_SIZE = 25
        const val DEFAULT_DANMAKU_COLOR = 0xFFFFFF
        const val DEFAULT_LAUNCH_ID = "-99998"
        const val DEFAULT_LIVE_STATUS = "live"
        const val DEFAULT_PLAY_TIME = "0.0"
        const val DEFAULT_SCREEN_STATUS = "2"
        const val UNKNOWN_EXT_VALUE = "-99998"
        const val DEFAULT_FLOW_EXTEND = """{"position":"1","s_position":"1","slide_direction":"-99998"}"""
        const val DEFAULT_BUSSINESS_EXTEND = """{"broadcast_type":"1","stream_scale":"1","watch_ui_type":"3"}"""
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
