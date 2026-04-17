package com.naaammme.bbspace.feature.settings.about

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naaammme.bbspace.core.data.AppSettings
import com.naaammme.bbspace.core.data.update.AppUpdateCheckResult
import com.naaammme.bbspace.core.data.update.AppUpdateChecker
import com.naaammme.bbspace.core.data.update.toDialogState
import com.naaammme.bbspace.core.designsystem.component.AppUpdateDialogState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class HasUpdate(val version: String) : UpdateState
    data object Error : UpdateState
}

@HiltViewModel
class AboutViewModel @Inject constructor(
    private val appSettings: AppSettings,
    private val updateChecker: AppUpdateChecker
) : ViewModel() {

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState

    private val _updateDialog = MutableStateFlow<AppUpdateDialogState?>(null)
    val updateDialog: StateFlow<AppUpdateDialogState?> = _updateDialog.asStateFlow()

    val autoCheckUpdate = appSettings.autoCheckUpdate.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        true
    )

    fun checkUpdate() {
        if (_updateState.value == UpdateState.Checking) return
        _updateState.value = UpdateState.Checking
        viewModelScope.launch {
            updateChecker.check().fold(
                onSuccess = { result ->
                    when (result) {
                        is AppUpdateCheckResult.UpToDate -> {
                            _updateState.value = UpdateState.UpToDate
                            _updateDialog.value = result.toDialogState()
                        }
                        is AppUpdateCheckResult.HasUpdate -> {
                            _updateState.value = UpdateState.HasUpdate(
                                version = result.release.version
                            )
                            _updateDialog.value = result.toDialogState()
                        }
                    }
                },
                onFailure = {
                    _updateState.value = UpdateState.Error
                    _updateDialog.value = it.toDialogState()
                }
            )
        }
    }

    fun updateAutoCheckEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appSettings.updateAutoCheckEnabled(enabled)
        }
    }

    fun dismissUpdateDialog() {
        _updateDialog.value = null
    }
}
