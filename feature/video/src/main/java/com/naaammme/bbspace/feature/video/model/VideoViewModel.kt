package com.naaammme.bbspace.feature.video.model

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naaammme.bbspace.core.data.AppSettings
import com.naaammme.bbspace.core.data.player.PlayerSessionManager
import com.naaammme.bbspace.core.model.CommentSubject
import com.naaammme.bbspace.core.model.CommentSubjectTool
import com.naaammme.bbspace.core.model.PlayBiz
import com.naaammme.bbspace.core.domain.danmaku.DanmakuRepository
import com.naaammme.bbspace.core.domain.video.VideoDetailRepository
import com.naaammme.bbspace.core.model.PlaybackError
import com.naaammme.bbspace.core.model.VideoDetail
import com.naaammme.bbspace.core.model.VideoHistoryMeta
import com.naaammme.bbspace.core.model.VideoRoute
import com.naaammme.bbspace.core.model.VideoRouteTool
import com.naaammme.bbspace.core.model.toPlayableParams
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class VideoViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val appSettings: AppSettings,
    private val sessionManager: PlayerSessionManager,
    private val detailRepo: VideoDetailRepository,
    danmakuRepository: DanmakuRepository
) : ViewModel() {

    private val ownerId = nextOwnerId.getAndIncrement()
    private val src = VideoRouteTool.custom(
        from = savedStateHandle.get<String>("from"),
        fromSpmid = savedStateHandle.get<String>("fromSpmid"),
        trackId = savedStateHandle.get<String>("trackId"),
        reportFlowData = savedStateHandle.get<String>("report")
    )
    private val route = savedStateHandle.toVideoRoute(src)
    private val ugcRoute = route as? VideoRoute.Ugc
    val commentSubject: CommentSubject?
        get() {
            val targetAid = sessionManager.state.value.playbackSource?.videoId?.aid?.takeIf { it > 0L }
                ?: ugcRoute?.aid
                ?: return null
            return CommentSubjectTool.video(targetAid, src)
        }
    private val _detail = MutableStateFlow<VideoDetail?>(null)
    private val _detailLoading = MutableStateFlow(
        route is VideoRoute.Ugc || route is VideoRoute.Pgc || route is VideoRoute.Pugv
    )
    private val _detailError = MutableStateFlow(
        when (route) {
            null -> "视频路由无效"
            else -> null
        }
    )
    private val initReq = route
        ?.toPlayableParams()
        ?.getResolveParams()
    private val _req = MutableStateFlow(initReq)
    private val danmakuController = VideoDanmakuController(
        scope = viewModelScope,
        repository = danmakuRepository
    )
    private var metaJob: Job? = null
    private var pgcDetailJob: Job? = null

    fun getPlayerForView() = sessionManager.playerEngine.getPlayerForView()

    val pageState = combine(
        sessionManager.state,
        _req,
        _detail,
        _detailLoading,
        _detailError
    ) { session, req, detail, detailLoading, detailError ->
        VideoPageState(
            detail = detail,
            detailLoading = detailLoading,
            detailError = detailError,
            curCid = session.playbackSource?.videoId?.cid ?: req?.videoId?.cid
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = VideoPageState(curCid = initReq?.videoId?.cid)
    )

    val playerState = combine(
        sessionManager.state,
        sessionManager.playerEngine.snapshot
    ) { session, snapshot ->
        VideoPlayerState(
            isLoading = session.isPreparing,
            playbackSource = session.playbackSource,
            currentStream = session.currentStream,
            currentAudio = session.currentAudio,
            cdnIndex = session.cdnIndex,
            snapshot = snapshot,
            error = session.error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = VideoPlayerState()
    )

    val playerMenuState = appSettings.playerMenuStateFlow().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = VideoPlayerMenuState()
    )
    val backgroundPlayback = appSettings.backgroundPlayback.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = false
    )

    internal val danmakuState = danmakuController.state

    init {
        when (val route = route) {
            is VideoRoute.Ugc -> {
                viewModelScope.launch {
                    val result = runCatching {
                        detailRepo.fetchVideoDetail(
                            aid = route.aid,
                            bvid = route.bvid,
                            src = route.src
                        )
                    }
                    _detail.value = result.getOrNull()
                    _detailError.value = result.exceptionOrNull()?.message
                        ?: if (result.isFailure) "加载视频详情失败" else null
                    _detailLoading.value = false
                }
            }

            is VideoRoute.Pgc, is VideoRoute.Pugv -> {
                Unit
            }

            else -> {
                _detailLoading.value = false
            }
        }
    }

    fun attach() {
        sessionManager.bindReporter(ownerId, viewModelScope)
        danmakuController.bind(
            playbackSourceFlow = sessionManager.state.map { it.playbackSource },
            snapshotFlow = sessionManager.playerEngine.snapshot,
            enabledFlow = appSettings.danmakuEnabled
        )
        if (metaJob?.isActive != true) {
            metaJob = viewModelScope.launch {
                combine(_detail, _req, sessionManager.state) { detail, req, session ->
                    detail.toHistoryMeta(session.playbackSource?.videoId?.cid ?: req?.videoId?.cid)
                }.collect { meta ->
                    sessionManager.updateVideoMeta(ownerId, meta)
                }
            }
        }
        if ((route is VideoRoute.Pgc || route is VideoRoute.Pugv) && pgcDetailJob?.isActive != true) {
            pgcDetailJob = viewModelScope.launch {
                var loadedAid = _detail.value?.aid?.takeIf { it > 0L } ?: 0L
                var loadedBvid = _detail.value?.bvid.orEmpty()
                sessionManager.state.collect { session ->
                    val videoId = session.playbackSource?.videoId
                    val aid = videoId?.aid?.takeIf { it > 0L }
                    if (aid != null) {
                        val bvid = videoId.bvid.orEmpty()
                        if (aid == loadedAid && bvid == loadedBvid) return@collect
                        loadedAid = aid
                        loadedBvid = bvid
                        _detailError.value = null
                        _detailLoading.value = true
                        val result = runCatching {
                            detailRepo.fetchVideoDetail(
                                aid = aid,
                                bvid = videoId.bvid,
                                src = route.src
                            )
                        }
                        _detail.value = result.getOrNull()
                        _detailError.value = result.exceptionOrNull()?.message
                            ?: if (result.isFailure) "加载视频详情失败" else null
                        _detailLoading.value = false
                        return@collect
                    }
                    val error = session.error
                    if (error != null && _detail.value == null) {
                        _detailError.value = error.toUiMsg()
                        _detailLoading.value = false
                    }
                }
            }
        }
    }

    fun ensureStarted() {
        val request = _req.value ?: return
        if (sessionManager.hasSession(ownerId, request)) return
        viewModelScope.launch {
            sessionManager.start(ownerId, request)
        }
    }

    fun togglePlayPause() {
        if (playerState.value.snapshot.isPlaying) {
            sessionManager.pause(ownerId)
        } else {
            sessionManager.play(ownerId)
        }
    }

    fun switchQuality(quality: Int) {
        sessionManager.switchQuality(ownerId, quality)
    }

    fun switchAudio(audioId: Int) {
        sessionManager.switchAudio(ownerId, audioId)
    }

    fun switchCdn(cdnIndex: Int) {
        viewModelScope.launch {
            appSettings.updatePlayerCdnIndex(cdnIndex)
            sessionManager.switchCdn(ownerId, cdnIndex)
        }
    }

    fun pause() {
        sessionManager.pause(ownerId)
    }

    fun seekTo(positionMs: Long) {
        sessionManager.seekTo(ownerId, positionMs)
    }

    fun setSpeed(speed: Float) {
        sessionManager.setSpeed(ownerId, speed)
    }

    fun updateBackgroundPlayback(enabled: Boolean) {
        viewModelScope.launch {
            appSettings.updateBackgroundPlayback(enabled)
        }
    }

    fun updateMinBufferMs(value: Int) {
        viewModelScope.launch {
            appSettings.updatePlayerMinBufferMs(value)
        }
    }

    fun updateMaxBufferMs(value: Int) {
        viewModelScope.launch {
            appSettings.updatePlayerMaxBufferMs(value)
        }
    }

    fun updatePlaybackBufferMs(value: Int) {
        viewModelScope.launch {
            appSettings.updatePlayerPlaybackBufferMs(value)
        }
    }

    fun updateRebufferMs(value: Int) {
        viewModelScope.launch {
            appSettings.updatePlayerRebufferMs(value)
        }
    }

    fun updateBackBufferMs(value: Int) {
        viewModelScope.launch {
            appSettings.updatePlayerBackBufferMs(value)
        }
    }

    fun updatePreferSoftwareDecode(enabled: Boolean) {
        viewModelScope.launch {
            appSettings.updatePreferSoftwareDecode(enabled)
        }
    }

    fun updateDecoderFallback(enabled: Boolean) {
        viewModelScope.launch {
            appSettings.updateDecoderFallback(enabled)
        }
    }

    fun updateDanmakuEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appSettings.updateDanmakuEnabled(enabled)
        }
    }

    fun updateDanmakuAreaPercent(percent: Int) {
        viewModelScope.launch {
            appSettings.updateDanmakuAreaPercent(percent)
        }
    }

    fun updateDanmakuOpacity(value: Float) {
        viewModelScope.launch {
            appSettings.updateDanmakuOpacity(value)
        }
    }

    fun updateDanmakuTextScale(value: Float) {
        viewModelScope.launch {
            appSettings.updateDanmakuTextScale(value)
        }
    }

    fun updateDanmakuSpeed(value: Float) {
        viewModelScope.launch {
            appSettings.updateDanmakuSpeed(value)
        }
    }

    fun updateDanmakuDensity(level: Int) {
        viewModelScope.launch {
            appSettings.updateDanmakuDensity(level)
        }
    }

    fun updateDanmakuMergeDuplicates(enabled: Boolean) {
        viewModelScope.launch {
            appSettings.updateDanmakuMergeDuplicates(enabled)
        }
    }

    fun updateDanmakuShowTop(enabled: Boolean) {
        viewModelScope.launch {
            appSettings.updateDanmakuShowTop(enabled)
        }
    }

    fun updateDanmakuShowBottom(enabled: Boolean) {
        viewModelScope.launch {
            appSettings.updateDanmakuShowBottom(enabled)
        }
    }

    fun updateDanmakuShowScrollRl(enabled: Boolean) {
        viewModelScope.launch {
            appSettings.updateDanmakuShowScrollRl(enabled)
        }
    }

    fun switchPage(cid: Long) {
        val request = _req.value ?: return
        if (request.videoId.cid == cid) return
        val next = request.copy(
            playable = request.playable.copy(
                videoId = request.videoId.copy(cid = cid)
            ),
            seekToMs = null
        )
        _req.value = next
        if (sessionManager.hasSession(ownerId, next)) return
        viewModelScope.launch {
            sessionManager.start(ownerId, next)
        }
    }

    fun close() {
        pgcDetailJob?.cancel()
        pgcDetailJob = null
        metaJob?.cancel()
        metaJob = null
        danmakuController.clear()
        sessionManager.closeCurrentSession(ownerId, viewModelScope)
    }

    override fun onCleared() {
        close()
        super.onCleared()
    }

    private companion object {
        val nextOwnerId = AtomicLong(1L)
    }
}

