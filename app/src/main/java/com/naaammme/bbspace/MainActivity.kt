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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.naaammme.bbspace.core.designsystem.component.AppUpdateDialog
import com.naaammme.bbspace.core.designsystem.component.AppUpdateDialogState
import com.naaammme.bbspace.core.designsystem.component.toDialogState
import com.naaammme.bbspace.core.designsystem.theme.BiliTheme
import com.naaammme.bbspace.core.model.AppUpdateCheckResult
import com.naaammme.bbspace.core.model.FrameRateMode
import com.naaammme.bbspace.core.model.WebLinkParser
import com.naaammme.bbspace.core.model.WebLinkTarget
import com.naaammme.bbspace.core.settings.SystemPrefs
import com.naaammme.bbspace.core.settings.update.AppUpdateChecker
import com.naaammme.bbspace.feature.settings.navigation.SettingsNavigation
import com.naaammme.bbspace.navigation.AppNavHost
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.math.abs
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var systemPrefs: SystemPrefs

    @Inject
    lateinit var updateChecker: AppUpdateChecker

    @Inject
    lateinit var settingsNavigation: SettingsNavigation

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
            val shellViewModel: AppShellViewModel = hiltViewModel()
            val visualConfig by shellViewModel.themeVisualConfig.collectAsStateWithLifecycle()
            val animationSpeed by shellViewModel.animationSpeed.collectAsStateWithLifecycle()
            val pullRefreshDistanceDp by shellViewModel.pullRefreshDistanceDp.collectAsStateWithLifecycle()
            val preferredFrameRate by shellViewModel.preferredFrameRate.collectAsStateWithLifecycle()

            LaunchedEffect(preferredFrameRate) {
                applyFrameRate(preferredFrameRate)
            }
            BiliTheme(
                config = visualConfig,
                animationSpeed = animationSpeed,
                pullRefreshDistanceDp = pullRefreshDistanceDp
            ) {
                ApplySystemBarAppearance()
                AppNavHost(
                    settingsNavigation = settingsNavigation,
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
            if (!systemPrefs.autoCheckUpdate.get()) return@launch
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
