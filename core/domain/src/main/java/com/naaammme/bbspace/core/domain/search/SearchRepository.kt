package com.naaammme.bbspace.core.domain.search

import com.naaammme.bbspace.core.model.SearchPage
import com.naaammme.bbspace.core.model.SearchReq

interface SearchRepository {
    suspend fun search(req: SearchReq): SearchPage
}