private fun SavedStateHandle.optLong(key: String): Long? {
    return get<Long>(key)?.takeIf { it > 0L }
}

private fun SavedStateHandle.optInt(key: String): Int? {
    return get<Int>(key)?.takeIf { it >= 0 }
}

private fun SavedStateHandle.toVideoRoute(src: com.naaammme.bbspace.core.model.VideoSrc): VideoRoute? {
    return when (PlayBiz.from(get<String>("biz"))) {
        PlayBiz.UGC -> {
            val aid = get<Long>("aid")?.takeIf { it > 0L } ?: return null
            val cid = get<Long>("cid") ?: 0L
            VideoRoute.Ugc(
                aid = aid,
                cid = cid,
                bvid = get<String>("bvid")?.takeIf(String::isNotBlank),
                src = src
            )
        }

        PlayBiz.PGC -> {
            optLong("epId")?.let {
                VideoRoute.Pgc(
                    epId = it,
                    seasonId = optLong("seasonId"),
                    subType = optInt("subType"),
                    src = src
                )
            }
        }

        PlayBiz.PUGV -> {
            optLong("epId")?.let {
                VideoRoute.Pugv(
                    epId = it,
                    seasonId = optLong("seasonId"),
                    src = src
                )
            }
        }
    }
}

private fun PlaybackError.toUiMsg(): String {
    return when (this) {
        is PlaybackError.RequestFailed -> message
        is PlaybackError.NoPlayableStream -> message
    }
}

