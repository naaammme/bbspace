package com.naaammme.bbspace.core.data.player

import android.os.SystemClock
import com.naaammme.bbspace.core.common.AuthProvider
import com.naaammme.bbspace.core.domain.player.PlayerSettings
import com.naaammme.bbspace.core.model.PlaybackHistoryMeta
import com.naaammme.bbspace.core.model.PlaybackRequest
import com.naaammme.bbspace.core.model.PlayerSessionState
import com.naaammme.bbspace.infra.crypto.BiliSessionId
import com.naaammme.bbspace.infra.crypto.DeviceIdentity
import com.naaammme.bbspace.infra.player.EnginePlaybackState
import com.naaammme.bbspace.infra.player.PlaybackSnapshot
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class PlaybackReporter @Inject constructor(
    private val authProvider: AuthProvider,
    private val deviceIdentity: DeviceIdentity,
    private val playerSettings: PlayerSettings,
    private val localHistoryRecorder: LocalPlaybackHistoryRecorder,
    private val remoteReporter: RemotePlaybackReporter
) {
    private val mu = Mutex()
    private var pageOwnerId = 0L
    private var pagePolarisId = ""
    @Volatile
    private var pageMeta: PlaybackHistoryMeta? = null
    private var ctx: PlaybackReportSession? = null

    fun bindOwner(who: Long) {
        if (pageOwnerId == who) return
        pageOwnerId = who
        pagePolarisId = BiliSessionId.polarisAction()
        pageMeta = null
    }

    fun updatePlaybackMeta(meta: PlaybackHistoryMeta?) {
        pageMeta = meta
    }

    suspend fun startSession(
        request: PlaybackRequest,
        state: PlayerSessionState,
        startPositionMs: Long
    ) {
        val source = state.playbackSource ?: return
        val quality = state.currentStream?.quality ?: DEFAULT_QN
        val startSec = msToSec(startPositionMs)
        val nowElapsed = SystemClock.elapsedRealtime()
        mu.withLock {
            ctx = PlaybackReportSession(
                uid = authProvider.mid,
                request = request,
                source = source,
                report = source.report,
                sessionId = BiliSessionId.view(deviceIdentity.buvid),
                polarisActionId = pagePolarisId.ifBlank(BiliSessionId::polarisAction),
                reportEnabled = playerSettings.state.value.playback.reportPlayback,
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
        snapshot: PlaybackSnapshot
    ) {
        val current = mu.withLock {
            val active = ctx ?: return@withLock null
            if (!samePlaybackSource(active.source, state.playbackSource)) return@withLock null
            val prev = active.lastSnapshot
            active.sync(snapshot, state)
            active.lastSnapshot = snapshot
            SnapshotEdge(
                ctx = active.copy(),
                firstFrame = !active.heartbeatStarted && snapshot.firstFrameSeq > 0L,
                paused = prev.playWhenReady &&
                        !snapshot.playWhenReady &&
                        snapshot.playbackState == EnginePlaybackState.Ready,
                ended = !active.finalized &&
                        snapshot.playbackState == EnginePlaybackState.Ended &&
                        prev.playbackState != EnginePlaybackState.Ended,
                startPlaybackHistory = !active.playbackHistoryStarted && snapshot.firstFrameSeq > 0L
            )
        }
        current ?: return

        if (current.firstFrame) {
            startHeartbeat(current.ctx)
            startPlaybackHistory(current.ctx, snapshot)
        } else if (current.startPlaybackHistory) {
            startPlaybackHistory(current.ctx, snapshot)
        }

        if (current.paused && current.ctx.playbackHistoryStarted) {
            reportPlaybackHistory(current.ctx, snapshot, complete = false)
        }
        if (current.ended && current.ctx.playbackHistoryStarted) {
            reportPlaybackHistory(current.ctx, snapshot, complete = true)
            endHeartbeat(current.ctx)
            mu.withLock {
                ctx = ctx?.takeIf { it.sessionId != current.ctx.sessionId } ?: ctx?.copy(finalized = true)
            }
        }
    }

    suspend fun finishSession(snapshot: PlaybackSnapshot) {
        val active = detachSession(snapshot) ?: return
        finalizeSession(active)
    }

    internal suspend fun detachSession(
        snapshot: PlaybackSnapshot,
        meta: PlaybackHistoryMeta? = null
    ): DetachedPlaybackReport? {
        return mu.withLock {
            val current = ctx ?: return@withLock null
            current.sync(snapshot, null)
            current.lastSnapshot = snapshot
            val detached = DetachedPlaybackReport(
                session = current.copy(),
                meta = meta ?: pageMeta,
                snapshot = snapshot
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
            recordAndReportPlaybackHistory(
                active = active,
                meta = report.meta,
                snapshot = report.snapshot,
                complete = active.isComplete(report.snapshot, allowEndedOnly = false)
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
            ctx = current.copy(
                heartbeatStarted = true,
                heartbeatStartTs = ts
            )
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
        snapshot: PlaybackSnapshot
    ) {
        reportPlaybackHistory(active, snapshot, complete = false)
    }

    private suspend fun reportPlaybackHistory(
        active: PlaybackReportSession,
        snapshot: PlaybackSnapshot,
        complete: Boolean
    ) {
        recordAndReportPlaybackHistory(
            active = active,
            meta = pageMeta,
            snapshot = snapshot,
            complete = complete
        )
        mu.withLock {
            val current = ctx ?: return@withLock
            if (current.sessionId != active.sessionId) return@withLock
            ctx = current.copy(playbackHistoryStarted = true)
        }
    }

    private suspend fun recordAndReportPlaybackHistory(
        active: PlaybackReportSession,
        meta: PlaybackHistoryMeta?,
        snapshot: PlaybackSnapshot,
        complete: Boolean
    ) {
        localHistoryRecorder.record(active, meta, snapshot)
        remoteReporter.reportPlaybackHistory(active, snapshot, complete)
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
    val meta: PlaybackHistoryMeta?,
    val snapshot: PlaybackSnapshot
)
