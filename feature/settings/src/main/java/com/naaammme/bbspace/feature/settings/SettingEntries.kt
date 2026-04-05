package com.naaammme.bbspace.feature.settings

import com.naaammme.bbspace.feature.settings.navigation.APPEARANCE_ROUTE
import com.naaammme.bbspace.feature.settings.navigation.FEED_SETTINGS_ROUTE
import com.naaammme.bbspace.feature.settings.navigation.PERFORMANCE_ROUTE
import com.naaammme.bbspace.feature.settings.navigation.PLAYBACK_ROUTE
import com.naaammme.bbspace.feature.settings.navigation.PRIVACY_ROUTE

data class SettingEntry(val title: String, val subtitle: String, val route: String)

val appearanceEntries = listOf(
    SettingEntry("主题模式", "浅色 深色 或跟随系统", APPEARANCE_ROUTE),
    SettingEntry("动态取色", "Android 12 以上从壁纸提取颜色", APPEARANCE_ROUTE),
    SettingEntry("主题色", "选择应用主题颜色", APPEARANCE_ROUTE),
    SettingEntry("纯色背景", "深色纯黑 浅色纯白", APPEARANCE_ROUTE),
    SettingEntry("字体大小", "调整应用字体大小", APPEARANCE_ROUTE),
    SettingEntry("动画速度", "调整界面动画速度", APPEARANCE_ROUTE),
    SettingEntry("过渡动画", "调整页面切换动画样式", APPEARANCE_ROUTE),
    SettingEntry("圆角风格", "调整界面圆角风格", APPEARANCE_ROUTE),
)

val performanceEntries = listOf(
    SettingEntry("屏幕刷新率", "设置应用渲染帧率上限", PERFORMANCE_ROUTE),
)

val playbackEntries = listOf(
    SettingEntry("默认画质", "设置默认视频和音频质量", PLAYBACK_ROUTE),
    SettingEntry("编码格式", "选择优先的编码格式", PLAYBACK_ROUTE),
    SettingEntry("强制 HTTPS", "使用 HTTPS 播放地址", PLAYBACK_ROUTE),
)

val feedEntries = listOf(
    SettingEntry("HD 推荐模式", "切换 HD 推荐接口", FEED_SETTINGS_ROUTE),
    SettingEntry("个性化推荐", "基于历史记录推荐内容", FEED_SETTINGS_ROUTE),
)

val privacyEntries = listOf(
    SettingEntry("禁止 Gaia 上报", "阻止应用列表风控数据上报", PRIVACY_ROUTE),
)

val allSettingEntries =
    appearanceEntries + performanceEntries + playbackEntries + feedEntries + privacyEntries
