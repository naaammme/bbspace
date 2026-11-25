package com.naaammme.bbspace

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
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
import com.naaammme.bbspace.core.model.StreamPlaybackTarget
import com.naaammme.bbspace.core.model.WebLinkParser
import com.naaammme.bbspace.core.model.WebLinkTarget
import com.naaammme.bbspace.navigation.AppNavHost
import com.naaammme.bbspace.playback.PipPlayback
import com.naaammme.bbspace.playback.PlaybackHostViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var appSettings: AppSettings

    @Inject
    lateinit var updateChecker: AppUpdateChecker

    private val playbackHostViewModel: PlaybackHostViewModel by viewModels()

    private var updateDialog by mutableStateOf<AppUpdateDialogState?>(null)

    private var cachedDisplayModes: Array<Display.Mode>? = null

    private var pendingAppLink by mutableStateOf<WebLinkTarget?>(null)

    /** 画中画按钮用的跳转时长（秒），缓存自设置，保证 onUserLeaveHint 能同步读取 */
    @Volatile
    private var pipSeekSeconds: Int = 10

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // 正在播放视频/直播时离开应用（回桌面等），进入原生画中画继续播放
        if (PipPlayback.isSupported() && playbackHostViewModel.shouldEnterPip()) {
            buildCurrentPipParams()?.let { enterPictureInPictureMode(it) }
        }
    }

    /**
     * 构建当前画中画参数（宽高比 + 自定义按钮：快退/快进/暂停播放，
     * 跳转时长同步设置的"双击侧边跳转时长"）。
     */
    private fun buildCurrentPipParams(): PictureInPictureParams? {
        val sessionState = playbackHostViewModel.sessionState.value
        return PipPlayback.buildParams(
            this,
            sessionState.videoWidth,
            sessionState.videoHeight,
            PipPlayback.buildActions(
                context = this,
                isPlaying = sessionState.isPlaying,
                isLive = sessionState.target is StreamPlaybackTarget.Live,
                seekMs = pipSeekSeconds * 1_000L
            )
        )
    }

    /** 画中画期间按钮状态（如暂停/播放图标）变化时刷新参数 */
    private fun refreshPipParamsIfActive() {
        if (!PipPlayback.isSupported() || !PipPlayback.isInPip) return
        if (playbackHostViewModel.sessionState.value.target == null) return
        buildCurrentPipParams()?.let { setPictureInPictureParams(it) }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        PipPlayback.updateInPip(isInPictureInPictureMode)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            autoCheckUpdate()
        }
        observePipState()
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

    /** 跟踪画中画需要的播放状态与设置（跳转时长），在画中画中刷新按钮 */
    private fun observePipState() {
        lifecycleScope.launch {
            combine(
                playbackHostViewModel.sessionState,
                appSettings.state
                    .map { it.playback.doubleTapSeekSeconds }
                    .distinctUntilChanged()
            ) { state, seekSeconds ->
                state to seekSeconds
            }.collect { (_, seekSeconds) ->
                pipSeekSeconds = seekSeconds
                refreshPipParamsIfActive()
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
