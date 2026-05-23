package com.naaammme.bbspace.core.domain

import com.naaammme.bbspace.core.model.ImConversationPage
import com.naaammme.bbspace.core.model.ImPage
import com.naaammme.bbspace.core.model.ImPaginationParams
import com.naaammme.bbspace.core.model.ImSessionTab

interface ImRepository {
    suspend fun fetchSessions(
        tab: ImSessionTab = ImSessionTab.DEFAULT,
        paginationParams: ImPaginationParams? = null
    ): ImPage

    suspend fun fetchConversation(
        talkerId: Long,
        sessionType: Int
    ): ImConversationPage

    suspend fun fetchOlderMessages(
        talkerId: Long,
        sessionType: Int,
        beforeSeqNo: Long,
        size: Int = 20
    ): ImConversationPage

    suspend fun updateAck(
        talkerId: Long,
        sessionType: Int,
        ackSeqNo: Long
    )
}
