package com.naaammme.bbspace

import android.content.Intent
import android.graphics.Color
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.naaammme.bbspace.core.settings.AppSettings
import com.naaammme.bbspace.core.settings.update.AppUpdateCheckResult
import com.naaammme.bbspace.core.settings.update.AppUpdateChecker
import com.naaammme.bbspace.core.settings.update.toDialogState
import com.naaammme.bbspace.core.designsystem.component.AppUpdateDialog
import com.naaammme.bbspace.core.designsystem.component.AppUpdateDialogState
import com.naaammme.bbspace.core.designsystem.theme.BiliTheme
import com.naaammme.bbspace.core.designsystem.theme.FrameRateMode
import com.naaammme.bbspace.core.designsystem.theme.ThemeConfig
import com.naaammme.bbspace.core.model.WebLinkParser
import com.naaammme.bbspace.core.model.WebLinkTarget
import com.naaammme.bbspace.navigation.AppNavHost
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var appSettings: AppSettings

    @Inject
    lateinit var updateChecker: AppUpdateChecker

    private var updateDialog by mutableStateOf<AppUpdateDialogState?>(null)

    private var cachedDisplayModes: Array<Display.Mode>? = null

    private var pendingAppLink by mutableStateOf<WebLinkTarget?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            autoCheckUpdate()
        }
        pendingAppLink = toAppLink(intent)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(
                lightScrim = Color.TRANSPARENT,
                darkScrim = Color.TRANSPARENT
            )
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        setContent {
            val themeConfig by appSettings.themeConfig.collectAsState(initial = ThemeConfig())

            LaunchedEffect(themeConfig.preferredFrameRate) {
                applyFrameRate(themeConfig.preferredFrameRate)
            }
            BiliTheme(config = themeConfig) {
                ApplySystemBarAppearance()
                AppNavHost(
                    themeConfig = themeConfig,
                    appLink = pendingAppLink,
                    onAppLinkConsumed = { pendingAppLink = null }
                )
                updateDialog?.let { release ->
                    AppUpdateDialog(
                        state = release,
                        onDismiss = { updateDialog = null },
                        onOpenUrl = {
                            updateDialog = null
                            startActivity(Intent(Intent.ACTION_VIEW, it.toUri()))
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingAppLink = toAppLink(intent)
    }

    private fun autoCheckUpdate() {
        lifecycleScope.launch {
            if (!appSettings.autoCheckUpdate.first()) return@launch
            val result = updateChecker.check().getOrNull() ?: return@launch
            if (result is AppUpdateCheckResult.HasUpdate) {
                updateDialog = result.toDialogState()
            }
        }
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
        val display = getSystemService(DisplayManager::class.java)
            ?.getDisplay(Display.DEFAULT_DISPLAY)
            ?: return null
        val currentMode = display.mode
        val w = currentMode.physicalWidth
        val h = currentMode.physicalHeight
        val modes = cachedDisplayModes ?: display.supportedModes
            ?.also { cachedDisplayModes = it }
            ?: return null
        return modes
            .filter { it.physicalWidth == w && it.physicalHeight == h }
            .minByOrNull { abs(it.refreshRate - targetHz) }
            ?.takeIf { abs(it.refreshRate - targetHz) < 5f }
    }

    private fun toAppLink(intent: Intent?): WebLinkTarget? {
        val url = intent?.dataString ?: return null
        return when (val target = WebLinkParser.parse(url)) {
            is WebLinkTarget.ToVideo,
            is WebLinkTarget.ToSpace,
            is WebLinkTarget.ToLive -> target
            is WebLinkTarget.External,
            is WebLinkTarget.Stay -> null
        }
    }

    @Composable
    private fun ApplySystemBarAppearance() {
        val view = LocalView.current
        val useDarkSystemBarContent = MaterialTheme.colorScheme.background.luminance() > 0.5f

        if (!view.isInEditMode) {
            SideEffect {
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = useDarkSystemBarContent
                insetsController.isAppearanceLightNavigationBars = useDarkSystemBarContent
            }
        }
    }
}
