package com.naaammme.bbspace.core.settings

import com.naaammme.bbspace.infra.crypto.BiliSessionId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class PageActionTracker @Inject constructor() {
    private val _actionId = MutableStateFlow(BiliSessionId.polarisAction())
    val actionId: StateFlow<String> = _actionId.asStateFlow()

    fun currentActionId(): String {
        return _actionId.value
    }

    fun refresh() {
        _actionId.value = BiliSessionId.polarisAction()
    }
}
