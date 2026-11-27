package com.naaammme.bbspace.feature.settings.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naaammme.bbspace.core.designsystem.component.SettingActionCard
import com.naaammme.bbspace.core.designsystem.component.SettingSwitchCard
import com.naaammme.bbspace.core.designsystem.component.SettingsPageScaffold
import com.naaammme.bbspace.core.designsystem.component.bindSetting
import com.naaammme.bbspace.core.settings.Setting
import com.naaammme.bbspace.feature.settings.SettingSwitch
import kotlin.math.roundToInt

@Composable
fun FeedSettingsScreen(
    onBack: () -> Unit,
    onNavigateToInterest: () -> Unit,
    viewModel: FeedSettingsViewModel = hiltViewModel()
) {
    val prefs = viewModel.feedPrefs
    val hdFeed by viewModel.hdFeed.collectAsStateWithLifecycle()
    val hdFeedAvailable by viewModel.hdFeedAvailable.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.refreshHdFeedAvailable()
    }

    SettingsPageScaffold(title = "推荐设置", onBack = onBack) {
        item(key = "hd_feed") {
            SettingSwitchCard(
                title = "HD 推荐模式",
                subtitle = if (hdFeedAvailable) {
                    "切换 HD 推荐接口，每页返回更多条目"
                } else {
                    "需先扫码绑定当前账号 HD key"
                },
                checked = hdFeed,
                enabled = hdFeedAvailable,
                onCheckedChange = viewModel::updateHdFeed
            )
        }
        item(key = "personalized_rcmd") {
            SettingSwitch(
                title = "个性化推荐",
                subtitle = "基于观看历史推荐内容，关闭后随机推荐",
                setting = prefs.personalizedRcmd
            )
        }
        item(key = "lessons_mode") {
            SettingSwitch(
                title = "课堂推荐模式",
                subtitle = "只推荐学习相关视频",
                setting = prefs.lessonsMode
            )
        }
        item(key = "teenagers_mode") {
            TeenagersModeCard(
                modeSetting = prefs.teenagersMode,
                ageSetting = prefs.teenagersAge
            )
        }
        item(key = "interest") {
            SettingActionCard(
                title = "内容偏好调节",
                subtitle = "查看和调整你的内容兴趣标签",
                onClick = onNavigateToInterest
            )
        }
    }
}

@Composable
private fun TeenagersModeCard(
    modeSetting: Setting<Boolean>,
    ageSetting: Setting<Int>
) {
    val (teenagersMode, setTeenagersMode) = modeSetting.flow.bindSetting(modeSetting.current, modeSetting::commit)
    val (teenagersAge, setTeenagersAge) = ageSetting.flow.bindSetting(ageSetting.current, ageSetting::commit)
    var teenagersAgeDraft by remember(teenagersAge) { mutableFloatStateOf(teenagersAge.toFloat()) }
    val teenagersAgeText = teenagersAgeDraft.roundToInt().coerceIn(1, 17).toString()

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
                    onCheckedChange = setTeenagersMode
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
                    val nextAge = teenagersAgeDraft.roundToInt()
                    if (nextAge != teenagersAge) {
                        setTeenagersAge(nextAge)
                    }
                },
                enabled = teenagersMode,
                valueRange = 1f..17f,
                steps = 15,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
                )
            )
        }
    }
}
