package com.naaammme.bbspace.feature.user.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naaammme.bbspace.core.domain.auth.AuthRepository
import com.naaammme.bbspace.core.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UserViewModel @Inject constructor(
    private val authRepo: AuthRepository
) : ViewModel() {

    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user.asStateFlow()

    init {
        _user.value = authRepo.getUserInfo()
        viewModelScope.launch {
            authRepo.currentMidFlow
                .collect { mid ->
                    if (mid > 0) fetchMineInfo()
                    else _user.value = null
                }
        }
    }

    fun fetchMineInfo() {
        val credential = authRepo.getSavedCredential() ?: return
        viewModelScope.launch {
            authRepo.fetchMineInfo(credential).onSuccess { _user.value = it }
        }
    }
}
