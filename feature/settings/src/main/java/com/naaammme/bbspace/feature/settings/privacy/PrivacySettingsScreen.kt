package com.naaammme.bbspace.feature.settings.privacy

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naaammme.bbspace.core.designsystem.component.SettingSectionHeader
import com.naaammme.bbspace.core.designsystem.component.SettingsPageScaffold
import com.naaammme.bbspace.feature.settings.SettingSwitch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun PrivacySettingsScreen(
    onBack: () -> Unit,
    viewModel: PrivacyViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(state.importResult) {
        state.importResult?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearImportResult()
        }
    }

    SettingsPageScaffold(title = "隐私安全", onBack = onBack) {
        // Gaia 风控上报
        item(key = "header_gaia") {
            SettingSectionHeader("风控上报")
        }
        item(key = "block_gaia") {
            SettingSwitch(
                title = "禁止 Gaia 上报",
                subtitle = "阻止应用列表风控数据上报",
                setting = viewModel.blockGaia
            )
        }

        // 设备信息
        item(key = "header_device") {
            SettingSectionHeader("设备信息")
        }
        item(key = "device_info") {
            InfoCard(state.deviceInfo.map { (k, v) -> k to v })
        }

        // 会话信息
        item(key = "header_session") {
            SettingSectionHeader("会话信息")
        }
        item(key = "session_info") {
            InfoCard(state.sessionInfo.map { (k, v) -> k to v })
        }

        // Ticket 缓存
        item(key = "header_ticket") {
            SettingSectionHeader("Ticket 缓存")
        }
        item(key = "ticket_info") {
            val ticketEntries = state.ticketInfo.map { (k, v) ->
                if (k == "expireAt" && v != "0") {
                    k to formatTimestamp(v.toLongOrNull() ?: 0)
                } else {
                    k to v
                }
            }
            InfoCard(ticketEntries)
        }

        // Guest 缓存
        item(key = "header_guest") {
            SettingSectionHeader("Guest 缓存")
        }
        item(key = "guest_info") {
            val guestEntries = state.guestInfo.map { (k, v) ->
                if (k == "cacheTime" && v != "0") {
                    k to formatTimestamp(v.toLongOrNull() ?: 0)
                } else {
                    k to v
                }
            }
            InfoCard(guestEntries)
        }

        // 地区码
        item(key = "header_region") {
            SettingSectionHeader("地区码")
        }
        item(key = "region_info") {
            InfoCard(listOf("regionCode" to state.regionCode))
        }

        // 缓存管理
        item(key = "header_cache") {
            SettingSectionHeader("缓存管理")
        }
        item(key = "cache_actions") {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            viewModel.clearImageCache()
                            Toast.makeText(context, "已清除图片缓存", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("清除图片缓存")
                    }
                    Button(
                        onClick = {
                            viewModel.clearAllCache()
                            Toast.makeText(context, "已清除所有缓存", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("清除所有缓存")
                    }
                }
            }
        }

        // HD 鉴权
        item(key = "header_hd") {
            SettingSectionHeader("HD 鉴权")
        }
        item(key = "hd_info") {
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
        item(key = "header_accounts") {
            SettingSectionHeader("账号凭证 (${state.accounts.size})")
        }
        state.accounts.forEach { account ->
            item(key = "account_${account.mid}") {
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
        item(key = "account_actions") {
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
        item(key = "bottom_spacer") { Spacer(modifier = Modifier.height(16.dp)) }
    }
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
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun formatTimestamp(ms: Long): String {
    if (ms == 0L) return "--"
    return TIMESTAMP_FORMAT.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))
}

private val TIMESTAMP_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
