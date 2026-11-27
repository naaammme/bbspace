package com.naaammme.bbspace.feature.settings.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naaammme.bbspace.core.auth.AuthRepository
import com.naaammme.bbspace.core.settings.FeedPrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class FeedSettingsViewModel @Inject constructor(
    internal val feedPrefs: FeedPrefs,
    private val authRepo: AuthRepository
) : ViewModel() {

    private val _hdFeedAvailable = MutableStateFlow(false)
    val hdFeedAvailable: StateFlow<Boolean> = _hdFeedAvailable.asStateFlow()

    val hdFeed = feedPrefs.hdFeed.stateIn(viewModelScope)

    fun refreshHdFeedAvailable() {
        val available = checkAvailability()
        if (!available) {
            viewModelScope.launch {
                if (feedPrefs.hdFeed.get()) {
                    feedPrefs.hdFeed.set(false)
                }
            }
        }
    }

    fun updateHdFeed(enabled: Boolean) {
        val available = checkAvailability()
        viewModelScope.launch {
            feedPrefs.hdFeed.set(enabled && available)
        }
    }

    private fun checkAvailability(): Boolean {
        val available = authRepo.hasHdAccessKeyForCurrent()
        _hdFeedAvailable.value = available
        return available
    }
}
