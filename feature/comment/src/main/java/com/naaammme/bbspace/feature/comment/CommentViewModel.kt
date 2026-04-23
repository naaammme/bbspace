package com.naaammme.bbspace.feature.comment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naaammme.bbspace.core.domain.auth.AuthRepository
import com.naaammme.bbspace.core.domain.comment.CommentRepository
import com.naaammme.bbspace.core.model.COMMENT_FILTER_ALL
import com.naaammme.bbspace.core.model.CommentPage
import com.naaammme.bbspace.core.model.CommentReply
import com.naaammme.bbspace.core.model.CommentSort
import com.naaammme.bbspace.core.model.CommentSubject
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class CommentViewModel @Inject constructor(
    private val repo: CommentRepository,
    authRepo: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CommentUiState())
    val uiState = _uiState.asStateFlow()
    private val _msg = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val msg = _msg.asSharedFlow()

    private var reqId = 0L

    init {
        viewModelScope.launch {
            authRepo.currentMidFlow.collectLatest { mid ->
                _uiState.update { cur ->
                    cur.copy(
                        currentMid = mid,
                        editor = if (mid > 0L) cur.editor else CommentEditorState()
                    )
                }
            }
        }
    }

    fun bind(subject: CommentSubject?) {
        if (_uiState.value.subject == subject) return
        reqId += 1L
        val currentMid = _uiState.value.currentMid
        _uiState.value = CommentUiState(
            subject = subject,
            currentMid = currentMid,
            loading = subject != null
        )
        if (subject != null) {
            refresh(
                subject = subject,
                sort = CommentSort.HOT,
                filter = COMMENT_FILTER_ALL
            )
        }
    }

    fun selectSort(sort: CommentSort) {
        val state = _uiState.value
        val subject = state.subject ?: return
        if (state.sort == sort && state.items.isNotEmpty()) return
        refresh(
            subject = subject,
            sort = sort,
            filter = state.selectedFilter
        )
    }

    fun retry() {
        val state = _uiState.value
        val subject = state.subject ?: return
        refresh(
            subject = subject,
            sort = state.sort,
            filter = state.selectedFilter
        )
    }

    fun loadMore() {
        val state = _uiState.value
        val subject = state.subject ?: return
        val nextOffset = state.nextOffset ?: return
        if (state.loading || state.loadingMore || !state.hasMore) return
        val callId = reqId
        _uiState.update {
            it.copy(
                loadingMore = true,
                loadMoreError = null
            )
        }
        viewModelScope.launch {
            val result = runCatching {
                repo.fetchMainPage(
                    subject = subject,
                    sort = state.sort,
                    filterTag = state.selectedFilter,
                    offset = nextOffset
                )
            }
            if (callId != reqId) return@launch
            result.fold(
                onSuccess = { page ->
                    _uiState.update { cur ->
                        cur.copy(
                            loading = false,
                            loadingMore = false,
                            error = null,
                            loadMoreError = null,
                            title = page.title,
                            count = page.count,
                            sort = page.sort,
                            canSwitchSort = page.canSwitchSort,
                            filterTags = page.filterTags,
                            selectedFilter = page.selectedFilter,
                            items = mergeReplies(cur.items, page.items),
                            nextOffset = page.nextOffset,
                            hasMore = page.hasMore,
                            endText = page.endText
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            loadingMore = false,
                            loadMoreError = err.message ?: "加载更多评论失败"
                        )
                    }
                }
            )
        }
    }

    fun openReplyThread(reply: CommentReply) {
        val state = _uiState.value
        val subject = state.subject ?: return
        val sort = state.sort
        _uiState.update {
            it.copy(
                threadPane = CommentThreadState(
                    root = reply,
                    count = reply.replyCount,
                    sort = sort,
                    loading = true,
                    items = emptyList()
                )
            )
        }
        fetchReplyThread(
            subject = subject,
            rootRpid = reply.rpid,
            sort = sort,
            offset = "",
            append = false
        )
    }

    fun closeReplyThread() {
        _uiState.update {
            it.copy(threadPane = null)
        }
    }

    fun retryReplyThread() {
        val state = _uiState.value
        val subject = state.subject ?: return
        val thread = state.threadPane ?: return
        _uiState.update {
            it.copy(
                threadPane = thread.copy(
                    loading = true,
                    loadingMore = false,
                    error = null,
                    loadMoreError = null
                )
            )
        }
        fetchReplyThread(
            subject = subject,
            rootRpid = thread.root.rpid,
            sort = thread.sort,
            offset = "",
            append = false
        )
    }

    fun loadMoreReplyThread() {
        val state = _uiState.value
        val subject = state.subject ?: return
        val thread = state.threadPane ?: return
        val nextOffset = thread.nextOffset ?: return
        if (thread.loading || thread.loadingMore || !thread.hasMore) return
        _uiState.update {
            it.copy(
                threadPane = thread.copy(
                    loadingMore = true,
                    loadMoreError = null
                )
            )
        }
        fetchReplyThread(
            subject = subject,
            rootRpid = thread.root.rpid,
            sort = thread.sort,
            offset = nextOffset,
            append = true
        )
    }

    fun toggleReplyThreadSort() {
        val state = _uiState.value
        val subject = state.subject ?: return
        val thread = state.threadPane ?: return
        if (!thread.canSwitchSort) return
        val nextSort = if (thread.sort == CommentSort.HOT) {
            CommentSort.TIME
        } else {
            CommentSort.HOT
        }
        _uiState.update {
            it.copy(
                threadPane = thread.copy(
                    sort = nextSort,
                    loading = true,
                    loadingMore = false,
                    error = null,
                    loadMoreError = null,
                    items = emptyList(),
                    nextOffset = null,
                    hasMore = false
                )
            )
        }
        fetchReplyThread(
            subject = subject,
            rootRpid = thread.root.rpid,
            sort = nextSort,
            offset = "",
            append = false
        )
    }

    fun translateReply(rpid: Long) {
        val state = _uiState.value
        val subject = state.subject ?: return
        if (rpid in state.busyReplyIds) return
        _uiState.update {
            it.copy(busyReplyIds = it.busyReplyIds + rpid)
        }
        viewModelScope.launch {
            val result = runCatching {
                repo.fetchTranslatedReply(subject, rpid)
            }
            if (_uiState.value.subject != subject) return@launch
            _uiState.update {
                it.copy(busyReplyIds = it.busyReplyIds - rpid)
            }
            result.fold(
                onSuccess = { translated ->
                    when {
                        translated == null -> _msg.tryEmit("评论已不存在")
                        translated.isBlank() -> _msg.tryEmit("切换语言为英文以支持中文翻译") // TODO:支持动态 x-bili-locale-bin, 从而实现汉译英汉,英译日等
                        else -> {
                            _uiState.update { cur ->
                                cur.copy(
                                    items = updateTranslatedReplies(cur.items, rpid, translated),
                                    threadPane = cur.threadPane?.let { thread ->
                                        thread.copy(
                                            root = if (thread.root.rpid == rpid) {
                                                thread.root.copy(translatedMessage = translated)
                                            } else {
                                                thread.root
                                            },
                                            items = updateTranslatedReplies(
                                                thread.items,
                                                rpid,
                                                translated
                                            )
                                        )
                                    }
                                )
                            }
                        }
                    }
                },
                onFailure = { err ->
                    _msg.tryEmit(err.message ?: "评论翻译失败")
                }
            )
        }
    }

    fun deleteReply(reply: CommentReply) {
        val state = _uiState.value
        val subject = state.subject ?: return
        if (reply.rpid in state.busyReplyIds) return
        _uiState.update {
            it.copy(busyReplyIds = it.busyReplyIds + reply.rpid)
        }
        viewModelScope.launch {
            val result = runCatching {
                repo.deleteReply(subject, reply.rpid)
            }
            if (_uiState.value.subject != subject) return@launch
            _uiState.update {
                it.copy(busyReplyIds = it.busyReplyIds - reply.rpid)
            }
            result.fold(
                onSuccess = {
                    _uiState.update { cur -> removeDeletedReply(cur, reply) }
                    _msg.tryEmit("评论已删除")
                },
                onFailure = { err ->
                    _msg.tryEmit(err.message ?: "删除评论失败")
                }
            )
        }
    }

    fun openEditor() {
        val state = _uiState.value
        if (state.subject == null) return
        if (state.currentMid <= 0L) {
            _msg.tryEmit("请先登录")
            return
        }
        showEditor(
            state = state,
            target = state.threadPane?.let { thread ->
                CommentEditorTarget(
                    rootRpid = thread.root.rpid,
                    parentRpid = thread.root.rpid,
                    parentName = thread.root.user.name
                )
            } ?: CommentEditorTarget()
        )
    }

    fun replyTo(reply: CommentReply) {
        val state = _uiState.value
        if (state.subject == null) return
        if (state.currentMid <= 0L) {
            _msg.tryEmit("请先登录")
            return
        }
        showEditor(
            state = state,
            target = replyTarget(state, reply)
        )
    }

    fun dismissEditor() {
        val editor = _uiState.value.editor
        if (editor.loading || !editor.visible) return
        _uiState.update {
            it.copy(editor = editor.copy(visible = false))
        }
    }

    fun updateEditorInput(value: String) {
        _uiState.update {
            it.copy(editor = it.editor.copy(input = value))
        }
    }

    fun submitEditor() {
        val state = _uiState.value
        val subject = state.subject ?: return
        if (state.currentMid <= 0L) {
            _msg.tryEmit("请先登录")
            return
        }
        val editor = state.editor
        if (editor.loading) return
        val message = editor.input.trim()
        if (message.isBlank()) {
            _msg.tryEmit("评论内容不能为空")
            return
        }
        val callId = reqId
        val target = editor.target
        val sort = state.threadPane
            ?.takeIf { it.root.rpid == target.rootRpid && target.rootRpid > 0L }
            ?.sort
            ?: state.sort
        _uiState.update {
            it.copy(editor = editor.copy(loading = true))
        }
        viewModelScope.launch {
            val publishResult = runCatching {
                repo.publishReply(
                    subject = subject,
                    message = message,
                    rootRpid = target.rootRpid,
                    parentRpid = target.parentRpid,
                    sort = sort
                )
            }
            if (callId != reqId || _uiState.value.subject != subject) return@launch
            publishResult.fold(
                onSuccess = { published ->
                    val insertedReply = if (target.isReply && published.parentName.isNullOrBlank()) {
                        published.copy(parentName = target.parentName)
                    } else {
                        published
                    }
                    _uiState.update {
                        insertPublishedReply(
                            state = it.copy(editor = CommentEditorState()),
                            target = target,
                            reply = insertedReply
                        )
                    }
                    _msg.tryEmit(if (target.isReply) "回复已发送" else "评论已发送")
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(editor = it.editor.copy(loading = false))
                    }
                    _msg.tryEmit(err.message ?: "发送评论失败")
                }
            )
        }
    }

    private fun refresh(
        subject: CommentSubject,
        sort: CommentSort,
        filter: String
    ) {
        val callId = reqId + 1L
        reqId = callId
        _uiState.update {
            it.copy(
                subject = subject,
                loading = true,
                loadingMore = false,
                error = null,
                loadMoreError = null,
                sort = sort,
                selectedFilter = filter,
                items = emptyList(),
                threadPane = null,
                editor = CommentEditorState(),
                nextOffset = null,
                hasMore = false,
                endText = null
            )
        }
        viewModelScope.launch {
            val result = runCatching {
                repo.fetchMainPage(
                    subject = subject,
                    sort = sort,
                    filterTag = filter
                )
            }
            if (callId != reqId) return@launch
            result.fold(
                onSuccess = { page ->
                    _uiState.value = page.toUiState(currentMid = _uiState.value.currentMid)
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            loading = false,
                            error = err.message ?: "加载评论失败"
                        )
                    }
                }
            )
        }
    }

    private fun fetchReplyThread(
        subject: CommentSubject,
        rootRpid: Long,
        sort: CommentSort,
        offset: String,
        append: Boolean
    ) {
        val callId = reqId
        viewModelScope.launch {
            val result = runCatching {
                repo.fetchReplyDetail(
                    subject = subject,
                    rootRpid = rootRpid,
                    sort = sort,
                    offset = offset
                )
            }
            if (callId != reqId || _uiState.value.subject != subject) return@launch
            result.fold(
                onSuccess = { page ->
                    _uiState.update { cur ->
                        val thread = cur.threadPane ?: return@update cur
                        if (thread.root.rpid != rootRpid && thread.root.rpid != page.root.rpid) {
                            return@update cur
                        }
                        val items = if (append) {
                            mergeReplies(thread.items, page.items)
                        } else {
                            keepTranslatedReplies(thread.items, page.items)
                        }
                        cur.copy(
                            threadPane = thread.copy(
                                root = keepTranslatedReply(thread.root, page.root),
                                count = page.count,
                                sort = page.sort,
                                canSwitchSort = page.canSwitchSort,
                                loading = false,
                                loadingMore = false,
                                error = null,
                                loadMoreError = null,
                                items = items,
                                nextOffset = page.nextOffset,
                                hasMore = page.hasMore
                            )
                        )
                    }
                },
                onFailure = { err ->
                    val msg = err.message ?: if (append) {
                        "加载更多回复失败"
                    } else {
                        "加载回复失败"
                    }
                    _uiState.update { cur ->
                        val thread = cur.threadPane ?: return@update cur
                        if (thread.root.rpid != rootRpid) return@update cur
                        cur.copy(
                            threadPane = if (append) {
                                thread.copy(
                                    loading = false,
                                    loadingMore = false,
                                    loadMoreError = msg
                                )
                            } else {
                                thread.copy(
                                    loading = false,
                                    loadingMore = false,
                                    error = msg
                                )
                            }
                        )
                    }
                }
            )
        }
    }

    private fun showEditor(
        state: CommentUiState,
        target: CommentEditorTarget
    ) {
        if (state.editor.loading) return
        _uiState.update { cur ->
            val reuseInput = if (cur.editor.target == target) cur.editor.input else ""
            cur.copy(
                editor = CommentEditorState(
                    visible = true,
                    input = reuseInput,
                    target = target
                )
            )
        }
    }

    private fun replyTarget(
        state: CommentUiState,
        reply: CommentReply
    ): CommentEditorTarget {
        val thread = state.threadPane
        return if (thread == null || thread.root.rpid == reply.rpid) {
            CommentEditorTarget(
                rootRpid = reply.rpid,
                parentRpid = reply.rpid,
                parentName = reply.user.name
            )
        } else {
            CommentEditorTarget(
                rootRpid = thread.root.rpid,
                parentRpid = reply.rpid,
                parentName = reply.user.name
            )
        }
    }

    private fun CommentPage.toUiState(currentMid: Long): CommentUiState {
        return CommentUiState(
            subject = subject,
            currentMid = currentMid,
            loading = false,
            title = title,
            count = count,
            sort = sort,
            canSwitchSort = canSwitchSort,
            filterTags = filterTags,
            selectedFilter = selectedFilter,
            items = items,
            nextOffset = nextOffset,
            hasMore = hasMore,
            endText = endText
        )
    }

    private fun mergeReplies(
        current: List<CommentReply>,
        append: List<CommentReply>
    ): List<CommentReply> {
        if (current.isEmpty()) return append
        if (append.isEmpty()) return current
        val items = LinkedHashMap<Long, CommentReply>(current.size + append.size)
        current.forEach { reply -> items[reply.rpid] = reply }
        append.forEach { reply ->
            val cur = items[reply.rpid]
            items[reply.rpid] = if (cur == null) {
                reply
            } else {
                reply.copy(translatedMessage = cur.translatedMessage ?: reply.translatedMessage)
            }
        }
        return items.values.toList()
    }

    private fun keepTranslatedReplies(
        current: List<CommentReply>,
        loaded: List<CommentReply>
    ): List<CommentReply> {
        if (current.isEmpty()) return loaded
        if (loaded.isEmpty()) return emptyList()
        val currentMap = current.associateBy { it.rpid }
        return loaded.map { reply ->
            val old = currentMap[reply.rpid]
            reply.copy(translatedMessage = old?.translatedMessage ?: reply.translatedMessage)
        }
    }

    private fun keepTranslatedReply(
        current: CommentReply,
        loaded: CommentReply
    ): CommentReply {
        return loaded.copy(translatedMessage = current.translatedMessage ?: loaded.translatedMessage)
    }

    private fun updateTranslatedReplies(
        items: List<CommentReply>,
        rpid: Long,
        translated: String
    ): List<CommentReply> {
        if (items.isEmpty()) return items
        var changed = false
        val next = items.map { reply ->
            if (reply.rpid == rpid) {
                changed = true
                reply.copy(translatedMessage = translated)
            } else {
                reply
            }
        }
        return if (changed) next else items
    }

    private fun insertPublishedReply(
        state: CommentUiState,
        target: CommentEditorTarget,
        reply: CommentReply
    ): CommentUiState {
        val nextCount = state.count + 1L
        if (!target.isReply) {
            val insertIndex = state.items.indexOfFirst { it.topLabel == null }
                .let { index -> if (index >= 0) index else state.items.size }
            return state.copy(
                count = nextCount,
                items = state.items.toMutableList().apply {
                    add(insertIndex, reply)
                }
            )
        }

        val thread = state.threadPane
        if (thread != null && thread.root.rpid == target.rootRpid) {
            val nextRoot = thread.root.copy(
                replyCount = thread.root.replyCount + 1L,
                replyEntryText = null
            )
            val insertIndex = if (target.parentRpid == target.rootRpid) {
                0
            } else {
                (thread.items.indexOfFirst { it.rpid == target.parentRpid } + 1).coerceAtLeast(0)
            }
            return state.copy(
                count = nextCount,
                items = state.items.map { item ->
                    if (item.rpid == nextRoot.rpid) {
                        nextRoot
                    } else {
                        item
                    }
                },
                threadPane = thread.copy(
                    root = nextRoot,
                    count = thread.count + 1L,
                    items = thread.items.toMutableList().apply {
                        add(insertIndex.coerceAtMost(size), reply)
                    }
                )
            )
        }

        return state.copy(
            count = nextCount,
            items = state.items.map { item ->
                if (item.rpid == target.rootRpid) {
                    item.copy(
                        replyCount = item.replyCount + 1L,
                        replyEntryText = null
                    )
                } else {
                    item
                }
            }
        )
    }

    private fun removeDeletedReply(
        state: CommentUiState,
        reply: CommentReply
    ): CommentUiState {
        val thread = state.threadPane
        if (thread != null && thread.root.rpid != reply.rpid) {
            val nextThreadItems = thread.items.filterNot { it.rpid == reply.rpid }
            if (nextThreadItems.size != thread.items.size) {
                val nextRoot = thread.root.copy(
                    replyCount = (thread.root.replyCount - 1L).coerceAtLeast(0L),
                    replyEntryText = null
                )
                return state.copy(
                    count = (state.count - 1L).coerceAtLeast(0L),
                    items = state.items.map { item ->
                        if (item.rpid == nextRoot.rpid) {
                            nextRoot
                        } else {
                            item
                        }
                    },
                    threadPane = thread.copy(
                        root = nextRoot,
                        count = (thread.count - 1L).coerceAtLeast(0L),
                        items = nextThreadItems
                    )
                )
            }
        }

        val nextItems = state.items.filterNot { it.rpid == reply.rpid }
        val removedFromMain = nextItems.size != state.items.size
        val isThreadRoot = thread?.root?.rpid == reply.rpid
        if (!removedFromMain && !isThreadRoot) return state
        return state.copy(
            count = (state.count - 1L).coerceAtLeast(0L),
            items = nextItems,
            threadPane = if (isThreadRoot) null else thread
        )
    }
}
