package com.naaammme.bbspace.feature.settings.audioVideo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naaammme.bbspace.core.data.AppSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AudioVideoSettingsViewModel @Inject constructor(
    private val appSettings: AppSettings
) : ViewModel() {

    val enableHdrAnd8k = appSettings.enableHdrAnd8k.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = false
    )

    val defaultVideoQuality = appSettings.defaultVideoQuality.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = 80
    )

    val defaultAudioQuality = appSettings.defaultAudioQuality.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = 0
    )

    val forceHost = appSettings.forceHost.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = 0
    )

    val needTrial = appSettings.needTrial.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = false
    )

    val preferredCodec = appSettings.preferredCodec.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = 2
    )

    fun updateEnableHdrAnd8k(enabled: Boolean) {
        viewModelScope.launch {
            appSettings.updateEnableHdrAnd8k(enabled)
        }
    }

    fun updateDefaultVideoQuality(quality: Int) {
        viewModelScope.launch {
            appSettings.updateDefaultVideoQuality(quality)
        }
    }

    fun updateDefaultAudioQuality(quality: Int) {
        viewModelScope.launch {
            appSettings.updateDefaultAudioQuality(quality)
        }
    }

    fun updateForceHost(value: Int) {
        viewModelScope.launch {
            appSettings.updateForceHost(value)
        }
    }

    fun updateNeedTrial(enabled: Boolean) {
        viewModelScope.launch {
            appSettings.updateNeedTrial(enabled)
        }
    }

    fun updatePreferredCodec(codec: Int) {
        viewModelScope.launch {
            appSettings.updatePreferredCodec(codec)
        }
    }
}
