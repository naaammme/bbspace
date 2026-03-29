package com.naaammme.bbspace.core.domain.feed

import com.naaammme.bbspace.core.model.FeedItem
import com.naaammme.bbspace.core.model.FeedToast
import com.naaammme.bbspace.core.model.InterestChoose
import kotlinx.coroutines.flow.SharedFlow

data class FeedResult(val items: List<FeedItem>, val toast: FeedToast?, val interestChoose: InterestChoose? = null)

interface FeedRepository {
    val toastFlow: SharedFlow<FeedToast>
    suspend fun fetchFeed(idx: Long, pull: Boolean, flush: Int): FeedResult
    suspend fun fetchFeedWithInterest(
        idx: Long,
        pull: Boolean,
        flush: Int,
        interestId: Int,
        interestResult: String,
        interestPosIds: String
    ): FeedResult
}
