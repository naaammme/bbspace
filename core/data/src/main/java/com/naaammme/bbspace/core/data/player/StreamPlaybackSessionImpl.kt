package com.naaammme.bbspace.core.data.player

import androidx.media3.common.Player
import androidx.media3.common.MediaMetadata
import com.naaammme.bbspace.core.common.AuthProvider
import com.naaammme.bbspace.core.common.log.Logger
import com.naaammme.bbspace.core.data.AppSettings
import com.naaammme.bbspace.core.domain.danmaku.VodDanmakuRepository
import com.naaammme.bbspace.core.domain.history.PlaybackHistoryRepository
import com.naaammme.bbspace.core.domain.live.LiveRepository
import com.naaammme.bbspace.core.domain.player.LivePlaybackController
import com.naaammme.bbspace.core.domain.player.PlayerSettings
import com.naaammme.bbspace.core.domain.player.StreamPlaybackSession
import com.naaammme.bbspace.core.domain.player.VideoPlaybackController
import com.naaammme.bbspace.core.domain.player.VideoPlayerRepository
import com.naaammme.bbspace.core.model.DanmakuSessionState
import com.naaammme.bbspace.core.model.LivePlaybackError
import com.naaammme.bbspace.core.model.LivePlaybackViewState
import com.naaammme.bbspace.core.model.LiveRoute
import com.naaammme.bbspace.core.model.PlayBiz
import com.naaammme.bbspace.core.model.PlaybackAudio
import com.naaammme.bbspace.core.model.PlaybackControlMode
import com.naaammme.bbspace.core.model.PlaybackError
import com.naaammme.bbspace.core.model.PlaybackHistory
import com.naaammme.bbspace.core.model.PlaybackHistoryKey
import com.naaammme.bbspace.core.model.PlaybackProgress
import com.naaammme.bbspace.core.model.PlaybackRequest
import com.naaammme.bbspace.core.model.PlaybackSource
import com.naaammme.bbspace.core.model.PlaybackStream
import com.naaammme.bbspace.core.model.PlayerSessionState
import com.naaammme.bbspace.core.model.StreamPlaybackSessionState
import com.naaammme.bbspace.core.model.StreamPlaybackTarget
import com.naaammme.bbspace.core.model.VideoPlaybackState
import com.naaammme.bbspace.core.model.VideoTarget
import com.naaammme.bbspace.core.model.buildPlaybackCdns
import com.naaammme.bbspace.core.model.isSameEntry
import com.naaammme.bbspace.core.model.toPlayableParams
import com.naaammme.bbspace.infra.player.DecoderMode
import com.naaammme.bbspace.infra.player.EngineSource
import com.naaammme.bbspace.infra.player.PlayerPlaybackState
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class StreamPlaybackSessionImpl @Inject constructor(
    private val videoRepository: VideoPlayerRepository,
    private val danmakuRepository: VodDanmakuRepository,
    private val appSettings: AppSettings,
    private val playerSettings: PlayerSettings,
    private val reporter: PlaybackReporter,
    private val authProvider: AuthProvider,
    private val playbackHistoryRepo: PlaybackHistoryRepository,
    private val liveRepository: LiveRepository,
    private val playerEngine: PlayerEngine
) : StreamPlaybackSession, VideoPlaybackController, LivePlaybackController {

    //  engine
    override val player: StateFlow<Player?> = playerEngine.player
    private val runtimeScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // danmaku
    private val danmakuSession = VodDanmakuSession(scope = runtimeScope, repository = danmakuRepository)
    override val danmakuState: StateFlow<DanmakuSessionState> = danmakuSession.state

    // vod state
    private val vodSession = MutableStateFlow(PlayerSessionState())
    private val _videoState = MutableStateFlow(VideoPlaybackState())
    override val videoState: StateFlow<VideoPlaybackState> = _videoState.asStateFlow()
    override val playbackProgress: StateFlow<PlaybackProgress> = playerEngine.playbackProgress
    private val prepMu = Mutex()
    private val openId = AtomicLong(0L)
    private var lastDiscontinuitySeq = 0L
    private var nextPlayWhenReady = true

    // live state
    private val _liveState = MutableStateFlow(LivePlaybackViewState())
    override val liveState: StateFlow<LivePlaybackViewState> = _liveState.asStateFlow()

    // session state
    private val _currentTarget = MutableStateFlow<StreamPlaybackTarget?>(null)
    override val currentTarget: StateFlow<StreamPlaybackTarget?> = _currentTarget.asStateFlow()
    private val _sessionState = MutableStateFlow(StreamPlaybackSessionState())
    override val sessionState: StateFlow<StreamPlaybackSessionState> = _sessionState.asStateFlow()

    init {
        runtimeScope.launch {
            playerEngine.playbackState.collect { state ->
                when (_currentTarget.value) {
                    is StreamPlaybackTarget.Video -> {
                        onVideoPlayerState(state)
                        reporter.onPlaybackState(
                            state = vodSession.value,
                            playbackState = state,
                            progress = currentPlaybackProgress()
                        )
                    }
                    is StreamPlaybackTarget.Live -> onLivePlayerState(state)
                    null -> {}
                }
            }
        }
        runtimeScope.launch {
            playerEngine.playbackProgress.collect { progress ->
                if (_currentTarget.value is StreamPlaybackTarget.Video) {
                    danmakuSession.onProgress(progress.positionMs)
                }
            }
        }
        runtimeScope.launch {
            playerSettings.state.map { it.danmaku.enabled }.collect { enabled ->
                if (_currentTarget.value !is StreamPlaybackTarget.Video) return@collect
                if (enabled) {
                    danmakuSession.setSource(vodSession.value.playbackSource)
                    danmakuSession.seekTo(currentPlaybackPositionMs())
                } else {
                    danmakuSession.clear()
                }
            }
        }
    }

    // StreamPlaybackSession: lifecycle
    override suspend fun prepare() {
        prepMu.withLock {
            val config = withContext(Dispatchers.IO) { buildPlayerConfig() }
            withContext(Dispatchers.Main.immediate) { playerEngine.updateConfig(config) }
        }
    }

    override fun openVideo(target: VideoTarget) {
        runtimeScope.launch {
            if (_currentTarget.value is StreamPlaybackTarget.Live) {
                finishLivePlayback(releasePlayer = false)
            }
            openVideoInternal(target)
        }
    }

    override suspend fun openLive(
        route: LiveRoute,
        preferredQuality: Int,
        reportEntry: Boolean
    ) {
        val live = _currentTarget.value as? StreamPlaybackTarget.Live
        if (
            live?.route?.roomId == route.roomId &&
            (preferredQuality <= 0 || _liveState.value.playbackSource?.currentQn == preferredQuality) &&
            _liveState.value.error == null &&
            (_liveState.value.playbackSource != null || _liveState.value.isPreparing)
        ) {
            _currentTarget.value = StreamPlaybackTarget.Live(route)
            syncSessionState()
            return
        }

        if (_currentTarget.value is StreamPlaybackTarget.Video) {
            finishVideoPlayback(invalidateOpen = true, releasePlayer = false)
        }
        _currentTarget.value = StreamPlaybackTarget.Live(route)
        _liveState.value = LivePlaybackViewState(isPreparing = true)
        syncSessionState()

        try {
            prepare()
            val source = liveRepository.fetchPlaybackSource(route.roomId, preferredQuality)
            playerEngine.setSource(
                source = EngineSource.LiveFlv(source.primaryUrl),
                playWhenReady = true,
                metadata = MediaMetadata.Builder()
                    .setTitle(route.title?.takeIf(String::isNotBlank))
                    .setArtist(source.currentDescription?.takeIf(String::isNotBlank))
                    .setArtworkUri(route.cover?.takeIf(String::isNotBlank)?.let(android.net.Uri::parse))
                    .build()
            )
            _liveState.value = _liveState.value.copy(
                isPreparing = false,
                playbackSource = source,
                error = null
            )
            syncSessionState()
            if (reportEntry) {
                reportRoomEntryAction(roomId = route.roomId, jumpFrom = route.jumpFrom)
            }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            Logger.e(TAG, t) {
                "load live source failed roomId=${route.roomId} qn=$preferredQuality msg=${t.message}"
            }
            _liveState.value = _liveState.value.copy(
                isPreparing = false,
                error = t.toLiveError(),
                playbackSource = _liveState.value.playbackSource.takeIf { it?.roomId == route.roomId }
            )
            syncSessionState()
        }
    }

    override fun close() {
        runtimeScope.launch {
            when (_currentTarget.value) {
                is StreamPlaybackTarget.Video -> finishVideoPlayback(invalidateOpen = true, releasePlayer = true)
                is StreamPlaybackTarget.Live -> finishLivePlayback(releasePlayer = true)
                null -> {}
            }
            _currentTarget.value = null
            _liveState.value = LivePlaybackViewState()
            syncSessionState()
        }
    }

    // StreamPlaybackSession: playback control
    override fun play() {
        if (_currentTarget.value is StreamPlaybackTarget.Video) {
            nextPlayWhenReady = true
        }
        playerEngine.play()
    }

    override fun pause() {
        if (_currentTarget.value is StreamPlaybackTarget.Video) {
            nextPlayWhenReady = false
        }
        playerEngine.pause()
    }

    override fun seekTo(positionMs: Long) {
        if (_currentTarget.value !is StreamPlaybackTarget.Video) return
        playerEngine.seekTo(positionMs)
        danmakuSession.seekTo(positionMs)
    }

    override fun setSpeed(speed: Float) {
        if (_currentTarget.value !is StreamPlaybackTarget.Video) return
        playerEngine.setSpeed(speed)
    }

    // StreamPlaybackSession
    override fun switchVideoQuality(quality: Int) {
        if (_currentTarget.value !is StreamPlaybackTarget.Video) return
        val state = vodSession.value
        val source = state.playbackSource ?: return
        val stream = source.streams.firstOrNull { it.quality == quality } ?: return
        val audio = selectAudio(stream, source.audios, state.currentAudio?.id ?: 0)
        val engineSource = buildVideoEngineSource(stream, audio, state.cdnIndex) ?: return
        val progress = currentPlaybackProgress()
        playerEngine.setSource(engineSource.first, progress.positionMs, currentPlayWhenReady())
        vodSession.value = state.copy(
            currentStream = stream,
            currentAudio = audio,
            cdnIndex = engineSource.second
        )
        refreshVideoState()
    }

    override fun switchVideoAudio(audioId: Int) {
        if (_currentTarget.value !is StreamPlaybackTarget.Video) return
        val state = vodSession.value
        val source = state.playbackSource ?: return
        val audio = source.audios.firstOrNull { it.id == audioId } ?: return
        val engineSource = buildVideoEngineSource(state.currentStream, audio, state.cdnIndex) ?: return
        val progress = currentPlaybackProgress()
        playerEngine.setSource(engineSource.first, progress.positionMs, currentPlayWhenReady())
        vodSession.value = state.copy(currentAudio = audio, cdnIndex = engineSource.second)
        refreshVideoState()
    }

    override fun switchVideoCdn(index: Int) {
        if (_currentTarget.value !is StreamPlaybackTarget.Video) return
        val state = vodSession.value
        val engineSource = buildVideoEngineSource(state.currentStream, state.currentAudio, index) ?: return
        val progress = currentPlaybackProgress()
        playerEngine.setSource(engineSource.first, progress.positionMs, currentPlayWhenReady())
        vodSession.value = state.copy(cdnIndex = engineSource.second)
        refreshVideoState()
    }

    override fun switchLiveQuality(quality: Int) {
        val route = (_currentTarget.value as? StreamPlaybackTarget.Live)?.route ?: return
        if (_liveState.value.playbackSource?.currentQn == quality) return
        runtimeScope.launch {
            openLive(route = route, preferredQuality = quality, reportEntry = false)
        }
    }

    // vod: open & state
    private suspend fun openVideoInternal(
        target: VideoTarget,
        seekToMs: Long? = null,
        preferredQuality: Int? = null,
        controlMode: PlaybackControlMode = PlaybackControlMode.Default
    ) {
        val request = target.toPlayableParams().getResolveParams(
            seekToMs = seekToMs,
            preferredQuality = preferredQuality,
            controlMode = controlMode
        )
        val state = vodSession.value
        val currentVideoTarget = (_currentTarget.value as? StreamPlaybackTarget.Video)?.target
        val reopenSameEntry = currentVideoTarget?.isSameEntry(target) == true &&
            state.playbackSource != null &&
            state.error == null
        if (
            currentVideoTarget == target &&
            state.error == null &&
            (state.playbackSource != null || state.isPreparing)
        ) {
            _currentTarget.value = StreamPlaybackTarget.Video(target)
            syncSessionState()
            return
        }

        val token = openId.incrementAndGet()
        nextPlayWhenReady = true
        reporter.bindOwner(token)
        if (reopenSameEntry) {
            _currentTarget.value = StreamPlaybackTarget.Video(target)
            val pendingState = state.copy(
                isPreparing = true,
                error = null
            )
            vodSession.value = pendingState
            refreshVideoState()
        } else {
            finishVideoPlayback(
                invalidateOpen = false,
                releasePlayer = false,
                nextTarget = target,
                nextState = PlayerSessionState(isPreparing = true),
                nextViewState = VideoPlaybackState(isPreparing = true),
                finalizeReportAsync = true
            )
        }

        try {
            val openCfg = coroutineScope {
                val prepareJob = async { prepare() }
                val sourceJob = async { videoRepository.fetchPlaybackSource(request) }
                val localResumeJob = async(Dispatchers.IO) { readLocalResumeIfResolvable(request) }
                val qualityJob = async {
                    request.preferredQuality ?: appSettings.defaultVideoQuality.first()
                }
                val audioJob = async { appSettings.defaultAudioQuality.first() }

                prepareJob.await()
                OpenConfig(
                    source = sourceJob.await(),
                    preferredQuality = qualityJob.await(),
                    preferredAudioId = audioJob.await(),
                    preferredCdnIndex = if (reopenSameEntry) state.cdnIndex else DEFAULT_CDN_INDEX,
                    localResume = localResumeJob.await()
                )
            }
            if (openId.get() != token) return

            val source = openCfg.source
            val stream = source.streams.firstOrNull { it.quality == openCfg.preferredQuality }
                ?: source.streams.firstOrNull()
            val audio = selectAudio(stream, source.audios, openCfg.preferredAudioId)
            val engineSource = buildVideoEngineSource(stream, audio, openCfg.preferredCdnIndex)
                ?: throw NoPlayableStreamException("暂无可用播放流")
            val startMs = resolveStartMs(request, source, openCfg.localResume)
            if (openId.get() != token) return
            if (reopenSameEntry) {
                reporter.finishSession(
                    playbackState = playerEngine.playbackState.value,
                    progress = currentPlaybackProgress()
                )
            }

            vodSession.value = PlayerSessionState(
                playbackSource = source,
                currentStream = stream,
                currentAudio = audio,
                cdnIndex = engineSource.second,
                isPreparing = true
            )
            if (playerSettings.state.first().danmaku.enabled) {
                danmakuSession.setSource(source)
                danmakuSession.seekTo(startMs ?: 0L)
            }
            playerEngine.setSource(
                source = engineSource.first,
                startPositionMs = startMs,
                playWhenReady = nextPlayWhenReady
            )
            if (openId.get() != token) return

            vodSession.value = PlayerSessionState(
                playbackSource = source,
                currentStream = stream,
                currentAudio = audio,
                cdnIndex = engineSource.second,
                isPreparing = false
            )
            syncSessionState()
            reporter.startSession(
                request = request,
                state = vodSession.value,
                startPositionMs = startMs ?: 0L
            )
            refreshVideoState()
        } catch (t: Throwable) {
            if (t is CancellationException) {
                if (openId.get() == token && vodSession.value.playbackSource == null) {
                    nextPlayWhenReady = true
                    _currentTarget.value = null
                    vodSession.value = PlayerSessionState()
                    _videoState.value = VideoPlaybackState()
                    danmakuSession.clear()
                    syncSessionState()
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
            if (reopenSameEntry) {
                vodSession.value = state
                refreshVideoState()
                return
            }
            vodSession.value = vodSession.value.copy(
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
            refreshVideoState()
        }
    }

    private suspend fun finishVideoPlayback(
        invalidateOpen: Boolean,
        releasePlayer: Boolean,
        nextTarget: VideoTarget? = null,
        nextState: PlayerSessionState? = null,
        nextViewState: VideoPlaybackState? = null,
        finalizeReportAsync: Boolean = false
    ) {
        val playbackState = playerEngine.playbackState.value
        val progress = currentPlaybackProgress()
        val hadVideo = vodSession.value.playbackSource != null
        val hasEngineMedia = playerEngine.currentSource.value != null ||
            player.value?.currentMediaItem != null
        if (invalidateOpen) openId.incrementAndGet()
        _currentTarget.value = nextTarget?.let { StreamPlaybackTarget.Video(it) }
        nextPlayWhenReady = true
        vodSession.value = nextState ?: PlayerSessionState()
        _videoState.value = nextViewState ?: VideoPlaybackState()
        if (nextState?.playbackSource == null) {
            danmakuSession.clear()
        }
        syncSessionState()
        if (hasEngineMedia) {
            when {
                releasePlayer -> playerEngine.release()
                else -> playerEngine.stopForReuse(resetPosition = true)
            }
        }

        val detached = if (hadVideo) {
            reporter.detachSession(
                playbackState = playbackState.playbackState,
                positionMs = progress.positionMs
            )
        } else {
            null
        }
        detached ?: return
        if (!finalizeReportAsync) {
            reporter.finalizeSession(detached)
            return
        }
        runtimeScope.launch(Dispatchers.IO) {
            reporter.finalizeSession(detached)
        }
    }

    private fun currentPlaybackProgress(): PlaybackProgress {
        val currentPlayer = player.value
        val progress = playerEngine.playbackProgress.value
        return PlaybackProgress(
            positionMs = currentPlayer?.currentPosition?.coerceAtLeast(0L)
                ?: progress.positionMs,
            bufferedPositionMs = currentPlayer?.bufferedPosition?.coerceAtLeast(0L)
                ?: progress.bufferedPositionMs,
            durationMs = currentPlayer?.duration?.takeIf { it > 0L } ?: progress.durationMs
        )
    }

    private fun currentPlayWhenReady(): Boolean {
        return player.value?.playWhenReady ?: playerEngine.playbackState.value.playWhenReady
    }

    private fun currentPlaybackPositionMs(): Long {
        return currentPlaybackProgress().positionMs
    }

    // vod: stream selection helpers
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

    private fun buildVideoEngineSource(
        stream: PlaybackStream?,
        audio: PlaybackAudio?,
        cdnIndex: Int
    ): Pair<EngineSource, Int>? {
        return when (stream) {
            is PlaybackStream.Dash -> {
                val cdns = buildPlaybackCdns(stream, audio)
                if (cdns.isEmpty()) return null
                val index = cdnIndex.coerceIn(0, cdns.lastIndex)
                EngineSource.Dash(
                    videoUrl = cdns[index].videoUrl,
                    audioUrl = cdns[index].audioUrl
                ) to index
            }

            is PlaybackStream.Progressive -> {
                EngineSource.Progressive(
                    segments = stream.segments.map {
                        EngineSource.ProgressiveSegment(it.url, it.durationMs)
                    }
                ) to 0
            }

            null -> null
        }
    }

    // vod: config & history
    private suspend fun buildPlayerConfig(): PlayerConfig {
        val settings = playerSettings.state.first()
        val buffer = settings.buffer.profile
        val playback = settings.playback
        return PlayerConfig(
            minBufferMs = buffer.minBufferMs,
            maxBufferMs = buffer.maxBufferMs,
            playBufferMs = buffer.playBufferMs,
            rebufferMs = buffer.rebufferMs,
            backBufferMs = buffer.backBufferMs,
            decoderMode = if (playback.preferSoftwareDecode) {
                DecoderMode.Soft
            } else {
                DecoderMode.Hard
            },
            decoderFallback = playback.decoderFallback
        )
    }

    private suspend fun resolveStartMs(
        request: PlaybackRequest,
        source: PlaybackSource,
        prefetchedLocal: PlaybackHistory?
    ): Long? {
        request.seekToMs?.let { return it }
        val key = PlaybackHistoryKey.video(source.report)
        val local = when {
            prefetchedLocal?.key == key -> prefetchedLocal
            else -> playbackHistoryRepo.getVideo(authProvider.mid, key)
        }
        if (local != null && canResume(local.progressMs, local.finished, source.durationMs)) {
            return local.progressMs
        }
        if (canResume(source.resumePositionMs, finished = false, durationMs = source.durationMs)) {
            return source.resumePositionMs
        }
        return 0L
    }

    private suspend fun readLocalResumeIfResolvable(request: PlaybackRequest): PlaybackHistory? {
        if (request.seekToMs != null) return null
        val report = request.playable.getReportCommonParams()
        val key = when {
            report.cid <= 0L -> null
            report.biz == PlayBiz.PGC && (report.epId ?: 0L) > 0L -> PlaybackHistoryKey.video(report)
            report.biz != PlayBiz.PGC && report.aid > 0L -> PlaybackHistoryKey.video(report)
            else -> null
        } ?: return null
        return playbackHistoryRepo.getVideo(authProvider.mid, key)
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

    // vod: state mapping
    private fun refreshVideoState(isNewSeekEvent: Boolean = false) {
        _videoState.value = vodSession.value.toVideoPlaybackState(
            state = playerEngine.playbackState.value,
            prev = _videoState.value,
            isNewSeekEvent = isNewSeekEvent
        )
        syncSessionState()
    }

    private fun PlayerSessionState.toVideoPlaybackState(
        state: PlayerPlaybackState,
        prev: VideoPlaybackState,
        isNewSeekEvent: Boolean
    ): VideoPlaybackState {
        return VideoPlaybackState(
            isPreparing = isPreparing,
            playbackSource = playbackSource,
            currentStream = currentStream,
            currentAudio = currentAudio,
            cdnIndex = cdnIndex,
            error = error,
            isPlaying = state.isPlaying,
            playWhenReady = state.playWhenReady,
            playbackState = state.playbackState,
            speed = state.speed,
            videoWidth = state.videoWidth,
            videoHeight = state.videoHeight,
            videoDecoderName = state.videoDecoderName,
            audioDecoderName = state.audioDecoderName,
            hasRenderedFirstFrame = state.firstFrameSeq > 0L,
            seekEventId = if (isNewSeekEvent) prev.seekEventId + 1L else prev.seekEventId,
            playerError = state.errorMessage
        )
    }

    private suspend fun onVideoPlayerState(state: PlayerPlaybackState) {
        val isNewSeekEvent = state.seekEventSeq != lastDiscontinuitySeq
        lastDiscontinuitySeq = state.seekEventSeq
        refreshVideoState(isNewSeekEvent = isNewSeekEvent)
    }

    // live: internals 
    private fun onLivePlayerState(state: PlayerPlaybackState) {
        _liveState.value = _liveState.value.copy(
            isPlaying = state.isPlaying,
            playWhenReady = state.playWhenReady,
            playbackState = state.playbackState,
            videoWidth = state.videoWidth,
            videoHeight = state.videoHeight,
            hasRenderedFirstFrame = state.firstFrameSeq > 0L,
            playerError = state.errorMessage
        )
        syncSessionState()
    }

    private fun finishLivePlayback(releasePlayer: Boolean) {
        val hadLive = _liveState.value.playbackSource != null
        _liveState.value = LivePlaybackViewState()
        if (hadLive) {
            if (releasePlayer) playerEngine.release()
            else playerEngine.stopForReuse(resetPosition = true)
        }
    }

    // session state
    private fun syncSessionState() {
        val mediaMetadata = player.value?.currentMediaItem?.mediaMetadata
        _sessionState.value = when (val target = _currentTarget.value) {
            is StreamPlaybackTarget.Video -> {
                val state = _videoState.value
                StreamPlaybackSessionState(
                    target = target,
                    isPreparing = state.isPreparing,
                    isPlaying = state.isPlaying,
                    playWhenReady = state.playWhenReady,
                    playbackState = state.playbackState,
                    title = mediaMetadata?.title?.toString().orEmpty(),
                    subtitle = mediaMetadata?.artist?.toString()?.takeIf(String::isNotBlank),
                    cover = mediaMetadata?.artworkUri?.toString()?.takeIf(String::isNotBlank),
                    videoWidth = state.videoWidth,
                    videoHeight = state.videoHeight,
                    hasRenderedFirstFrame = state.hasRenderedFirstFrame,
                    playerError = state.playerError
                )
            }

            is StreamPlaybackTarget.Live -> {
                val state = _liveState.value
                StreamPlaybackSessionState(
                    target = target,
                    isPreparing = state.isPreparing,
                    isPlaying = state.isPlaying,
                    playWhenReady = state.playWhenReady,
                    playbackState = state.playbackState,
                    title = target.route.title?.takeIf(String::isNotBlank)
                        ?: mediaMetadata?.title?.toString().orEmpty(),
                    subtitle = target.route.ownerName?.takeIf(String::isNotBlank)
                        ?: mediaMetadata?.artist?.toString()?.takeIf(String::isNotBlank),
                    cover = target.route.cover?.takeIf(String::isNotBlank)
                        ?: mediaMetadata?.artworkUri?.toString()?.takeIf(String::isNotBlank),
                    videoWidth = state.videoWidth,
                    videoHeight = state.videoHeight,
                    hasRenderedFirstFrame = state.hasRenderedFirstFrame,
                    playerError = state.playerError
                )
            }

            null -> StreamPlaybackSessionState()
        }
    }

    private suspend fun reportRoomEntryAction(roomId: Long, jumpFrom: Int) {
        runCatching {
            liveRepository.reportRoomEntryAction(roomId = roomId, jumpFrom = jumpFrom)
        }.onFailure { error ->
            Logger.w(TAG) {
                "report room entry failed roomId=$roomId jumpFrom=$jumpFrom msg=${error.message}"
            }
        }
    }

    private fun Throwable.toLiveError(): LivePlaybackError {
        val msg = message ?: "直播取流失败"
        return when (this) {
            is IllegalStateException -> LivePlaybackError.NoPlayableStream(msg)
            else -> LivePlaybackError.RequestFailed(msg, this)
        }
    }

    private companion object {
        const val TAG = "StreamPlayback"
        const val COMPLETE_THRESHOLD_MS = 3_000L
        const val DEFAULT_CDN_INDEX = 0
    }
}

private data class OpenConfig(
    val source: PlaybackSource,
    val preferredQuality: Int,
    val preferredAudioId: Int,
    val preferredCdnIndex: Int,
    val localResume: PlaybackHistory?
)

private class NoPlayableStreamException(
    message: String
) : IllegalStateException(message)
