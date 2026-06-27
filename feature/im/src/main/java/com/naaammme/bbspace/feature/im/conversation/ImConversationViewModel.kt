package com.naaammme.bbspace.feature.im.conversation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naaammme.bbspace.core.common.log.Logger
import com.naaammme.bbspace.core.im.ImRepository
import com.naaammme.bbspace.core.model.ImConversationPage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ImConversationViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val imRepo: ImRepository
) : ViewModel() {

    private val talkerId = savedStateHandle.get<Long>("talkerId") ?: 0L
    private val sessionType = savedStateHandle.get<Int>("sessionType") ?: 0
    private val routeTitle = savedStateHandle.get<String>("title").orEmpty()
    private val routeAvatar = savedStateHandle.get<String>("avatar")?.takeIf(String::isNotBlank)

    private val _uiState = MutableStateFlow(ImConversationUiState())
    val uiState: StateFlow<ImConversationUiState> = _uiState.asStateFlow()

    init {
        _uiState.value = ImConversationUiState(
            title = routeTitle,
            avatar = routeAvatar
        )
        refresh()
    }

    fun refresh() {
        val state = _uiState.value
        if (state.isRefreshing || state.isLoadingMore) return
        _uiState.update {
            it.copy(
                isLoading = it.messages.isEmpty(),
                isRefreshing = it.messages.isNotEmpty()
            )
        }
        viewModelScope.launch {
            try {
                val page = imRepo.fetchConversation(
                    talkerId = talkerId,
                    sessionType = sessionType
                )
                applyConversation(page)
                reportAck(page.messages.maxOfOrNull { it.seqNo })
            } catch (_: Throwable) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false
                    )
                }
            }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isRefreshing || state.isLoadingMore || !state.hasMoreHistory) return
        val beforeSeqNo = state.messages.lastOrNull()?.seqNo ?: return
        _uiState.update {
            it.copy(
                isLoadingMore = true
            )
        }
        viewModelScope.launch {
            try {
                val page = imRepo.fetchOlderMessages(
                    talkerId = talkerId,
                    sessionType = sessionType,
                    beforeSeqNo = beforeSeqNo
                )
                applyOlderMessages(page)
            } catch (_: Throwable) {
                _uiState.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    fun clearSendError() {
        _uiState.update { it.copy(sendErrorMessage = null) }
    }

    fun sendMessage(text: String) {
        val state = _uiState.value
        if (state.isSending || text.isBlank()) return
        if (talkerId <= 0L || sessionType <= 0) {
            _uiState.update { it.copy(sendErrorMessage = "会话信息无效") }
            return
        }
        _uiState.update { it.copy(isSending = true, sendErrorMessage = null) }
        viewModelScope.launch {
            val result = runCatching {
                imRepo.sendConversationMessage(
                    talkerId = talkerId,
                    sessionType = sessionType,
                    text = text
                )
            }
            result.fold(
                onSuccess = { message ->
                    _uiState.update { cur ->
                        cur.copy(
                            messages = listOf(message) + cur.messages,
                            isSending = false,
                            sendErrorMessage = null,
                            lastSentMessageKey = message.key
                        )
                    }
                },
                onFailure = { err ->
                    Logger.e(TAG, err) {
                        "send im message failed talkerId=$talkerId sessionType=$sessionType"
                    }
                    _uiState.update {
                        it.copy(
                            isSending = false,
                            sendErrorMessage = err.message ?: "发送消息失败"
                        )
                    }
                }
            )
        }
    }

    private fun reportAck(seqNo: Long?) {
        val ackSeqNo = seqNo?.takeIf { it > 0L } ?: return
        viewModelScope.launch {
            imRepo.updateAck(
                talkerId = talkerId,
                sessionType = sessionType,
                ackSeqNo = ackSeqNo
            )
        }
    }

    private fun applyConversation(
        page: ImConversationPage
    ) {
        _uiState.update { state ->
            val messages = page.messages
            state.copy(
                title = state.title.ifBlank { routeTitle },
                avatar = state.avatar ?: routeAvatar,
                hasMoreHistory = page.hasMoreHistory,
                messages = messages,
                isLoading = false,
                isRefreshing = false,
                isLoadingMore = false
            )
        }
    }

    private fun applyOlderMessages(page: ImConversationPage) {
        _uiState.update { state ->
            val messages = state.messages + page.messages
            state.copy(
                hasMoreHistory = page.hasMoreHistory,
                messages = messages,
                isLoadingMore = false
            )
        }
    }

    private companion object {
        const val TAG = "ImConversationViewModel"
    }
}
