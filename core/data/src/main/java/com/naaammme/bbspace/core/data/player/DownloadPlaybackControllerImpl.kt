package com.naaammme.bbspace.core.data.player

import android.net.Uri
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.naaammme.bbspace.core.domain.download.VideoDownloadRepository
import com.naaammme.bbspace.core.domain.player.DownloadPlaybackController
import com.naaammme.bbspace.core.model.DownloadPlaybackState
import com.naaammme.bbspace.core.model.DownloadPlaybackStatus
import com.naaammme.bbspace.core.model.PlaybackState
import com.naaammme.bbspace.core.model.VideoDownloadTask
import com.naaammme.bbspace.core.model.summaryLabel
import com.naaammme.bbspace.infra.player.EngineSource
import com.naaammme.bbspace.infra.player.PlayerEngine
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Singleton
class DownloadPlaybackControllerImpl @Inject constructor(
    private val downloadRepository: VideoDownloadRepository,
    private val playerEngine: PlayerEngine
) : DownloadPlaybackController {

    override val player: StateFlow<Player?> = playerEngine.player

    private val runtimeScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val session = MutableStateFlow(DownloadPlaybackSession())
    private val _state = MutableStateFlow(DownloadPlaybackState())
    override val state: StateFlow<DownloadPlaybackState> = _state.asStateFlow()

    init {
        runtimeScope.launch {
            combine(session, playerEngine.playbackState, playerEngine.playbackProgress) { session, state, progress ->
                Triple(session, state, progress)
            }.distinctUntilChanged { old, new ->
                old.first === new.first && old.second == new.second && old.third == new.third
            }.collect { (session, state, progress) ->
                val task = session.task
                _state.value = DownloadPlaybackState(
                    taskId = task?.id,
                    title = task?.title.orEmpty(),
                    subtitle = task?.summaryLabel(),
                    isPreparing = session.isPreparing,
                    isPlaying = state.isPlaying,
                    playWhenReady = state.playWhenReady,
                    playbackStatus = state.playbackState.toModel(),
                    positionMs = progress.positionMs,
                    durationMs = if (progress.durationMs > 0L) {
                        progress.durationMs
                    } else {
                        task?.durationMs ?: 0L
                    },
                    speed = state.speed,
                    seekEventId = state.seekEventSeq,
                    error = session.error
                )
            }
        }
    }

    override suspend fun open(taskId: Long) {
        if (taskId <= 0L) {
            session.value = DownloadPlaybackSession(error = "缓存任务无效")
            return
        }
        val task = downloadRepository.getTask(taskId)
        if (task == null) {
            session.value = DownloadPlaybackSession(error = "缓存不存在")
            return
        }
        if (!task.isPlayable) {
            session.value = DownloadPlaybackSession(task = task, error = "缓存未完成")
            return
        }
        session.value = DownloadPlaybackSession(task = task, isPreparing = true)
        try {
            playerEngine.setSource(
                source = task.toEngineSource(),
                startPositionMs = 0L,
                playWhenReady = true,
                metadata = MediaMetadata.Builder()
                    .setTitle(task.title.takeIf(String::isNotBlank))
                    .setArtist(task.summaryLabel().takeIf(String::isNotBlank))
                    .build()
            )
            session.value = DownloadPlaybackSession(task = task)
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            session.value = DownloadPlaybackSession(
                task = task,
                error = t.message ?: "打开缓存失败"
            )
        }
    }

    override fun play() {
        playerEngine.play()
    }

    override fun pause() {
        playerEngine.pause()
    }

    override fun seekTo(positionMs: Long) {
        playerEngine.seekTo(positionMs)
    }

    override fun setSpeed(speed: Float) {
        playerEngine.setSpeed(speed)
    }

    override fun release() {
        session.value = DownloadPlaybackSession()
        playerEngine.release()
    }

    private fun VideoDownloadTask.toEngineSource(): EngineSource {
        val videoUri = videoPath?.let { Uri.fromFile(java.io.File(it)).toString() }
        val audioUri = audioPath?.let { Uri.fromFile(java.io.File(it)).toString() }
        return when {
            !videoUri.isNullOrBlank() -> EngineSource.Dash(
                videoUrl = videoUri,
                audioUrl = audioUri
            )
            !audioUri.isNullOrBlank() -> EngineSource.Progressive(
                segments = listOf(EngineSource.ProgressiveSegment(audioUri, durationMs))
            )
            else -> error("缓存文件不存在")
        }
    }

    private fun PlaybackState.toModel(): DownloadPlaybackStatus {
        return when (this) {
            PlaybackState.Buffering -> DownloadPlaybackStatus.BUFFERING
            PlaybackState.Ready -> DownloadPlaybackStatus.READY
            PlaybackState.Ended -> DownloadPlaybackStatus.ENDED
            PlaybackState.Idle -> DownloadPlaybackStatus.IDLE
        }
    }
}

private data class DownloadPlaybackSession(
    val task: VideoDownloadTask? = null,
    val isPreparing: Boolean = false,
    val error: String? = null
)
