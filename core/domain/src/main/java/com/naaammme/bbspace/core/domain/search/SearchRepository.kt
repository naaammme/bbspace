package com.naaammme.bbspace.core.domain.search

import com.naaammme.bbspace.core.model.SearchHistoryOrder
import com.naaammme.bbspace.core.model.SearchPage
import com.naaammme.bbspace.core.model.SearchReq
import kotlinx.coroutines.flow.Flow

interface SearchRepository {
    suspend fun search(req: SearchReq): SearchPage

    suspend fun recordHistory(keyword: String)

    suspend fun deleteHistory(keyword: String)

    fun observeHistory(
        order: SearchHistoryOrder
    ): Flow<List<String>>
}
