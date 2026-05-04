package com.naaammme.bbspace.feature.video

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.naaammme.bbspace.core.domain.player.PlayerSettings
import com.naaammme.bbspace.core.domain.player.StreamPlaybackSession
import com.naaammme.bbspace.core.domain.video.VideoDetailRepository
import com.naaammme.bbspace.core.model.CommentSubject
import com.naaammme.bbspace.core.model.CommentSubjectTool
import com.naaammme.bbspace.core.model.StreamPlaybackTarget
import com.naaammme.bbspace.core.model.DanmakuConfig
import com.naaammme.bbspace.core.model.PlayBiz
import com.naaammme.bbspace.core.model.PlaybackError
import com.naaammme.bbspace.core.model.PlaybackHistoryMeta
import com.naaammme.bbspace.core.model.PlaybackViewState
import com.naaammme.bbspace.core.model.PlayerBufferSettings
import com.naaammme.bbspace.core.model.PlayerPlaybackPrefs
import com.naaammme.bbspace.core.model.VideoDetail
import com.naaammme.bbspace.core.model.VideoDownloadKind
import com.naaammme.bbspace.core.model.VideoDownloadMeta
import com.naaammme.bbspace.core.model.VideoDownloadRequest
import com.naaammme.bbspace.core.model.VideoSrc
import com.naaammme.bbspace.core.model.VideoTarget
import com.naaammme.bbspace.core.model.isSameEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class VideoViewModel @Inject constructor(
    private val streamPlaybackSession: StreamPlaybackSession,
    private val playerSettings: PlayerSettings,
    private val detailRepo: VideoDetailRepository
) : ViewModel() {

    private val _targetStack = MutableStateFlow<List<VideoTarget>>(emptyList())
    private val _detail = MutableStateFlow<VideoDetail?>(null)
    private val _detailLoading = MutableStateFlow(false)
    private val _detailError = MutableStateFlow<String?>(null)

    val player: StateFlow<Player?> = streamPlaybackSession.player
    val playerState: StateFlow<PlaybackViewState> = streamPlaybackSession.videoState
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

    val pageState = combine(
        combine(
            playerState,
            currentTarget,
            currentPageTarget,
            _detail,
            _detailLoading
        ) { playbackState, activeTarget, pageTarget, detail, detailLoading ->
            PageStateSnapshot(
                playbackState = playbackState,
                activeTarget = activeTarget,
                pageTarget = pageTarget,
                detail = detail,
                detailLoading = detailLoading
            )
        },
        _detailError
    ) { snap, detailError ->
        val activeCid = (pageSessionTarget(snap.pageTarget, snap.activeTarget) as? VideoTarget.Ugc)?.cid
        val curCid = snap.playbackState.playbackSource?.videoId?.cid
            ?: activeCid
            ?: (snap.pageTarget as? VideoTarget.Ugc)?.cid
        VideoPageState(
            detail = snap.detail,
            detailLoading = snap.detailLoading,
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
            val aid = playerState.value.playbackSource?.videoId?.aid?.takeIf { it > 0L }
                ?: (pageTarget as? VideoTarget.Ugc)?.aid
                ?: return null
            return CommentSubjectTool.video(aid, src)
        }

    internal val danmakuState = streamPlaybackSession.danmakuState

    init {
        bindPlaybackMeta()
        bindPgcDetail()
        syncWithPlaybackTarget()
    }

    fun openRoot(target: VideoTarget) {
        _targetStack.value = listOf(target)
        loadTargetDetail(target)
        streamPlaybackSession.openVideo(target)
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
        streamPlaybackSession.openVideo(target)
    }

    fun syncToPlayback(target: VideoTarget) {
        val current = currentPageTarget.value
        if (current == null) {
            _targetStack.value = listOf(target)
            loadTargetDetail(target)
            return
        }
        if (!current.isSameEntry(target) || current != target) {
            _targetStack.value = _targetStack.value.dropLast(1) + target
            loadTargetDetail(target)
        }
    }

    fun popPage(): Boolean {
        val stack = _targetStack.value
        if (stack.size <= 1) return false
        val nextStack = stack.dropLast(1)
        val nextTarget = nextStack.last()
        _targetStack.value = nextStack
        loadTargetDetail(nextTarget)
        streamPlaybackSession.openVideo(nextTarget)
        return true
    }

    private fun clearContent() {
        _targetStack.value = emptyList()
        _detail.value = null
        _detailLoading.value = false
        _detailError.value = null
    }

    fun ensureStarted() {
        val pageTarget = currentPageTarget.value ?: return
        if (hasActivePageSession(pageTarget)) return
        streamPlaybackSession.openVideo(pageTarget)
    }

    fun togglePlayPause() {
        if (playerState.value.isPlaying) {
            streamPlaybackSession.pause()
        } else {
            streamPlaybackSession.play()
        }
    }

    fun switchQuality(quality: Int) {
        streamPlaybackSession.switchVideoQuality(quality)
    }

    fun switchAudio(audioId: Int) {
        streamPlaybackSession.switchVideoAudio(audioId)
    }

    fun switchCdn(cdnIndex: Int) {
        streamPlaybackSession.switchVideoCdn(cdnIndex)
    }

    fun seekTo(positionMs: Long) {
        streamPlaybackSession.seekTo(positionMs)
    }

    fun setSpeed(speed: Float) {
        streamPlaybackSession.setSpeed(speed)
    }

    fun updateBackgroundPlayback(enabled: Boolean) {
        updatePlayback { copy(backgroundPlayback = enabled) }
    }

    fun updateInAppMiniPlayer(enabled: Boolean) {
        updatePlayback { copy(inAppMiniPlayer = enabled) }
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

    fun updateAutoRotateFullscreen(enabled: Boolean) {
        updatePlayback { copy(autoRotateFullscreen = enabled) }
    }

    fun updateDanmaku(config: DanmakuConfig) {
        viewModelScope.launch {
            playerSettings.updateDanmaku(config)
        }
    }

    fun switchPage(cid: Long) {
        val pageTarget = currentPageTarget.value as? VideoTarget.Ugc ?: return
        val activeTarget = pageSessionTarget(pageTarget) as? VideoTarget.Ugc
        if (activeTarget != null) {
            if (activeTarget.cid == cid) return
            streamPlaybackSession.switchVideoPage(cid)
            return
        }
        if (pageTarget.cid == cid) return
        val nextTarget = pageTarget.copy(cid = cid)
        _targetStack.value = _targetStack.value.dropLast(1) + nextTarget
        streamPlaybackSession.openVideo(nextTarget)
    }

    fun currentDownloadRequest(
        kind: VideoDownloadKind,
        videoQuality: Int,
        audioQuality: Int
    ): VideoDownloadRequest? {
        val pageTarget = currentPageTarget.value ?: return null
        val id = playerState.value.playbackSource?.videoId
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

    private fun syncWithPlaybackTarget() {
        viewModelScope.launch {
            currentTarget.collect { target ->
                val active = target
                if (active == null) return@collect
                val pageTarget = currentPageTarget.value ?: return@collect
                if (!active.isSameEntry(pageTarget)) return@collect
                if (pageTarget != active) {
                    _targetStack.value = _targetStack.value.dropLast(1) + active
                }
            }
        }
    }

    private fun bindPlaybackMeta() {
        viewModelScope.launch {
            combine(_detail, currentPageTarget, currentTarget, playerState) { detail, pageTarget, activeTarget, playbackState ->
                val activeCid = (pageSessionTarget(pageTarget, activeTarget) as? VideoTarget.Ugc)?.cid
                detail.toPlaybackHistoryMeta(
                    playbackState.playbackSource?.videoId?.cid
                        ?: activeCid
                        ?: (pageTarget as? VideoTarget.Ugc)?.cid
                )
            }.collect { meta ->
                if (meta != null) {
                    streamPlaybackSession.updatePlaybackMeta(meta)
                }
            }
        }
    }

    private fun bindPgcDetail() {
        viewModelScope.launch {
            var loadedAid = 0L
            var loadedBvid = ""
            combine(currentPageTarget, currentTarget, playerState) { pageTarget, activeTarget, playbackState ->
                Triple(pageTarget, activeTarget, playbackState)
            }.collect { (pageTarget, activeTarget, playbackState) ->
                if (pageTarget !is VideoTarget.Pgc && pageTarget !is VideoTarget.Pugv) return@collect
                if (pageSessionTarget(pageTarget, activeTarget) == null) return@collect
                val videoId = playbackState.playbackSource?.videoId
                val aid = videoId?.aid?.takeIf { it > 0L }
                if (aid != null) {
                    val bvid = videoId.bvid.orEmpty()
                    if (aid == loadedAid && bvid == loadedBvid) return@collect
                    loadedAid = aid
                    loadedBvid = bvid
                    fetchDetail(aid, videoId.bvid, pageTarget.src)
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

    private fun loadTargetDetail(target: VideoTarget) {
        when (target) {
            is VideoTarget.Ugc -> {
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

    private fun buildDownloadMeta(): VideoDownloadMeta {
        val detail = _detail.value
        val pageTarget = currentPageTarget.value
        val cid = playerState.value.playbackSource?.videoId?.cid
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
        return playerState.value.error == null &&
            (playerState.value.playbackSource != null || playerState.value.isPreparing)
    }
}

private fun PlaybackError.toUiMsg(): String {
    return when (this) {
        is PlaybackError.RequestFailed -> message
        is PlaybackError.NoPlayableStream -> message
    }
}

private data class PageStateSnapshot(
    val playbackState: PlaybackViewState,
    val activeTarget: VideoTarget?,
    val pageTarget: VideoTarget?,
    val detail: VideoDetail?,
    val detailLoading: Boolean
)

private fun VideoDetail?.toPlaybackHistoryMeta(cid: Long?): PlaybackHistoryMeta? {
    this ?: return null
    val idx = cid?.let { target -> pages.indexOfFirst { it.cid == target } } ?: -1
    val part = if (idx >= 0) pages[idx] else null
    return PlaybackHistoryMeta(
        title = title,
        cover = cover,
        ownerUid = owner?.mid?.takeIf { it > 0L },
        ownerName = owner?.name,
        part = if (idx >= 0) idx + 1 else null,
        partTitle = part?.part
    )
}
