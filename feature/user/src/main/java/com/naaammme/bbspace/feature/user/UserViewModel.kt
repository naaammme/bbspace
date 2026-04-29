package com.naaammme.bbspace.feature.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naaammme.bbspace.core.domain.auth.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UserViewModel @Inject constructor(
    private val authRepo: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(UserUiState())
    val uiState: StateFlow<UserUiState> = _uiState.asStateFlow()

    init {
        _uiState.update { it.copy(user = authRepo.getUserInfo()) }
        viewModelScope.launch {
            authRepo.currentMidFlow
                .collect { mid ->
                    if (mid > 0) {
                        fetchMineInfo()
                    } else {
                        _uiState.value = UserUiState()
                    }
                }
        }
    }

    fun fetchMineInfo() {
        val credential = authRepo.getSavedCredential() ?: return
        viewModelScope.launch {
            authRepo.fetchMineInfo(credential).onSuccess {
                if (it.mid == 0L) {
                    _uiState.update { state ->
                        state.copy(
                            user = null,
                            showAccountExpiredDialog = true
                        )
                    }
                } else {
                    _uiState.update { state ->
                        state.copy(
                            user = it,
                            showAccountExpiredDialog = false
                        )
                    }
                }
            }
        }
    }

    fun dismissAccountExpiredDialog() {
        _uiState.update { it.copy(showAccountExpiredDialog = false) }
    }
}
