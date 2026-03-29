package com.naaammme.bbspace.feature.settings.privacy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naaammme.bbspace.core.data.AppSettings
import com.naaammme.bbspace.core.data.AuthStore
import com.naaammme.bbspace.core.data.CacheManager
import com.naaammme.bbspace.core.model.LoginCredential
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PrivacyUiState(
    val deviceInfo: Map<String, String> = emptyMap(),
    val sessionInfo: Map<String, String> = emptyMap(),
    val ticketInfo: Map<String, String> = emptyMap(),
    val guestInfo: Map<String, String> = emptyMap(),
    val regionCode: String = "",
    val hdInfo: Map<String, String> = emptyMap(),
    val accounts: List<LoginCredential> = emptyList(),
    val currentMid: Long = 0L,
    val importResult: String? = null
)

@HiltViewModel
class PrivacyViewModel @Inject constructor(
    private val authStore: AuthStore,
    private val cacheManager: CacheManager,
    private val appSettings: AppSettings
) : ViewModel() {

    private val _uiState = MutableStateFlow(PrivacyUiState())
    val uiState: StateFlow<PrivacyUiState> = _uiState.asStateFlow()

    val blockGaia = appSettings.blockGaia.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        false
    )

    init {
        refresh()
    }

    fun refresh() {
        _uiState.value = PrivacyUiState(
            deviceInfo = cacheManager.getDeviceInfo(),
            sessionInfo = mapOf(
                "guestId" to cacheManager.guestId,
                "sessionId" to cacheManager.sessionId,
                "loginSessionId" to cacheManager.loginSessionId
            ),
            ticketInfo = mapOf("ticket" to cacheManager.getCachedTicket()),
            guestInfo = mapOf("guestId" to cacheManager.guestId),
            regionCode = cacheManager.getRegionCode(),
            hdInfo = mapOf(
                "currentMid" to authStore.mid.toString(),
                "hasHdAccessKey" to authStore.hasHdAccessKeyForCurrent().toString(),
                "hdAccessKey" to authStore.getHdAccessKeyForCurrent()
            ),
            accounts = authStore.getAllAccounts(),
            currentMid = authStore.mid
        )
    }

    fun setBlockGaia(enabled: Boolean) {
        viewModelScope.launch { appSettings.updateBlockGaia(enabled) }
    }

    fun exportAllAccounts(): String = authStore.exportAllAccounts()

    fun importAccounts(json: String) {
        val result = authStore.importAccounts(json)
        _uiState.value = _uiState.value.copy(
            importResult = if (result.isNotEmpty()) "导入 ${result.size} 个账号" else "导入失败",
            accounts = authStore.getAllAccounts()
        )
    }

    fun clearImportResult() {
        _uiState.value = _uiState.value.copy(importResult = null)
    }

    fun clearTicketCache() {
        cacheManager.clearTicketCache()
        refresh()
    }

    fun clearGuestCache() {
        cacheManager.clearGuestCache()
        refresh()
    }

    fun clearSessionCache() {
        cacheManager.clearSession()
        refresh()
    }

    fun clearRegionCache() {
        cacheManager.clearRegionCache()
        refresh()
    }

    fun clearImageCache() {
        cacheManager.clearImageCache()
    }

    fun clearAllCache() {
        cacheManager.clearAllCache()
        refresh()
    }
}
