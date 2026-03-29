package com.naaammme.bbspace.feature.settings.navigation

import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.naaammme.bbspace.feature.settings.about.AboutScreen
import com.naaammme.bbspace.feature.settings.appearance.AppearanceSettingsScreen
import com.naaammme.bbspace.feature.settings.errorlog.ErrorLogScreen
import com.naaammme.bbspace.feature.settings.feed.FeedSettingsScreen
import com.naaammme.bbspace.feature.settings.performance.PerformanceSettingsScreen
import com.naaammme.bbspace.feature.settings.playback.PlaybackSettingsScreen
import com.naaammme.bbspace.feature.settings.privacy.PrivacySettingsScreen
import com.naaammme.bbspace.feature.settings.SettingsScreen

const val SETTINGS_ROUTE = "settings"
const val APPEARANCE_ROUTE = "settings/appearance"
const val PERFORMANCE_ROUTE = "settings/performance"
const val PRIVACY_ROUTE = "settings/privacy"
const val FEED_SETTINGS_ROUTE = "settings/feed"
const val PLAYBACK_ROUTE = "settings/playback"
const val ERROR_LOG_ROUTE = "settings/error_log"
const val ABOUT_ROUTE = "settings/about"

fun NavGraphBuilder.settingsScreen(navController: NavHostController) {
    composable(SETTINGS_ROUTE) {
        SettingsScreen(
            onBack = { navController.popBackStack() },
            onNavigateToAppearance = { navController.navigate(APPEARANCE_ROUTE) },
            onNavigateToPerformance = { navController.navigate(PERFORMANCE_ROUTE) },
            onNavigateToFeed = { navController.navigate(FEED_SETTINGS_ROUTE) },
            onNavigateToPlayback = { navController.navigate(PLAYBACK_ROUTE) },
            onNavigateToPrivacy = { navController.navigate(PRIVACY_ROUTE) },
            onNavigateToErrorLog = { navController.navigate(ERROR_LOG_ROUTE) },
            onNavigateToAbout = { navController.navigate(ABOUT_ROUTE) }
        )
    }

    composable(APPEARANCE_ROUTE) {
        AppearanceSettingsScreen(onBack = { navController.popBackStack() })
    }

    composable(PERFORMANCE_ROUTE) {
        PerformanceSettingsScreen(onBack = { navController.popBackStack() })
    }

    composable(PLAYBACK_ROUTE) {
        PlaybackSettingsScreen(onBack = { navController.popBackStack() })
    }

    composable(PRIVACY_ROUTE) {
        PrivacySettingsScreen(onBack = { navController.popBackStack() })
    }

    composable(FEED_SETTINGS_ROUTE) {
        FeedSettingsScreen(onBack = { navController.popBackStack() })
    }

    composable(ERROR_LOG_ROUTE) {
        ErrorLogScreen(onBack = { navController.popBackStack() })
    }

    composable(ABOUT_ROUTE) {
        val context = LocalContext.current
        val pm = context.packageManager
        val info = pm.getPackageInfo(context.packageName, 0)
        @Suppress("DEPRECATION")
        val versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P)
            info.longVersionCode else info.versionCode.toLong()
        AboutScreen(
            onBack = { navController.popBackStack() },
            versionName = info.versionName ?: "unknown",
            versionCode = versionCode
        )
    }
}
