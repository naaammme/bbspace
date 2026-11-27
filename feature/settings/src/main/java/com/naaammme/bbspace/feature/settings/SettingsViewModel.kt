package com.naaammme.bbspace.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naaammme.bbspace.core.settings.SettingStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingStore
) : ViewModel() {

    fun resetAllSettings() {
        viewModelScope.launch {
            settings.resetAllSettings()
        }
    }
}
