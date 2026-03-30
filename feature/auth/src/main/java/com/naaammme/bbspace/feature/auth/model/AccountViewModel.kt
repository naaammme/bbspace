package com.naaammme.bbspace.feature.auth.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naaammme.bbspace.core.model.LoginCredential
import com.naaammme.bbspace.core.model.User
import com.naaammme.bbspace.core.domain.auth.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val authRepo: AuthRepository
) : ViewModel() {

    private val _accounts = MutableStateFlow<List<LoginCredential>>(emptyList())
    val accounts: StateFlow<List<LoginCredential>> = _accounts.asStateFlow()

    private val _currentMid = MutableStateFlow(0L)
    val currentMid: StateFlow<Long> = _currentMid.asStateFlow()

    private val _userInfoMap = MutableStateFlow<Map<Long, User>>(emptyMap())
    val userInfoMap: StateFlow<Map<Long, User>> = _userInfoMap.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _accounts.value = authRepo.getAllAccounts()
        _currentMid.value = authRepo.getSavedCredential()?.mid ?: 0L
        _userInfoMap.value = authRepo.getAllUserInfos()
    }

    fun switchToGuest() {
        authRepo.guestMode = true
        refresh()
    }

    fun switchAccount(mid: Long) {
        authRepo.guestMode = false
        authRepo.switchAccount(mid)
        refresh()
    }

    fun removeAccount(mid: Long) {
        viewModelScope.launch {
            authRepo.removeAccount(mid)
            refresh()
        }
    }

    fun logout(credential: LoginCredential) {
        viewModelScope.launch {
            authRepo.logout(credential)
            authRepo.removeAccount(credential.mid)
            refresh()
        }
    }
}