private fun VideoDetail?.toHistoryMeta(cid: Long?): VideoHistoryMeta? {
    this ?: return null
    val idx = cid?.let { target -> pages.indexOfFirst { it.cid == target } } ?: -1
    val part = if (idx >= 0) pages[idx] else null
    return VideoHistoryMeta(
        title = title,
        cover = cover,
        ownerUid = owner?.mid?.takeIf { it > 0L },
        ownerName = owner?.name,
        part = if (idx >= 0) idx + 1 else null,
        partTitle = part?.part
    )
}

private fun AppSettings.playerMenuStateFlow(): Flow<VideoPlayerMenuState> {
    val bufferSettings = combine(
        playerMinBufferMs,
        playerMaxBufferMs,
        playerPlaybackBufferMs,
        playerRebufferMs,
        playerBackBufferMs
    ) { minBufferMs, maxBufferMs, playbackBufferMs, rebufferMs, backBufferMs ->
        PlayerBufferSettings(
            minBufferMs = minBufferMs,
            maxBufferMs = maxBufferMs,
            playbackBufferMs = playbackBufferMs,
            rebufferMs = rebufferMs,
            backBufferMs = backBufferMs
        )
    }

    val playbackSettings = combine(
        backgroundPlayback,
        preferSoftwareDecode,
        decoderFallback
    ) { backgroundPlayback, preferSoftwareDecode, decoderFallback ->
        PlayerPlaybackSettings(
            backgroundPlayback = backgroundPlayback,
            preferSoftwareDecode = preferSoftwareDecode,
            decoderFallback = decoderFallback
        )
    }

    val danmakuDisplaySettings = combine(
        danmakuEnabled,
        danmakuAreaPercent,
        danmakuOpacity,
        danmakuTextScale,
        danmakuSpeed
    ) { enabled, areaPercent, opacity, textScale, speed ->
        DanmakuDisplaySettings(
            enabled = enabled,
            areaPercent = areaPercent,
            opacity = opacity,
            textScale = textScale,
            speed = speed
        )
    }

    val danmakuBehaviorSettings = combine(
        danmakuDensity,
        danmakuMergeDuplicates,
        danmakuShowTop,
        danmakuShowBottom,
        danmakuShowScrollRl
    ) { densityLevel, mergeDuplicates, showTop, showBottom, showScrollRl ->
        DanmakuBehaviorSettings(
            densityLevel = densityLevel,
            mergeDuplicates = mergeDuplicates,
            showTop = showTop,
            showBottom = showBottom,
            showScrollRl = showScrollRl
        )
    }

    val danmakuConfig = combine(
        danmakuDisplaySettings,
        danmakuBehaviorSettings
    ) { displaySettings, behaviorSettings ->
        VideoDanmakuConfig(
            enabled = displaySettings.enabled,
            areaPercent = displaySettings.areaPercent,
            opacity = displaySettings.opacity,
            textScale = displaySettings.textScale,
            speed = displaySettings.speed,
            densityLevel = behaviorSettings.densityLevel,
            mergeDuplicates = behaviorSettings.mergeDuplicates,
            showTop = behaviorSettings.showTop,
            showBottom = behaviorSettings.showBottom,
            showScrollRl = behaviorSettings.showScrollRl
        )
    }

    return combine(
        bufferSettings,
        playbackSettings,
        danmakuConfig
    ) { bufferSettings, playbackSettings, danmakuConfig ->
        VideoPlayerMenuState(
            minBufferMs = bufferSettings.minBufferMs,
            maxBufferMs = bufferSettings.maxBufferMs,
            playbackBufferMs = bufferSettings.playbackBufferMs,
            rebufferMs = bufferSettings.rebufferMs,
            backBufferMs = bufferSettings.backBufferMs,
            backgroundPlayback = playbackSettings.backgroundPlayback,
            preferSoftwareDecode = playbackSettings.preferSoftwareDecode,
            decoderFallback = playbackSettings.decoderFallback,
            danmaku = danmakuConfig
        )
    }
}

private data class PlayerBufferSettings(
    val minBufferMs: Int,
    val maxBufferMs: Int,
    val playbackBufferMs: Int,
    val rebufferMs: Int,
    val backBufferMs: Int
)

private data class PlayerPlaybackSettings(
    val backgroundPlayback: Boolean,
    val preferSoftwareDecode: Boolean,
    val decoderFallback: Boolean
)

private data class DanmakuDisplaySettings(
    val enabled: Boolean,
    val areaPercent: Int,
    val opacity: Float,
    val textScale: Float,
    val speed: Float
)

private data class DanmakuBehaviorSettings(
    val densityLevel: Int,
    val mergeDuplicates: Boolean,
    val showTop: Boolean,
    val showBottom: Boolean,
    val showScrollRl: Boolean
)
