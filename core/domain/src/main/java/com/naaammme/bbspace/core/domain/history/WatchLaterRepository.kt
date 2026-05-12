package com.naaammme.bbspace.core.domain.history

import com.naaammme.bbspace.core.model.WatchLaterCursor
import com.naaammme.bbspace.core.model.WatchLaterPage
import com.naaammme.bbspace.core.model.WatchLaterTab

interface WatchLaterRepository {
    suspend fun fetchPage(
        tab: WatchLaterTab,
        asc: Boolean,
        cursor: WatchLaterCursor = WatchLaterCursor()
    ): WatchLaterPage
}
