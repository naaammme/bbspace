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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class VideoViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    appSettings: AppSettings,
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
            snapshot = snapshot,
            error = session.error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = VideoPlayerState()
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
            snapshotFlow = sessionManager.playerEngine.snapshot
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

    fun pause() {
        sessionManager.pause(ownerId)
    }

    fun seekTo(positionMs: Long) {
        sessionManager.seekTo(ownerId, positionMs)
    }

    fun setSpeed(speed: Float) {
        sessionManager.setSpeed(ownerId, speed)
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
