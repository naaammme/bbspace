package com.naaammme.bbspace.feature.video.model

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naaammme.bbspace.core.domain.danmaku.VodDanmakuRepository
import com.naaammme.bbspace.core.domain.player.PlayerSettings
import com.naaammme.bbspace.core.domain.player.VideoPlaybackController
import com.naaammme.bbspace.core.domain.video.VideoDetailRepository
import com.naaammme.bbspace.core.model.CommentSubject
import com.naaammme.bbspace.core.model.CommentSubjectTool
import com.naaammme.bbspace.core.model.DanmakuConfig
import com.naaammme.bbspace.core.model.PlayBiz
import com.naaammme.bbspace.core.model.PlaybackError
import com.naaammme.bbspace.core.model.PlaybackRequest
import com.naaammme.bbspace.core.model.PlaybackViewState
import com.naaammme.bbspace.core.model.PlayerBufferSettings
import com.naaammme.bbspace.core.model.PlayerPlaybackPrefs
import com.naaammme.bbspace.core.model.VideoDetail
import com.naaammme.bbspace.core.model.VideoHistoryMeta
import com.naaammme.bbspace.core.model.VideoRoute
import com.naaammme.bbspace.core.model.VideoRouteTool
import com.naaammme.bbspace.core.model.toPlayableParams
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class VideoViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val playbackController: VideoPlaybackController,
    private val playerSettings: PlayerSettings,
    private val detailRepo: VideoDetailRepository,
    vodDanmakuRepository: VodDanmakuRepository
) : ViewModel() {

    private val src = VideoRouteTool.custom(
        from = savedStateHandle.get<String>("from"),
        fromSpmid = savedStateHandle.get<String>("fromSpmid"),
        trackId = savedStateHandle.get<String>("trackId"),
        reportFlowData = savedStateHandle.get<String>("report")
    )
    private val route = savedStateHandle.toVideoRoute(src)
    private val ugcRoute = route as? VideoRoute.Ugc
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
    private val danmakuSession = VodDanmakuSession(
        scope = viewModelScope,
        repository = vodDanmakuRepository
    )
    private val handle = MutableStateFlow<VideoPlaybackController.Handle?>(null)
    private var startJob: Job? = null
    private var openingRequest: PlaybackRequest? = null

    val player = handle.flatMapLatest { it?.player ?: flowOf<Player?>(null) }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null
    )

    val playerState: StateFlow<PlaybackViewState> = handle.flatMapLatest {
        it?.state ?: flowOf(PlaybackViewState())
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PlaybackViewState()
    )

    val settingsState = playerSettings.state

    val pageState = combine(
        playerState,
        _req,
        _detail,
        _detailLoading,
        _detailError
    ) { playbackState, req, detail, detailLoading, detailError ->
        VideoPageState(
            detail = detail,
            detailLoading = detailLoading,
            detailError = detailError,
            curCid = playbackState.playbackSource?.videoId?.cid ?: req?.videoId?.cid
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = VideoPageState(curCid = initReq?.videoId?.cid)
    )

    val commentSubject: CommentSubject?
        get() {
            val targetAid = playerState.value.playbackSource?.videoId?.aid?.takeIf { it > 0L }
                ?: ugcRoute?.aid
                ?: return null
            return CommentSubjectTool.video(targetAid, src)
        }

    internal val danmakuState = danmakuSession.state

    init {
        acquireHandle()
        loadInitialDetail()
    }

    fun ensureStarted() {
        handle.value?.let { playbackHandle ->
            danmakuSession.bind(
                playbackStateFlow = playbackHandle.state,
                enabledFlow = settingsState.map { it.danmaku.enabled }
            )
        }
        val request = _req.value ?: return
        if (openingRequest == request && startJob?.isActive == true) return
        startPlayback(request)
    }

    fun togglePlayPause() {
        val playbackHandle = handle.value ?: return
        if (playerState.value.isPlaying) {
            playbackHandle.pause()
        } else {
            playbackHandle.play()
        }
    }

    fun onDanmakuTick(positionMs: Long) {
        danmakuSession.onTick(positionMs)
    }

    fun switchQuality(quality: Int) {
        handle.value?.switchQuality(quality)
    }

    fun switchAudio(audioId: Int) {
        handle.value?.switchAudio(audioId)
    }

    fun switchCdn(cdnIndex: Int) {
        handle.value?.switchCdn(cdnIndex)
    }

    fun pause() {
        handle.value?.pause()
    }

    fun seekTo(positionMs: Long) {
        handle.value?.seekTo(positionMs)
    }

    fun setSpeed(speed: Float) {
        handle.value?.setSpeed(speed)
    }

    fun updateBackgroundPlayback(enabled: Boolean) {
        updatePlayback { copy(backgroundPlayback = enabled) }
    }

    fun updateReportPlayback(enabled: Boolean) {
        updatePlayback { copy(reportPlayback = enabled) }
    }

    fun updateMinBufferMs(value: Int) {
        updateBuffer { copy(minBufferMs = value) }
    }

    fun updateMaxBufferMs(value: Int) {
        updateBuffer { copy(maxBufferMs = value) }
    }

    fun updatePlaybackBufferMs(value: Int) {
        updateBuffer { copy(playbackBufferMs = value) }
    }

    fun updateRebufferMs(value: Int) {
        updateBuffer { copy(rebufferMs = value) }
    }

    fun updateBackBufferMs(value: Int) {
        updateBuffer { copy(backBufferMs = value) }
    }

    fun updatePreferSoftwareDecode(enabled: Boolean) {
        updatePlayback { copy(preferSoftwareDecode = enabled) }
    }

    fun updateDecoderFallback(enabled: Boolean) {
        updatePlayback { copy(decoderFallback = enabled) }
    }

    fun updateDanmaku(config: DanmakuConfig) {
        viewModelScope.launch {
            playerSettings.updateDanmaku(config)
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
        startPlayback(next)
    }

    fun currentDownloadRoute(): VideoRoute? {
        val base = route ?: return null
        if (base !is VideoRoute.Ugc) return base
        val id = playerState.value.playbackSource?.videoId ?: _req.value?.videoId
        return base.copy(
            aid = id?.aid?.takeIf { it > 0L } ?: base.aid,
            cid = id?.cid?.takeIf { it > 0L } ?: base.cid,
            bvid = id?.bvid?.takeIf(String::isNotBlank) ?: base.bvid
        )
    }

    fun currentDownloadTitle(): String? {
        val detail = _detail.value ?: return null
        val cid = playerState.value.playbackSource?.videoId?.cid ?: _req.value?.videoId?.cid
        val part = cid?.let { target -> detail.pages.firstOrNull { it.cid == target } }
        return listOfNotNull(
            detail.title.takeIf(String::isNotBlank),
            part?.part?.takeIf(String::isNotBlank)
        ).joinToString(" - ").takeIf(String::isNotBlank)
    }

    fun closePage() {
        startJob?.cancel()
        startJob = null
        openingRequest = null
        danmakuSession.clear()
        handle.value?.release()
    }

    override fun onCleared() {
        startJob?.cancel()
        startJob = null
        openingRequest = null
        val playbackHandle = handle.value ?: run {
            danmakuSession.clear()
            super.onCleared()
            return
        }
        playbackHandle.release()
        danmakuSession.clear()
        super.onCleared()
    }

    private fun startPlayback(request: PlaybackRequest) {
        if (openingRequest == request && startJob?.isActive == true) return
        startJob?.cancel()
        openingRequest = request
        startJob = viewModelScope.launch {
            try {
                handle.value?.open(request)
            } finally {
                if (openingRequest == request) {
                    openingRequest = null
                }
            }
        }
    }

    private fun acquireHandle() {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            val playbackHandle = playbackController.acquire()
            handle.value = playbackHandle
            launch {
                combine(_detail, _req, playbackHandle.state) { detail, req, playbackState ->
                    detail.toHistoryMeta(playbackState.playbackSource?.videoId?.cid ?: req?.videoId?.cid)
                }.collect { meta ->
                    playbackHandle.updateMeta(meta)
                }
            }
            if (route is VideoRoute.Pgc || route is VideoRoute.Pugv) {
                launch {
                    var loadedAid = _detail.value?.aid?.takeIf { it > 0L } ?: 0L
                    var loadedBvid = _detail.value?.bvid.orEmpty()
                    playbackHandle.state.collect { playbackState ->
                        val videoId = playbackState.playbackSource?.videoId
                        val aid = videoId?.aid?.takeIf { it > 0L }
                        if (aid != null) {
                            val bvid = videoId.bvid.orEmpty()
                            if (aid == loadedAid && bvid == loadedBvid) return@collect
                            loadedAid = aid
                            loadedBvid = bvid
                            fetchDetail(aid, videoId.bvid, route.src)
                            return@collect
                        }
                        val error = playbackState.error
                        if (error != null && _detail.value == null) {
                            _detailError.value = error.toUiMsg()
                            _detailLoading.value = false
                        }
                    }
                }
            }
        }
    }

    private fun loadInitialDetail() {
        when (val route = route) {
            is VideoRoute.Ugc -> {
                viewModelScope.launch {
                    fetchDetail(route.aid, route.bvid, route.src)
                }
            }

            is VideoRoute.Pgc, is VideoRoute.Pugv -> Unit
            else -> _detailLoading.value = false
        }
    }

    private fun updateBuffer(transform: PlayerBufferSettings.() -> PlayerBufferSettings) {
        viewModelScope.launch {
            playerSettings.updateBuffer(settingsState.value.buffer.transform())
        }
    }

    private fun updatePlayback(transform: PlayerPlaybackPrefs.() -> PlayerPlaybackPrefs) {
        viewModelScope.launch {
            playerSettings.updatePlayback(settingsState.value.playback.transform())
        }
    }

    private suspend fun fetchDetail(
        aid: Long,
        bvid: String?,
        src: com.naaammme.bbspace.core.model.VideoSrc
    ) {
        _detailError.value = null
        _detailLoading.value = true
        val result = runCatching {
            detailRepo.fetchVideoDetail(
                aid = aid,
                bvid = bvid,
                src = src
            )
        }
        _detail.value = result.getOrNull()
        _detailError.value = result.exceptionOrNull()?.message
            ?: if (result.isFailure) "加载视频详情失败" else null
        _detailLoading.value = false
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
