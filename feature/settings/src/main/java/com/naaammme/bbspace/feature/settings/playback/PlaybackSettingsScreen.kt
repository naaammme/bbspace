package com.naaammme.bbspace.feature.settings.playback

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
fun PlaybackSettingsScreen(
    onBack: () -> Unit,
    viewModel: PlaybackSettingsViewModel = hiltViewModel()
) {
    val enableHdrAnd8k by viewModel.enableHdrAnd8k.collectAsStateWithLifecycle()
    val defaultVideoQuality by viewModel.defaultVideoQuality.collectAsStateWithLifecycle()
    val defaultAudioQuality by viewModel.defaultAudioQuality.collectAsStateWithLifecycle()
    val forceHost by viewModel.forceHost.collectAsStateWithLifecycle()
    val needTrial by viewModel.needTrial.collectAsStateWithLifecycle()
    val preferredCodec by viewModel.preferredCodec.collectAsStateWithLifecycle()

    var showVideoQualityDialog by remember { mutableStateOf(false) }
    var showAudioQualityDialog by remember { mutableStateOf(false) }
    var showCodecDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("播放设置")
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
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
            SettingCategory(title = "画质")

            SettingSwitch(
                title = "启用 HDR 和 8K",
                subtitle = "允许请求 HDR 和 8K 视频流（如果视频支持）",
                checked = enableHdrAnd8k,
                onCheckedChange = { viewModel.updateEnableHdrAnd8k(it) }
            )

            SettingCategory(title = "本地选择")

            QualityItem(
                title = "默认视频画质",
                quality = defaultVideoQuality,
                onClick = { showVideoQualityDialog = true }
            )

            QualityItem(
                title = "默认音频质量",
                quality = defaultAudioQuality,
                onClick = { showAudioQualityDialog = true }
            )

            SettingCategory(title = "其他")

            QualityItem(
                title = "优先编码格式",
                quality = preferredCodec,
                label = getCodecName(preferredCodec),
                onClick = { showCodecDialog = true }
            )

            SettingSwitch(
                title = "使用https播放",
                checked = forceHost > 0,
                onCheckedChange = { viewModel.updateForceHost(if (it) 1 else 0) }
            )

            SettingSwitch(
                title = "需要4k",
                checked = needTrial,
                onCheckedChange = { viewModel.updateNeedTrial(it) }
            )
        }

        if (showVideoQualityDialog) {
            VideoQualityDialog(
                currentQuality = defaultVideoQuality,
                onSelect = { viewModel.updateDefaultVideoQuality(it) },
                onDismiss = { showVideoQualityDialog = false }
            )
        }

        if (showAudioQualityDialog) {
            AudioQualityDialog(
                currentQuality = defaultAudioQuality,
                onSelect = { viewModel.updateDefaultAudioQuality(it) },
                onDismiss = { showAudioQualityDialog = false }
            )
        }

        if (showCodecDialog) {
            CodecDialog(
                currentCodec = preferredCodec,
                onSelect = { viewModel.updatePreferredCodec(it) },
                onDismiss = { showCodecDialog = false }
            )
        }
    }
}

@Composable
fun SettingCategory(title: String) {
    Text(
        text = title,
        style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
        color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
fun QualityItem(
    title: String,
    quality: Int,
    onClick: () -> Unit,
    label: String? = null
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
            Text(title)
            Text(
                text = label ?: when {
                    quality == 0 -> "自动"
                    quality < 100 -> getVideoQualityName(quality)
                    else -> getAudioQualityName(quality)
                },
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun VideoQualityDialog(
    currentQuality: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val qualities = listOf(16, 32, 64, 80, 112, 116, 120, 125, 126, 127)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择默认视频画质") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                qualities.forEach { quality ->
                    Text(
                        text = if (quality == currentQuality) "✓ ${getVideoQualityName(quality)}" else getVideoQualityName(quality),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(quality); onDismiss() }
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

@Composable
fun AudioQualityDialog(
    currentQuality: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val qualities = listOf(0, 30216, 30232, 30280, 30250, 30251)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择默认音频质量") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                qualities.forEach { quality ->
                    Text(
                        text = if (quality == currentQuality) "✓ ${getAudioQualityName(quality)}" else getAudioQualityName(quality),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(quality); onDismiss() }
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

private fun getVideoQualityName(quality: Int): String {
    return when (quality) {
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
}

private fun getAudioQualityName(quality: Int): String {
    return when (quality) {
        0 -> "自动"
        30216 -> "64K"
        30232 -> "132K"
        30280 -> "192K"
        30250 -> "杜比全景声"
        30251 -> "Hi-Res 无损"
        else -> "未知"
    }
}

@Composable
fun CodecDialog(
    currentCodec: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val codecs = listOf(1, 2, 3)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择优先编码格式") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                codecs.forEach { codec ->
                    Text(
                        text = if (codec == currentCodec) "✓ ${getCodecName(codec)}" else getCodecName(codec),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(codec); onDismiss() }
                            .padding(vertical = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

private fun getCodecName(codec: Int): String {
    return when (codec) {
        1 -> "AVC/H.264"
        2 -> "HEVC/H.265"
        3 -> "AV1"
        else -> "未知"
    }
}
