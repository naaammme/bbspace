package com.naaammme.bbspace.infra.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.ConcatenatingMediaSource2
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import com.naaammme.bbspace.core.common.UserAgentBuilder
import com.naaammme.bbspace.core.common.log.Logger
import com.naaammme.bbspace.core.model.PlaybackProgress
import com.naaammme.bbspace.core.model.PlaybackState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Dns
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Suppress("UnsafeOptInUsageError")
@UnstableApi
@Singleton
class Media3PlayerEngine @Inject constructor(
    @ApplicationContext context: Context,
    okHttpClient: OkHttpClient
) : PlayerEngine {

    private val appContext = context.applicationContext
    private val videoOkHttpClient = okHttpClient.newBuilder()
        .dns(Dns.SYSTEM)
        .build()
    private val webRequestHeaders = mapOf("Referer" to "https://www.bilibili.com")

    private val _player = MutableStateFlow<Player?>(null)
    override val player: StateFlow<Player?> = _player.asStateFlow()
    private val _currentSource = MutableStateFlow<EngineSource?>(null)
    override val currentSource: StateFlow<EngineSource?> = _currentSource.asStateFlow()
    private val _playbackState = MutableStateFlow(PlayerPlaybackState())
    override val playbackState: StateFlow<PlayerPlaybackState> = _playbackState.asStateFlow()
    private val _playbackProgress = MutableStateFlow(PlaybackProgress())
    override val playbackProgress: StateFlow<PlaybackProgress> = _playbackProgress.asStateFlow()
    private val runtimeScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var playerConfig = PlayerConfig()
    private var videoDecoderName: String? = null
    private var audioDecoderName: String? = null
    private var firstFrameSeq = 0L
    private var lastEventsPlaybackState = Player.STATE_IDLE
    private var lastEventsIsPlaying = false
    private var progressJob: Job? = null

    private val playerListener = object : Player.Listener {
        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            if (reason.isSeekDiscontinuity()) {
                updatePlaybackState(seekEventSeq = _playbackState.value.seekEventSeq + 1L)
            }
            updatePlaybackProgress()
        }

        override fun onPlayerError(error: PlaybackException) {
            Logger.e(TAG, error) {
                "player error code=${error.errorCodeName} msg=${error.message} " +
                        "videoDec=$videoDecoderName audioDec=$audioDecoderName"
            }
            updatePlaybackState(errorMessage = error.message)
        }

        override fun onRenderedFirstFrame() {
            firstFrameSeq += 1L
            updatePlaybackState()
        }

        override fun onEvents(player: Player, events: Player.Events) {
            val state = player.playbackState
            val playing = player.isPlaying
            if (state != lastEventsPlaybackState || playing != lastEventsIsPlaying) {
                lastEventsPlaybackState = state
                lastEventsIsPlaying = playing
                updatePlaybackState()
                updateProgressPolling()
            }
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
            updatePlaybackState()
        }

        override fun onAudioDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long
        ) {
            audioDecoderName = decoderName
            updatePlaybackState()
        }

        override fun onVideoDecoderReleased(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String
        ) {
            if (videoDecoderName == decoderName) {
                videoDecoderName = null
                updatePlaybackState()
            }
        }

        override fun onAudioDecoderReleased(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String
        ) {
            if (audioDecoderName == decoderName) {
                audioDecoderName = null
                updatePlaybackState()
            }
        }
    }
    private var exoPlayer: ExoPlayer? = null

    override fun updateConfig(config: PlayerConfig) {
        val next = normalizeConfig(config)
        if (next == playerConfig && exoPlayer != null) return

        val prev = exoPlayer
        progressJob?.cancel()
        progressJob = null
        playerConfig = next
        resetRuntimeState()
        _currentSource.value = null
        val nextPlayer = buildPlayer(appContext, next)
        exoPlayer = nextPlayer
        _player.value = nextPlayer
        _playbackState.value = PlayerPlaybackState()
        _playbackProgress.value = PlaybackProgress()
        prev?.release()
    }

    override fun setSource(
        source: EngineSource,
        startPositionMs: Long?,
        playWhenReady: Boolean,
        metadata: MediaMetadata?
    ) {
        val player = ensurePlayer()
        val itemMetadata = metadata ?: player.currentMediaItem?.mediaMetadata ?: MediaMetadata.EMPTY
        firstFrameSeq = 0L
        player.setMediaSource(buildMediaSource(source, itemMetadata))
        if (startPositionMs != null && startPositionMs > 0) {
            player.seekTo(startPositionMs.coerceAtLeast(0L))
        }
        player.playWhenReady = playWhenReady
        player.prepare()
        _currentSource.value = source
        updatePlaybackState(errorMessage = null)
        updatePlaybackProgress()
    }

    override fun play() {
        val player = ensurePlayer()
        player.play()
    }

    override fun pause() {
        val player = exoPlayer ?: return
        player.pause()
    }

    override fun setSpeed(speed: Float) {
        val player = ensurePlayer()
        player.playbackParameters = PlaybackParameters(speed.coerceIn(0.25f, 3f))
        updatePlaybackState()
    }

    override fun seekTo(positionMs: Long) {
        val player = exoPlayer ?: return
        player.seekTo(positionMs.coerceAtLeast(0L))
        updatePlaybackProgress()
    }

    override fun setMediaMetadata(metadata: MediaMetadata) {
        val player = exoPlayer ?: return
        val current = player.currentMediaItem ?: return
        val next = current.buildUpon()
            .setMediaMetadata(metadata)
            .build()
        player.replaceMediaItem(player.currentMediaItemIndex, next)
    }

    override fun stopForReuse(resetPosition: Boolean) {
        progressJob?.cancel()
        progressJob = null
        val player = exoPlayer ?: run {
            resetRuntimeState()
            _currentSource.value = null
            _playbackState.value = PlayerPlaybackState()
            _playbackProgress.value = PlaybackProgress()
            return
        }
        resetRuntimeState()
        _currentSource.value = null
        player.playWhenReady = false
        player.stop()
        player.clearMediaItems()
        if (resetPosition) {
            player.seekTo(0)
        }
        _playbackState.value = PlayerPlaybackState()
        _playbackProgress.value = PlaybackProgress()
    }

    override fun release() {
        progressJob?.cancel()
        progressJob = null
        val player = exoPlayer ?: return
        resetRuntimeState()
        exoPlayer = null
        _player.value = null
        _currentSource.value = null
        _playbackState.value = PlayerPlaybackState()
        _playbackProgress.value = PlaybackProgress()
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
                    infos.sortedBy { it.softwareOnly }
                }

                DecoderMode.Soft -> MediaCodecSelector.PREFER_SOFTWARE
                    .getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
            }
        }
    }

    private fun ensurePlayer(): ExoPlayer {
        exoPlayer?.let { return it }
        val player = buildPlayer(appContext, playerConfig)
        exoPlayer = player
        _player.value = player
        _playbackState.value = PlayerPlaybackState()
        _playbackProgress.value = PlaybackProgress()
        return player
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

    private fun buildMediaSource(
        source: EngineSource,
        metadata: MediaMetadata
    ): MediaSource {
        val mediaSourceFactory = buildMediaSourceFactory(source)
        return when (source) {
            is EngineSource.LiveFlv -> {
                val item = mediaItem(source.url, metadata)
                    .buildUpon()
                    .setLiveConfiguration(MediaItem.LiveConfiguration.Builder().build())
                    .build()
                mediaSourceFactory.createMediaSource(item)
            }

            is EngineSource.Dash -> {
                val video = mediaSourceFactory.createMediaSource(mediaItem(source.videoUrl, metadata))
                if (source.audioUrl.isNullOrBlank()) {
                    video
                } else {
                    val audio = mediaSourceFactory.createMediaSource(mediaItem(source.audioUrl, metadata))
                    MergingMediaSource(true, video, audio)
                }
            }

            is EngineSource.Progressive -> {
                if (source.segments.size == 1) {
                    mediaSourceFactory.createMediaSource(
                        mediaItem(source.segments.first().url, metadata)
                    )
                } else {
                    val builder = ConcatenatingMediaSource2.Builder()
                    source.segments.forEach { segment ->
                        builder.add(
                            mediaSourceFactory.createMediaSource(mediaItem(segment.url, metadata)),
                            segment.durationMs ?: C.TIME_UNSET
                        )
                    }
                    builder.build()
                }
            }
        }
    }

    private fun buildMediaSourceFactory(source: EngineSource): ProgressiveMediaSource.Factory {
        val useWebPlaybackHeaders = source.usesWebPlaybackHeaders()
        val upstreamFactory = OkHttpDataSource.Factory(videoOkHttpClient)
            .setUserAgent(
                if (useWebPlaybackHeaders) {
                    UserAgentBuilder.buildWebUserAgent()
                } else {
                    UserAgentBuilder.buildPlayerUserAgent()
                }
            )
        if (useWebPlaybackHeaders) {
            upstreamFactory.setDefaultRequestProperties(webRequestHeaders)
        }
        return ProgressiveMediaSource.Factory(
            DefaultDataSource.Factory(appContext, upstreamFactory)
        )
    }

    private fun EngineSource.usesWebPlaybackHeaders(): Boolean {
        return when (this) {
            is EngineSource.LiveFlv -> false
            is EngineSource.Dash -> videoUrl.isWebPlaybackUrl() || audioUrl?.isWebPlaybackUrl() == true
            is EngineSource.Progressive -> segments.any { it.url.isWebPlaybackUrl() }
        }
    }

    private fun String.isWebPlaybackUrl(): Boolean {
        return contains("platform=pc", ignoreCase = true)
    }

    private fun mediaItem(uri: String, metadata: MediaMetadata): MediaItem {
        return MediaItem.Builder()
            .setUri(uri)
            .setMediaMetadata(metadata)
            .build()
    }

    private fun updatePlaybackState(
        playbackState: PlaybackState = (exoPlayer?.playbackState ?: Player.STATE_IDLE).toPlaybackState(),
        isPlaying: Boolean = exoPlayer?.isPlaying ?: false,
        playWhenReady: Boolean = exoPlayer?.playWhenReady ?: false,
        seekEventSeq: Long = _playbackState.value.seekEventSeq,
        errorMessage: String? = _playbackState.value.errorMessage
    ) {
        val player = exoPlayer
        _playbackState.value = PlayerPlaybackState(
            isPlaying = isPlaying,
            playWhenReady = playWhenReady,
            playbackState = playbackState,
            speed = player?.playbackParameters?.speed ?: 1f,
            videoWidth = player?.videoSize?.width ?: 0,
            videoHeight = player?.videoSize?.height ?: 0,
            firstFrameSeq = firstFrameSeq,
            videoDecoderName = videoDecoderName,
            audioDecoderName = audioDecoderName,
            seekEventSeq = seekEventSeq,
            errorMessage = errorMessage
        )
    }

    private fun updatePlaybackProgress() {
        val player = exoPlayer
        _playbackProgress.value = PlaybackProgress(
            positionMs = player?.currentPosition?.coerceAtLeast(0L) ?: 0L,
            bufferedPositionMs = player?.bufferedPosition?.coerceAtLeast(0L) ?: 0L,
            durationMs = player?.duration?.takeIf { it > 0L } ?: 0L
        )
    }

    private fun updateProgressPolling() {
        val player = exoPlayer
        val shouldPoll = player != null &&
            lastEventsIsPlaying &&
            lastEventsPlaybackState == Player.STATE_READY
        if (shouldPoll) {
            if (progressJob?.isActive != true) {
                progressJob = runtimeScope.launch {
                    while (isActive) {
                        delay(1_000)
                        updatePlaybackProgress()
                    }
                }
            }
        } else {
            progressJob?.cancel()
            progressJob = null
        }
    }

    private fun resetRuntimeState() {
        firstFrameSeq = 0L
        videoDecoderName = null
        audioDecoderName = null
        lastEventsPlaybackState = Player.STATE_IDLE
        lastEventsIsPlaying = false
    }

    private fun Int.toPlaybackState(): PlaybackState {
        return when (this) {
            Player.STATE_BUFFERING -> PlaybackState.Buffering
            Player.STATE_READY -> PlaybackState.Ready
            Player.STATE_ENDED -> PlaybackState.Ended
            else -> PlaybackState.Idle
        }
    }

    private fun Int.isSeekDiscontinuity(): Boolean {
        return this == Player.DISCONTINUITY_REASON_SEEK ||
            this == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT
    }

    private companion object {
        const val TAG = "Media3Player"
    }
}
