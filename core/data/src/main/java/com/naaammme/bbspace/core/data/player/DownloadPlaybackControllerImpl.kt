package com.naaammme.bbspace.core.data.player

import android.net.Uri
import androidx.media3.common.Player
import com.naaammme.bbspace.core.domain.download.VideoDownloadRepository
import com.naaammme.bbspace.core.domain.player.DownloadPlaybackController
import com.naaammme.bbspace.core.model.DownloadPlaybackState
import com.naaammme.bbspace.core.model.DownloadPlaybackStatus
import com.naaammme.bbspace.core.model.VideoDownloadTask
import com.naaammme.bbspace.core.model.summaryLabel
import com.naaammme.bbspace.infra.player.EnginePlaybackState
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
            combine(session, playerEngine.snapshot) { session, snapshot ->
                session to snapshot
            }.collect { (session, snapshot) ->
                val task = session.task
                _state.value = DownloadPlaybackState(
                    taskId = task?.id,
                    title = task?.title.orEmpty(),
                    subtitle = task?.summaryLabel(),
                    isPreparing = session.isPreparing,
                    isPlaying = snapshot.isPlaying,
                    playWhenReady = snapshot.playWhenReady,
                    playbackStatus = snapshot.playbackState.toModel(),
                    positionMs = snapshot.positionMs,
                    durationMs = if (snapshot.durationMs > 0L) {
                        snapshot.durationMs
                    } else {
                        task?.durationMs ?: 0L
                    },
                    speed = snapshot.speed,
                    seekEventId = snapshot.discontinuitySeq,
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
                playWhenReady = true
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
        val taskTitle = title
        val subtitle = summaryLabel()
        val videoUri = videoPath?.let { Uri.fromFile(java.io.File(it)).toString() }
        val audioUri = audioPath?.let { Uri.fromFile(java.io.File(it)).toString() }
        return when {
            !videoUri.isNullOrBlank() -> EngineSource.Dash(
                videoUrl = videoUri,
                audioUrl = audioUri,
                title = taskTitle,
                subtitle = subtitle
            )

            !audioUri.isNullOrBlank() -> EngineSource.Progressive(
                segments = listOf(EngineSource.ProgressiveSegment(audioUri, durationMs)),
                title = taskTitle,
                subtitle = subtitle
            )

            else -> error("缓存文件不存在")
        }
    }

    private fun EnginePlaybackState.toModel(): DownloadPlaybackStatus {
        return when (this) {
            EnginePlaybackState.Buffering -> DownloadPlaybackStatus.BUFFERING
            EnginePlaybackState.Ready -> DownloadPlaybackStatus.READY
            EnginePlaybackState.Ended -> DownloadPlaybackStatus.ENDED
            EnginePlaybackState.Idle -> DownloadPlaybackStatus.IDLE
        }
    }
}

private data class DownloadPlaybackSession(
    val task: VideoDownloadTask? = null,
    val isPreparing: Boolean = false,
    val error: String? = null
)
