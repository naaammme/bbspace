package com.naaammme.bbspace.feature.video.model

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naaammme.bbspace.core.data.AppSettings
import com.naaammme.bbspace.core.data.player.PlayerSessionManager
import com.naaammme.bbspace.core.domain.danmaku.DanmakuRepository
import com.naaammme.bbspace.core.domain.video.VideoDetailRepository
import com.naaammme.bbspace.core.model.PlaybackRequest
import com.naaammme.bbspace.core.model.VideoDetail
import com.naaammme.bbspace.core.model.VideoJump
import com.naaammme.bbspace.core.model.VideoJumpTool
import com.naaammme.bbspace.core.model.VideoPlaybackId
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
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
    private val aid = savedStateHandle.get<Long>("aid") ?: 0L
    private val src = VideoJumpTool.custom(
        from = savedStateHandle.get<String>("from"),
        fromSpmid = savedStateHandle.get<String>("fromSpmid"),
        trackId = savedStateHandle.get<String>("trackId"),
        reportFlowData = savedStateHandle.get<String>("report")
    )
    private val _detail = MutableStateFlow<VideoDetail?>(null)
    private val _detailLoading = MutableStateFlow(aid > 0L)
    private val _detailError = MutableStateFlow<String?>(null)
    private val initReq = run {
        val cid = savedStateHandle.get<Long>("cid") ?: 0L
        if (aid > 0 && cid > 0) {
            PlaybackRequest(
                videoId = VideoPlaybackId(aid = aid, cid = cid),
                fromSpmid = src.fromSpmid,
                trackId = src.trackId,
                reportFlowData = src.reportFlowData
            )
        } else {
            null
        }
    }
    private val _req = MutableStateFlow(initReq)
    private val danmakuController = VideoDanmakuController(
        scope = viewModelScope,
        repository = danmakuRepository
    )

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
        danmakuController.bind(
            playbackSourceFlow = sessionManager.state.map { it.playbackSource },
            snapshotFlow = sessionManager.playerEngine.snapshot,
            enabledFlow = appSettings.danmakuEnabled
        )

        if (aid > 0L) {
            viewModelScope.launch {
                val result = runCatching { detailRepo.fetchVideoDetail(aid, src) }
                _detail.value = result.getOrNull()
                _detailError.value = result.exceptionOrNull()?.message
                    ?: if (result.isFailure) "加载视频详情失败" else null
                _detailLoading.value = false
            }
        } else {
            _detailLoading.value = false
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
            videoId = request.videoId.copy(cid = cid),
            seekToMs = null
        )
        _req.value = next
        if (sessionManager.hasSession(ownerId, next)) return
        viewModelScope.launch {
            sessionManager.start(ownerId, next)
        }
    }

    fun buildJump(
        aid: Long,
        cid: Long
    ): VideoJump {
        return VideoJump(aid = aid, cid = cid, src = src)
    }

    fun close() {
        sessionManager.closeCurrentSession(ownerId)
    }

    override fun onCleared() {
        danmakuController.clear()
        close()
        super.onCleared()
    }

    private companion object {
        val nextOwnerId = AtomicLong(1L)
    }
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
