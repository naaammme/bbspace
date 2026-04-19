package com.naaammme.bbspace.core.domain.live

import com.naaammme.bbspace.core.model.LiveRecommendPage

interface LiveRecommendRepository {
    suspend fun fetchRecommendPage(
        page: Int,
        relationPage: Int,
        isRefresh: Boolean,
        loginEvent: Int
    ): LiveRecommendPage
}
