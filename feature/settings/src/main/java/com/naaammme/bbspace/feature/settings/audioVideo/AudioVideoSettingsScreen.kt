package com.naaammme.bbspace.feature.settings.audioVideo

import androidx.compose.runtime.Composable
import com.naaammme.bbspace.core.designsystem.component.SettingSectionHeader
import com.naaammme.bbspace.core.designsystem.component.SettingsPageScaffold
import com.naaammme.bbspace.core.model.VideoCodec
import com.naaammme.bbspace.core.settings.PlaybackPrefs
import com.naaammme.bbspace.feature.settings.SettingDialogChoice
import com.naaammme.bbspace.feature.settings.SettingSwitch

@Composable
fun AudioVideoSettingsScreen(
    onBack: () -> Unit,
    prefs: PlaybackPrefs
) {
    SettingsPageScaffold(title = "音视频设置", onBack = onBack) {
        item(key = "header_quality") {
            SettingSectionHeader("画质")
        }
        item(key = "enable_hdr_8k") {
            SettingSwitch(
                title = "启用 HDR 和 8K",
                subtitle = "允许请求 HDR 和 8K 视频流（如果视频支持）",
                setting = prefs.enableHdrAnd8k
            )
        }
        item(key = "header_local") {
            SettingSectionHeader("本地选择")
        }
        item(key = "default_video_quality") {
            SettingDialogChoice(
                title = "默认视频画质",
                dialogTitle = "选择默认视频画质",
                setting = prefs.defaultVideoQuality,
                options = VIDEO_QUALITIES,
                label = ::videoQualityName
            )
        }
        item(key = "default_audio_quality") {
            SettingDialogChoice(
                title = "默认音频质量",
                dialogTitle = "选择默认音频质量",
                setting = prefs.defaultAudioQuality,
                options = AUDIO_QUALITIES,
                label = ::audioQualityName
            )
        }
        item(key = "header_other") {
            SettingSectionHeader("其他")
        }
        item(key = "preferred_codec") {
            SettingDialogChoice(
                title = "优先编码格式",
                dialogTitle = "选择优先编码格式",
                setting = prefs.preferredCodec,
                options = VideoCodec.entries,
                label = VideoCodec::label,
                keyOf = VideoCodec::name
            )
        }
        item(key = "force_https") {
            SettingSwitch(
                title = "使用https播放",
                subtitle = "强制请求 HTTPS 播放地址",
                setting = prefs.forceHttps
            )
        }
        item(key = "need_trial") {
            SettingSwitch(
                title = "需要4k",
                setting = prefs.needTrial
            )
        }
        item(key = "enable_web_playback") {
            SettingSwitch(
                title = "免登录看1080p",
                subtitle = "打开就算不登录也能看1080p喵",
                setting = prefs.enableWebPlayback
            )
        }
    }
}

private val VIDEO_QUALITIES = listOf(16, 32, 64, 80, 112, 116, 120, 125, 126, 127)
private val AUDIO_QUALITIES = listOf(0, 30216, 30232, 30280, 30250, 30251)

private fun videoQualityName(quality: Int): String = when (quality) {
    16 -> "360P"
    32 -> "480P"
    64 -> "720P"
    80 -> "1080P"
    112 -> "1080P+"
    116 -> "1080P 60fps"
    120 -> "4K"
    125 -> "HDR"
    126 -> "杜比视界"
    127 -> "8K"
    else -> "未知"
}

private fun audioQualityName(quality: Int): String = when (quality) {
    0 -> "自动"
    30216 -> "64K"
    30232 -> "132K"
    30280 -> "192K"
    30250 -> "杜比全景声"
    30251 -> "Hi-Res 无损"
    else -> "未知"
}
