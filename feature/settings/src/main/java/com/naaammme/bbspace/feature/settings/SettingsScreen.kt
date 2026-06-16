package com.naaammme.bbspace.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.naaammme.bbspace.core.designsystem.component.CapsuleGroup
import com.naaammme.bbspace.core.designsystem.component.CapsuleListItem
import com.naaammme.bbspace.core.designsystem.component.CollapsingTopBarScaffold
import com.naaammme.bbspace.core.designsystem.component.SearchCapsuleField
import com.naaammme.bbspace.feature.settings.components.SettingCategory
import com.naaammme.bbspace.feature.settings.navigation.APPEARANCE_ROUTE
import com.naaammme.bbspace.feature.settings.navigation.FEED_SETTINGS_ROUTE
import com.naaammme.bbspace.feature.settings.navigation.AUDIO_VIDEO_ROUTE
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

    val routeNav = mapOf(
        APPEARANCE_ROUTE to onNavigateToAppearance,
        PERFORMANCE_ROUTE to onNavigateToPerformance,
        FEED_SETTINGS_ROUTE to onNavigateToFeed,
        AUDIO_VIDEO_ROUTE to onNavigateToAudioVideo,
        PRIVACY_ROUTE to onNavigateToPrivacy,
    )
    val filtered = remember(query) {
        if (query.isBlank()) {
            emptyList()
        } else {
            allSettingEntries.filter {
                it.title.contains(query, ignoreCase = true) ||
                    it.subtitle.contains(query, ignoreCase = true)
            }
        }
    }
    val homeItems = remember(
        onNavigateToAppearance,
        onNavigateToPerformance,
        onNavigateToFeed,
        onNavigateToAudioVideo,
        onNavigateToPrivacy,
        onNavigateToErrorLog,
        onNavigateToAbout
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
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SearchCapsuleField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "搜索设置",
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (query.isNotBlank()) {
                if (filtered.isEmpty()) {
                    item {
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
                    item {
                        SearchResultGroup(
                            entries = filtered,
                            onEntryClick = { entry -> routeNav[entry.route]?.invoke() }
                        )
                    }
                }
            } else {
                item {
                    SettingsHomeGroup(items = homeItems)
                }
                item {
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
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        SettingCategory(
            icon = item.icon,
            title = item.title,
            subtitle = item.subtitle,
            shape = shape,
            onClick = item.onClick
        )
    }
}

@Composable
private fun SearchResultGroup(
    entries: List<SettingEntry>,
    onEntryClick: (SettingEntry) -> Unit
) {
    CapsuleGroup(
        items = entries,
        modifier = Modifier.fillMaxWidth()
    ) { entry, _, shape ->
        CapsuleListItem(
            title = entry.title,
            subtitle = entry.subtitle,
            shape = shape,
            trailingContent = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            onClick = { onEntryClick(entry) }
        )
    }
}
