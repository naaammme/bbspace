package com.naaammme.bbspace.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.naaammme.bbspace.feature.settings.components.SettingCategory
import com.naaammme.bbspace.feature.settings.navigation.APPEARANCE_ROUTE
import com.naaammme.bbspace.feature.settings.navigation.FEED_SETTINGS_ROUTE
import com.naaammme.bbspace.feature.settings.navigation.PERFORMANCE_ROUTE
import com.naaammme.bbspace.feature.settings.navigation.PLAYBACK_ROUTE
import com.naaammme.bbspace.feature.settings.navigation.PRIVACY_ROUTE

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToAppearance: () -> Unit,
    onNavigateToPerformance: () -> Unit,
    onNavigateToFeed: () -> Unit,
    onNavigateToPlayback: () -> Unit,
    onNavigateToPrivacy: () -> Unit,
    onNavigateToErrorLog: () -> Unit,
    onNavigateToAbout: () -> Unit
) {
    var query by remember { mutableStateOf("") }

    val routeNav = mapOf(
        APPEARANCE_ROUTE to onNavigateToAppearance,
        PERFORMANCE_ROUTE to onNavigateToPerformance,
        FEED_SETTINGS_ROUTE to onNavigateToFeed,
        PRIVACY_ROUTE to onNavigateToPrivacy,
    )

    val filtered = remember(query) {
        if (query.isBlank()) emptyList()
        else allSettingEntries.filter {
            it.title.contains(query, ignoreCase = true) || it.subtitle.contains(query, ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
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
                            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("无匹配结果", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    items(filtered.size) { i ->
                        val entry = filtered[i]
                        Card(
                            modifier = Modifier.fillMaxWidth()
                                .clickable { routeNav[entry.route]?.invoke() }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(entry.title, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        entry.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            } else {
                item {
                    SettingCategory(
                        icon = Icons.Default.Edit,
                        title = "外观设计",
                        subtitle = "主题、颜色、字体",
                        onClick = onNavigateToAppearance
                    )
                }
                item {
                    SettingCategory(
                        icon = Icons.Default.Settings,
                        title = "性能设置",
                        subtitle = "刷新率、流畅度",
                        onClick = onNavigateToPerformance
                    )
                }
                item {
                    SettingCategory(
                        icon = Icons.Default.PlayArrow,
                        title = "播放设置",
                        subtitle = "画质、弹幕、自动播放",
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
                        subtitle = "历史记录、缓存管理",
                        onClick = onNavigateToPrivacy
                    )
                }
                item {
                    SettingCategory(
                        icon = Icons.Default.Info,
                        title = "关于",
                        subtitle = "版本信息、开源许可",
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
            }
        }
    }
}
