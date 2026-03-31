package com.naaammme.bbspace.feature.settings.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naaammme.bbspace.feature.settings.components.SettingSwitch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSettingsScreen(
    onBack: () -> Unit,
    viewModel: PlayerSettingsViewModel = hiltViewModel()
) {
    val minBufferMs by viewModel.minBufferMs.collectAsStateWithLifecycle()
    val maxBufferMs by viewModel.maxBufferMs.collectAsStateWithLifecycle()
    val playbackBufferMs by viewModel.playbackBufferMs.collectAsStateWithLifecycle()
    val rebufferMs by viewModel.rebufferMs.collectAsStateWithLifecycle()
    val backBufferMs by viewModel.backBufferMs.collectAsStateWithLifecycle()
    val preferSoftwareDecode by viewModel.preferSoftwareDecode.collectAsStateWithLifecycle()
    val decoderFallback by viewModel.decoderFallback.collectAsStateWithLifecycle()
    val backgroundPlayback by viewModel.backgroundPlayback.collectAsStateWithLifecycle()

    var dialog by remember { mutableStateOf<PlayerDialog?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("播放器设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SectionTitle("缓冲")

            PlayerValueItem(
                title = "最小缓冲时长",
                subtitle = "持续补缓冲的最低时长",
                value = formatBufferMs(minBufferMs),
                onClick = { dialog = PlayerDialog.MinBuffer }
            )

            PlayerValueItem(
                title = "最大缓冲时长",
                subtitle = "播放器最多预读多久",
                value = formatBufferMs(maxBufferMs),
                onClick = { dialog = PlayerDialog.MaxBuffer }
            )

            PlayerValueItem(
                title = "起播缓冲时长",
                subtitle = "开始播放前至少缓冲多久",
                value = formatBufferMs(playbackBufferMs),
                onClick = { dialog = PlayerDialog.PlayBuffer }
            )

            PlayerValueItem(
                title = "重缓冲恢复时长",
                subtitle = "卡顿后恢复播放前至少缓冲多久",
                value = formatBufferMs(rebufferMs),
                onClick = { dialog = PlayerDialog.Rebuffer }
            )

            PlayerValueItem(
                title = "回看缓冲时长",
                subtitle = "保留已播内容用于回退拖动",
                value = formatBufferMs(backBufferMs),
                onClick = { dialog = PlayerDialog.BackBuffer }
            )

            SectionTitle("解码")

            SettingSwitch(
                title = "软解优先",
                subtitle = "关闭时使用硬解优先",
                checked = preferSoftwareDecode,
                onCheckedChange = viewModel::updatePreferSoftwareDecode
            )

            SettingSwitch(
                title = "解码失败自动回退",
                subtitle = "允许切到低优先级解码器",
                checked = decoderFallback,
                onCheckedChange = viewModel::updateDecoderFallback
            )

            SectionTitle("后台")

            SettingSwitch(
                title = "后台播放",
                subtitle = "退到后台时继续播放 当前未接入系统通知控制",
                checked = backgroundPlayback,
                onCheckedChange = viewModel::updateBackgroundPlayback
            )
        }
    }

    when (dialog) {
        PlayerDialog.MinBuffer -> {
            IntOptionDialog(
                title = "选择最小缓冲时长",
                currentValue = minBufferMs,
                options = listOf(5_000, 10_000, 15_000, 30_000, 60_000),
                label = ::formatBufferMs,
                onSelect = viewModel::updateMinBufferMs,
                onDismiss = { dialog = null }
            )
        }

        PlayerDialog.MaxBuffer -> {
            IntOptionDialog(
                title = "选择最大缓冲时长",
                currentValue = maxBufferMs,
                options = listOf(15_000, 30_000, 60_000, 90_000, 120_000),
                label = ::formatBufferMs,
                onSelect = viewModel::updateMaxBufferMs,
                onDismiss = { dialog = null }
            )
        }

        PlayerDialog.PlayBuffer -> {
            IntOptionDialog(
                title = "选择起播缓冲时长",
                currentValue = playbackBufferMs,
                options = listOf(250, 500, 1_000, 1_500, 2_000),
                label = ::formatBufferMs,
                onSelect = viewModel::updatePlaybackBufferMs,
                onDismiss = { dialog = null }
            )
        }

        PlayerDialog.Rebuffer -> {
            IntOptionDialog(
                title = "选择重缓冲恢复时长",
                currentValue = rebufferMs,
                options = listOf(500, 750, 1_000, 1_500, 2_000, 3_000),
                label = ::formatBufferMs,
                onSelect = viewModel::updateRebufferMs,
                onDismiss = { dialog = null }
            )
        }

        PlayerDialog.BackBuffer -> {
            IntOptionDialog(
                title = "选择回看缓冲时长",
                currentValue = backBufferMs,
                options = listOf(0, 5_000, 10_000, 15_000, 30_000),
                label = ::formatBufferMs,
                onSelect = viewModel::updateBackBufferMs,
                onDismiss = { dialog = null }
            )
        }

        null -> Unit
    }
}

private enum class PlayerDialog {
    MinBuffer,
    MaxBuffer,
    PlayBuffer,
    Rebuffer,
    BackBuffer
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun PlayerValueItem(
    title: String,
    subtitle: String,
    value: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun IntOptionDialog(
    title: String,
    currentValue: Int,
    options: List<Int>,
    label: (Int) -> String,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { option ->
                    Text(
                        text = if (option == currentValue) "✓ ${label(option)}" else label(option),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(option)
                                onDismiss()
                            }
                            .padding(vertical = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

private fun formatBufferMs(value: Int): String {
    return when {
        value == 0 -> "关闭"
        value < 1_000 -> "${value}ms"
        value % 1_000 == 0 -> "${value / 1_000}s"
        else -> "${value / 1_000f}s"
    }
}
