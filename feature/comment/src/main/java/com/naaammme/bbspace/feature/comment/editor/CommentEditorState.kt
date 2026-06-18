package com.naaammme.bbspace.feature.comment.editor

import androidx.compose.runtime.Immutable

@Immutable
data class CommentEditorState(
    val visible: Boolean = false,
    val loading: Boolean = false,
    val input: String = "",
    val target: CommentEditorTarget = CommentEditorTarget()
)

@Immutable
data class CommentEditorTarget(
    val rootRpid: Long = 0L,
    val parentRpid: Long = 0L
) {
    val isReply: Boolean
        get() = rootRpid > 0L
}
