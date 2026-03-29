package com.naaammme.bbspace.feature.settings.privacy

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.ui.Alignment
import androidx.compose.runtime.collectAsState
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.naaammme.bbspace.feature.settings.privacy.PrivacyViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacySettingsScreen(
    onBack: () -> Unit,
    viewModel: PrivacyViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val blockGaia by viewModel.blockGaia.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(state.importResult) {
        state.importResult?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearImportResult()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("隐私安全") },
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
            // Gaia 风控上报
            item {
                SectionTitle("风控上报")
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("禁止 Gaia 上报", style = MaterialTheme.typography.bodyLarge)
                            Text("阻止应用列表风控数据上报", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = blockGaia, onCheckedChange = { viewModel.setBlockGaia(it) })
                    }
                }
            }

            // 设备信息
            item {
                SectionTitle("设备信息")
            }
            item {
                InfoCard(state.deviceInfo.map { (k, v) -> k to v })
            }

            // 会话信息
            item {
                SectionTitle("会话信息")
            }
            item {
                InfoCardLongPress(
                    entries = state.sessionInfo.map { (k, v) -> k to v },
                    onLongPress = {
                        viewModel.clearSessionCache()
                        Toast.makeText(context, "已清除会话信息", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            // Ticket 缓存
            item {
                SectionTitle("Ticket 缓存 ")
            }
            item {
                val ticketEntries = state.ticketInfo.map { (k, v) ->
                    if (k == "expireAt" && v != "0") {
                        k to formatTimestamp(v.toLongOrNull() ?: 0)
                    } else {
                        k to v
                    }
                }
                InfoCardLongPress(
                    entries = ticketEntries,
                    onLongPress = {
                        viewModel.clearTicketCache()
                        Toast.makeText(context, "已清除 Ticket 缓存", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            // Guest 缓存
            item {
                SectionTitle("Guest 缓存")
            }
            item {
                val guestEntries = state.guestInfo.map { (k, v) ->
                    if (k == "cacheTime" && v != "0") {
                        k to formatTimestamp(v.toLongOrNull() ?: 0)
                    } else {
                        k to v
                    }
                }
                InfoCardLongPress(
                    entries = guestEntries,
                    onLongPress = {
                        viewModel.clearGuestCache()
                        Toast.makeText(context, "已清除 Guest 缓存", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            // 地区码
            item {
                SectionTitle("地区码 ")
            }
            item {
                InfoCardLongPress(
                    entries = listOf("regionCode" to state.regionCode),
                    onLongPress = {
                        viewModel.clearRegionCache()
                        Toast.makeText(context, "已清除地区码", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            // 缓存管理
            item {
                SectionTitle("缓存管理")
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                viewModel.clearImageCache()
                                Toast.makeText(context, "已清除图片缓存", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("清除图片缓存")
                        }
                        Button(
                            onClick = {
                                viewModel.clearAllCache()
                                Toast.makeText(context, "已清除所有缓存", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("清除所有缓存")
                        }
                    }
                }
            }

            // HD 鉴权
            item {
                SectionTitle("HD 鉴权")
            }
            item {
                val hdKey = state.hdInfo["hdAccessKey"].orEmpty()
                val hdMasked = if (hdKey.isNotEmpty()) hdKey.take(20) + "..." else "--"
                InfoCard(
                    entries = listOf(
                        "currentMid" to (state.hdInfo["currentMid"] ?: "0"),
                        "hasHdAccessKey" to (state.hdInfo["hasHdAccessKey"] ?: "false"),
                        "hdAccessKey" to hdMasked
                    )
                )
            }

            // 账号凭证
            item {
                SectionTitle("账号凭证 (${state.accounts.size})")
            }
            state.accounts.forEach { account ->
                item {
                    val isCurrent = account.mid == state.currentMid
                    InfoCard(
                        entries = listOf(
                            "mid" to "${account.mid}${if (isCurrent) " (当前)" else ""}",
                            "accessToken" to account.accessToken.take(20) + "...",
                            "refreshToken" to account.refreshToken.take(20) + "...",
                            "cookies" to "${account.cookies.size} 个"
                        )
                    )
                }
            }

            // 导出/导入按钮
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val json = viewModel.exportAllAccounts()
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("accounts", json))
                            Toast.makeText(context, "已复制到剪切板", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("导出到剪切板")
                    }
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = clipboard.primaryClip
                            val text = clip?.getItemAt(0)?.text?.toString()
                            if (text.isNullOrBlank()) {
                                Toast.makeText(context, "剪切板为空", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.importAccounts(text)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("从剪切板导入")
                    }
                }
            }

            // 底部留白
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun InfoCard(entries: List<Pair<String, String>>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            entries.forEach { (key, value) ->
                InfoRow(key, value)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InfoCardLongPress(
    entries: List<Pair<String, String>>,
    onLongPress: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = onLongPress
            )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            entries.forEach { (key, value) ->
                InfoRow(key, value)
            }
        }
    }
}

@Composable
private fun InfoCardWithAction(
    entries: List<Pair<String, String>>,
    actionLabel: String,
    onAction: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            entries.forEach { (key, value) ->
                InfoRow(key, value)
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onAction
            ) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun InfoRow(key: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Text(
            text = key,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(100.dp)
        )
        Text(
            text = value.ifEmpty { "--" },
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun formatTimestamp(ms: Long): String {
    if (ms == 0L) return "--"
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(ms))
}
