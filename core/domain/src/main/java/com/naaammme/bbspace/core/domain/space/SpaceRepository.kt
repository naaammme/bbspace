package com.naaammme.bbspace.core.domain.space

import com.naaammme.bbspace.core.model.SpaceArchivePage
import com.naaammme.bbspace.core.model.SpaceHome
import com.naaammme.bbspace.core.model.SpaceRoute

interface SpaceRepository {
    suspend fun fetchHome(route: SpaceRoute): SpaceHome

    suspend fun fetchArchive(
        mid: Long,
        order: String,
        cursorAid: Long? = null,
        fromViewAid: Long? = null
    ): SpaceArchivePage
}
