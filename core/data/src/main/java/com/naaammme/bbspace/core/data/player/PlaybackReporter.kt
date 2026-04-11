package com.naaammme.bbspace.core.data.player

import android.os.SystemClock
import com.naaammme.bbspace.core.common.AuthProvider
import com.naaammme.bbspace.core.common.BiliConstants
import com.naaammme.bbspace.core.common.log.Logger
import com.naaammme.bbspace.core.domain.history.LocalHistoryRepository
import com.naaammme.bbspace.core.model.LocalHistoryKey
import com.naaammme.bbspace.core.model.PlayBiz
import com.naaammme.bbspace.core.model.PlayReportParams
import com.naaammme.bbspace.core.model.PlaybackRequest
import com.naaammme.bbspace.core.model.PlaybackSource
import com.naaammme.bbspace.core.model.PlayerSessionState
import com.naaammme.bbspace.core.model.VideoHistory
import com.naaammme.bbspace.core.model.VideoHistoryMeta
import com.naaammme.bbspace.core.model.VideoRouteTool
import com.naaammme.bbspace.infra.crypto.BiliSessionId
import com.naaammme.bbspace.infra.crypto.DeviceIdentity
import com.naaammme.bbspace.infra.network.BiliRestClient
import com.naaammme.bbspace.infra.player.EnginePlaybackState
import com.naaammme.bbspace.infra.player.PlaybackSnapshot
import java.util.Locale
import kotlin.math.max
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackReporter @Inject constructor(
    private val restClient: BiliRestClient,
    private val authProvider: AuthProvider,
    private val deviceIdentity: DeviceIdentity,
    private val localHistoryRepo: LocalHistoryRepository
) {
    private val mu = Mutex()
    private var pageOwnerId = 0L
    private var pagePolarisId = ""
    @Volatile
    private var pageMeta: VideoHistoryMeta? = null
    private var ctx: SessionCtx? = null

    fun bindOwner(who: Long) {
        if (pageOwnerId == who) return
        pageOwnerId = who
        pagePolarisId = BiliSessionId.polarisAction()
        pageMeta = null
    }

    fun updateVideoMeta(meta: VideoHistoryMeta?) {
        pageMeta = meta
    }

    suspend fun startSession(
        request: PlaybackRequest,
        state: PlayerSessionState,
        startPositionMs: Long
    ) {
        val token = authProvider.accessToken
        val source = state.playbackSource ?: return
        val quality = state.currentStream?.quality ?: DEFAULT_QN
        val startSec = msToSec(startPositionMs)
        val nowElapsed = SystemClock.elapsedRealtime()
        mu.withLock {
            ctx = SessionCtx(
                uid = authProvider.mid,
                request = request,
                source = source,
                report = source.report,
                sessionId = BiliSessionId.view(deviceIdentity.buvid),
                polarisActionId = pagePolarisId.ifBlank(BiliSessionId::polarisAction),
                reportEnabled = token.isNotBlank(),
                historyStartTs = nowSec(),
                startElapsedMs = nowElapsed,
                lastSampleElapsedMs = nowElapsed,
                quality = quality,
                lastProgressSec = startSec,
                maxProgressSec = startSec
            )
        }
    }

    suspend fun onPlaybackState(
        state: PlayerSessionState,
        snapshot: PlaybackSnapshot
    ) {
        val current = mu.withLock {
            val active = ctx ?: return@withLock null
            if (!sameSource(active.source, state.playbackSource)) return@withLock null
            val prev = active.lastSnapshot
            sync(active, snapshot, state)
            active.lastSnapshot = snapshot
            SnapshotEdge(
                ctx = active.copy(),
                firstFrame = !active.heartbeatStarted && snapshot.firstFrameSeq > 0L,
                paused = prev.isPlaying && !snapshot.isPlaying && snapshot.playbackState != EnginePlaybackState.Ended,
                ended = !active.finalized &&
                        snapshot.playbackState == EnginePlaybackState.Ended &&
                        prev.playbackState != EnginePlaybackState.Ended,
                interval = active.historyStarted &&
                        snapshot.isPlaying &&
                        active.lastHistoryReportAt > 0L &&
                        active.lastSampleElapsedMs - active.lastHistoryReportAt >= HISTORY_INTERVAL_MS,
                startHistory = !active.historyStarted && snapshot.firstFrameSeq > 0L
            )
        }
        current ?: return

        if (current.firstFrame) {
            startHeartbeat(current.ctx)
            startHistory(current.ctx, snapshot)
        } else if (current.startHistory) {
            startHistory(current.ctx, snapshot)
        }

        if (current.interval) {
            reportHistory(current.ctx, snapshot, complete = false)
        }
        if (current.paused) {
            reportHistory(current.ctx, snapshot, complete = false)
        }
        if (current.ended) {
            reportHistory(current.ctx, snapshot, complete = true)
            endHeartbeat(current.ctx)
            mu.withLock {
                ctx = ctx?.takeIf { it.sessionId != current.ctx.sessionId } ?: ctx?.copy(finalized = true)
            }
        }
    }

    suspend fun finishSession(snapshot: PlaybackSnapshot) {
        val active = mu.withLock {
            val current = ctx ?: return@withLock null
            sync(current, snapshot, null)
            current.lastSnapshot = snapshot
            current.copy()
        }
        active ?: return

        if (!active.finalized) {
            reportHistory(active, snapshot, complete = isComplete(active, snapshot, allowEndedOnly = false))
            endHeartbeat(active)
        }

        mu.withLock { ctx = null }
    }

    private suspend fun startHeartbeat(active: SessionCtx) {
        val ts = if (active.reportEnabled) {
            val params = buildHeartbeatParams(
                active = active,
                startPacket = true
            )
            request(HB_URL, params)
                ?.opt("data")
                ?.let(::readStartTs)
                ?: 0L
        } else {
            0L
        }

        mu.withLock {
            val current = ctx ?: return@withLock
            if (current.sessionId != active.sessionId) return@withLock
            ctx = current.copy(
                heartbeatStarted = true,
                heartbeatStartTs = ts,
                lastHistoryReportAt = current.lastHistoryReportAt
            )
        }
    }

    private suspend fun endHeartbeat(active: SessionCtx) {
        if (active.heartbeatEnded) return
        if (active.reportEnabled) {
            val params = buildHeartbeatParams(
                active = active,
                startPacket = false
            )
            request(HB_URL, params)
        }
        mu.withLock {
            val current = ctx ?: return@withLock
            if (current.sessionId != active.sessionId) return@withLock
            ctx = current.copy(heartbeatEnded = true, finalized = true)
        }
    }

    private suspend fun startHistory(
        active: SessionCtx,
        snapshot: PlaybackSnapshot
    ) {
        reportHistory(active, snapshot, complete = false)
        mu.withLock {
            val current = ctx ?: return@withLock
            if (current.sessionId != active.sessionId) return@withLock
            ctx = current.copy(historyStarted = true, lastHistoryReportAt = current.lastSampleElapsedMs)
        }
    }

    private suspend fun reportHistory(
        active: SessionCtx,
        snapshot: PlaybackSnapshot,
        complete: Boolean
    ) {
        saveLocalHistory(active, snapshot)
        if (active.reportEnabled) {
            request(HISTORY_URL, buildHistoryParams(active, snapshot, complete))
        }
        mu.withLock {
            val current = ctx ?: return@withLock
            if (current.sessionId != active.sessionId) return@withLock
            ctx = current.copy(
                historyStarted = true,
                lastHistoryReportAt = current.lastSampleElapsedMs
            )
        }
    }

    private suspend fun saveLocalHistory(
        active: SessionCtx,
        snapshot: PlaybackSnapshot
    ) {
        val key = LocalHistoryKey.video(active.report)
        val meta = pageMeta
        val nowMs = System.currentTimeMillis()
        localHistoryRepo.upsertVideo(
            VideoHistory(
                uid = active.uid,
                key = key,
                biz = active.report.biz.name.lowercase(Locale.ROOT),
                aid = active.report.aid,
                cid = active.report.cid,
                bvid = active.report.bvid,
                epId = active.report.epId,
                seasonId = active.report.seasonId,
                title = meta?.title.orEmpty(),
                cover = meta?.cover,
                part = meta?.part,
                partTitle = meta?.partTitle,
                ownerUid = meta?.ownerUid,
                ownerName = meta?.ownerName,
                durationMs = active.source.durationMs.coerceAtLeast(0L),
                progressMs = snapshot.positionMs.coerceAtLeast(0L),
                watchMs = active.actualPlayedTimeMs.coerceAtLeast(0L),
                updatedAt = nowMs,
                finished = isComplete(active, snapshot, allowEndedOnly = false)
            )
        )
    }

    private suspend fun request(
        url: String,
        params: Map<String, String>
    ): JSONObject? {
        return runCatching {
            restClient.postSignedRaw(url, params).also { json ->
                val code = json.optInt("code", 0)
                if (code != 0) {
                    Logger.w(TAG) { "report code=$code msg=${json.optString("message")}" }
                }
            }
        }.onFailure { error ->
            Logger.w(TAG) { "report failed ${error.message}" }
        }.getOrNull()
    }

    private fun sync(
        active: SessionCtx,
        snapshot: PlaybackSnapshot,
        state: PlayerSessionState?
    ) {
        val nowElapsed = SystemClock.elapsedRealtime()
        val deltaMs = (nowElapsed - active.lastSampleElapsedMs).coerceAtLeast(0L)
        if (deltaMs > 0L) {
            when {
                active.lastSnapshot.isPlaying -> {
                    active.playedTimeMs += deltaMs
                    active.actualPlayedTimeMs += deltaMs
                }
                active.heartbeatStarted && active.lastSnapshot.playbackState == EnginePlaybackState.Ready -> {
                    active.pausedTimeMs += deltaMs
                }
            }
        }
        active.totalTimeMs = (nowElapsed - active.startElapsedMs).coerceAtLeast(0L)
        active.lastProgressSec = msToSec(snapshot.positionMs)
        active.maxProgressSec = max(active.maxProgressSec, active.lastProgressSec)
        active.lastSampleElapsedMs = nowElapsed
        state?.currentStream?.quality?.takeIf { it > 0 }?.let { active.quality = it }
    }

    private fun buildHeartbeatParams(
        active: SessionCtx,
        startPacket: Boolean
    ): Map<String, String> {
        val src = active.request.playable.src
        val report = active.report
        val now = nowSec()
        val common = buildCommonParams(now)
        val base = mutableMapOf<String, String>()
        base["session"] = active.sessionId
        base["mid"] = active.uid.toString()
        base["aid"] = report.aid.toString()
        base["cid"] = report.cid.toString()
        base["type"] = report.type.toString()
        base["sub_type"] = (report.subType ?: 0).toString()
        base["quality"] = active.quality.toString()
        base["video_duration"] = durationSec(active.source).toString()
        base["play_type"] = playType(active.request).toString()
        base["network_type"] = NETWORK_TYPE_WIFI
        base["from"] = src.from
        base["from_spmid"] = src.fromSpmid
        base["spmid"] = VideoRouteTool.SPMID
        base["play_status"] = playStatus(active.request).toString()
        base["user_status"] = USER_STATUS
        base["auto_play"] = ZERO
        base["play_mode"] = DEFAULT_PLAY_MODE
        base["cur_language"] = ""
        base["oaid"] = ""
        base["is_auto_qn"] = ONE
        base["polaris_action_id"] = active.polarisActionId
        base["extra"] = JSONObject().put("from_outer_spmid", src.fromSpmid).toString()
        src.trackId?.takeIf(String::isNotBlank)?.let { base["track_id"] = it }
        src.reportFlowData?.takeIf(String::isNotBlank)?.let { base["report_flow_data"] = it }
        report.seasonId?.takeIf { it > 0L }?.let { base["sid"] = it.toString() }
        report.epId?.takeIf { it > 0L }?.let { base["epid"] = it.toString() }

        if (startPacket) {
            base["start_ts"] = ZERO
            base["total_time"] = ZERO
            base["paused_time"] = ZERO
            base["played_time"] = ZERO
            base["last_play_progress_time"] = ZERO
            base["max_play_progress_time"] = ZERO
            base["actual_played_time"] = ZERO
            base["list_play_time"] = ZERO
            base["miniplayer_play_time"] = ZERO
        } else {
            base["start_ts"] = active.heartbeatStartTs.toString()
            base["total_time"] = msToSec(active.totalTimeMs).toString()
            base["paused_time"] = msToSec(active.pausedTimeMs).toString()
            base["played_time"] = msToSec(active.playedTimeMs).toString()
            base["last_play_progress_time"] = active.lastProgressSec.toString()
            base["max_play_progress_time"] = active.maxProgressSec.toString()
            base["actual_played_time"] = msToSec(active.actualPlayedTimeMs).toString()
            base["list_play_time"] = ZERO
            base["miniplayer_play_time"] = ZERO
        }

        if (report.biz != PlayBiz.UGC) {
            base["realtime"] = msToSec(active.actualPlayedTimeMs).toString()
        }

        return common + base
    }

    private fun buildHistoryParams(
        active: SessionCtx,
        snapshot: PlaybackSnapshot,
        complete: Boolean
    ): Map<String, String> {
        val report = active.report
        val now = nowSec()
        val base = mutableMapOf<String, String>()
        base["aid"] = report.aid.toString()
        base["cid"] = report.cid.toString()
        base["duration"] = durationSec(active.source).toString()
        base["progress"] = progressSec(active, snapshot, complete).toString()
        base["type"] = report.type.toString()
        base["device_ts"] = now.toString()
        base["start_ts"] = active.historyStartTs.toString()
        base["source"] = HISTORY_SOURCE
        base["scene"] = HISTORY_SCENE_FRONT
        report.seasonId?.takeIf { it > 0L }?.let { base["sid"] = it.toString() }
        report.epId?.takeIf { it > 0L }?.let { base["epid"] = it.toString() }
        report.subType?.let { base["sub_type"] = it.toString() }
        if (report.biz != PlayBiz.UGC) {
            base["realtime"] = msToSec(active.actualPlayedTimeMs).toString()
        }
        return buildCommonParams(now) + base
    }

    private fun buildCommonParams(ts: Long): Map<String, String> {
        val token = authProvider.accessToken
        return buildMap {
            put("build", BiliConstants.BUILD_STR)
            put("c_locale", LOCALE)
            put("channel", BiliConstants.CHANNEL)
            put("disable_rcmd", ZERO)
            put("mobi_app", BiliConstants.MOBI_APP)
            put("platform", BiliConstants.PLATFORM)
            put("s_locale", LOCALE)
            put("statistics", BiliConstants.STATISTICS_JSON)
            put("ts", ts.toString())
            if (token.isNotBlank()) {
                put("access_key", token)
            }
        }
    }

    private fun playType(request: PlaybackRequest): Int {
        return request.playable.biz.playType ?: when (request.playable.biz.biz) {
            PlayBiz.PGC -> 2
            else -> 1
        }
    }

    private fun playStatus(request: PlaybackRequest): Int {
        return when (request.playable.biz.biz) {
            PlayBiz.PGC -> 1
            else -> 0
        }
    }

    private fun durationSec(source: PlaybackSource): Int {
        return msToSec(source.durationMs).coerceAtLeast(0)
    }

    private fun progressSec(
        active: SessionCtx,
        snapshot: PlaybackSnapshot,
        complete: Boolean
    ): Int {
        if (complete && isComplete(active, snapshot, allowEndedOnly = false)) return -1
        return msToSec(snapshot.positionMs).coerceAtLeast(0)
    }

    private fun isComplete(
        active: SessionCtx,
        snapshot: PlaybackSnapshot,
        allowEndedOnly: Boolean
    ): Boolean {
        if (active.source.isPreview) return false
        if (snapshot.playbackState == EnginePlaybackState.Ended) return true
        if (allowEndedOnly) return false
        val durationMs = active.source.durationMs
        if (durationMs <= 0L) return false
        return durationMs - snapshot.positionMs <= COMPLETE_THRESHOLD_MS
    }

    private fun readStartTs(data: Any): Long {
        return when (data) {
            is JSONObject -> data.optLong("ts", 0L)
            is String -> runCatching { JSONObject(data).optLong("ts", 0L) }.getOrDefault(0L)
            else -> 0L
        }
    }

    private fun msToSec(value: Long): Int {
        return (value.coerceAtLeast(0L) / 1000L).toInt()
    }

    private fun nowSec(): Long {
        return System.currentTimeMillis() / 1000L
    }

    private fun sameSource(
        left: PlaybackSource,
        right: PlaybackSource?
    ): Boolean {
        return right != null &&
                left.videoId.aid == right.videoId.aid &&
                left.videoId.cid == right.videoId.cid
    }

    private data class SessionCtx(
        val uid: Long,
        val request: PlaybackRequest,
        val source: PlaybackSource,
        val report: PlayReportParams,
        val sessionId: String,
        val polarisActionId: String,
        val reportEnabled: Boolean,
        val historyStartTs: Long,
        val startElapsedMs: Long,
        var lastSampleElapsedMs: Long,
        var quality: Int,
        var lastProgressSec: Int,
        var maxProgressSec: Int,
        var totalTimeMs: Long = 0L,
        var playedTimeMs: Long = 0L,
        var actualPlayedTimeMs: Long = 0L,
        var pausedTimeMs: Long = 0L,
        var heartbeatStartTs: Long = 0L,
        var heartbeatStarted: Boolean = false,
        var heartbeatEnded: Boolean = false,
        var historyStarted: Boolean = false,
        var finalized: Boolean = false,
        var lastHistoryReportAt: Long = 0L,
        var lastSnapshot: PlaybackSnapshot = PlaybackSnapshot()
    )

    private data class SnapshotEdge(
        val ctx: SessionCtx,
        val firstFrame: Boolean,
        val paused: Boolean,
        val ended: Boolean,
        val interval: Boolean,
        val startHistory: Boolean
    )

    private companion object {
        const val TAG = "PlaybackReporter"
        const val HISTORY_SOURCE = "player-old"
        const val HISTORY_SCENE_FRONT = "front"
        const val LOCALE = "zh-Hans_CN"
        const val DEFAULT_QN = 64
        const val ZERO = "0"
        const val ONE = "1"
        const val USER_STATUS = "0"
        const val NETWORK_TYPE_WIFI = "1"
        const val DEFAULT_PLAY_MODE = "1"
        const val COMPLETE_THRESHOLD_MS = 3_000L
        const val HISTORY_INTERVAL_MS = 30_000L
        const val HB_URL = "${BiliConstants.BASE_URL_API}/x/report/heartbeat/mobile"
        const val HISTORY_URL = "${BiliConstants.BASE_URL_API}/x/v2/history/report"
    }
}
