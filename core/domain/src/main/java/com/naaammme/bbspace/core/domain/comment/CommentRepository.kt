package com.naaammme.bbspace.core.domain.comment

import com.naaammme.bbspace.core.model.COMMENT_FILTER_ALL
import com.naaammme.bbspace.core.model.CommentPage
import com.naaammme.bbspace.core.model.CommentSort
import com.naaammme.bbspace.core.model.CommentSubject

interface CommentRepository {
    suspend fun fetchMainPage(
        subject: CommentSubject,
        sort: CommentSort = CommentSort.HOT,
        filterTag: String = COMMENT_FILTER_ALL,
        offset: String = ""
    ): CommentPage

    suspend fun fetchTranslatedReply(
        subject: CommentSubject,
        rpid: Long
    ): String?
}
