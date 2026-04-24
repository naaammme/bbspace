package com.naaammme.bbspace.feature.settings.feed

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naaammme.bbspace.core.designsystem.component.CollapsingTopBarScaffold
import com.naaammme.bbspace.feature.settings.SettingsViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val hdFeed by viewModel.hdFeed.collectAsStateWithLifecycle()
    val hdFeedAvailable by viewModel.hdFeedAvailable.collectAsStateWithLifecycle()
    val personalizedRcmd by viewModel.personalizedRcmd.collectAsStateWithLifecycle()
    val lessonsMode by viewModel.lessonsMode.collectAsStateWithLifecycle()
    val teenagersMode by viewModel.teenagersMode.collectAsStateWithLifecycle()
    val teenagersAge by viewModel.teenagersAge.collectAsStateWithLifecycle()
    var teenagersAgeDraft by remember(teenagersAge) { mutableFloatStateOf(teenagersAge.toFloat()) }
    val teenagersAgeText = teenagersAgeDraft.roundToInt().coerceIn(1, 17).toString()

    LaunchedEffect(Unit) {
        viewModel.refreshHdFeedAvailable()
    }

    CollapsingTopBarScaffold(
        topBar = { scrollBehavior ->
            TopAppBar(
                title = { Text("推荐设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
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
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("HD 推荐模式", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                if (hdFeedAvailable) {
                                    "切换 HD 推荐接口，每页返回更多条目"
                                } else {
                                    "需先扫码绑定当前账号 HD key"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = hdFeed,
                            enabled = hdFeedAvailable,
                            onCheckedChange = viewModel::updateHdFeed
                        )
                    }
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("个性化推荐", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "基于观看历史推荐内容，关闭后随机推荐",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = personalizedRcmd,
                            onCheckedChange = viewModel::updatePersonalizedRcmd
                        )
                    }
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("课堂推荐模式", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "只推荐学习相关视频",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = lessonsMode,
                            onCheckedChange = viewModel::updateLessonsMode
                        )
                    }
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("未成年推荐", style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "按指定年龄请求未成年推荐内容",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = teenagersMode,
                                onCheckedChange = viewModel::updateTeenagersMode
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "年龄",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (teenagersMode) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                            Text(
                                teenagersAgeText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (teenagersMode) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                        Slider(
                            value = teenagersAgeDraft,
                            onValueChange = { teenagersAgeDraft = it },
                            onValueChangeFinished = {
                                viewModel.updateTeenagersAge(teenagersAgeDraft.roundToInt())
                            },
                            enabled = teenagersMode,
                            valueRange = 1f..17f,
                            steps = 15
                        )
                    }
                }
            }
        }
    }
}
