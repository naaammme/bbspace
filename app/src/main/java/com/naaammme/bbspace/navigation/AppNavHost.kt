package com.naaammme.bbspace.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.naaammme.bbspace.core.designsystem.theme.ThemeConfig
import com.naaammme.bbspace.core.designsystem.theme.buildNavTransitions
import com.naaammme.bbspace.core.model.LiveRoute
import com.naaammme.bbspace.core.model.VideoRoute
import com.naaammme.bbspace.feature.auth.navigation.ACCOUNT_ROUTE
import com.naaammme.bbspace.feature.auth.navigation.LOGIN_ROUTE
import com.naaammme.bbspace.feature.auth.navigation.SMS_LOGIN_ROUTE
import com.naaammme.bbspace.feature.auth.navigation.accountScreen
import com.naaammme.bbspace.feature.auth.navigation.loginScreen
import com.naaammme.bbspace.feature.auth.navigation.smsLoginScreen
import com.naaammme.bbspace.feature.bbspace.navigation.bbSpaceScreen
import com.naaammme.bbspace.feature.bbspace.navigation.navigateToBbSpace
import com.naaammme.bbspace.feature.home.ui.HomeScreen
import com.naaammme.bbspace.feature.live.navigation.liveScreen
import com.naaammme.bbspace.feature.live.navigation.navigateToLive
import com.naaammme.bbspace.feature.search.navigation.navigateToSearch
import com.naaammme.bbspace.feature.search.navigation.searchScreen
import com.naaammme.bbspace.feature.settings.navigation.SETTINGS_ROUTE
import com.naaammme.bbspace.feature.settings.navigation.settingsScreen
import com.naaammme.bbspace.feature.user.UserScreen
import com.naaammme.bbspace.feature.video.navigation.navigateToVideo
import com.naaammme.bbspace.feature.video.navigation.videoScreen

private const val MAIN_ROUTE = "main"

@Composable
fun AppNavHost(themeConfig: ThemeConfig = ThemeConfig()) {
    val rootNavController = rememberNavController()
    val transitions = remember(themeConfig.transitionStyle, themeConfig.animationSpeed) {
        buildNavTransitions<NavBackStackEntry>(
            themeConfig.transitionStyle,
            themeConfig.animationSpeed
        )
    }

    NavHost(
        navController = rootNavController,
        startDestination = MAIN_ROUTE,
        enterTransition = { transitions.enter(this) },
        exitTransition = { transitions.exit(this) },
        popEnterTransition = { transitions.popEnter(this) },
        popExitTransition = { transitions.popExit(this) }
    ) {
        composable(MAIN_ROUTE) {
            MainTabsScaffold(
                onNavigateToSearch = { rootNavController.navigateToSearch() },
                onNavigateToSettings = { rootNavController.navigate(SETTINGS_ROUTE) },
                onNavigateToAccount = { rootNavController.navigate(ACCOUNT_ROUTE) },
                onNavigateToBbSpace = { rootNavController.navigateToBbSpace() },
                onNavigateToVideo = rootNavController::navigateToVideo,
                onNavigateToLive = rootNavController::navigateToLive
            )
        }

        loginScreen(
            onLoginSuccess = { rootNavController.popBackStack() },
            onBack = { rootNavController.popBackStack() },
            onSwitchToSms = {
                rootNavController.navigate(SMS_LOGIN_ROUTE) {
                    popUpTo(LOGIN_ROUTE) { inclusive = true }
                }
            }
        )

        smsLoginScreen(
            onLoginSuccess = { rootNavController.popBackStack() },
            onBack = { rootNavController.popBackStack() },
            onSwitchToQr = {
                rootNavController.navigate(LOGIN_ROUTE) {
                    popUpTo(SMS_LOGIN_ROUTE) { inclusive = true }
                }
            }
        )

        accountScreen(
            onBack = { rootNavController.popBackStack() },
            onAddAccount = { rootNavController.navigate(SMS_LOGIN_ROUTE) },
            onSwitched = { rootNavController.popBackStack() }
        )

        bbSpaceScreen(rootNavController)
        settingsScreen(rootNavController)
        searchScreen(
            onBack = { rootNavController.popBackStack() },
            onOpenVideo = rootNavController::navigateToVideo
        )
        videoScreen(
            onBack = { rootNavController.popBackStack() },
            onOpenVideo = rootNavController::navigateToVideo
        )
        liveScreen(
            onBack = { rootNavController.popBackStack() }
        )
    }
}

@Composable
private fun MainTabsScaffold(
    onNavigateToSearch: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAccount: () -> Unit,
    onNavigateToBbSpace: () -> Unit,
    onNavigateToVideo: (VideoRoute) -> Unit,
    onNavigateToLive: (LiveRoute) -> Unit
) {
    var currentTab by rememberSaveable { mutableStateOf(TopLevelRoute.HOME) }
    val saveableStateHolder = rememberSaveableStateHolder()

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            NavigationBar {
                TopLevelRoute.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = currentTab == tab,
                        onClick = { currentTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            TopLevelRoute.entries.forEach { tab ->
                if (currentTab == tab) {
                    saveableStateHolder.SaveableStateProvider(tab.route) {
                        when (tab) {
                            TopLevelRoute.HOME -> HomeScreen(
                                onNavigateToSearch = onNavigateToSearch,
                                onNavigateToSettings = onNavigateToSettings,
                                onOpenVideo = onNavigateToVideo,
                                onOpenLive = onNavigateToLive
                            )
                            TopLevelRoute.DYNAMIC -> PlaceholderScreen("动态")
                            TopLevelRoute.MESSAGE -> PlaceholderScreen("消息")
                            TopLevelRoute.PROFILE -> UserScreen(
                                onNavigateToAccount = onNavigateToAccount,
                                onNavigateToBbSpace = onNavigateToBbSpace
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaceholderScreen(title: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(title)
    }
}
