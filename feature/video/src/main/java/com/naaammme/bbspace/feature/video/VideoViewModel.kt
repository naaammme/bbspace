package com.naaammme.bbspace.feature.video

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.naaammme.bbspace.core.domain.player.PlayerSettings
import com.naaammme.bbspace.core.domain.player.StreamPlaybackSession
import com.naaammme.bbspace.core.domain.player.VideoPlaybackController
import com.naaammme.bbspace.core.domain.video.VideoDetailRepository
import com.naaammme.bbspace.core.model.CommentSubject
import com.naaammme.bbspace.core.model.CommentSubjectTool
import com.naaammme.bbspace.core.model.DanmakuConfig
import com.naaammme.bbspace.core.model.PlayBiz
import com.naaammme.bbspace.core.model.PlaybackError
import com.naaammme.bbspace.core.model.PlaybackProgress
import com.naaammme.bbspace.core.model.PlayerBufferProfile
import com.naaammme.bbspace.core.model.StreamPlaybackTarget
import com.naaammme.bbspace.core.model.VideoDetail
import com.naaammme.bbspace.core.model.VideoDownloadKind
import com.naaammme.bbspace.core.model.VideoDownloadMeta
import com.naaammme.bbspace.core.model.VideoDownloadRequest
import com.naaammme.bbspace.core.model.VideoPlaybackId
import com.naaammme.bbspace.core.model.VideoPlaybackState
import com.naaammme.bbspace.core.model.VideoSrc
import com.naaammme.bbspace.core.model.VideoTarget
import com.naaammme.bbspace.core.model.isSameEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class VideoViewModel @Inject constructor(
    private val playbackController: VideoPlaybackController,
    private val streamPlaybackSession: StreamPlaybackSession,
    private val playerSettings: PlayerSettings,
    private val detailRepo: VideoDetailRepository
) : ViewModel() {

    private val _targetStack = MutableStateFlow<List<VideoTarget>>(emptyList())
    private val _detail = MutableStateFlow<VideoDetail?>(null)
    private val _detailLoading = MutableStateFlow(false)
    private val _detailError = MutableStateFlow<String?>(null)

    val player: StateFlow<Player?> = playbackController.player
    val videoState: StateFlow<VideoPlaybackState> = playbackController.videoState
    val playbackProgress: StateFlow<PlaybackProgress> = playbackController.playbackProgress
    private val currentTarget = streamPlaybackSession.currentTarget
        .map { (it as? StreamPlaybackTarget.Video)?.target }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )
    val settingsState = playerSettings.state
    val currentPageTarget: StateFlow<VideoTarget?> = _targetStack
        .map { it.lastOrNull() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )
    private val activeVideoId: StateFlow<VideoPlaybackId?> = videoState
        .map { it.playbackSource?.videoId }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )
    private val activeCid: StateFlow<Long?> = combine(
        activeVideoId,
        currentTarget,
        currentPageTarget
    ) { videoId, activeTarget, pageTarget ->
        val activeCid = (pageSessionTarget(pageTarget, activeTarget) as? VideoTarget.Ugc)?.cid
        videoId?.cid
            ?: activeCid
            ?: (pageTarget as? VideoTarget.Ugc)?.cid
    }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    val pageState = combine(
        activeCid,
        _detail,
        _detailLoading,
        _detailError
    ) { curCid, detail, detailLoading, detailError ->
        VideoPageState(
            detail = detail,
            detailLoading = detailLoading,
            detailError = detailError,
            curCid = curCid
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = VideoPageState()
    )

    val commentSubject: CommentSubject?
        get() {
            val pageTarget = currentPageTarget.value
            val src = pageTarget?.src ?: return null
            val aid = activeVideoId.value?.aid?.takeIf { it > 0L }
                ?: (pageTarget as? VideoTarget.Ugc)?.aid
                ?: return null
            return CommentSubjectTool.video(aid, src)
        }

    internal val danmakuState = playbackController.danmakuState

    init {
        bindPgcDetail()
    }

    fun openRoot(target: VideoTarget) {
        _targetStack.value = listOf(target)
        loadTargetDetail(target)
        playbackController.openVideo(target)
    }

    fun openTarget(target: VideoTarget) {
        val current = currentPageTarget.value
        if (current == target) return
        _targetStack.value = when {
            current == null -> listOf(target)
            current.isSameEntry(target) -> _targetStack.value.dropLast(1) + target
            else -> _targetStack.value + target
        }
        loadTargetDetail(target)
        playbackController.openVideo(target)
    }

    fun popPage(): Boolean {
        val stack = _targetStack.value
        if (stack.size <= 1) return false
        val nextStack = stack.dropLast(1)
        val nextTarget = nextStack.last()
        _targetStack.value = nextStack
        loadTargetDetail(nextTarget)
        playbackController.openVideo(nextTarget)
        return true
    }

    fun ensureStarted() {
        val pageTarget = currentPageTarget.value ?: return
        if (hasActivePageSession(pageTarget)) return
        playbackController.openVideo(pageTarget)
    }

    fun togglePlayPause() {
        if (videoState.value.isPlaying) {
            playbackController.pause()
        } else {
            playbackController.play()
        }
    }

    fun switchQuality(quality: Int) {
        playbackController.switchVideoQuality(quality)
    }

    fun switchAudio(audioId: Int) {
        playbackController.switchVideoAudio(audioId)
    }

    fun switchCdn(cdnIndex: Int) {
        playbackController.switchVideoCdn(cdnIndex)
    }

    fun seekTo(positionMs: Long) {
        playbackController.seekTo(positionMs)
    }

    fun setSpeed(speed: Float) {
        playbackController.setSpeed(speed)
    }

    fun updateBackgroundPlayback(enabled: Boolean) {
        viewModelScope.launch {
            playerSettings.setBackgroundPlayback(enabled)
        }
    }

    fun updateInAppMiniPlayer(enabled: Boolean) {
        viewModelScope.launch {
            playerSettings.setInAppMiniPlayer(enabled)
        }
    }

    fun updateReportPlayback(enabled: Boolean) {
        viewModelScope.launch {
            playerSettings.setReportPlayback(enabled)
        }
    }

    fun updateBufferProfile(profile: PlayerBufferProfile) {
        viewModelScope.launch {
            playerSettings.setBufferProfile(profile)
        }
    }

    fun updatePreferSoftwareDecode(enabled: Boolean) {
        viewModelScope.launch {
            playerSettings.setPreferSoftwareDecode(enabled)
        }
    }

    fun updateDecoderFallback(enabled: Boolean) {
        viewModelScope.launch {
            playerSettings.setDecoderFallback(enabled)
        }
    }

    fun updateAutoRotateFullscreen(enabled: Boolean) {
        viewModelScope.launch {
            playerSettings.setAutoRotateFullscreen(enabled)
        }
    }

    fun updateGestureSpeed(speed: Float) {
        viewModelScope.launch {
            playerSettings.setGestureSpeed(speed)
        }
    }

    fun updateDanmaku(config: DanmakuConfig) {
        viewModelScope.launch {
            playerSettings.setDanmaku(config)
        }
    }

    fun switchPage(cid: Long) {
        val pageTarget = currentPageTarget.value as? VideoTarget.Ugc ?: return
        if (pageTarget.cid == cid) return
        val nextTarget = pageTarget.copy(cid = cid)
        _targetStack.value = _targetStack.value.dropLast(1) + nextTarget
        playbackController.openVideo(nextTarget)
    }

    fun currentDownloadRequest(
        kind: VideoDownloadKind,
        videoQuality: Int,
        audioQuality: Int
    ): VideoDownloadRequest? {
        val pageTarget = currentPageTarget.value ?: return null
        val id = activeVideoId.value
        val meta = buildDownloadMeta()
        return when (pageTarget) {
            is VideoTarget.Ugc -> {
                val aid = id?.aid?.takeIf { it > 0L } ?: pageTarget.aid
                val cid = id?.cid?.takeIf { it > 0L } ?: pageTarget.cid
                if (aid <= 0L || cid <= 0L) return null
                VideoDownloadRequest(
                    biz = PlayBiz.UGC,
                    aid = aid,
                    cid = cid,
                    bvid = id?.bvid?.takeIf(String::isNotBlank) ?: pageTarget.bvid,
                    kind = kind,
                    videoQuality = videoQuality,
                    audioQuality = audioQuality,
                    meta = meta
                )
            }

            is VideoTarget.Pgc -> VideoDownloadRequest(
                biz = PlayBiz.PGC,
                epId = pageTarget.epId,
                seasonId = pageTarget.seasonId ?: 0L,
                kind = kind,
                videoQuality = videoQuality,
                audioQuality = audioQuality,
                meta = meta
            )

            is VideoTarget.Pugv -> VideoDownloadRequest(
                biz = PlayBiz.PUGV,
                epId = pageTarget.epId,
                seasonId = pageTarget.seasonId ?: 0L,
                kind = kind,
                videoQuality = videoQuality,
                audioQuality = audioQuality,
                meta = meta
            )
        }
    }

    private fun bindPgcDetail() {
        viewModelScope.launch {
            combine(currentPageTarget, currentTarget, activeVideoId) { pageTarget, activeTarget, videoId ->
                when {
                    pageTarget !is VideoTarget.Pgc && pageTarget !is VideoTarget.Pugv -> null
                    pageSessionTarget(pageTarget, activeTarget) == null -> null
                    else -> {
                        val aid = videoId?.aid?.takeIf { it > 0L } ?: return@combine null
                        Triple(aid, videoId.bvid, pageTarget.src)
                    }
                }
            }
                .distinctUntilChanged()
                .collect { params ->
                    params ?: return@collect
                    fetchDetail(params.first, params.second, params.third)
                }
        }
        viewModelScope.launch {
            combine(
                currentPageTarget,
                currentTarget,
                videoState.map { it.error }.distinctUntilChanged()
            ) { pageTarget, activeTarget, error ->
                val hasActivePgc = (pageTarget is VideoTarget.Pgc || pageTarget is VideoTarget.Pugv) &&
                    pageSessionTarget(pageTarget, activeTarget) != null
                hasActivePgc to error
            }.collect { (hasActivePgc, error) ->
                if (hasActivePgc && error != null && _detail.value == null) {
                    _detailError.value = error.toUiMsg()
                    _detailLoading.value = false
                }
            }
        }
    }

    private fun loadTargetDetail(target: VideoTarget) {
        when (target) {
            is VideoTarget.Ugc -> {
                _detail.value = null
                _detailError.value = null
                _detailLoading.value = true
                viewModelScope.launch {
                    fetchDetail(target.aid, target.bvid, target.src)
                }
            }

            is VideoTarget.Pgc, is VideoTarget.Pugv -> {
                _detail.value = null
                _detailError.value = null
                _detailLoading.value = true
            }
        }
    }

    private fun buildDownloadMeta(): VideoDownloadMeta {
        val detail = _detail.value
        val pageTarget = currentPageTarget.value
        val cid = activeVideoId.value?.cid
            ?: (pageSessionTarget(pageTarget) as? VideoTarget.Ugc)?.cid
            ?: (pageTarget as? VideoTarget.Ugc)?.cid
        val part = cid?.let { targetCid -> detail?.pages?.firstOrNull { it.cid == targetCid } }
        val title = detail?.let {
            listOfNotNull(
                it.title.takeIf(String::isNotBlank),
                part?.part?.takeIf(String::isNotBlank)
            ).joinToString(" - ").takeIf(String::isNotBlank)
        }
        return VideoDownloadMeta(
            title = title,
            cover = detail?.cover,
            ownerUid = detail?.owner?.mid?.takeIf { it > 0L },
            ownerName = detail?.owner?.name?.takeIf(String::isNotBlank)
        )
    }

    private suspend fun fetchDetail(
        aid: Long,
        bvid: String?,
        src: VideoSrc
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

    private fun pageSessionTarget(
        pageTarget: VideoTarget? = currentPageTarget.value,
        activeTarget: VideoTarget? = currentTarget.value
    ): VideoTarget? {
        val page = pageTarget ?: return null
        val active = activeTarget ?: return null
        return active.takeIf { it.isSameEntry(page) }
    }

    private fun hasActivePageSession(pageTarget: VideoTarget): Boolean {
        if (pageSessionTarget(pageTarget) == null) return false
        return videoState.value.error == null &&
            (videoState.value.playbackSource != null || videoState.value.isPreparing)
    }
}

private fun PlaybackError.toUiMsg(): String {
    return when (this) {
        is PlaybackError.RequestFailed -> message
        is PlaybackError.NoPlayableStream -> message
    }
}
