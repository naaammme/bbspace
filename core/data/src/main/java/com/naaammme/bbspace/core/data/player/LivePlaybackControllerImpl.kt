package com.naaammme.bbspace.core.data.player

import androidx.media3.common.Player
import com.naaammme.bbspace.core.common.log.Logger
import com.naaammme.bbspace.core.data.AppSettings
import com.naaammme.bbspace.core.domain.live.LivePlaybackController
import com.naaammme.bbspace.core.domain.live.LiveRepository
import com.naaammme.bbspace.core.model.LivePlaybackError
import com.naaammme.bbspace.core.model.LivePlaybackViewState
import com.naaammme.bbspace.core.model.PlaybackState
import com.naaammme.bbspace.infra.player.DecoderMode
import com.naaammme.bbspace.infra.player.EnginePlaybackState
import com.naaammme.bbspace.infra.player.EngineSource
import com.naaammme.bbspace.infra.player.PlayerConfig
import com.naaammme.bbspace.infra.player.PlayerEngine
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class LivePlaybackControllerImpl @Inject constructor(
    private val repository: LiveRepository,
    private val appSettings: AppSettings,
    private val playerEngine: PlayerEngine
) : LivePlaybackController {

    override val player: StateFlow<Player?> = playerEngine.player

    private val _state = MutableStateFlow(LivePlaybackViewState())
    override val state: StateFlow<LivePlaybackViewState> = _state.asStateFlow()

    private val runtimeScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val prepMu = Mutex()
    private val openId = AtomicLong(0L)
    private var currentRoomId: Long? = null

    init {
        runtimeScope.launch {
            playerEngine.snapshot.collect { snapshot ->
                _state.value = _state.value.copy(
                    isPlaying = snapshot.isPlaying,
                    playWhenReady = snapshot.playWhenReady,
                    playbackState = snapshot.playbackState.toModelState(),
                    videoWidth = snapshot.videoWidth,
                    videoHeight = snapshot.videoHeight,
                    hasRenderedFirstFrame = snapshot.firstFrameSeq > 0L,
                    playerError = snapshot.errorMessage
                )
            }
        }
    }

    override suspend fun open(
        roomId: Long,
        preferredQuality: Int
    ) {
        load(
            roomId = roomId,
            qn = preferredQuality,
            playWhenReady = true
        )
    }

    override fun play() {
        playerEngine.play()
    }

    override fun pause() {
        playerEngine.pause()
    }

    override fun switchQuality(quality: Int) {
        val roomId = currentRoomId ?: return
        if (_state.value.playbackSource?.currentQn == quality) return
        val playWhenReady = playerEngine.snapshot.value.playWhenReady
        runtimeScope.launch {
            load(
                roomId = roomId,
                qn = quality,
                playWhenReady = playWhenReady
            )
        }
    }

    override fun release() {
        openId.incrementAndGet()
        currentRoomId = null
        playerEngine.release()
        _state.value = LivePlaybackViewState()
    }

    private suspend fun load(
        roomId: Long,
        qn: Int,
        playWhenReady: Boolean
    ) {
        val token = openId.incrementAndGet()
        currentRoomId = roomId
        _state.value = _state.value.copy(
            isPreparing = true,
            error = null
        )
        try {
            prepareEngine()
            val source = repository.fetchPlaybackSource(roomId, qn)
            if (openId.get() != token) return
            playerEngine.setSource(
                source = EngineSource.LiveFlv(source.primaryUrl),
                playWhenReady = playWhenReady
            )
            if (openId.get() != token) return
            _state.value = _state.value.copy(
                isPreparing = false,
                playbackSource = source,
                error = null
            )
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            if (openId.get() != token) return
            Logger.e(TAG, t) {
                "load live source failed roomId=$roomId qn=$qn msg=${t.message}"
            }
            _state.value = _state.value.copy(
                isPreparing = false,
                error = t.toLiveError(),
                playbackSource = _state.value.playbackSource.takeIf { it?.roomId == roomId }
            )
        }
    }

    private suspend fun prepareEngine() {
        prepMu.withLock {
            val config = withContext(Dispatchers.IO) {
                PlayerConfig(
                    minBufferMs = appSettings.playerMinBufferMs.first(),
                    maxBufferMs = appSettings.playerMaxBufferMs.first(),
                    playBufferMs = appSettings.playerPlaybackBufferMs.first(),
                    rebufferMs = appSettings.playerRebufferMs.first(),
                    backBufferMs = appSettings.playerBackBufferMs.first(),
                    decoderMode = if (appSettings.preferSoftwareDecode.first()) {
                        DecoderMode.Soft
                    } else {
                        DecoderMode.Hard
                    },
                    decoderFallback = appSettings.decoderFallback.first()
                )
            }
            withContext(Dispatchers.Main.immediate) {
                playerEngine.updateConfig(config)
            }
        }
    }

    private fun Throwable.toLiveError(): LivePlaybackError {
        val msg = message ?: "直播取流失败"
        return when (this) {
            is IllegalStateException -> LivePlaybackError.NoPlayableStream(msg)
            else -> LivePlaybackError.RequestFailed(msg, this)
        }
    }

    private fun EnginePlaybackState.toModelState(): PlaybackState {
        return when (this) {
            EnginePlaybackState.Buffering -> PlaybackState.Buffering
            EnginePlaybackState.Ready -> PlaybackState.Ready
            EnginePlaybackState.Ended -> PlaybackState.Ended
            EnginePlaybackState.Idle -> PlaybackState.Idle
        }
    }

    private companion object {
        const val TAG = "LivePlayback"
    }
}
