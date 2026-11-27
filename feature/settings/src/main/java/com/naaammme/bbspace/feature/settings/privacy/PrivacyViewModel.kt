package com.naaammme.bbspace.feature.settings.privacy

import androidx.lifecycle.ViewModel
import com.naaammme.bbspace.core.auth.AuthStore
import com.naaammme.bbspace.core.auth.CacheManager
import com.naaammme.bbspace.core.model.LoginCredential
import com.naaammme.bbspace.core.settings.SystemPrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
    systemPrefs: SystemPrefs
) : ViewModel() {

    private val _uiState = MutableStateFlow(PrivacyUiState())
    val uiState: StateFlow<PrivacyUiState> = _uiState.asStateFlow()

    internal val blockGaia = systemPrefs.blockGaia

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
            ticketInfo = cacheManager.getTicketInfo(),
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

    fun exportAllAccounts(): String = authStore.exportAllAccounts()

    fun importAccounts(json: String) {
        val result = authStore.importAccounts(json)
        _uiState.value = _uiState.value.copy(
            importResult = if (result.isNotEmpty()) "导入 ${result.size} 个账号" else "导失败你写错",
            accounts = authStore.getAllAccounts()
        )
    }

    fun clearImportResult() {
        _uiState.value = _uiState.value.copy(importResult = null)
    }

    fun clearImageCache() {
        cacheManager.clearImageCache()
    }

    fun clearAllCache() {
        cacheManager.clearAllCache()
        refresh()
    }
}
