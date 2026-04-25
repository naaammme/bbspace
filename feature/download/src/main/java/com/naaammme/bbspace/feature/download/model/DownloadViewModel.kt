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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class DownloadViewModel @Inject constructor(
    private val detailRepository: VideoDetailRepository,
    private val downloadRepository: VideoDownloadRepository
) : ViewModel() {
    private var title: String? = null
    private var route: VideoRoute? = null
    private val _formState = MutableStateFlow(DownloadUiState())
    val state: StateFlow<DownloadUiState> = combine(
        _formState,
        downloadRepository.tasks
    ) { form, tasks ->
        form.copy(tasks = tasks)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DownloadUiState()
    )
    private var parseJob: Job? = null

    fun selectTab(tab: DownloadTab) {
        _formState.update { it.copy(tab = tab) }
    }

    fun updateInput(value: String) {
        route = null
        title = null
        _formState.update {
            it.copy(
                input = value,
                hasTask = false,
                pendingTitle = null,
                error = null
            )
        }
    }

    fun startRouteDownload(
        route: VideoRoute,
        title: String?,
        kind: VideoDownloadKind,
        videoQuality: Int,
        audioQuality: Int
    ) {
        parseJob?.cancel()
        this.route = null
        this.title = null
        _formState.update {
            it.copy(
                kind = kind,
                videoQuality = videoQuality,
                audioQuality = audioQuality,
                hasTask = false,
                pendingTitle = null,
                error = null
            )
        }
        enqueue(
            VideoDownloadRequest(
                route = route,
                kind = kind,
                videoQuality = videoQuality,
                audioQuality = audioQuality,
                title = title
            )
        )
    }

    fun selectKind(kind: VideoDownloadKind) {
        _formState.update { it.copy(kind = kind, error = null) }
    }

    fun selectQuality(quality: Int) {
        _formState.update { it.copy(videoQuality = quality, error = null) }
    }

    fun selectAudio(audioQuality: Int) {
        _formState.update { it.copy(audioQuality = audioQuality, error = null) }
    }

    fun startInputTask() {
        val input = _formState.value.input.trim()
        if (input.isBlank()) return setError("请输入链接、av号或BV号")
        parseJob?.cancel()
        parseJob = viewModelScope.launch {
            _formState.update {
                it.copy(
                    loading = true,
                    hasTask = false,
                    pendingTitle = null,
                    error = null
                )
            }
            try {
                val task = parseInput(input)
                route = task.route
                title = task.title
                _formState.update {
                    it.copy(
                        loading = false,
                        hasTask = true,
                        pendingTitle = taskTitle(task.title, task.route)
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _formState.update {
                    it.copy(
                        loading = false,
                        error = e.message ?: "解析下载目标失败"
                    )
                }
            }
        }
    }

    fun startDownload() {
        val target = route ?: return setError("下载路由无效")
        val state = _formState.value
        enqueue(
            VideoDownloadRequest(
                route = target,
                kind = state.kind,
                videoQuality = state.videoQuality,
                audioQuality = state.audioQuality,
                title = title
            )
        )
        route = null
        title = null
        _formState.update {
            it.copy(
                hasTask = false,
                pendingTitle = null,
                error = null
            )
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
        _formState.update { it.copy(error = message) }
    }

    private fun enqueue(request: VideoDownloadRequest) {
        downloadRepository.enqueue(request)
        _formState.update { it.copy(error = null) }
        viewModelScope.launch {
            downloadRepository.runPending()
        }
    }

    private fun taskTitle(
        title: String?,
        route: VideoRoute
    ): String {
        title?.trim()
            ?.takeIf(String::isNotBlank)
            ?.let { return it }
        return when (route) {
            is VideoRoute.Ugc -> route.bvid?.takeIf(String::isNotBlank) ?: "av${route.aid}"
            is VideoRoute.Pgc -> "ep${route.epId}"
            is VideoRoute.Pugv -> "pugv ep${route.epId}"
        }
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
