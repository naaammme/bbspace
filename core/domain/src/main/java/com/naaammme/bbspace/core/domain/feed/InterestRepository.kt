package com.naaammme.bbspace.core.domain.feed

import com.naaammme.bbspace.core.model.UinterestResponse

interface InterestRepository {
    suspend fun fetchUinterest(): UinterestResponse
}
