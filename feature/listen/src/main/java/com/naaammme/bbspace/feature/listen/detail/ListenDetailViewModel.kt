package com.naaammme.bbspace.feature.listen.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaMetadata
import com.naaammme.bbspace.core.common.log.Logger
import com.naaammme.bbspace.core.listen.ListenRepository
import com.naaammme.bbspace.core.model.PlaybackState
import com.naaammme.bbspace.infra.player.EngineSource
import com.naaammme.bbspace.infra.player.PlayerEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ListenDetailViewModel @Inject constructor(
    private val listenRepo: ListenRepository,
    private val playerEngine: PlayerEngine
) : ViewModel() {

    companion object {
        private const val TAG = "ListenDetailVM"
    }

    private val _uiState = MutableStateFlow(ListenDetailUiState())
    val uiState = _uiState.asStateFlow()

    private var currentOid = -1L
    private var currentItemType = -1
    private var currentSubId = -1L

    init {
        viewModelScope.launch {
            combine(
                playerEngine.currentSource,
                playerEngine.playbackState,
                playerEngine.playbackProgress
            ) { source, state, progress ->
                Triple(source, state, progress)
            }.distinctUntilChanged { old, new ->
                old.first === new.first && old.second == new.second && old.third == new.third
            }.collect { (source, state, progress) ->
                val audioUrl = _uiState.value.audioUrl
                val isListenSource = audioUrl != null &&
                    (source as? EngineSource.Progressive)?.segments?.any { it.url == audioUrl } == true
                if (!isListenSource) return@collect
                _uiState.update {
                    it.copy(
                        isPreparing = state.playbackState == PlaybackState.Buffering,
                        positionMs = progress.positionMs,
                        durationMs = if (progress.durationMs > 0L) progress.durationMs else it.durationMs,
                        isPlaying = state.isPlaying,
                        errorMessage = state.errorMessage ?: it.errorMessage
                    )
                }
            }
        }
    }

    fun load(oid: Long, itemType: Int, subId: Long, title: String, author: String) {
        if (oid <= 0L || itemType <= 0 || subId <= 0L) {
            _uiState.update { it.copy(isLoading = false, errorMessage = "听视频参数无效") }
            return
        }
        if (currentOid == oid && currentItemType == itemType && currentSubId == subId && _uiState.value.audioUrl != null) {
            return
        }
        currentOid = oid
        currentItemType = itemType
        currentSubId = subId
        viewModelScope.launch {
            try {
                _uiState.value = ListenDetailUiState(isLoading = true)
                val info = listenRepo.fetchPlayUrl(oid, itemType, subId)
                val audioUrl = info.audioUrl
                if (audioUrl.isNullOrEmpty()) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isPreparing = false,
                            errorMessage = "当前内容不可播放"
                        )
                    }
                    return@launch
                }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isPreparing = true,
                        audioUrl = audioUrl,
                        durationMs = info.durationMs,
                        positionMs = 0L,
                        errorMessage = null
                    )
                }
                playerEngine.setSource(
                    source = EngineSource.Progressive(
                        segments = listOf(EngineSource.ProgressiveSegment(audioUrl, info.durationMs))
                    ),
                    startPositionMs = 0L,
                    playWhenReady = true,
                    metadata = MediaMetadata.Builder()
                        .setTitle(title.takeIf(String::isNotBlank))
                        .setArtist(author.takeIf(String::isNotBlank))
                        .build()
                )
            } catch (e: Exception) {
                Logger.e(TAG, e) { "获取播放地址失败" }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isPreparing = false,
                        errorMessage = e.message ?: "加载失败"
                    )
                }
            }
        }
    }

    fun togglePlayPause() {
        if (_uiState.value.isPlaying) {
            playerEngine.pause()
        } else {
            playerEngine.play()
        }
    }

    fun seekTo(positionMs: Long) {
        playerEngine.seekTo(positionMs)
    }

    override fun onCleared() {
        val audioUrl = _uiState.value.audioUrl
        val isListenSource = audioUrl != null &&
            (playerEngine.currentSource.value as? EngineSource.Progressive)
                ?.segments
                ?.any { it.url == audioUrl } == true
        if (isListenSource) {
            playerEngine.stopForReuse(resetPosition = true)
        }
        super.onCleared()
    }
}

data class ListenDetailUiState(
    val isLoading: Boolean = false,
    val isPreparing: Boolean = false,
    val audioUrl: String? = null,
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val isPlaying: Boolean = false,
    val errorMessage: String? = null
)
