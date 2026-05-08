package com.naaammme.bbspace.core.domain.dynamic

import com.naaammme.bbspace.core.model.DynamicCursor
import com.naaammme.bbspace.core.model.DynamicPage
import com.naaammme.bbspace.core.model.DynamicRefresh

interface DynamicRepository {
    suspend fun fetchAll(
        cursor: DynamicCursor,
        refresh: DynamicRefresh
    ): DynamicPage
}
