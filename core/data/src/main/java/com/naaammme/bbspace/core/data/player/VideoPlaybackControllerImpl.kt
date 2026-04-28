package com.naaammme.bbspace.core.data.player

import androidx.media3.common.Player
import com.naaammme.bbspace.core.common.AuthProvider
import com.naaammme.bbspace.core.data.AppSettings
import com.naaammme.bbspace.core.common.log.Logger
import com.naaammme.bbspace.core.domain.history.LocalHistoryRepository
import com.naaammme.bbspace.core.domain.player.VideoPlaybackController
import com.naaammme.bbspace.core.domain.player.VideoPlayerRepository
import com.naaammme.bbspace.core.model.LocalHistoryKey
import com.naaammme.bbspace.core.model.PlaybackAudio
import com.naaammme.bbspace.core.model.PlaybackError
import com.naaammme.bbspace.core.model.PlaybackRequest
import com.naaammme.bbspace.core.model.PlaybackState
import com.naaammme.bbspace.core.model.PlaybackStream
import com.naaammme.bbspace.core.model.PlaybackSource
import com.naaammme.bbspace.core.model.PlaybackViewState
import com.naaammme.bbspace.core.model.PlayerSessionState
import com.naaammme.bbspace.core.model.VideoHistoryMeta
import com.naaammme.bbspace.core.model.buildPlaybackCdns
import com.naaammme.bbspace.infra.player.DecoderMode
import com.naaammme.bbspace.infra.player.EngineDiscontinuityReason
import com.naaammme.bbspace.infra.player.EnginePlaybackState
import com.naaammme.bbspace.infra.player.EngineSource
import com.naaammme.bbspace.infra.player.PlaybackSnapshot
import com.naaammme.bbspace.infra.player.PlayerConfig
import com.naaammme.bbspace.infra.player.PlayerEngine
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class VideoPlaybackControllerImpl @Inject constructor(
    private val repository: VideoPlayerRepository,
    private val appSettings: AppSettings,
    private val playerSettingsStore: PlayerSettingsStore,
    private val reporter: PlaybackReporter,
    private val authProvider: AuthProvider,
    private val localHistoryRepo: LocalHistoryRepository,
    private val playerEngine: PlayerEngine
) : VideoPlaybackController {
    private val _sessionState = MutableStateFlow(PlayerSessionState())
    val sessionState: StateFlow<PlayerSessionState> = _sessionState.asStateFlow()
    private val _viewState = MutableStateFlow(PlaybackViewState())
    val playbackState: StateFlow<PlaybackViewState> = _viewState.asStateFlow()
    private val _pageMeta = MutableStateFlow<VideoHistoryMeta?>(null)
    val pageMeta: StateFlow<VideoHistoryMeta?> = _pageMeta.asStateFlow()
    val playerState: StateFlow<Player?> = playerEngine.player
    private val runtimeScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val prepMu = Mutex()
    private val ownerId = AtomicLong(0L)
    private val ownerSessionId = MutableStateFlow(0L)
    private val openId = AtomicLong(0L)
    private val nextSessionId = AtomicLong(1L)
    private var lastDiscontinuitySeq = 0L
    @Volatile
    private var nextPlayWhenReady = true

    init {
        runtimeScope.launch {
            combine(sessionState, playerEngine.snapshot) { session, snapshot ->
                session to snapshot
            }.collect { (session, snapshot) ->
                val isNewSeekEvent = snapshot.discontinuitySeq != lastDiscontinuitySeq &&
                        snapshot.discontinuityReason.isSeek()
                lastDiscontinuitySeq = snapshot.discontinuitySeq
                _viewState.value = session.toViewState(
                    snapshot = snapshot,
                    prev = _viewState.value,
                    isNewSeekEvent = isNewSeekEvent
                )
                reporter.onPlaybackState(session, snapshot)
            }
        }
    }

    override suspend fun acquire(): VideoPlaybackController.Handle {
        return Handle(nextSessionId.getAndIncrement())
    }

    suspend fun prepareEngine() {
        prepMu.withLock {
            val config = withContext(Dispatchers.IO) { buildPlayerConfig() }
            withContext(Dispatchers.Main.immediate) {
                playerEngine.updateConfig(config)
            }
        }
    }

    fun stopPlayback() {
        runtimeScope.launch {
            finishCurrentSession(
                invalidateOpen = true,
                releasePlayer = true
            )
        }
    }

    private inner class Handle(
        private val sessionId: Long
    ) : VideoPlaybackController.Handle {
        override val player: StateFlow<Player?> = combine(
            playerState,
            ownerSessionId
        ) { player, owner ->
            player.takeIf { owner == sessionId }
        }.stateIn(
            scope = runtimeScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

        override val state: StateFlow<PlaybackViewState> = combine(
            playbackState,
            ownerSessionId
        ) { state, owner ->
            if (owner == sessionId) state else PlaybackViewState()
        }.stateIn(
            scope = runtimeScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PlaybackViewState()
        )

        override suspend fun open(request: PlaybackRequest) {
            start(sessionId, request)
        }

        override fun play() {
            if (!isOwner(sessionId)) return
            nextPlayWhenReady = true
            playerEngine.play()
        }

        override fun pause() {
            if (!isOwner(sessionId)) return
            nextPlayWhenReady = false
            playerEngine.pause()
        }

        override fun seekTo(positionMs: Long) {
            if (!isOwner(sessionId)) return
            playerEngine.seekTo(positionMs)
        }

        override fun setSpeed(speed: Float) {
            if (!isOwner(sessionId)) return
            playerEngine.setSpeed(speed)
        }

        override fun switchQuality(quality: Int) {
            if (!isOwner(sessionId)) return
            val state = _sessionState.value
            val snapshot = latestSnapshot()
            val source = state.playbackSource ?: return
            val stream = source.streams.firstOrNull { it.quality == quality } ?: return
            val audio = selectAudio(stream, source.audios, state.currentAudio?.id ?: 0)
            val engineSource = buildEngineSource(stream, audio, state.cdnIndex) ?: return
            playerEngine.setSource(engineSource.first, snapshot.positionMs, snapshot.playWhenReady)
            _sessionState.value = state.copy(
                currentStream = stream,
                currentAudio = audio,
                cdnIndex = engineSource.second
            )
        }

        override fun switchAudio(audioId: Int) {
            if (!isOwner(sessionId)) return
            val state = _sessionState.value
            val snapshot = latestSnapshot()
            val source = state.playbackSource ?: return
            val audio = source.audios.firstOrNull { it.id == audioId } ?: return
            val engineSource = buildEngineSource(state.currentStream, audio, state.cdnIndex) ?: return
            playerEngine.setSource(engineSource.first, snapshot.positionMs, snapshot.playWhenReady)
            _sessionState.value = state.copy(
                currentAudio = audio,
                cdnIndex = engineSource.second
            )
        }

        override fun switchCdn(index: Int) {
            if (!isOwner(sessionId)) return
            val state = _sessionState.value
            val snapshot = latestSnapshot()
            val engineSource = buildEngineSource(state.currentStream, state.currentAudio, index) ?: return
            playerEngine.setSource(engineSource.first, snapshot.positionMs, snapshot.playWhenReady)
            _sessionState.value = state.copy(cdnIndex = engineSource.second)
            runtimeScope.launch {
                playerSettingsStore.updatePlayerCdnIndex(engineSource.second)
            }
        }

        override fun updateMeta(meta: VideoHistoryMeta?) {
            if (!isOwner(sessionId)) return
            _pageMeta.value = meta
            reporter.updateVideoMeta(meta)
        }

        override fun release() {
            if (!isOwner(sessionId)) return
            val snapshot = clearCurrentSession(
                invalidateOpen = true,
                releasePlayer = true
            ) ?: return
            runtimeScope.launch {
                reporter.finishSession(snapshot)
            }
        }
    }

    private suspend fun start(
        who: Long,
        request: PlaybackRequest
    ) {
        val state = _sessionState.value
        val owner = ownerId.get()
        if (
            state.currentRequest == request &&
            state.error == null &&
            (state.playbackSource != null || state.isPreparing) &&
            (owner == who || owner == 0L)
        ) {
            ownerId.set(who)
            ownerSessionId.value = who
            reporter.bindOwner(who)
            return
        }
        val token = openId.incrementAndGet()
        finishCurrentSession(
            invalidateOpen = false,
            releasePlayer = false
        )
        ownerId.set(who)
        ownerSessionId.value = who
        reporter.bindOwner(who)
        nextPlayWhenReady = true
        _pageMeta.value = null
        _sessionState.value = PlayerSessionState(
            currentRequest = request,
            isPreparing = true
        )
        try {
            val (source, preferredQuality, preferredAudioId, preferredCdnIndex) = coroutineScope {
                val prepareJob = async { prepareEngine() }
                val sourceJob = async { repository.fetchPlaybackSource(request) }
                val qualityJob = async {
                    request.preferredQuality ?: appSettings.defaultVideoQuality.first()
                }
                val audioJob = async { appSettings.defaultAudioQuality.first() }
                val cdnJob = async { playerSettingsStore.playerCdnIndex.first() }

                prepareJob.await()
                OpenConfig(
                    sourceJob.await(),
                    qualityJob.await(),
                    audioJob.await(),
                    cdnJob.await()
                )
            }
            if (openId.get() != token) return
            val stream = source.streams.firstOrNull { it.quality == preferredQuality }
                ?: source.streams.firstOrNull()
            val audio = selectAudio(stream, source.audios, preferredAudioId)
            val engineSource = buildEngineSource(stream, audio, preferredCdnIndex)
                ?: throw NoPlayableStreamException("暂无可用播放流")
            val startMs = resolveStartMs(request, source)
            if (openId.get() != token) return
            _sessionState.value = PlayerSessionState(
                currentRequest = request,
                playbackSource = source,
                currentStream = stream,
                currentAudio = audio,
                cdnIndex = engineSource.second,
                isPreparing = true
            )
            playerEngine.setSource(
                source = engineSource.first,
                startPositionMs = startMs,
                playWhenReady = nextPlayWhenReady
            )
            if (openId.get() != token) return

            _sessionState.value = PlayerSessionState(
                currentRequest = request,
                playbackSource = source,
                currentStream = stream,
                currentAudio = audio,
                cdnIndex = engineSource.second,
                isPreparing = false
            )
            reporter.startSession(
                request = request,
                state = _sessionState.value,
                startPositionMs = startMs ?: 0L
            )
        } catch (t: Throwable) {
            if (t is CancellationException) {
                if (openId.get() == token && _sessionState.value.playbackSource == null) {
                    nextPlayWhenReady = true
                    _sessionState.value = PlayerSessionState()
                }
                throw t
            }
            if (openId.get() != token) return
            nextPlayWhenReady = true
            Logger.e(TAG, t) {
                "load playback source failed biz=${request.playable.biz.biz} " +
                        "aid=${request.videoId.aid} cid=${request.videoId.cid} " +
                        "epId=${request.playable.biz.epId} seasonId=${request.playable.biz.seasonId} " +
                        "q=${request.preferredQuality} msg=${t.message}"
            }
            _sessionState.value = _sessionState.value.copy(
                isPreparing = false,
                error = when (t) {
                    is NoPlayableStreamException -> PlaybackError.NoPlayableStream(
                        t.message ?: "暂无可用播放流"
                    )
                    else -> PlaybackError.RequestFailed(
                        t.message ?: "Failed to load playback source",
                        t
                    )
                }
            )
        }
    }

    private suspend fun finishCurrentSession(
        invalidateOpen: Boolean,
        releasePlayer: Boolean
    ) {
        val snapshot = clearCurrentSession(
            invalidateOpen = invalidateOpen,
            releasePlayer = releasePlayer
        ) ?: return
        reporter.finishSession(snapshot)
    }

    private fun clearCurrentSession(
        invalidateOpen: Boolean,
        releasePlayer: Boolean
    ): PlaybackSnapshot? {
        val snapshot = latestSnapshot()
        val hadSession = _sessionState.value.playbackSource != null
        if (invalidateOpen) {
            openId.incrementAndGet()
        }
        ownerId.set(0L)
        ownerSessionId.value = 0L
        nextPlayWhenReady = true
        // 先清空 session，避免 teardown 期间的 snapshot 继续按旧视频触发上报
        _pageMeta.value = null
        _sessionState.value = PlayerSessionState()
        if (hadSession) {
            if (releasePlayer) {
                playerEngine.release()
            } else {
                playerEngine.stopForReuse(resetPosition = true)
            }
        }
        return snapshot.takeIf { hadSession }
    }

    private fun latestSnapshot(): PlaybackSnapshot {
        val player = playerState.value ?: return playerEngine.snapshot.value
        val snapshot = playerEngine.snapshot.value
        return snapshot.copy(
            isPlaying = player.isPlaying,
            playWhenReady = player.playWhenReady,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            bufferedPositionMs = player.bufferedPosition.coerceAtLeast(0L),
            totalBufferedDurationMs = player.totalBufferedDuration.coerceAtLeast(0L),
            durationMs = player.duration.takeIf { it > 0L } ?: snapshot.durationMs,
            speed = player.playbackParameters.speed
        )
    }

    private fun selectAudio(
        stream: PlaybackStream?,
        audios: List<PlaybackAudio>,
        preferredId: Int
    ): PlaybackAudio? {
        if (audios.isEmpty()) return null
        val linkedId = (stream as? PlaybackStream.Dash)?.audioId
        return audios.firstOrNull { it.id == preferredId && preferredId > 0 }
            ?: audios.firstOrNull { it.id == linkedId }
            ?: audios.firstOrNull()
    }

    private fun buildEngineSource(
        stream: PlaybackStream?,
        audio: PlaybackAudio?,
        cdnIndex: Int
    ): Pair<EngineSource, Int>? {
        return when (stream) {
            is PlaybackStream.Dash -> {
                val cdns = buildPlaybackCdns(stream, audio)
                if (cdns.isEmpty()) return null
                val index = cdnIndex.coerceIn(0, cdns.lastIndex)
                EngineSource.Dash(cdns[index].videoUrl, cdns[index].audioUrl) to index
            }

            is PlaybackStream.Progressive -> {
                EngineSource.Progressive(
                    stream.segments.map { EngineSource.ProgressiveSegment(it.url, it.durationMs) }
                ) to 0
            }

            null -> null
        }
    }

    private suspend fun buildPlayerConfig(): PlayerConfig {
        return PlayerConfig(
            minBufferMs = playerSettingsStore.playerMinBufferMs.first(),
            maxBufferMs = playerSettingsStore.playerMaxBufferMs.first(),
            playBufferMs = playerSettingsStore.playerPlaybackBufferMs.first(),
            rebufferMs = playerSettingsStore.playerRebufferMs.first(),
            backBufferMs = playerSettingsStore.playerBackBufferMs.first(),
            decoderMode = if (playerSettingsStore.preferSoftwareDecode.first()) {
                DecoderMode.Soft
            } else {
                DecoderMode.Hard
            },
            decoderFallback = playerSettingsStore.decoderFallback.first()
        )
    }

    private suspend fun resolveStartMs(
        request: PlaybackRequest,
        source: PlaybackSource
    ): Long? {
        request.seekToMs?.let { return it }
        val key = LocalHistoryKey.video(source.report)
        val local = localHistoryRepo.getVideo(authProvider.mid, key)
        if (local != null) {
            if (canResume(local.progressMs, local.finished, source.durationMs)) {
                return local.progressMs
            }
            return 0L
        }
        return source.resumePositionMs
    }

    private fun canResume(
        progressMs: Long?,
        finished: Boolean,
        durationMs: Long
    ): Boolean {
        val progress = progressMs?.coerceAtLeast(0L) ?: return false
        if (progress <= 0L || finished) return false
        if (durationMs <= 0L) return true
        return durationMs - progress > COMPLETE_THRESHOLD_MS
    }

    private fun isOwner(who: Long): Boolean {
        return ownerId.get() == who
    }

    private fun PlayerSessionState.toViewState(
        snapshot: PlaybackSnapshot,
        prev: PlaybackViewState,
        isNewSeekEvent: Boolean
    ): PlaybackViewState {
        return PlaybackViewState(
            isPreparing = isPreparing,
            playbackSource = playbackSource,
            currentStream = currentStream,
            currentAudio = currentAudio,
            cdnIndex = cdnIndex,
            error = error,
            isPlaying = snapshot.isPlaying,
            playWhenReady = snapshot.playWhenReady,
            playbackState = snapshot.playbackState.toModelState(),
            positionMs = snapshot.positionMs,
            bufferedPositionMs = snapshot.bufferedPositionMs,
            totalBufferedDurationMs = snapshot.totalBufferedDurationMs,
            durationMs = snapshot.durationMs,
            speed = snapshot.speed,
            videoWidth = snapshot.videoWidth,
            videoHeight = snapshot.videoHeight,
            hasRenderedFirstFrame = snapshot.firstFrameSeq > 0L,
            seekEventId = if (isNewSeekEvent) prev.seekEventId + 1L else prev.seekEventId,
            playerError = snapshot.errorMessage
        )
    }

    private fun EnginePlaybackState.toModelState(): PlaybackState {
        return when (this) {
            EnginePlaybackState.Buffering -> PlaybackState.Buffering
            EnginePlaybackState.Ready -> PlaybackState.Ready
            EnginePlaybackState.Ended -> PlaybackState.Ended
            EnginePlaybackState.Idle -> PlaybackState.Idle
        }
    }

    private fun EngineDiscontinuityReason?.isSeek(): Boolean {
        return this == EngineDiscontinuityReason.Seek ||
                this == EngineDiscontinuityReason.SeekAdjustment
    }

    private companion object {
        const val TAG = "VideoPlayback"
        const val COMPLETE_THRESHOLD_MS = 3_000L
    }
}

private data class OpenConfig(
    val source: PlaybackSource,
    val preferredQuality: Int,
    val preferredAudioId: Int,
    val preferredCdnIndex: Int
)

private class NoPlayableStreamException(
    message: String
) : IllegalStateException(message)
