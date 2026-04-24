package com.naaammme.bbspace.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naaammme.bbspace.core.domain.auth.AuthRepository
import com.naaammme.bbspace.core.data.AppSettings
import com.naaammme.bbspace.core.designsystem.theme.AnimationSpeed
import com.naaammme.bbspace.core.designsystem.theme.CornerStyle
import com.naaammme.bbspace.core.designsystem.theme.FrameRateMode
import com.naaammme.bbspace.core.designsystem.theme.ThemeConfig
import com.naaammme.bbspace.core.designsystem.theme.ThemeMode
import com.naaammme.bbspace.core.designsystem.theme.TransitionStyle
import androidx.compose.ui.graphics.Color
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appSettings: AppSettings,
    private val authRepo: AuthRepository
) : ViewModel() {

    val hdFeedAvailable = MutableStateFlow(false)

    val themeConfig = appSettings.themeConfig.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        ThemeConfig()
    )

    val hdFeed = appSettings.hdFeed.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        false
    )

    val personalizedRcmd = appSettings.personalizedRcmd.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        true
    )

    val lessonsMode = appSettings.lessonsMode.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        false
    )

    val teenagersMode = appSettings.teenagersMode.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        false
    )

    val teenagersAge = appSettings.teenagersAge.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        16
    )

    fun updateThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            appSettings.updateThemeMode(mode)
        }
    }

    fun updateSeedColor(color: Color) {
        viewModelScope.launch {
            appSettings.updateSeedColor(color)
        }
    }

    fun updateUseDynamicColor(use: Boolean) {
        viewModelScope.launch {
            appSettings.updateUseDynamicColor(use)
        }
    }

    fun updateFontScale(scale: Float) {
        viewModelScope.launch {
            appSettings.updateFontScale(scale)
        }
    }

    fun updateAnimationSpeed(speed: AnimationSpeed) {
        viewModelScope.launch {
            appSettings.updateAnimationSpeed(speed)
        }
    }

    fun updateTransitionStyle(style: TransitionStyle) {
        viewModelScope.launch {
            appSettings.updateTransitionStyle(style)
        }
    }

    fun updateIsPureBlack(isPure: Boolean) {
        viewModelScope.launch {
            appSettings.updateIsPureBlack(isPure)
        }
    }

    fun updateFrameRateMode(mode: FrameRateMode) {
        viewModelScope.launch {
            appSettings.updateFrameRateMode(mode)
        }
    }

    fun updateCornerStyle(style: CornerStyle) {
        viewModelScope.launch {
            appSettings.updateCornerStyle(style)
        }
    }

    fun updateHdFeed(enabled: Boolean) {
        viewModelScope.launch {
            refreshHdFeedAvailable()
            if (enabled && !hdFeedAvailable.value) {
                appSettings.updateHdFeed(false)
            } else {
                appSettings.updateHdFeed(enabled)
            }
        }
    }

    fun updatePersonalizedRcmd(enabled: Boolean) {
        viewModelScope.launch {
            appSettings.updatePersonalizedRcmd(enabled)
        }
    }

    fun updateLessonsMode(enabled: Boolean) {
        viewModelScope.launch {
            appSettings.updateLessonsMode(enabled)
        }
    }

    fun updateTeenagersMode(enabled: Boolean) {
        viewModelScope.launch {
            appSettings.updateTeenagersMode(enabled)
        }
    }

    fun updateTeenagersAge(age: Int) {
        viewModelScope.launch {
            appSettings.updateTeenagersAge(age)
        }
    }

    fun refreshHdFeedAvailable() {
        val available = authRepo.hasHdAccessKeyForCurrent()
        hdFeedAvailable.value = available
        if (!available) {
            viewModelScope.launch {
                appSettings.updateHdFeed(false)
            }
        }
    }

    fun resetAllSettings() {
        viewModelScope.launch {
            appSettings.resetAllSettings()
        }
    }
}
