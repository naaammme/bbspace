package com.naaammme.bbspace.feature.user

import androidx.compose.runtime.Immutable
import com.naaammme.bbspace.core.model.User

@Immutable
data class UserUiState(
    val user: User? = null,
    val showAccountExpiredDialog: Boolean = false
)
