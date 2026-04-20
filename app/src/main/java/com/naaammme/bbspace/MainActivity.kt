package com.naaammme.bbspace

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.naaammme.bbspace.core.data.AppSettings
import com.naaammme.bbspace.core.data.update.AppUpdateCheckResult
import com.naaammme.bbspace.core.data.update.AppUpdateChecker
import com.naaammme.bbspace.core.data.update.toDialogState
import com.naaammme.bbspace.core.designsystem.component.AppUpdateDialog
import com.naaammme.bbspace.core.designsystem.component.AppUpdateDialogState
import com.naaammme.bbspace.core.designsystem.theme.BiliTheme
import com.naaammme.bbspace.core.designsystem.theme.FrameRateMode
import com.naaammme.bbspace.core.designsystem.theme.ThemeConfig
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            autoCheckUpdate()
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
                    AppNavHost(themeConfig = themeConfig)
                }
                updateDialog?.let { release ->
                    AppUpdateDialog(
                        state = release,
                        onDismiss = { updateDialog = null },
                        onOpenUrl = {
                            updateDialog = null
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it)))
                        }
                    )
                }
            }
        }
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
        val currentMode = display?.mode ?: return null
        val w = currentMode.physicalWidth
        val h = currentMode.physicalHeight
        return display?.supportedModes
            ?.filter { it.physicalWidth == w && it.physicalHeight == h }
            ?.minByOrNull { abs(it.refreshRate - targetHz) }
            ?.takeIf { abs(it.refreshRate - targetHz) < 5f }
    }
}
