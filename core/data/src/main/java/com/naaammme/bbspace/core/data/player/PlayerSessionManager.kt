package com.naaammme.bbspace.core.data.player

import com.naaammme.bbspace.core.common.AuthProvider
import com.naaammme.bbspace.core.data.AppSettings
import com.naaammme.bbspace.core.domain.history.LocalHistoryRepository
import com.naaammme.bbspace.core.domain.player.VideoPlayerRepository
import com.naaammme.bbspace.core.model.LocalHistoryKey
import com.naaammme.bbspace.core.model.PlaybackAudio
import com.naaammme.bbspace.core.model.buildPlaybackCdns
import com.naaammme.bbspace.core.model.PlaybackError
import com.naaammme.bbspace.core.model.PlaybackRequest
import com.naaammme.bbspace.core.model.PlaybackStream
import com.naaammme.bbspace.core.model.PlayerSessionState
import com.naaammme.bbspace.core.model.VideoHistoryMeta
import com.naaammme.bbspace.infra.player.DecoderMode
import com.naaammme.bbspace.infra.player.EngineSource
import com.naaammme.bbspace.infra.player.PlayerConfig
import com.naaammme.bbspace.infra.player.PlayerEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayerSessionManager @Inject constructor(
    private val repository: VideoPlayerRepository,
    private val appSettings: AppSettings,
    private val reporter: PlaybackReporter,
    private val authProvider: AuthProvider,
    private val localHistoryRepo: LocalHistoryRepository,
    val playerEngine: PlayerEngine
) {
    private val _state = MutableStateFlow(PlayerSessionState())
    val state: StateFlow<PlayerSessionState> = _state.asStateFlow()
    private val ownerId = AtomicLong(0L)
    private val prepMu = Mutex()
    private var reportJob: Job? = null
    private var reportOwnerId = 0L

    fun bindReporter(
        who: Long,
        scope: CoroutineScope
    ) {
        if (reportOwnerId == who && reportJob?.isActive == true) return
        reportJob?.cancel()
        reportOwnerId = who
        reporter.bindOwner(who)
        reportJob = scope.launch {
            combine(state, playerEngine.snapshot) { session, snapshot ->
                session to snapshot
            }.collect { (session, snapshot) ->
                if (!isOwner(who)) return@collect
                reporter.onPlaybackState(session, snapshot)
            }
        }
    }

    fun updateVideoMeta(
        who: Long,
        meta: VideoHistoryMeta?
    ) {
        if (reportOwnerId != who) return
        reporter.updateVideoMeta(meta)
    }

    suspend fun prepareEngine() {
        prepMu.withLock {
            val config = withContext(Dispatchers.IO) { buildPlayerConfig() }
            withContext(Dispatchers.Main.immediate) {
                playerEngine.updateConfig(config)
            }
        }
    }

    suspend fun start(
        who: Long,
        request: PlaybackRequest
    ) {
        if (_state.value.playbackSource != null) {
            reporter.finishSession(playerEngine.snapshot.value)
        }
        ownerId.set(who)
        reporter.bindOwner(who)
        _state.value = _state.value.copy(isPreparing = true, error = null, currentRequest = request)
        try {
            val (source, preferredQuality, preferredAudioId) = coroutineScope {
                val prepJob = async { prepareEngine() }
                val srcJob = async { repository.fetchPlaybackSource(request) }
                val qualityJob = async {
                    request.preferredQuality ?: appSettings.defaultVideoQuality.first()
                }
                val audioJob = async { appSettings.defaultAudioQuality.first() }

                prepJob.await()
                Triple(
                    srcJob.await(),
                    qualityJob.await(),
                    audioJob.await()
                )
            }
            if (!isOwner(who)) return

            val stream = source.streams.firstOrNull { it.quality == preferredQuality }
                ?: source.streams.firstOrNull()
            val audio = selectAudio(stream, source.audios, preferredAudioId)
            val eng = buildEngineSource(stream, audio, appSettings.playerCdnIndex.first())
                ?: error("No playable stream")
            val startMs = resolveStartMs(request, source)
            if (!isOwner(who)) return
            playerEngine.setSource(
                eng.first,
                startMs
            )
            if (!isOwner(who)) return

            _state.value = PlayerSessionState(
                currentRequest = request,
                playbackSource = source,
                currentStream = stream,
                currentAudio = audio,
                cdnIndex = eng.second,
                isPreparing = false
            )
            reporter.startSession(
                request = request,
                state = _state.value,
                startPositionMs = startMs ?: 0L
            )
        } catch (t: Throwable) {
            if (!isOwner(who)) return
            _state.value = _state.value.copy(
                isPreparing = false,
                error = PlaybackError.RequestFailed(t.message ?: "Failed to load playback source", t)
            )
        }
    }

    fun play(who: Long) {
        if (!isOwner(who)) return
        playerEngine.play()
    }

    fun pause(who: Long) {
        if (!isOwner(who)) return
        playerEngine.pause()
    }

    fun setSpeed(
        who: Long,
        speed: Float
    ) {
        if (!isOwner(who)) return
        playerEngine.setSpeed(speed)
    }

    fun seekTo(
        who: Long,
        positionMs: Long
    ) {
        if (!isOwner(who)) return
        playerEngine.seekTo(positionMs)
    }

    fun switchQuality(
        who: Long,
        quality: Int
    ) {
        if (!isOwner(who)) return
        val s = _state.value
        val snap = playerEngine.snapshot.value
        val source = s.playbackSource ?: return
        val stream = source.streams.firstOrNull { it.quality == quality } ?: return
        val audio = selectAudio(stream, source.audios, s.currentAudio?.id ?: 0)
        val eng = buildEngineSource(stream, audio, s.cdnIndex) ?: return
        playerEngine.setSource(eng.first, snap.positionMs, snap.playWhenReady)
        _state.value = s.copy(currentStream = stream, currentAudio = audio, cdnIndex = eng.second)
    }

    fun switchAudio(
        who: Long,
        audioId: Int
    ) {
        if (!isOwner(who)) return
        val s = _state.value
        val snap = playerEngine.snapshot.value
        val source = s.playbackSource ?: return
        val audio = source.audios.firstOrNull { it.id == audioId } ?: return
        val eng = buildEngineSource(s.currentStream, audio, s.cdnIndex) ?: return
        playerEngine.setSource(eng.first, snap.positionMs, snap.playWhenReady)
        _state.value = s.copy(currentAudio = audio, cdnIndex = eng.second)
    }

    fun switchCdn(
        who: Long,
        cdnIndex: Int
    ) {
        if (!isOwner(who)) return
        val s = _state.value
        val snap = playerEngine.snapshot.value
        val eng = buildEngineSource(s.currentStream, s.currentAudio, cdnIndex) ?: return
        playerEngine.setSource(eng.first, snap.positionMs, snap.playWhenReady)
        _state.value = s.copy(cdnIndex = eng.second)
    }

    suspend fun closeCurrentSession(who: Long) {
        if (!ownerId.compareAndSet(who, 0L)) return
        reporter.finishSession(playerEngine.snapshot.value)
        playerEngine.stopForReuse(resetPosition = true)
        _state.value = PlayerSessionState()
    }

    fun hasSession(
        who: Long,
        request: PlaybackRequest
    ): Boolean {
        val state = _state.value
        return ownerId.get() == who &&
                state.currentRequest == request &&
                (state.playbackSource != null || state.isPreparing) &&
                state.error == null
    }

    private fun selectAudio(
        stream: PlaybackStream?,
        audios: List<PlaybackAudio>,
        preferredId: Int
    ): PlaybackAudio? {
        if (audios.isEmpty()) return null
        val linkedId = (stream as? PlaybackStream.Dash)?.audioId
        return audios.firstOrNull { it.id == preferredId && preferredId > 0 }
            ?: audios.firstOrNull { it.id == linkedId }
            ?: audios.firstOrNull()
    }

    private fun buildEngineSource(
        stream: PlaybackStream?,
        audio: PlaybackAudio?,
        cdnIndex: Int
    ): Pair<EngineSource, Int>? {
        return when (stream) {
            is PlaybackStream.Dash -> {
                val cdns = buildPlaybackCdns(stream, audio)
                if (cdns.isEmpty()) return null
                val index = cdnIndex.coerceIn(0, cdns.lastIndex)
                EngineSource.Dash(cdns[index].videoUrl, cdns[index].audioUrl) to index
            }
            is PlaybackStream.Progressive -> EngineSource.Progressive(
                stream.segments.map { EngineSource.ProgressiveSegment(it.url, it.durationMs) }
            ) to 0
            null -> null
        }
    }

    private suspend fun buildPlayerConfig(): PlayerConfig {
        return PlayerConfig(
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

    private suspend fun resolveStartMs(
        request: PlaybackRequest,
        source: com.naaammme.bbspace.core.model.PlaybackSource
    ): Long? {
        request.seekToMs?.let { return it }
        val key = LocalHistoryKey.video(source.report)
        val local = localHistoryRepo.getVideo(authProvider.mid, key)
        if (local != null) {
            if (canResume(local.progressMs, local.finished, source.durationMs)) {
                return local.progressMs
            }
            return 0L
        }
        return source.resumePositionMs
    }

    private fun canResume(
        progressMs: Long?,
        finished: Boolean,
        durationMs: Long
    ): Boolean {
        val progress = progressMs?.coerceAtLeast(0L) ?: return false
        if (progress <= 0L || finished) return false
        if (durationMs <= 0L) return true
        if (durationMs - progress <= COMPLETE_THRESHOLD_MS) return false
        return progress * 100 < durationMs * COMPLETE_RATIO
    }

    private fun isOwner(who: Long): Boolean {
        return ownerId.get() == who
    }

    private companion object {
        const val COMPLETE_THRESHOLD_MS = 5_000L
        const val COMPLETE_RATIO = 95L
    }
}
