package com.naaammme.bbspace.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.naaammme.bbspace.core.designsystem.component.CapsuleGroup
import com.naaammme.bbspace.core.designsystem.component.CapsuleListItem
import com.naaammme.bbspace.core.designsystem.component.CollapsingTopBarScaffold
import com.naaammme.bbspace.core.designsystem.component.SearchCapsuleField
import com.naaammme.bbspace.core.designsystem.component.SettingActionCard
import com.naaammme.bbspace.feature.settings.navigation.ABOUT_ROUTE
import com.naaammme.bbspace.feature.settings.navigation.APPEARANCE_ROUTE
import com.naaammme.bbspace.feature.settings.navigation.AUDIO_VIDEO_ROUTE
import com.naaammme.bbspace.feature.settings.navigation.ERROR_LOG_ROUTE
import com.naaammme.bbspace.feature.settings.navigation.FEED_SETTINGS_ROUTE
import com.naaammme.bbspace.feature.settings.navigation.PERFORMANCE_ROUTE
import com.naaammme.bbspace.feature.settings.navigation.PRIVACY_ROUTE

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToAppearance: () -> Unit,
    onNavigateToPerformance: () -> Unit,
    onNavigateToFeed: () -> Unit,
    onNavigateToAudioVideo: () -> Unit,
    onNavigateToPrivacy: () -> Unit,
    onNavigateToErrorLog: () -> Unit,
    onNavigateToAbout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    var query by remember { mutableStateOf("") }
    var showResetDialog by remember { mutableStateOf(false) }
    val shapes = MaterialTheme.shapes

    val routeNav = remember(
        onNavigateToAppearance,
        onNavigateToPerformance,
        onNavigateToFeed,
        onNavigateToAudioVideo,
        onNavigateToPrivacy,
        onNavigateToErrorLog,
        onNavigateToAbout
    ) {
        mapOf(
            APPEARANCE_ROUTE to onNavigateToAppearance,
            PERFORMANCE_ROUTE to onNavigateToPerformance,
            FEED_SETTINGS_ROUTE to onNavigateToFeed,
            AUDIO_VIDEO_ROUTE to onNavigateToAudioVideo,
            PRIVACY_ROUTE to onNavigateToPrivacy,
            ERROR_LOG_ROUTE to onNavigateToErrorLog,
            ABOUT_ROUTE to onNavigateToAbout
        )
    }

    val filtered = remember(query) {
        if (query.isBlank()) emptyList() else SearchEntries.filter { it.matches(query) }
    }

    val homeItems = remember(
        onNavigateToAppearance,
        onNavigateToPerformance,
        onNavigateToAudioVideo,
        onNavigateToFeed,
        onNavigateToPrivacy,
        onNavigateToAbout,
        onNavigateToErrorLog
    ) {
        listOf(
            SettingsHomeItem(
                icon = Icons.Default.Edit,
                title = "外观设置",
                subtitle = "主题 颜色 字体",
                onClick = onNavigateToAppearance
            ),
            SettingsHomeItem(
                icon = Icons.Default.Settings,
                title = "性能设置",
                subtitle = "刷新率和渲染策略",
                onClick = onNavigateToPerformance
            ),
            SettingsHomeItem(
                icon = Icons.Default.PlayArrow,
                title = "音视频设置",
                subtitle = "画质 音质 和编码格式",
                onClick = onNavigateToAudioVideo
            ),
            SettingsHomeItem(
                icon = Icons.Default.Settings,
                title = "推荐设置",
                subtitle = "HD 推荐模式",
                onClick = onNavigateToFeed
            ),
            SettingsHomeItem(
                icon = Icons.Default.Lock,
                title = "隐私安全",
                subtitle = "历史记录和缓存管理",
                onClick = onNavigateToPrivacy
            ),
            SettingsHomeItem(
                icon = Icons.Default.Info,
                title = "关于",
                subtitle = "版本信息和开源许可",
                onClick = onNavigateToAbout
            ),
            SettingsHomeItem(
                icon = Icons.Default.Warning,
                title = "错误日志",
                subtitle = "查看和导出应用错误记录",
                onClick = onNavigateToErrorLog
            )
        )
    }

    CollapsingTopBarScaffold(
        topBar = { scrollBehavior ->
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "search_field") {
                SearchCapsuleField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "搜索设置",
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (query.isNotBlank()) {
                if (filtered.isEmpty()) {
                    item(key = "empty_search") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("没有匹配结果", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    items(items = filtered, key = { "${it.route}_${it.title}" }) { entry ->
                        SettingActionCard(
                            title = entry.title,
                            subtitle = entry.subtitle,
                            onClick = { routeNav[entry.route]?.invoke() }
                        )
                    }
                }
            } else {
                item(key = "home_group") {
                    SettingsHomeGroup(items = homeItems)
                }
                item(key = "reset_card") {
                    Card(
                        onClick = { showResetDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = shapes.large
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Column {
                                Text(
                                    text = "恢复默认设置",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "一键重置外观 音视频 推荐和隐私等设置",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showResetDialog) {
            AlertDialog(
                onDismissRequest = { showResetDialog = false },
                title = { Text("恢复默认设置") },
                text = {
                    Text("这会把当前各项设置恢复到默认值 不会退出登录")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.resetAllSettings()
                            showResetDialog = false
                        }
                    ) {
                        Text("确定")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showResetDialog = false }) {
                        Text("取消")
                    }
                }
            )
        }
    }
}

private data class SettingSearchEntry(
    val title: String,
    val subtitle: String?,
    val route: String,
    val keywords: List<String> = emptyList()
) {
    fun matches(query: String): Boolean {
        return title.contains(query, ignoreCase = true) ||
            subtitle?.contains(query, ignoreCase = true) == true ||
            keywords.any { it.contains(query, ignoreCase = true) }
    }
}

private val SearchEntries = listOf(
    SettingSearchEntry("主题模式", "浅色 深色 或跟随系统", APPEARANCE_ROUTE, listOf("暗黑", "夜间", "dark", "light")),
    SettingSearchEntry("动态取色", "Android 12+ 使用系统动态配色", APPEARANCE_ROUTE, listOf("壁纸", "莫奈", "monet")),
    SettingSearchEntry("调色盘风格", null, APPEARANCE_ROUTE, listOf("配色", "黑白", "鲜艳")),
    SettingSearchEntry("反转颜色", "交换页面背景和低饱和容器色，强调色保持不变", APPEARANCE_ROUTE),
    SettingSearchEntry("主题色", "选择应用主题颜色", APPEARANCE_ROUTE, listOf("配色", "粉色", "蓝色")),
    SettingSearchEntry("纯色背景", "深色用纯黑，浅色用纯白，会参与背景层级反转", APPEARANCE_ROUTE, listOf("oled", "纯黑")),
    SettingSearchEntry("字体大小", "调整应用字体大小", APPEARANCE_ROUTE),
    SettingSearchEntry("界面缩放", "调整界面元素的整体大小", APPEARANCE_ROUTE, listOf("dpi", "尺寸")),
    SettingSearchEntry("圆屏安全边距", "仅圆形屏幕生效，0% 为关闭，100% 为默认", APPEARANCE_ROUTE, listOf("手表", "圆角")),
    SettingSearchEntry("下拉刷新行程", "数值越大，下拉越长才会触发刷新", APPEARANCE_ROUTE),
    SettingSearchEntry("固定底栏", "主页底栏不再随滚动自动收起", APPEARANCE_ROUTE, listOf("导航栏")),
    SettingSearchEntry("动画速度", "调整界面动画速度", APPEARANCE_ROUTE),
    SettingSearchEntry("圆角风格", "调整界面圆角风格", APPEARANCE_ROUTE),
    SettingSearchEntry("过渡动画", "调整页面切换动画样式", APPEARANCE_ROUTE),
    SettingSearchEntry("使用系统 DNS", "重启后生效，关闭后使用 HTTPDNS", PERFORMANCE_ROUTE, listOf("网络", "解析", "httpdns")),
    SettingSearchEntry("屏幕刷新率", "高刷新率可提升滑动流畅度，但会增加耗电", PERFORMANCE_ROUTE, listOf("hz", "fps", "高刷")),
    SettingSearchEntry("启用 HDR 和 8K", "允许请求 HDR 和 8K 视频流", AUDIO_VIDEO_ROUTE, listOf("杜比", "4k", "hdr", "8k")),
    SettingSearchEntry("默认视频画质", null, AUDIO_VIDEO_ROUTE, listOf("默认画质", "清晰度", "分辨率", "1080p", "4k")),
    SettingSearchEntry("默认音频质量", null, AUDIO_VIDEO_ROUTE, listOf("音质", "杜比", "无损", "hires")),
    SettingSearchEntry("优先编码格式", null, AUDIO_VIDEO_ROUTE, listOf("avc", "h264", "hevc", "h265", "av1")),
    SettingSearchEntry("使用https播放", "强制请求 HTTPS 播放地址", AUDIO_VIDEO_ROUTE, listOf("强制 HTTPS", "https", "加密")),
    SettingSearchEntry("需要4k", null, AUDIO_VIDEO_ROUTE, listOf("试看", "4k")),
    SettingSearchEntry("免登录看1080p", "打开就算不登录也能看1080p喵", AUDIO_VIDEO_ROUTE, listOf("web", "游客", "1080p")),
    SettingSearchEntry("HD 推荐模式", "切换 HD 推荐接口", FEED_SETTINGS_ROUTE, listOf("平板", "hd")),
    SettingSearchEntry("个性化推荐", "基于观看历史推荐内容", FEED_SETTINGS_ROUTE),
    SettingSearchEntry("课堂推荐模式", "只推荐学习相关视频", FEED_SETTINGS_ROUTE),
    SettingSearchEntry("未成年推荐", "按指定年龄请求未成年推荐内容", FEED_SETTINGS_ROUTE),
    SettingSearchEntry("内容偏好调节", "查看和调整你的内容兴趣标签", FEED_SETTINGS_ROUTE),
    SettingSearchEntry("禁止 Gaia 上报", "阻止应用列表风控数据上报", PRIVACY_ROUTE, listOf("隐私", "风控")),
    SettingSearchEntry("清除缓存", "清除图片缓存和全部缓存", PRIVACY_ROUTE, listOf("缓存管理")),
    SettingSearchEntry("自动检查更新", "应用初始化时自动检查并弹出更新说明", ABOUT_ROUTE, listOf("版本", "升级")),
    SettingSearchEntry("错误日志", "查看和导出应用错误记录", ERROR_LOG_ROUTE, listOf("log", "崩溃", "异常"))
)

private data class SettingsHomeItem(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val onClick: () -> Unit
)

@Composable
private fun SettingsHomeGroup(
    items: List<SettingsHomeItem>
) {
    CapsuleGroup(
        items = items,
        modifier = Modifier.fillMaxWidth()
    ) { item, _, shape ->
        SettingCategoryItem(
            icon = item.icon,
            title = item.title,
            subtitle = item.subtitle,
            shape = shape,
            onClick = item.onClick
        )
    }
}

@Composable
private fun SettingCategoryItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    shape: Shape,
    onClick: () -> Unit
) {
    CapsuleListItem(
        title = title,
        subtitle = subtitle,
        shape = shape,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        },
        onClick = onClick
    )
}
