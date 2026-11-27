package com.naaammme.bbspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naaammme.bbspace.core.settings.AppearancePrefs
import com.naaammme.bbspace.core.settings.stateInWhileSubscribed
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AppShellViewModel @Inject constructor(
    appearancePrefs: AppearancePrefs
) : ViewModel() {
    val themeVisualConfig = appearancePrefs.themeVisualConfig
        .stateInWhileSubscribed(viewModelScope, appearancePrefs.currentThemeVisual())
    val animationSpeed = appearancePrefs.animationSpeed.stateIn(viewModelScope)
    val pullRefreshDistanceDp = appearancePrefs.pullRefreshDistanceDp.stateIn(viewModelScope)
    val preferredFrameRate = appearancePrefs.preferredFrameRate.stateIn(viewModelScope)
    val navTransitionConfig = appearancePrefs.navTransitionConfig
        .stateInWhileSubscribed(viewModelScope, appearancePrefs.currentNavTransition())
    val roundScreenSafePaddingScale =
        appearancePrefs.roundScreenSafePaddingScale.stateIn(viewModelScope)
    val fixBottomBar = appearancePrefs.fixBottomBar.stateIn(viewModelScope)
}
