package com.naaammme.bbspace.feature.auth.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.naaammme.bbspace.feature.auth.AccountScreen
import com.naaammme.bbspace.feature.auth.LoginScreen
import com.naaammme.bbspace.feature.auth.SmsLoginScreen

const val LOGIN_ROUTE = "login"
const val SMS_LOGIN_ROUTE = "sms_login"
const val ACCOUNT_ROUTE = "account"

fun NavController.navigateToLogin() {
    navigate(LOGIN_ROUTE)
}

fun NavController.navigateToSmsLogin() {
    navigate(SMS_LOGIN_ROUTE)
}

fun NavController.navigateToAccount() {
    navigate(ACCOUNT_ROUTE)
}

fun NavGraphBuilder.loginScreen(
    onLoginSuccess: () -> Unit,
    onBack: () -> Unit,
    onSwitchToSms: () -> Unit = {}
) {
    composable(route = LOGIN_ROUTE) {
        LoginScreen(
            onLoginSuccess = onLoginSuccess,
            onBack = onBack,
            onSwitchToSms = onSwitchToSms
        )
    }
}

fun NavGraphBuilder.smsLoginScreen(
    onLoginSuccess: () -> Unit,
    onBack: () -> Unit,
    onSwitchToQr: () -> Unit = {}
) {
    composable(route = SMS_LOGIN_ROUTE) {
        SmsLoginScreen(
            onLoginSuccess = onLoginSuccess,
            onBack = onBack,
            onSwitchToQr = onSwitchToQr
        )
    }
}

fun NavGraphBuilder.accountScreen(
    onBack: () -> Unit,
    onAddAccount: () -> Unit,
    onSwitched: () -> Unit = {}
) {
    composable(route = ACCOUNT_ROUTE) {
        AccountScreen(onBack = onBack, onAddAccount = onAddAccount, onSwitched = onSwitched)
    }
}
