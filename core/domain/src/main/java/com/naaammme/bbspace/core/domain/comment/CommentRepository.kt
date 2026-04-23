package com.naaammme.bbspace.core.domain.comment

import com.naaammme.bbspace.core.model.COMMENT_FILTER_ALL
import com.naaammme.bbspace.core.model.CommentPage
import com.naaammme.bbspace.core.model.CommentReply
import com.naaammme.bbspace.core.model.CommentReplyDetailPage
import com.naaammme.bbspace.core.model.CommentSort
import com.naaammme.bbspace.core.model.CommentSubject

interface CommentRepository {
    suspend fun deleteReply(
        subject: CommentSubject,
        rpid: Long
    )

    suspend fun publishReply(
        subject: CommentSubject,
        message: String,
        rootRpid: Long = 0L,
        parentRpid: Long = 0L,
        sort: CommentSort = CommentSort.HOT
    ): CommentReply

    suspend fun fetchMainPage(
        subject: CommentSubject,
        sort: CommentSort = CommentSort.HOT,
        filterTag: String = COMMENT_FILTER_ALL,
        offset: String = ""
    ): CommentPage

    suspend fun fetchReplyDetail(
        subject: CommentSubject,
        rootRpid: Long,
        sort: CommentSort = CommentSort.HOT,
        offset: String = ""
    ): CommentReplyDetailPage

    suspend fun fetchTranslatedReply(
        subject: CommentSubject,
        rpid: Long
    ): String?
}
