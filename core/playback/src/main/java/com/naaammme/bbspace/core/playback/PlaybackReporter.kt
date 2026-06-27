package com.naaammme.bbspace.core.playback

import android.os.SystemClock
import com.naaammme.bbspace.core.common.AuthProvider
import com.naaammme.bbspace.core.settings.AppSettings
import com.naaammme.bbspace.core.model.PlayReportParams
import com.naaammme.bbspace.core.model.PlaybackProgress
import com.naaammme.bbspace.core.model.PlaybackRequest
import com.naaammme.bbspace.core.model.PlaybackState
import com.naaammme.bbspace.core.model.PlayerSessionState
import com.naaammme.bbspace.infra.crypto.BiliSessionId
import com.naaammme.bbspace.infra.crypto.DeviceIdentity
import com.naaammme.bbspace.infra.player.PlayerPlaybackState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class PlaybackReporter @Inject constructor(
    private val authProvider: AuthProvider,
    private val deviceIdentity: DeviceIdentity,
    private val playerSettings: AppSettings,
    private val localHistoryRecorder: LocalPlaybackHistoryRecorder,
    private val remoteReporter: RemotePlaybackReporter
) {
    private val mu = Mutex()
    private var pageOwnerId = 0L
    private var pagePolarisId = ""
    private var ctx: PlaybackReportSession? = null

    fun bindOwner(who: Long) {
        if (pageOwnerId == who) return
        pageOwnerId = who
        pagePolarisId = BiliSessionId.polarisAction()
    }

    suspend fun startSession(
        request: PlaybackRequest,
        state: PlayerSessionState,
        report: PlayReportParams,
        startPositionMs: Long
    ) {
        val source = state.playbackSource ?: return
        val quality = state.currentStream?.quality ?: DEFAULT_QN
        val startSec = msToSec(startPositionMs)
        val nowElapsed = SystemClock.elapsedRealtime()
        val reportEnabled = playerSettings.state.first().playback.reportPlayback
        mu.withLock {
            val active = ctx
            if (
                active != null &&
                active.request == request &&
                active.source == source &&
                active.report == report
            ) {
                return@withLock
            }
            ctx = PlaybackReportSession(
                uid = authProvider.mid,
                request = request,
                source = source,
                report = report,
                sessionId = BiliSessionId.view(deviceIdentity.buvid),
                polarisActionId = pagePolarisId.ifBlank(BiliSessionId::polarisAction),
                reportEnabled = reportEnabled,
                playbackHistoryStartTs = nowSec(),
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
        playbackState: PlayerPlaybackState,
        progress: PlaybackProgress
    ) {
        val current = mu.withLock {
            val active = ctx ?: return@withLock null
            if (active.source != state.playbackSource) return@withLock null
            val prevPlayWhenReady = active.lastPlayWhenReady
            val prevPlaybackState = active.lastPlaybackState
            active.sync(progress.positionMs, state)
            active.lastIsPlaying = playbackState.isPlaying
            active.lastPlayWhenReady = playbackState.playWhenReady
            active.lastPlaybackState = playbackState.playbackState
            val firstFrame = !active.heartbeatStarted && playbackState.firstFrameSeq > 0L
            val startPlaybackHistory = !active.playbackHistoryStarted && playbackState.firstFrameSeq > 0L
            if (firstFrame) {
                active.heartbeatStarted = true
            }
            if (firstFrame || startPlaybackHistory) {
                active.playbackHistoryStarted = true
            }
            SnapshotEdge(
                ctx = active.copy(),
                firstFrame = firstFrame,
                paused = prevPlayWhenReady &&
                        !playbackState.playWhenReady &&
                        playbackState.playbackState == PlaybackState.Ready,
                ended = !active.finalized &&
                        playbackState.playbackState == PlaybackState.Ended &&
                        prevPlaybackState != PlaybackState.Ended,
                startPlaybackHistory = startPlaybackHistory
            )
        }
        current ?: return

        if (current.firstFrame) {
            startHeartbeat(current.ctx)
            startPlaybackHistory(current.ctx, progress.positionMs)
        } else if (current.startPlaybackHistory) {
            startPlaybackHistory(current.ctx, progress.positionMs)
        }

        if (current.paused && current.ctx.playbackHistoryStarted) {
            reportPlaybackHistory(current.ctx, progress.positionMs, completePlayback = false)
        }
        if (current.ended && current.ctx.playbackHistoryStarted) {
            reportPlaybackHistory(current.ctx, progress.positionMs, completePlayback = true)
            endHeartbeat(current.ctx)
            mu.withLock {
                ctx = ctx?.takeIf { it.sessionId != current.ctx.sessionId } ?: ctx?.copy(finalized = true)
            }
        }
    }

    suspend fun finishSession(
        playbackState: PlayerPlaybackState,
        progress: PlaybackProgress
    ) {
        val active = detachSession(
            playbackState = playbackState.playbackState,
            positionMs = progress.positionMs
        ) ?: return
        finalizeSession(active)
    }

    internal suspend fun detachSession(
        playbackState: PlaybackState,
        positionMs: Long
    ): DetachedPlaybackReport? {
        return mu.withLock {
            val current = ctx ?: return@withLock null
            current.sync(positionMs, null)
            current.lastPlaybackState = playbackState
            val detached = DetachedPlaybackReport(
                session = current.copy(),
                playbackState = playbackState,
                positionMs = positionMs
            )
            if (ctx?.sessionId == current.sessionId) {
                ctx = null
            }
            detached
        }
    }

    internal suspend fun finalizeSession(report: DetachedPlaybackReport) {
        val active = report.session
        if (!active.playbackHistoryStarted) return
        if (!active.finalized) {
            val completePlayback = active.isComplete(
                positionMs = report.positionMs,
                playbackState = report.playbackState,
                allowEndedOnly = false
            )
            recordAndReportPlaybackHistory(
                active = active,
                positionMs = report.positionMs,
                completePlayback = completePlayback
            )
            if (active.heartbeatStarted && !active.heartbeatEnded) {
                remoteReporter.endHeartbeat(active)
            }
        }
    }

    private suspend fun startHeartbeat(active: PlaybackReportSession) {
        val ts = remoteReporter.startHeartbeat(active)
        mu.withLock {
            val current = ctx ?: return@withLock
            if (current.sessionId != active.sessionId) return@withLock
            ctx = current.copy(heartbeatStartTs = ts)
        }
    }

    private suspend fun endHeartbeat(active: PlaybackReportSession) {
        if (active.heartbeatEnded) return
        remoteReporter.endHeartbeat(active)
        mu.withLock {
            val current = ctx ?: return@withLock
            if (current.sessionId != active.sessionId) return@withLock
            ctx = current.copy(heartbeatEnded = true, finalized = true)
        }
    }

    private suspend fun startPlaybackHistory(
        active: PlaybackReportSession,
        positionMs: Long
    ) {
        reportPlaybackHistory(active, positionMs, completePlayback = false)
    }

    private suspend fun reportPlaybackHistory(
        active: PlaybackReportSession,
        positionMs: Long,
        completePlayback: Boolean
    ) {
        recordAndReportPlaybackHistory(
            active = active,
            positionMs = positionMs,
            completePlayback = completePlayback
        )
    }

    private suspend fun recordAndReportPlaybackHistory(
        active: PlaybackReportSession,
        positionMs: Long,
        completePlayback: Boolean
    ) {
        localHistoryRecorder.record(active, positionMs)
        remoteReporter.reportPlaybackHistory(active, positionMs, completePlayback)
    }

    private data class SnapshotEdge(
        val ctx: PlaybackReportSession,
        val firstFrame: Boolean,
        val paused: Boolean,
        val ended: Boolean,
        val startPlaybackHistory: Boolean
    )

    private companion object {
        const val DEFAULT_QN = 64
    }
}

internal data class DetachedPlaybackReport(
    val session: PlaybackReportSession,
    val playbackState: PlaybackState,
    val positionMs: Long
)
