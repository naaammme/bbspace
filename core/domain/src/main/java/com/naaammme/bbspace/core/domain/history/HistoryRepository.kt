package com.naaammme.bbspace.core.domain.history

import com.naaammme.bbspace.core.model.HistoryCursor
import com.naaammme.bbspace.core.model.HistoryPage
import com.naaammme.bbspace.core.model.HistoryTab

interface HistoryRepository {
    suspend fun fetchPage(
        tab: HistoryTab,
        cursor: HistoryCursor = HistoryCursor()
    ): HistoryPage
}
