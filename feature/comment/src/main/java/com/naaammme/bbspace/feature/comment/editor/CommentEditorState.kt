package com.naaammme.bbspace.feature.comment.editor

import android.net.Uri
import androidx.compose.runtime.Immutable

const val COMMENT_EDITOR_MAX_IMAGE_COUNT = 9

@Immutable
data class CommentEditorState(
    val visible: Boolean = false,
    val loading: Boolean = false,
    val uploading: Boolean = false,
    val input: String = "",
    val selectedImageUris: List<Uri> = emptyList(),
    val target: CommentEditorTarget = CommentEditorTarget()
)

@Immutable
data class CommentEditorTarget(
    val rootRpid: Long = 0L,
    val parentRpid: Long = 0L,
    val parentName: String? = null
) {
    val isReply: Boolean
        get() = rootRpid > 0L
}
