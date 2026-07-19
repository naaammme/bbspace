package com.naaammme.bbspace.core.playback

import androidx.media3.common.Player
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import com.naaammme.bbspace.core.common.AuthProvider
import com.naaammme.bbspace.core.common.log.Logger
import com.naaammme.bbspace.core.settings.AppSettings
import com.naaammme.bbspace.core.video.VideoDetailRepository
import com.naaammme.bbspace.core.danmaku.VodDanmakuRepository
import com.naaammme.bbspace.core.history.PlaybackHistoryRepository
import com.naaammme.bbspace.core.live.LiveRepository
import com.naaammme.bbspace.core.playback.LivePlaybackController
import com.naaammme.bbspace.core.playback.StreamPlaybackSession
import com.naaammme.bbspace.core.playback.VideoPlaybackController
import com.naaammme.bbspace.core.playback.VideoPlayerRepository
import com.naaammme.bbspace.core.model.DanmakuSessionState
import com.naaammme.bbspace.core.model.LivePlaybackError
import com.naaammme.bbspace.core.model.LivePlaybackViewState
import com.naaammme.bbspace.core.model.LiveRoute
import com.naaammme.bbspace.core.model.PlayBiz
import com.naaammme.bbspace.core.model.PlayReportParams
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
import com.naaammme.bbspace.core.model.ResolvedVideoIds
import com.naaammme.bbspace.core.model.StreamPlaybackSessionState
import com.naaammme.bbspace.core.model.StreamPlaybackTarget
import com.naaammme.bbspace.core.model.VideoPlaybackState
import com.naaammme.bbspace.core.model.VideoTarget
import com.naaammme.bbspace.core.model.VideoCdnMode
import com.naaammme.bbspace.core.model.selectPlaybackCdn
import com.naaammme.bbspace.core.model.toReportParams
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class StreamPlaybackSessionImpl @Inject constructor(
    private val videoRepository: VideoPlayerRepository,
    private val detailRepository: VideoDetailRepository,
    danmakuRepository: VodDanmakuRepository,
    private val appSettings: AppSettings,
    private val playerSettings: AppSettings,
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
    private val vodSession = MutableStateFlow(PlayerSessionState(biz = PlayBiz.UGC))
    private val _videoState = MutableStateFlow(VideoPlaybackState(biz = PlayBiz.UGC))
    override val videoState: StateFlow<VideoPlaybackState> = _videoState.asStateFlow()
    override val playbackProgress: StateFlow<PlaybackProgress> = playerEngine.playbackProgress
    private val prepMu = Mutex()
    private val openId = AtomicLong(0L)
    private var lastDiscontinuitySeq = 0L
    private var nextPlayWhenReady = true
    private var currentVideoCdnMode = VideoCdnMode.Backup2

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
                if (!enabled) {
                    danmakuSession.clear()
                    return@collect
                }
                syncDanmakuSource(vodSession.value)
            }
        }
        runtimeScope.launch {
            playerSettings.state.map { it.playback.videoCdnMode }.collect { mode ->
                if (currentVideoCdnMode == mode) return@collect
                currentVideoCdnMode = mode
                if (_currentTarget.value !is StreamPlaybackTarget.Video) return@collect
                val state = vodSession.value
                if (state.playbackSource == null || state.currentStream == null) return@collect
                applyVideoSelection(state)
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
                    .setArtist(source.currentDescription.takeIf(String::isNotBlank))
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
        applyVideoSelection(state = state.copy(currentStream = stream, currentAudio = audio))
    }

    override fun switchVideoAudio(audioId: Int) {
        if (_currentTarget.value !is StreamPlaybackTarget.Video) return
        val state = vodSession.value
        val source = state.playbackSource ?: return
        val audio = source.audios.firstOrNull { it.id == audioId } ?: return
        applyVideoSelection(state = state.copy(currentAudio = audio))
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
        val currentVideoTarget = (_currentTarget.value as? StreamPlaybackTarget.Video)?.target
        val currentState = vodSession.value
        if (
            currentVideoTarget == target &&
            currentState.error == null &&
            (currentState.playbackSource != null || currentState.isPreparing)
        ) {
            _currentTarget.value = StreamPlaybackTarget.Video(target)
            syncSessionState()
            return
        }
        val reuseDetail = currentState.takeIf {
            currentVideoTarget is VideoTarget.Ugc &&
                target is VideoTarget.Ugc &&
                currentVideoTarget.aid > 0L &&
                currentVideoTarget.aid == target.aid
        }

        val token = openId.incrementAndGet()
        nextPlayWhenReady = true
        reporter.bindOwner(token)
        val initState = PlayerSessionState(
            request = request,
            biz = reuseDetail?.biz ?: request.playable.biz.biz,
            ids = ResolvedVideoIds(
                aid = request.ids.aid,
                cid = request.ids.cid,
                epId = request.ids.epId,
                seasonId = request.ids.seasonId,
                bvid = request.ids.bvid
            ),
            detail = reuseDetail?.detail,
            detailLoading = reuseDetail == null,
            isPreparing = true
        )
        finishVideoPlayback(
            invalidateOpen = false,
            releasePlayer = false,
            nextTarget = target,
            nextState = initState,
            finalizeReportAsync = true
        )

        try {
            coroutineScope {
                val prepareJob = async { prepare() }
                val sourceJob = async { videoRepository.fetchPlaybackSource(request) }
                val localResumeJob = async(Dispatchers.IO) { readLocalResumeIfResolvable(request) }
                val qualityJob = async {
                    request.preferredQuality ?: appSettings.defaultVideoQuality.first()
                }
                val audioJob = async { appSettings.defaultAudioQuality.first() }

                prepareJob.await()
                val source = sourceJob.await()
                val preferredQualityValue = qualityJob.await()
                val preferredAudioId = audioJob.await()
                val localResume = localResumeJob.await()
                if (openId.get() != token) return@coroutineScope

                val stream = source.streams.firstOrNull { it.quality == preferredQualityValue }
                    ?: source.streams.firstOrNull()
                val audio = selectAudio(stream, source.audios, preferredAudioId)
                val cdnMode = playerSettings.state.first().playback.videoCdnMode
                currentVideoCdnMode = cdnMode
                val engineSource = buildVideoEngineSource(
                    stream = stream,
                    audio = audio,
                    cdnMode = cdnMode,
                    durationMs = source.durationMs
                )
                    ?: throw NoPlayableStreamException("暂无可用播放流")
                val startMs = resolveStartMs(request, source, localResume)
                if (openId.get() != token) return@coroutineScope

                val playbackState = vodSession.value.copy(
                    playbackSource = source,
                    currentStream = stream,
                    currentAudio = audio,
                    error = null
                )
                vodSession.value = playbackState.copy(isPreparing = true)
                playerEngine.setSource(
                    source = engineSource,
                    startPositionMs = startMs,
                    playWhenReady = nextPlayWhenReady
                )
                if (openId.get() != token) return@coroutineScope

                vodSession.value = playbackState.copy(isPreparing = false)
                refreshVideoState()
                if (reuseDetail != null) {
                    runtimeScope.launch {
                        startReportIfReady(vodSession.value)
                        syncDanmakuSource(vodSession.value)
                    }
                    return@coroutineScope
                }

                val detailResult = runCatching {
                    withContext(Dispatchers.IO) {
                        detailRepository.fetchVideoDetail(
                            ids = request.ids,
                            src = target.src
                        )
                    }
                }.getOrElse { error ->
                    if (error is CancellationException) throw error
                    if (openId.get() != token) return@coroutineScope
                    val currentTarget = (_currentTarget.value as? StreamPlaybackTarget.Video)?.target
                    if (currentTarget != target) return@coroutineScope
                    vodSession.value = vodSession.value.copy(
                        detail = null,
                        detailLoading = false,
                        detailError = error.message ?: "加载视频详情失败"
                    )
                    refreshVideoState()
                    return@coroutineScope
                }
                if (openId.get() != token) return@coroutineScope
                val currentTarget = (_currentTarget.value as? StreamPlaybackTarget.Video)?.target
                if (currentTarget != target) return@coroutineScope
                applyDetailResult(detailResult)
            }
        } catch (t: Throwable) {
            if (t is CancellationException) {
                if (openId.get() == token && vodSession.value.playbackSource == null) {
                    nextPlayWhenReady = true
                    _currentTarget.value = null
                    vodSession.value = PlayerSessionState(biz = PlayBiz.UGC)
                    _videoState.value = VideoPlaybackState(biz = PlayBiz.UGC)
                    danmakuSession.clear()
                    syncSessionState()
                }
                throw t
            }
            if (openId.get() != token) return

            nextPlayWhenReady = true
            Logger.e(TAG, t) {
                "load playback source failed biz=${request.playable.biz.biz} " +
                    "aid=${request.ids.aid} cid=${request.ids.cid} " +
                    "epId=${request.ids.epId} seasonId=${request.ids.seasonId} " +
                    "q=${request.preferredQuality} msg=${t.message}"
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
        vodSession.value = nextState ?: PlayerSessionState(biz = PlayBiz.UGC)
        _videoState.value = nextState?.toVideoPlaybackState(
            state = playerEngine.playbackState.value,
            prev = _videoState.value,
            isNewSeekEvent = false
        ) ?: VideoPlaybackState(biz = PlayBiz.UGC)
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

    private fun applyDetailResult(detailResult: com.naaammme.bbspace.core.model.VideoDetailResult) {
        vodSession.value = vodSession.value.copy(
            biz = detailResult.biz,
            ids = detailResult.ids,
            detail = detailResult.detail,
            detailLoading = false,
            detailError = null
        )
        playerEngine.setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(detailResult.detail.title.takeIf(String::isNotBlank))
                .setArtist(detailResult.detail.owner?.name?.takeIf(String::isNotBlank))
                .setArtworkUri(
                    detailResult.detail.cover?.takeIf(String::isNotBlank)?.let(android.net.Uri::parse)
                )
                .build()
        )
        refreshVideoState()
        runtimeScope.launch {
            startReportIfReady(vodSession.value)
            syncDanmakuSource(vodSession.value)
        }
    }

    private fun applyVideoSelection(
        state: PlayerSessionState
    ) {
        val source = buildVideoEngineSource(
            stream = state.currentStream,
            audio = state.currentAudio,
            cdnMode = currentVideoCdnMode,
            durationMs = state.playbackSource?.durationMs
        ) ?: return
        runtimeScope.launch {
            try {
                playerEngine.setSource(
                    source,
                    currentPlaybackPositionMs(),
                    currentPlayWhenReady()
                )
                vodSession.value = state
                refreshVideoState()
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                Logger.e(TAG, t) { "apply video selection failed msg=${t.message}" }
                refreshVideoState()
            }
        }
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
        cdnMode: VideoCdnMode,
        durationMs: Long?
    ): EngineSource? {
        return when (stream) {
            is PlaybackStream.Dash -> {
                val cdn = selectPlaybackCdn(stream, audio, cdnMode) ?: return null
                EngineSource.SingleFileDash(
                    videoUrl = cdn.videoUrl,
                    audioUrl = cdn.audioUrl,
                    videoSampleMimeType = resolveVideoSampleMimeType(stream.codecId),
                    audioSampleMimeType = audio?.let { resolveAudioSampleMimeType(it.id) },
                    durationMs = durationMs
                )
            }

            is PlaybackStream.Progressive -> {
                EngineSource.Progressive(
                    segments = stream.segments.map {
                        EngineSource.ProgressiveSegment(it.url, it.durationMs)
                    }
                )
            }

            null -> null
        }
    }

    // B站 codec/audio id 映射为 Media3 sampleMimeType
    // TODO: 未来需要为 eac3 提供软解 ?
    private fun resolveVideoSampleMimeType(codecId: Int): String = when (codecId) {
        12 -> MimeTypes.VIDEO_H265
        13 -> MimeTypes.VIDEO_AV1
        else -> MimeTypes.VIDEO_H264
    }

    private fun resolveAudioSampleMimeType(audioId: Int): String = when (audioId) {
        30250 -> MimeTypes.AUDIO_E_AC3
        30251 -> MimeTypes.AUDIO_FLAC
        else -> MimeTypes.AUDIO_AAC
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
        val report = request.toInitialReportParams() ?: return source.resumePositionMs ?: 0L
        val key = PlaybackHistoryKey.video(report)
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
        val report = request.toInitialReportParams() ?: return null
        val key = PlaybackHistoryKey.video(report)
        return playbackHistoryRepo.getVideo(authProvider.mid, key)
    }

    private fun PlaybackRequest.toInitialReportParams(): PlayReportParams? {
        val reqIds = ids
        val resolved = ResolvedVideoIds(
            aid = reqIds.aid,
            cid = reqIds.cid,
            epId = reqIds.epId,
            seasonId = reqIds.seasonId,
            bvid = reqIds.bvid
        )
        if (!resolved.danmakuReady) return null
        return resolved.toReportParams(
            biz = playable.biz.biz,
            subType = playable.biz.subType
        )
    }

    private suspend fun startReportIfReady(state: PlayerSessionState) {
        if (state.detail == null) return
        val request = state.request ?: return
        val ids = state.ids.takeIf { it.danmakuReady } ?: return
        if (state.playbackSource == null) return
        val biz = state.biz
        reporter.startSession(
            request = request,
            state = state,
            report = ids.toReportParams(
                biz = biz,
                subType = request.playable.biz.subType
            ),
            startPositionMs = currentPlaybackPositionMs()
        )
        reporter.onPlaybackState(
            state = vodSession.value,
            playbackState = playerEngine.playbackState.value,
            progress = currentPlaybackProgress()
        )
    }

    private suspend fun syncDanmakuSource(state: PlayerSessionState) {
        if (state.detail == null) return
        val ids = state.ids.takeIf { it.danmakuReady } ?: return
        val source = state.playbackSource ?: return
        if (!playerSettings.state.first().danmaku.enabled) return
        danmakuSession.setSource(ids, source.durationMs)
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
            biz = biz,
            ids = ids,
            detail = detail,
            detailLoading = detailLoading,
            detailError = detailError,
            isPreparing = isPreparing,
            playbackSource = playbackSource,
            currentStream = currentStream,
            currentAudio = currentAudio,
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

    private fun onVideoPlayerState(state: PlayerPlaybackState) {
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
        _currentTarget.value = null
        _liveState.value = LivePlaybackViewState()
        if (hadLive) {
            if (releasePlayer) playerEngine.release()
            else playerEngine.stopForReuse(resetPosition = true)
        }
        syncSessionState()
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
                    title = state.detail?.title?.takeIf(String::isNotBlank)
                        ?: mediaMetadata?.title?.toString().orEmpty(),
                    subtitle = state.detail?.owner?.name?.takeIf(String::isNotBlank)
                        ?: mediaMetadata?.artist?.toString()?.takeIf(String::isNotBlank),
                    cover = state.detail?.cover?.takeIf(String::isNotBlank)
                        ?: mediaMetadata?.artworkUri?.toString()?.takeIf(String::isNotBlank),
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
    }
}

private class NoPlayableStreamException(
    message: String
) : IllegalStateException(message)
