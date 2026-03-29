package com.naaammme.bbspace.core.data.player

import com.naaammme.bbspace.core.data.AppSettings
import com.naaammme.bbspace.core.domain.player.VideoPlayerRepository
import com.naaammme.bbspace.core.model.PlaybackAudio
import com.naaammme.bbspace.core.model.PlaybackError
import com.naaammme.bbspace.core.model.PlaybackRequest
import com.naaammme.bbspace.core.model.PlaybackStream
import com.naaammme.bbspace.core.model.PlayerSessionState
import com.naaammme.bbspace.infra.player.EngineSource
import com.naaammme.bbspace.infra.player.PlayerEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayerSessionManager @Inject constructor(
    private val repository: VideoPlayerRepository,
    private val appSettings: AppSettings,
    val playerEngine: PlayerEngine
) {
    private val _state = MutableStateFlow(PlayerSessionState())
    val state: StateFlow<PlayerSessionState> = _state.asStateFlow()

    suspend fun start(request: PlaybackRequest) {
        _state.value = _state.value.copy(isPreparing = true, error = null, currentRequest = request)
        try {
            val source = repository.fetchPlaybackSource(request)
            val preferredQuality = request.preferredQuality ?: appSettings.defaultVideoQuality.first()
            val preferredAudioId = appSettings.defaultAudioQuality.first()

            val stream = source.streams.firstOrNull { it.quality == preferredQuality }
                ?: source.streams.firstOrNull()
            val audio = selectAudio(stream, source.audios, preferredAudioId)

            playerEngine.setSource(
                buildEngineSource(stream, audio) ?: throw IllegalStateException("No playable stream"),
                request.seekToMs ?: source.resumePositionMs
            )
            _state.value = PlayerSessionState(
                currentRequest = request,
                playbackSource = source,
                currentStream = stream,
                currentAudio = audio,
                isPreparing = false
            )
        } catch (t: Throwable) {
            _state.value = _state.value.copy(
                isPreparing = false,
                error = PlaybackError.RequestFailed(t.message ?: "Failed to load playback source", t)
            )
        }
    }

    fun play() = playerEngine.play()
    fun pause() = playerEngine.pause()
    fun seekTo(positionMs: Long) = playerEngine.seekTo(positionMs)

    fun switchQuality(quality: Int) {
        val s = _state.value
        val source = s.playbackSource ?: return
        val stream = source.streams.firstOrNull { it.quality == quality } ?: return
        val audio = selectAudio(stream, source.audios, s.currentAudio?.id ?: 0)
        playerEngine.setSource(buildEngineSource(stream, audio) ?: return, playerEngine.snapshot.value.positionMs)
        _state.value = s.copy(currentStream = stream, currentAudio = audio)
    }

    fun switchAudio(audioId: Int) {
        val s = _state.value
        val source = s.playbackSource ?: return
        val audio = source.audios.firstOrNull { it.id == audioId } ?: return
        playerEngine.setSource(buildEngineSource(s.currentStream, audio) ?: return, playerEngine.snapshot.value.positionMs)
        _state.value = s.copy(currentAudio = audio)
    }

    fun closeCurrentSession() {
        playerEngine.stopForReuse(resetPosition = true)
        _state.value = PlayerSessionState()
    }

    private fun selectAudio(
        stream: PlaybackStream?,
        audios: List<PlaybackAudio>,
        preferredId: Int
    ): PlaybackAudio? {
        if (audios.isEmpty()) return null
        val linkedId = (stream as? PlaybackStream.Dash)?.audioId
        return audios.firstOrNull { it.id == linkedId }
            ?: audios.firstOrNull { it.id == preferredId && preferredId > 0 }
            ?: audios.firstOrNull()
    }

    private fun buildEngineSource(stream: PlaybackStream?, audio: PlaybackAudio?): EngineSource? {
        return when (stream) {
            is PlaybackStream.Dash -> EngineSource.Dash(stream.videoUrl, audio?.url)
            is PlaybackStream.Progressive -> EngineSource.Progressive(
                stream.segments.map { EngineSource.ProgressiveSegment(it.url, it.durationMs) }
            )
            null -> null
        }
    }
}
