package com.naaammme.bbspace.feature.settings.performance

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naaammme.bbspace.core.designsystem.component.CollapsingTopBarScaffold
import com.naaammme.bbspace.core.designsystem.theme.FrameRateMode
import com.naaammme.bbspace.feature.settings.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerformanceSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val config by viewModel.themeConfig.collectAsStateWithLifecycle()

    CollapsingTopBarScaffold(
        topBar = { scrollBehavior ->
            TopAppBar(
                title = { Text("性能设置") },
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                FrameRateSelector(
                    selected = config.preferredFrameRate,
                    onSelect = viewModel::updateFrameRateMode
                )
            }
        }
    }
}

@Composable
private fun FrameRateSelector(
    selected: FrameRateMode,
    onSelect: (FrameRateMode) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("屏幕刷新率", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "高刷新率可提升滑动流畅度，但会增加耗电",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            FrameRateMode.entries.forEach { mode ->
                FilterChip(
                    modifier = Modifier.fillMaxWidth(),
                    selected = selected == mode,
                    onClick = { onSelect(mode) },
                    label = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                when (mode) {
                                    FrameRateMode.AUTO -> "自动"
                                    FrameRateMode.RATE_60 -> "60Hz"
                                    FrameRateMode.RATE_90 -> "90Hz"
                                    FrameRateMode.RATE_120 -> "120Hz"
                                    FrameRateMode.RATE_144 -> "144Hz"
                                }
                            )
                            if (mode == FrameRateMode.AUTO) {
                                Text(
                                    "默认",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                )
                if (mode != FrameRateMode.entries.last()) {
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}
