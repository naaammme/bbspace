package com.naaammme.bbspace.feature.settings.performance

import androidx.compose.runtime.Composable
import com.naaammme.bbspace.core.designsystem.component.SettingsPageScaffold
import com.naaammme.bbspace.core.model.FrameRateMode
import com.naaammme.bbspace.core.settings.Setting
import com.naaammme.bbspace.feature.settings.SettingChoiceChips
import com.naaammme.bbspace.feature.settings.SettingSwitch

@Composable
fun PerformanceSettingsScreen(
    onBack: () -> Unit,
    useSystemDns: Setting<Boolean>,
    preferredFrameRate: Setting<FrameRateMode>
) {
    SettingsPageScaffold(title = "性能设置", onBack = onBack) {
        item(key = "use_system_dns") {
            SettingSwitch(
                title = "使用系统 DNS",
                subtitle = "重启后生效，关闭后使用 HTTPDNS",
                setting = useSystemDns
            )
        }
        item(key = "frame_rate_mode") {
            SettingChoiceChips(
                title = "屏幕刷新率",
                subtitle = "高刷新率可提升滑动流畅度，但会增加耗电",
                setting = preferredFrameRate,
                options = FrameRateMode.entries,
                label = { mode ->
                    when (mode) {
                        FrameRateMode.AUTO -> "自动"
                        FrameRateMode.RATE_60 -> "60Hz"
                        FrameRateMode.RATE_90 -> "90Hz"
                        FrameRateMode.RATE_120 -> "120Hz"
                        FrameRateMode.RATE_144 -> "144Hz"
                    }
                }
            )
        }
    }
}
