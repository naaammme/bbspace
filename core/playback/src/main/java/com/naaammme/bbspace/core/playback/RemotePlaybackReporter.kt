package com.naaammme.bbspace.core.playback

import com.naaammme.bbspace.core.common.AuthProvider
import com.naaammme.bbspace.core.common.BiliConstants
import com.naaammme.bbspace.core.common.log.Logger
import com.naaammme.bbspace.core.settings.AppSettings
import com.naaammme.bbspace.core.model.PlayBiz
import com.naaammme.bbspace.core.model.PlaybackRequest
import com.naaammme.bbspace.core.model.VideoTargetTool
import com.naaammme.bbspace.infra.network.BiliRestClient
import com.naaammme.bbspace.infra.network.BiliRestParamBuilder
import com.naaammme.bbspace.infra.network.BiliRestProfile
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import org.json.JSONObject

@Singleton
class RemotePlaybackReporter @Inject constructor(
    private val restClient: BiliRestClient,
    private val authProvider: AuthProvider,
    private val restParamBuilder: BiliRestParamBuilder,
    private val playerSettings: AppSettings
) {
    internal suspend fun startHeartbeat(active: PlaybackReportSession): Long {
        if (!canReport(active)) return 0L
        val params = buildHeartbeatParams(
            active = active,
            startPacket = true
        )
        return request(HB_URL, params)
            ?.opt("data")
            ?.let(::readStartTs)
            ?: 0L
    }

    internal suspend fun endHeartbeat(active: PlaybackReportSession) {
        if (!active.reportEnabled) return
        val params = buildHeartbeatParams(
            active = active,
            startPacket = false
        )
        request(HB_URL, params)
    }

    internal suspend fun reportPlaybackHistory(
        active: PlaybackReportSession,
        positionMs: Long,
        completePlayback: Boolean
    ) {
        if (canReport(active)) {
            request(
                PLAYBACK_HISTORY_URL,
                buildPlaybackHistoryParams(active, positionMs, completePlayback)
            )
        }
    }

    private suspend fun request(
        url: String,
        params: Map<String, String>
    ): JSONObject? {
        return runCatching {
            restClient.postSignedRaw(url, params, BiliRestProfile.APP).also { json ->
                val code = json.optInt("code", 0)
                if (code != 0) {
                    Logger.w(TAG) { "report code=$code msg=${json.optString("message")}" }
                }
            }
        }.onFailure { error ->
            Logger.w(TAG) { "report failed ${error.message}" }
        }.getOrNull()
    }

    private fun buildHeartbeatParams(
        active: PlaybackReportSession,
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
        base["video_duration"] = active.durationSec().toString()
        base["play_type"] = playType(active.request).toString()
        base["network_type"] = NETWORK_TYPE_WIFI
        base["from"] = src.from
        base["from_spmid"] = src.fromSpmid
        base["spmid"] = VideoTargetTool.SPMID
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

    private fun buildPlaybackHistoryParams(
        active: PlaybackReportSession,
        positionMs: Long,
        completePlayback: Boolean
    ): Map<String, String> {
        val report = active.report
        val now = nowSec()
        val base = mutableMapOf<String, String>()
        base["aid"] = report.aid.toString()
        base["cid"] = report.cid.toString()
        base["duration"] = active.durationSec().toString()
        base["progress"] = progressSec(positionMs, completePlayback).toString()
        base["type"] = report.type.toString()
        base["device_ts"] = now.toString()
        base["start_ts"] = active.playbackHistoryStartTs.toString()
        base["source"] = PLAYBACK_HISTORY_SOURCE
        base["scene"] = PLAYBACK_HISTORY_SCENE_FRONT
        report.seasonId?.takeIf { it > 0L }?.let { base["sid"] = it.toString() }
        report.epId?.takeIf { it > 0L }?.let { base["epid"] = it.toString() }
        report.subType?.let { base["sub_type"] = it.toString() }
        if (report.biz != PlayBiz.UGC) {
            base["realtime"] = msToSec(active.actualPlayedTimeMs).toString()
        }
        return buildCommonParams(now) + base
    }

    private fun buildCommonParams(ts: Long): Map<String, String> {
        return restParamBuilder.app(BiliRestProfile.APP, ts, authProvider.accessToken)
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

    private fun readStartTs(data: Any): Long {
        return when (data) {
            is JSONObject -> data.optLong("ts", 0L)
            is String -> runCatching { JSONObject(data).optLong("ts", 0L) }.getOrDefault(0L)
            else -> 0L
        }
    }

    private suspend fun canReport(active: PlaybackReportSession): Boolean {
        return active.reportEnabled && playerSettings.state.first().playback.reportPlayback
    }

    private companion object {
        const val TAG = "PlaybackReporter"
        const val PLAYBACK_HISTORY_SOURCE = "player-old"
        const val PLAYBACK_HISTORY_SCENE_FRONT = "front"
        const val ZERO = "0"
        const val ONE = "1"
        const val USER_STATUS = "0"
        const val NETWORK_TYPE_WIFI = "1"
        const val DEFAULT_PLAY_MODE = "1"
        const val HB_URL = "${BiliConstants.BASE_URL_API}/x/report/heartbeat/mobile"
        const val PLAYBACK_HISTORY_URL = "${BiliConstants.BASE_URL_API}/x/v2/history/report"
    }
}
