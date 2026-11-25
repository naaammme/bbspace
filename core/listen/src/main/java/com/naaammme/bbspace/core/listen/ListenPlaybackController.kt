package com.naaammme.bbspace.core.listen

import androidx.media3.common.MediaMetadata
import com.naaammme.bbspace.core.common.log.Logger
import com.naaammme.bbspace.core.model.PlaybackState
import com.naaammme.bbspace.core.model.listen.ListenPlaybackState
import com.naaammme.bbspace.infra.player.EngineSource
import com.naaammme.bbspace.infra.player.PlayerEngine
import dagger.hilt.android.scopes.ViewModelScoped
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@ViewModelScoped
class ListenPlaybackController @Inject constructor(
    private val listenRepository: ListenRepository,
    private val playerEngine: PlayerEngine
) {
    private companion object {
        const val TAG = "ListenPlayback"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(ListenPlaybackState())
    val state = _state.asStateFlow()
    private var currentKey: Triple<Long, Int, Long>? = null

    init {
        scope.launch {
            combine(
                playerEngine.currentSource,
                playerEngine.playbackState,
                playerEngine.playbackProgress
            ) { source, playback, progress ->
                Triple(source, playback, progress)
            }.distinctUntilChanged { old, new ->
                old.first === new.first && old.second == new.second && old.third == new.third
            }.collect { (source, playback, progress) ->
                if (!isCurrentSource(source)) return@collect
                _state.update {
                    it.copy(
                        isPreparing = playback.playbackState == PlaybackState.Buffering,
                        positionMs = progress.positionMs,
                        durationMs = progress.durationMs.takeIf { duration -> duration > 0L }
                            ?: it.durationMs,
                        isPlaying = playback.isPlaying,
                        errorMessage = playback.errorMessage ?: it.errorMessage
                    )
                }
            }
        }
    }

    suspend fun open(oid: Long, itemType: Int, subId: Long, title: String, author: String) {
        val key = Triple(oid, itemType, subId)
        if (oid <= 0L || itemType <= 0 || subId <= 0L) {
            currentKey = null
            _state.value = ListenPlaybackState(errorMessage = "听视频参数无效")
            return
        }
        if (currentKey == key && _state.value.audioUrl != null) return

        currentKey = key
        _state.value = ListenPlaybackState(isLoading = true)
        try {
            val info = listenRepository.fetchPlayUrl(oid, itemType, subId)
            val audioUrl = info.audioUrl
            if (audioUrl.isNullOrBlank()) {
                _state.value = ListenPlaybackState(errorMessage = "当前内容不可播放")
                return
            }
            _state.value = ListenPlaybackState(
                isPreparing = true,
                audioUrl = audioUrl,
                durationMs = info.durationMs
            )
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
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Logger.e(TAG, error) { "获取播放地址失败" }
            _state.update {
                it.copy(
                    isLoading = false,
                    isPreparing = false,
                    errorMessage = error.message ?: "加载失败"
                )
            }
        }
    }

    fun togglePlayPause() {
        if (playerEngine.playbackState.value.isPlaying) playerEngine.pause() else playerEngine.play()
    }

    fun seekTo(positionMs: Long) = playerEngine.seekTo(positionMs)

    fun release() {
        scope.cancel()
        if (isCurrentSource(playerEngine.currentSource.value)) {
            playerEngine.stopForReuse(resetPosition = true)
        }
        currentKey = null
        _state.value = ListenPlaybackState()
    }

    private fun isCurrentSource(source: EngineSource?): Boolean {
        val audioUrl = _state.value.audioUrl ?: return false
        return (source as? EngineSource.Progressive)?.segments?.any { it.url == audioUrl } == true
    }
}
