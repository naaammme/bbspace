package com.naaammme.bbspace.infra.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ConcatenatingMediaSource2
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.naaammme.bbspace.infra.crypto.DeviceIdentity
import com.naaammme.bbspace.infra.network.UserAgentBuilder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("UnsafeOptInUsageError")
@UnstableApi
@Singleton
class Media3PlayerEngine @Inject constructor(
    @ApplicationContext context: Context,
    deviceIdentity: DeviceIdentity,
    okHttpClient: OkHttpClient
) : PlayerEngine {

    private val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
        .setUserAgent("Bilibili Freedoooooom/MarkII")

    private val mediaSourceFactory = ProgressiveMediaSource.Factory(dataSourceFactory)

    private val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            10_000,
            30_000,
            250,
            750
        )
        .setBackBuffer(10_000, true)
        .build()

    private val trackSelector = DefaultTrackSelector(context)

    private val exoPlayer: ExoPlayer = ExoPlayer.Builder(
        context,
        DefaultRenderersFactory(context).setEnableDecoderFallback(true)
    )
        .setLoadControl(loadControl)
        .setTrackSelector(trackSelector)
        .build()
        .apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true
            )
            setHandleAudioBecomingNoisy(true)
        }

    private val _snapshot = MutableStateFlow(PlaybackSnapshot())
    override val snapshot: StateFlow<PlaybackSnapshot> = _snapshot.asStateFlow()

    override fun getPlayerForView(): Player = exoPlayer

    init {
        exoPlayer.addListener(
            object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    updateSnapshot(playbackState = playbackState)
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    updateSnapshot(isPlaying = isPlaying)
                }

                override fun onPlayerError(error: PlaybackException) {
                    _snapshot.value = _snapshot.value.copy(errorMessage = error.message)
                }

                override fun onEvents(player: Player, events: Player.Events) {
                    updateSnapshot()
                }
            }
        )
    }

    override fun setSource(source: EngineSource, startPositionMs: Long?) {
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        exoPlayer.setMediaSource(buildMediaSource(source))
        exoPlayer.prepare()
        if (startPositionMs != null && startPositionMs > 0) {
            exoPlayer.seekTo(startPositionMs)
        }
        exoPlayer.playWhenReady = true
    }

    override fun play() {
        exoPlayer.play()
    }

    override fun pause() {
        exoPlayer.pause()
    }

    override fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
    }

    override fun stopForReuse(resetPosition: Boolean) {
        exoPlayer.playWhenReady = false
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        if (resetPosition) {
            exoPlayer.seekTo(0)
        }
        _snapshot.value = PlaybackSnapshot()
    }

    override fun release() {
        exoPlayer.release()
    }

    private fun buildMediaSource(source: EngineSource): MediaSource {
        return when (source) {
            is EngineSource.Dash -> {
                val video = mediaSourceFactory.createMediaSource(MediaItem.fromUri(source.videoUrl))
                if (source.audioUrl.isNullOrBlank()) {
                    video
                } else {
                    val audio = mediaSourceFactory.createMediaSource(MediaItem.fromUri(source.audioUrl))
                    MergingMediaSource(true, video, audio)
                }
            }

            is EngineSource.Progressive -> {
                if (source.segments.size == 1) {
                    mediaSourceFactory.createMediaSource(MediaItem.fromUri(source.segments.first().url))
                } else {
                    val builder = ConcatenatingMediaSource2.Builder()
                    source.segments.forEach { segment ->
                        builder.add(
                            mediaSourceFactory.createMediaSource(MediaItem.fromUri(segment.url)),
                            segment.durationMs ?: C.TIME_UNSET
                        )
                    }
                    builder.build()
                }
            }
        }
    }

    private fun updateSnapshot(
        playbackState: Int = exoPlayer.playbackState,
        isPlaying: Boolean = exoPlayer.isPlaying
    ) {
        _snapshot.value = _snapshot.value.copy(
            isPlaying = isPlaying,
            playbackState = playbackState.toEngineState(),
            positionMs = exoPlayer.currentPosition.coerceAtLeast(0L),
            bufferedPositionMs = exoPlayer.bufferedPosition.coerceAtLeast(0L),
            durationMs = exoPlayer.duration.takeIf { it > 0 } ?: 0L,
            speed = exoPlayer.playbackParameters.speed,
            videoWidth = exoPlayer.videoSize.width,
            videoHeight = exoPlayer.videoSize.height,
            errorMessage = null
        )
    }

    private fun Int.toEngineState(): EnginePlaybackState {
        return when (this) {
            Player.STATE_BUFFERING -> EnginePlaybackState.Buffering
            Player.STATE_READY -> EnginePlaybackState.Ready
            Player.STATE_ENDED -> EnginePlaybackState.Ended
            else -> EnginePlaybackState.Idle
        }
    }
}
