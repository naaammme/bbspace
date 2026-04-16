package com.naaammme.bbspace

import android.os.Bundle
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.naaammme.bbspace.core.data.AppSettings
import com.naaammme.bbspace.core.designsystem.theme.BiliTheme
import com.naaammme.bbspace.core.designsystem.theme.FrameRateMode
import com.naaammme.bbspace.core.designsystem.theme.ThemeConfig
import com.naaammme.bbspace.core.model.VideoRoute
import com.naaammme.bbspace.navigation.AppNavHost
import com.naaammme.bbspace.playback.PlaybackLaunchIntents
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.math.abs

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var appSettings: AppSettings

    private var pendingVideoRoute by mutableStateOf<VideoRoute?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingVideoRoute = if (savedInstanceState == null) {
            PlaybackLaunchIntents.consumeRoute(intent)
        } else {
            null
        }
        enableEdgeToEdge()
        setContent {
            val themeConfig by appSettings.themeConfig.collectAsState(initial = ThemeConfig())

            LaunchedEffect(themeConfig.preferredFrameRate) {
                applyFrameRate(themeConfig.preferredFrameRate)
            }
            // 全局文本选择
            BiliTheme(config = themeConfig) {
                SelectionContainer {
                    AppNavHost(
                        themeConfig = themeConfig,
                        pendingVideoRoute = pendingVideoRoute,
                        onPendingVideoRouteConsumed = { pendingVideoRoute = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingVideoRoute = PlaybackLaunchIntents.consumeRoute(intent)
    }

    private fun applyFrameRate(mode: FrameRateMode) {
        val attrs = window.attributes
        if (mode == FrameRateMode.AUTO) {
            attrs.preferredDisplayModeId = 0
            attrs.preferredRefreshRate = 0f
        } else {
            val targetMode = findBestDisplayMode(mode.value)
            if (targetMode != null) {
                attrs.preferredDisplayModeId = targetMode.modeId
            }
            attrs.preferredRefreshRate = mode.value
        }
        window.attributes = attrs
    }

    private fun findBestDisplayMode(targetHz: Float): Display.Mode? {
        val currentMode = display?.mode ?: return null
        val w = currentMode.physicalWidth
        val h = currentMode.physicalHeight
        return display?.supportedModes
            ?.filter { it.physicalWidth == w && it.physicalHeight == h }
            ?.minByOrNull { abs(it.refreshRate - targetHz) }
            ?.takeIf { abs(it.refreshRate - targetHz) < 5f }
    }
}
