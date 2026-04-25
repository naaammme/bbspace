package com.naaammme.bbspace.feature.download.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naaammme.bbspace.core.domain.download.VideoDownloadRepository
import com.naaammme.bbspace.core.domain.video.VideoDetailRepository
import com.naaammme.bbspace.core.model.VideoDownloadKind
import com.naaammme.bbspace.core.model.VideoDownloadRequest
import com.naaammme.bbspace.core.model.VideoRoute
import com.naaammme.bbspace.core.model.VideoRouteTool
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class DownloadViewModel @Inject constructor(
    private val detailRepository: VideoDetailRepository,
    private val downloadRepository: VideoDownloadRepository
) : ViewModel() {
    private var title: String? = null
    private var route: VideoRoute? = null
    private val _state = MutableStateFlow(DownloadUiState())
    val state: StateFlow<DownloadUiState> = _state.asStateFlow()
    private var job: Job? = null

    fun updateInput(value: String) {
        _state.update { it.copy(input = value, error = null) }
    }

    fun startRouteDownload(
        route: VideoRoute,
        title: String?,
        kind: VideoDownloadKind,
        videoQuality: Int,
        audioQuality: Int
    ) {
        this.route = route
        this.title = title
        _state.update {
            it.copy(
                kind = kind,
                videoQuality = videoQuality,
                audioQuality = audioQuality,
                hasTask = true,
                error = null
            )
        }
        startDownload()
    }

    fun selectKind(kind: VideoDownloadKind) {
        _state.update { it.copy(kind = kind, error = null) }
    }

    fun selectQuality(quality: Int) {
        _state.update { it.copy(videoQuality = quality, error = null) }
    }

    fun selectAudio(audioQuality: Int) {
        _state.update { it.copy(audioQuality = audioQuality, error = null) }
    }

    fun startInputTask() {
        if (_state.value.downloading) return
        val input = _state.value.input.trim()
        if (input.isBlank()) return setError("请输入链接、av号或BV号")
        job?.cancel()
        job = viewModelScope.launch {
            _state.update { it.copy(loading = true, progress = null, error = null) }
            try {
                val task = parseInput(input)
                route = task.route
                title = task.title
                _state.update { it.copy(loading = false, hasTask = true) }
                collectDownload(task.route)
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                _state.update {
                    it.copy(
                        loading = false,
                        progress = null,
                        error = t.message ?: "解析下载目标失败"
                    )
                }
            }
        }
    }

    fun startDownload() {
        if (_state.value.downloading) return
        val target = route ?: return setError("下载路由无效")
        job?.cancel()
        job = viewModelScope.launch {
            collectDownload(target)
        }
    }

    private suspend fun collectDownload(target: VideoRoute) {
        val state = _state.value
        _state.update { it.copy(loading = false, progress = null, error = null) }
        try {
            downloadRepository.download(
                VideoDownloadRequest(
                    route = target,
                    kind = state.kind,
                    videoQuality = state.videoQuality,
                    audioQuality = state.audioQuality,
                    title = title
                )
            ).collect { progress ->
                _state.update { it.copy(progress = progress, error = null) }
            }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            _state.update {
                it.copy(
                    progress = null,
                    error = t.message ?: "下载失败"
                )
            }
        }
    }

    private suspend fun parseInput(input: String): ParsedTask {
        val src = VideoRouteTool.feed()
        val epId = VideoRouteTool.epId(input)
            ?: EP_REGEX.find(input)?.groupValues?.get(1)?.toLongOrNull()
        if (epId != null && epId > 0L) {
            return ParsedTask(VideoRoute.Pgc(epId = epId, src = src), null)
        }

        val bvid = VideoRouteTool.bvid(input) ?: BV_REGEX.find(input)?.value
        val aid = VideoRouteTool.aid(input)
            ?: AV_REGEX.find(input)?.groupValues?.get(1)?.toLongOrNull()
            ?: input.toLongOrNull()
        val cid = VideoRouteTool.cid(input)
        if (aid == null && bvid == null) error("请输入链接、av号或BV号")
        if (aid != null && aid > 0L && cid != null && cid > 0L) {
            return ParsedTask(
                VideoRoute.Ugc(
                    aid = aid,
                    cid = cid,
                    bvid = bvid,
                    src = src
                ),
                null
            )
        }

        val detail = detailRepository.fetchVideoDetail(
            aid = aid ?: 0L,
            bvid = bvid,
            src = src
        )
        val page = detail.pages.firstOrNull() ?: error("没有找到可下载分P")
        return ParsedTask(
            VideoRoute.Ugc(
                aid = detail.aid,
                cid = page.cid,
                bvid = detail.bvid.takeIf(String::isNotBlank),
                src = src
            ),
            detail.title
        )
    }

    private fun setError(message: String) {
        _state.update { it.copy(error = message) }
    }

    private data class ParsedTask(
        val route: VideoRoute,
        val title: String?
    )

    private companion object {
        val AV_REGEX = Regex("""(?i)(?:^|\D)av(\d+)""")
        val BV_REGEX = Regex("""BV[0-9A-Za-z]+""")
        val EP_REGEX = Regex("""(?i)(?:^|\D)ep(\d+)""")
    }
}
