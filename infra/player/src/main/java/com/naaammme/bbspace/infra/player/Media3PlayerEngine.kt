package com.naaammme.bbspace.infra.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.ConcatenatingMediaSource2
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import com.naaammme.bbspace.core.common.log.Logger
import com.naaammme.bbspace.infra.crypto.DeviceIdentity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Suppress("UnsafeOptInUsageError")
@UnstableApi
@Singleton
class Media3PlayerEngine @Inject constructor(
    @ApplicationContext context: Context,
    deviceIdentity: DeviceIdentity,
    okHttpClient: OkHttpClient
) : PlayerEngine {

    private val appContext = context.applicationContext

    private val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
        .setUserAgent("Bilibili Freedoooooom/MarkII")

    private val mediaSourceFactory = ProgressiveMediaSource.Factory(dataSourceFactory)

    private val _player = MutableStateFlow<Player?>(null)
    override val player: StateFlow<Player?> = _player.asStateFlow()
    private val _snapshot = MutableStateFlow(PlaybackSnapshot())
    override val snapshot: StateFlow<PlaybackSnapshot> = _snapshot.asStateFlow()
    private var playerConfig = PlayerConfig()
    private var videoDecoderName: String? = null
    private var audioDecoderName: String? = null
    private var firstFrameSeq = 0L
    private val playerListener = object : Player.Listener {
        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            updateSnapshot(
                discontinuitySeq = _snapshot.value.discontinuitySeq + 1L,
                discontinuityReason = reason.toEngineDiscontinuityReason()
            )
        }

        override fun onPlayerError(error: PlaybackException) {
            Logger.e(TAG, error) {
                "player error code=${error.errorCodeName} msg=${error.message} " +
                        "videoDec=$videoDecoderName audioDec=$audioDecoderName"
            }
            updateSnapshot(errorMessage = error.message)
        }

        override fun onRenderedFirstFrame() {
            firstFrameSeq += 1L
            updateSnapshot()
        }

        override fun onEvents(player: Player, events: Player.Events) {
            updateSnapshot()
        }
    }
    private val analyticsListener = object : AnalyticsListener {
        override fun onVideoDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long
        ) {
            videoDecoderName = decoderName
            updateSnapshot()
        }

        override fun onAudioDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long
        ) {
            audioDecoderName = decoderName
            updateSnapshot()
        }

        override fun onVideoDecoderReleased(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String
        ) {
            if (videoDecoderName == decoderName) {
                videoDecoderName = null
                updateSnapshot()
            }
        }

        override fun onAudioDecoderReleased(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String
        ) {
            if (audioDecoderName == decoderName) {
                audioDecoderName = null
                updateSnapshot()
            }
        }
    }
    private var exoPlayer: ExoPlayer? = null

    override fun updateConfig(config: PlayerConfig) {
        val next = normalizeConfig(config)
        if (next == playerConfig && exoPlayer != null) return

        val prev = exoPlayer
        playerConfig = next
        resetRuntimeState()
        val nextPlayer = buildPlayer(appContext, next)
        exoPlayer = nextPlayer
        _player.value = nextPlayer
        _snapshot.value = PlaybackSnapshot(playerInstanceId = System.identityHashCode(nextPlayer))
        prev?.release()
    }

    override fun setSource(
        source: EngineSource,
        startPositionMs: Long?,
        playWhenReady: Boolean
    ) {
        val player = checkNotNull(exoPlayer) { "Player not prepared" }
        firstFrameSeq = 0L
        player.stop()
        player.clearMediaItems()
        player.setMediaSource(buildMediaSource(source))
        if (startPositionMs != null && startPositionMs > 0) {
            player.seekTo(startPositionMs.coerceAtLeast(0L))
        }
        player.playWhenReady = playWhenReady
        player.prepare()
        updateSnapshot(errorMessage = null)
    }

    override fun play() {
        val player = exoPlayer ?: return
        player.play()
    }

    override fun pause() {
        val player = exoPlayer ?: return
        player.pause()
    }

    override fun setSpeed(speed: Float) {
        val player = exoPlayer ?: return
        player.playbackParameters = PlaybackParameters(speed.coerceIn(0.25f, 3f))
    }

    override fun seekTo(positionMs: Long) {
        val player = exoPlayer ?: return
        player.seekTo(positionMs.coerceAtLeast(0L))
    }

    override fun stopForReuse(resetPosition: Boolean) {
        val player = exoPlayer ?: run {
            resetRuntimeState()
            _snapshot.value = PlaybackSnapshot()
            return
        }
        resetRuntimeState()
        player.playWhenReady = false
        player.stop()
        player.clearMediaItems()
        if (resetPosition) {
            player.seekTo(0)
        }
        _snapshot.value = PlaybackSnapshot()
    }

    override fun release() {
        val player = exoPlayer ?: return
        resetRuntimeState()
        exoPlayer = null
        _player.value = null
        _snapshot.value = PlaybackSnapshot()
        player.release()
    }

    private fun buildPlayer(context: Context, config: PlayerConfig): ExoPlayer {
        val renderersFactory = DefaultRenderersFactory(context)
            // .forceDisableMediaCodecAsynchronousQueueing()
            .setEnableDecoderFallback(config.decoderFallback)
            .setMediaCodecSelector(buildCodecSelector(config))

        return ExoPlayer.Builder(context, renderersFactory)
            .setLoadControl(buildLoadControl(config))
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
                addListener(playerListener)
                addAnalyticsListener(analyticsListener)
            }
    }

    private fun buildCodecSelector(config: PlayerConfig): MediaCodecSelector {
        return MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
            when (config.decoderMode) {
                DecoderMode.Hard -> {
                    val infos = MediaCodecSelector.DEFAULT
                        .getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
                    if (mimeType.startsWith("video/")) {
                        infos.sortedBy { it.softwareOnly }
                    } else {
                        infos
                    }
                }

                DecoderMode.Soft -> MediaCodecSelector.PREFER_SOFTWARE
                    .getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
            }
        }
    }

    private fun buildLoadControl(config: PlayerConfig): DefaultLoadControl {
        return DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                config.minBufferMs,
                config.maxBufferMs,
                config.playBufferMs,
                config.rebufferMs
            )
            .setBackBuffer(config.backBufferMs, true)
            .build()
    }

    private fun normalizeConfig(config: PlayerConfig): PlayerConfig {
        val playMs = max(config.playBufferMs, 0)
        val rebufMs = max(config.rebufferMs, 0)
        val minBufMs = max(config.minBufferMs, max(playMs, rebufMs))
        val maxBufMs = max(config.maxBufferMs, minBufMs)
        return config.copy(
            minBufferMs = minBufMs,
            maxBufferMs = maxBufMs,
            playBufferMs = playMs,
            rebufferMs = rebufMs,
            backBufferMs = max(config.backBufferMs, 0)
        )
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
        playbackState: Int = exoPlayer?.playbackState ?: Player.STATE_IDLE,
        isPlaying: Boolean = exoPlayer?.isPlaying ?: false,
        playWhenReady: Boolean = exoPlayer?.playWhenReady ?: false,
        discontinuitySeq: Long = _snapshot.value.discontinuitySeq,
        discontinuityReason: EngineDiscontinuityReason? = _snapshot.value.discontinuityReason,
        errorMessage: String? = _snapshot.value.errorMessage
    ) {
        val player = exoPlayer
        _snapshot.value = _snapshot.value.copy(
            playerInstanceId = player?.let(System::identityHashCode) ?: 0,
            isPlaying = isPlaying,
            playWhenReady = playWhenReady,
            playbackState = playbackState.toEngineState(),
            positionMs = player?.currentPosition?.coerceAtLeast(0L) ?: 0L,
            bufferedPositionMs = player?.bufferedPosition?.coerceAtLeast(0L) ?: 0L,
            totalBufferedDurationMs = player?.totalBufferedDuration?.coerceAtLeast(0L) ?: 0L,
            durationMs = player?.duration?.takeIf { it > 0 } ?: 0L,
            speed = player?.playbackParameters?.speed ?: 1f,
            videoWidth = player?.videoSize?.width ?: 0,
            videoHeight = player?.videoSize?.height ?: 0,
            firstFrameSeq = firstFrameSeq,
            videoDecoderName = videoDecoderName,
            audioDecoderName = audioDecoderName,
            discontinuitySeq = discontinuitySeq,
            discontinuityReason = discontinuityReason,
            errorMessage = errorMessage
        )
    }

    private fun resetRuntimeState() {
        firstFrameSeq = 0L
        videoDecoderName = null
        audioDecoderName = null
    }

    private fun Int.toEngineState(): EnginePlaybackState {
        return when (this) {
            Player.STATE_BUFFERING -> EnginePlaybackState.Buffering
            Player.STATE_READY -> EnginePlaybackState.Ready
            Player.STATE_ENDED -> EnginePlaybackState.Ended
            else -> EnginePlaybackState.Idle
        }
    }

    private fun Int.toEngineDiscontinuityReason(): EngineDiscontinuityReason {
        return when (this) {
            Player.DISCONTINUITY_REASON_AUTO_TRANSITION -> EngineDiscontinuityReason.AutoTransition
            Player.DISCONTINUITY_REASON_SEEK -> EngineDiscontinuityReason.Seek
            Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT -> EngineDiscontinuityReason.SeekAdjustment
            Player.DISCONTINUITY_REASON_SKIP -> EngineDiscontinuityReason.Skip
            Player.DISCONTINUITY_REASON_REMOVE -> EngineDiscontinuityReason.Remove
            else -> EngineDiscontinuityReason.Internal
        }
    }

    private companion object {
        const val TAG = "Media3Player"
    }
}
