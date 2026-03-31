package com.naaammme.bbspace.feature.settings.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naaammme.bbspace.core.data.AppSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerSettingsViewModel @Inject constructor(
    private val appSettings: AppSettings
) : ViewModel() {

    val minBufferMs = appSettings.playerMinBufferMs.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = 2_000
    )

    val maxBufferMs = appSettings.playerMaxBufferMs.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = 15_000
    )

    val playbackBufferMs = appSettings.playerPlaybackBufferMs.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = 250
    )

    val rebufferMs = appSettings.playerRebufferMs.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = 500
    )

    val backBufferMs = appSettings.playerBackBufferMs.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = 5_000
    )

    val preferSoftwareDecode = appSettings.preferSoftwareDecode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = false
    )

    val decoderFallback = appSettings.decoderFallback.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = true
    )

    val backgroundPlayback = appSettings.backgroundPlayback.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = false
    )

    fun updateMinBufferMs(value: Int) {
        viewModelScope.launch {
            appSettings.updatePlayerMinBufferMs(value)
        }
    }

    fun updateMaxBufferMs(value: Int) {
        viewModelScope.launch {
            appSettings.updatePlayerMaxBufferMs(value)
        }
    }

    fun updatePlaybackBufferMs(value: Int) {
        viewModelScope.launch {
            appSettings.updatePlayerPlaybackBufferMs(value)
        }
    }

    fun updateRebufferMs(value: Int) {
        viewModelScope.launch {
            appSettings.updatePlayerRebufferMs(value)
        }
    }

    fun updateBackBufferMs(value: Int) {
        viewModelScope.launch {
            appSettings.updatePlayerBackBufferMs(value)
        }
    }

    fun updatePreferSoftwareDecode(enabled: Boolean) {
        viewModelScope.launch {
            appSettings.updatePreferSoftwareDecode(enabled)
        }
    }

    fun updateDecoderFallback(enabled: Boolean) {
        viewModelScope.launch {
            appSettings.updateDecoderFallback(enabled)
        }
    }

    fun updateBackgroundPlayback(enabled: Boolean) {
        viewModelScope.launch {
            appSettings.updateBackgroundPlayback(enabled)
        }
    }
}
