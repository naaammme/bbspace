package com.naaammme.bbspace.core.domain.listen

import com.naaammme.bbspace.core.model.listen.ListenPlayInfo
import com.naaammme.bbspace.core.model.listen.ListenRcmdResult

interface ListenRepository {
    suspend fun fetchRcmdPlaylist(needTopCards: Boolean): ListenRcmdResult
    suspend fun fetchRcmdPlaylistNext(nextToken: String): ListenRcmdResult
    suspend fun fetchPlayUrl(oid: Long, itemType: Int, subId: Long): ListenPlayInfo
}
