package com.naaammme.bbspace.feature.download.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naaammme.bbspace.core.domain.download.VideoDownloadRepository
import com.naaammme.bbspace.core.domain.video.VideoDetailRepository
import com.naaammme.bbspace.core.model.VideoDownloadEnqueueResult
import com.naaammme.bbspace.core.model.VideoDownloadKind
import com.naaammme.bbspace.core.model.VideoDownloadMeta
import com.naaammme.bbspace.core.model.VideoDownloadRequest
import com.naaammme.bbspace.core.model.PlayBiz
import com.naaammme.bbspace.core.model.VideoRouteTool
import com.naaammme.bbspace.core.model.fallbackTitle
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
    private var pendingRequest: VideoDownloadRequest? = null
    private var pendingMeta: VideoDownloadMeta = VideoDownloadMeta()
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
        pendingRequest = null
        pendingMeta = VideoDownloadMeta()
        _formState.update {
            it.copy(
                input = value,
                hasTask = false,
                pendingTitle = null,
                error = null
            )
        }
    }

    fun enqueueDownload(request: VideoDownloadRequest) {
        parseJob?.cancel()
        pendingRequest = null
        pendingMeta = VideoDownloadMeta()
        _formState.update {
            it.copy(
                kind = request.kind,
                videoQuality = request.videoQuality,
                audioQuality = request.audioQuality,
                hasTask = false,
                pendingTitle = null,
                error = null
            )
        }
        enqueue(request)
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
            pendingRequest = null
            pendingMeta = VideoDownloadMeta()
            _formState.update {
                it.copy(
                    loading = true,
                    hasTask = false,
                    pendingTitle = null,
                    error = null
                )
            }
            try {
                val request = parseInput(input)
                pendingRequest = request
                pendingMeta = request.meta
                _formState.update {
                    it.copy(
                        loading = false,
                        hasTask = true,
                        pendingTitle = requestTitle(request)
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _formState.update {
                    it.copy(
                        loading = false,
                        error = e.message ?: "解析缓存目标失败"
                    )
                }
            }
        }
    }

    fun startDownload() {
        val request = pendingRequest ?: return setError("缓存目标无效")
        val state = _formState.value
        enqueue(
            request.copy(
                kind = state.kind,
                videoQuality = state.videoQuality,
                audioQuality = state.audioQuality,
                meta = pendingMeta
            )
        )
        pendingRequest = null
        pendingMeta = VideoDownloadMeta()
        _formState.update {
            it.copy(
                hasTask = false,
                pendingTitle = null,
                error = null
            )
        }
    }

    fun pauseTask(taskId: Long) {
        viewModelScope.launch {
            downloadRepository.pause(taskId)
        }
    }

    fun resumeTask(taskId: Long) {
        viewModelScope.launch {
            downloadRepository.resume(taskId)
        }
    }

    fun deleteTask(taskId: Long) {
        viewModelScope.launch {
            downloadRepository.delete(taskId)
        }
    }

    private suspend fun parseInput(input: String): VideoDownloadRequest {
        val state = _formState.value
        val epId = VideoRouteTool.epId(input)
            ?: EP_REGEX.find(input)?.groupValues?.get(1)?.toLongOrNull()
        val seasonId = VideoRouteTool.arg(input, "season_id")
            ?.toLongOrNull()
            ?: VideoRouteTool.arg(input, "seasonId")?.toLongOrNull()
            ?: SS_REGEX.find(input)?.groupValues?.get(1)?.toLongOrNull()
        if ((epId != null && epId > 0L) || (seasonId != null && seasonId > 0L)) {
            val title = when {
                epId != null && epId > 0L -> "ep$epId"
                seasonId != null && seasonId > 0L -> "ss$seasonId"
                else -> null
            }
            return VideoDownloadRequest(
                biz = PlayBiz.PGC,
                epId = epId ?: 0L,
                seasonId = seasonId ?: 0L,
                kind = state.kind,
                videoQuality = state.videoQuality,
                audioQuality = state.audioQuality,
                meta = VideoDownloadMeta(title = title)
            )
        }

        val bvid = VideoRouteTool.bvid(input) ?: BV_REGEX.find(input)?.value
        val aid = VideoRouteTool.aid(input)
            ?: AV_REGEX.find(input)?.groupValues?.get(1)?.toLongOrNull()
            ?: input.toLongOrNull()
        val cid = VideoRouteTool.cid(input)
        if (aid == null && bvid == null) error("请输入链接、av号或BV号")
        val detail = detailRepository.fetchVideoDetail(
            aid = aid ?: 0L,
            bvid = bvid,
            src = VideoRouteTool.feed()
        )
        val targetCid = cid?.takeIf { it > 0L }
            ?: detail.pages.firstOrNull()?.cid
            ?: error("没有找到可缓存分P")
        val page = detail.pages.firstOrNull { it.cid == targetCid }
            ?: detail.pages.firstOrNull()
        val title = listOfNotNull(
            detail.title.takeIf(String::isNotBlank),
            page?.part?.takeIf(String::isNotBlank)
        ).joinToString(" - ").takeIf(String::isNotBlank)
        return VideoDownloadRequest(
            biz = PlayBiz.UGC,
            aid = detail.aid,
            cid = targetCid,
            bvid = detail.bvid.takeIf(String::isNotBlank),
            kind = state.kind,
            videoQuality = state.videoQuality,
            audioQuality = state.audioQuality,
            meta = VideoDownloadMeta(
                title = title,
                cover = detail.cover,
                ownerUid = detail.owner?.mid?.takeIf { it > 0L },
                ownerName = detail.owner?.name?.takeIf(String::isNotBlank)
            )
        )
    }

    private fun setError(message: String) {
        _formState.update { it.copy(error = message) }
    }

    private fun enqueue(request: VideoDownloadRequest) {
        _formState.update { it.copy(error = null) }
        viewModelScope.launch {
            when (downloadRepository.enqueue(request)) {
                is VideoDownloadEnqueueResult.Enqueued -> Unit
                is VideoDownloadEnqueueResult.AlreadyExists -> {
                    setError("任务已存在")
                }
            }
        }
    }

    private fun requestTitle(request: VideoDownloadRequest): String {
        val title = request.meta.title?.trim()
        return if (!title.isNullOrBlank()) title else request.fallbackTitle()
    }

    private companion object {
        val AV_REGEX = Regex("""(?i)(?:^|\D)av(\d+)""")
        val BV_REGEX = Regex("""BV[0-9A-Za-z]+""")
        val EP_REGEX = Regex("""(?i)(?:^|\D)ep(\d+)""")
        val SS_REGEX = Regex("""(?i)(?:^|\D)ss(\d+)""")
    }
}
