package com.naaammme.bbspace.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.naaammme.bbspace.feature.settings.components.SettingCategory
import com.naaammme.bbspace.feature.settings.navigation.APPEARANCE_ROUTE
import com.naaammme.bbspace.feature.settings.navigation.FEED_SETTINGS_ROUTE
import com.naaammme.bbspace.feature.settings.navigation.PERFORMANCE_ROUTE
import com.naaammme.bbspace.feature.settings.navigation.PLAYER_ROUTE
import com.naaammme.bbspace.feature.settings.navigation.PLAYBACK_ROUTE
import com.naaammme.bbspace.feature.settings.navigation.PRIVACY_ROUTE

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToAppearance: () -> Unit,
    onNavigateToPerformance: () -> Unit,
    onNavigateToPlayer: () -> Unit,
    onNavigateToFeed: () -> Unit,
    onNavigateToPlayback: () -> Unit,
    onNavigateToPrivacy: () -> Unit,
    onNavigateToErrorLog: () -> Unit,
    onNavigateToAbout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    var query by remember { mutableStateOf("") }
    var showResetDialog by remember { mutableStateOf(false) }

    val routeNav = mapOf(
        APPEARANCE_ROUTE to onNavigateToAppearance,
        PERFORMANCE_ROUTE to onNavigateToPerformance,
        PLAYER_ROUTE to onNavigateToPlayer,
        FEED_SETTINGS_ROUTE to onNavigateToFeed,
        PLAYBACK_ROUTE to onNavigateToPlayback,
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
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
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("搜索设置") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large
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
                    items(filtered) { entry ->
                        Card(
                            onClick = { routeNav[entry.route]?.invoke() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Box(modifier = Modifier.align(Alignment.CenterStart)) {
                                    androidx.compose.foundation.layout.Column {
                                        Text(entry.title, style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            entry.subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.align(Alignment.CenterEnd)
                                )
                            }
                        }
                    }
                }
            } else {
                item {
                    SettingCategory(
                        icon = Icons.Default.Edit,
                        title = "外观设置",
                        subtitle = "主题 颜色 字体",
                        onClick = onNavigateToAppearance
                    )
                }
                item {
                    SettingCategory(
                        icon = Icons.Default.Settings,
                        title = "性能设置",
                        subtitle = "刷新率和渲染策略",
                        onClick = onNavigateToPerformance
                    )
                }
                item {
                    SettingCategory(
                        icon = Icons.Default.PlayArrow,
                        title = "播放器设置",
                        subtitle = "缓冲 解码和后台播放",
                        onClick = onNavigateToPlayer
                    )
                }
                item {
                    SettingCategory(
                        icon = Icons.Default.PlayArrow,
                        title = "音视频设置",
                        subtitle = "画质 音质和编码格式",
                        onClick = onNavigateToPlayback
                    )
                }
                item {
                    SettingCategory(
                        icon = Icons.Default.Settings,
                        title = "推荐设置",
                        subtitle = "HD 推荐模式",
                        onClick = onNavigateToFeed
                    )
                }
                item {
                    SettingCategory(
                        icon = Icons.Default.Lock,
                        title = "隐私安全",
                        subtitle = "历史记录和缓存管理",
                        onClick = onNavigateToPrivacy
                    )
                }
                item {
                    SettingCategory(
                        icon = Icons.Default.Info,
                        title = "关于",
                        subtitle = "版本信息和开源许可",
                        onClick = onNavigateToAbout
                    )
                }
                item {
                    SettingCategory(
                        icon = Icons.Default.Warning,
                        title = "错误日志",
                        subtitle = "查看和导出应用错误记录",
                        onClick = onNavigateToErrorLog
                    )
                }
                item {
                    Card(
                        onClick = { showResetDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            androidx.compose.foundation.layout.Column {
                                Text(
                                    text = "恢复默认设置",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = "一键重置外观 播放 推荐和隐私等设置",
                                    style = MaterialTheme.typography.bodySmall,
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
                    Text(
                        "这会把当前各项设置恢复到默认值 不会退出登录"
                    )
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
