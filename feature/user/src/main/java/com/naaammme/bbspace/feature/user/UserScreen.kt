package com.naaammme.bbspace.feature.user

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.naaammme.bbspace.core.common.media.thumbnailUrl
import com.naaammme.bbspace.core.designsystem.component.CollapsingTopBarScaffold
import com.naaammme.bbspace.core.model.SpaceRoute
import com.naaammme.bbspace.core.model.User
import com.naaammme.bbspace.feature.user.model.UserViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserScreen(
    onNavigateToAccount: () -> Unit,
    onNavigateToBbSpace: () -> Unit,
    onOpenSpace: (SpaceRoute) -> Unit = {},
    vm: UserViewModel = hiltViewModel()
) {
    val user by vm.user.collectAsStateWithLifecycle()
    val showAccountExpiredDialog by vm.showAccountExpiredDialog.collectAsStateWithLifecycle()

    CollapsingTopBarScaffold(
        topBar = { scrollBehavior ->
            TopAppBar(
                title = { Text("我的") },
                actions = {
                    IconButton(onClick = onNavigateToAccount) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = "账号管理"
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            UserInfoSection(
                user = user,
                onOpenSpace = onOpenSpace
            )

            Spacer(modifier = Modifier.height(20.dp))

            SocialStatRow(user)

            Spacer(modifier = Modifier.height(16.dp))

            FeatureEntryRow()

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = onNavigateToBbSpace,
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.DateRange,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "bb空间",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }

    if (showAccountExpiredDialog) {
        AlertDialog(
            onDismissRequest = vm::dismissAccountExpiredDialog,
            title = { Text("账号已过期") },
            text = { Text("当前账号已经过期，请删除账号。") },
            confirmButton = {
                TextButton(onClick = vm::dismissAccountExpiredDialog) {
                    Text("知道了")
                }
            }
        )
    }
}

@Composable
private fun UserInfoSection(
    user: User?,
    onOpenSpace: (SpaceRoute) -> Unit
) {
    val spaceRoute = user?.let {
        if (it.mid <= 0L && it.name.isBlank()) {
            null
        } else {
            SpaceRoute(
                mid = it.mid,
                name = it.name.takeIf(String::isNotBlank)
            )
        }
    }
    val avatarClickModifier = if (spaceRoute == null) {
        Modifier
    } else {
        Modifier.clickable { onOpenSpace(spaceRoute) }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (user?.avatar?.isNotEmpty() == true) {
            AsyncImage(
                model = thumbnailUrl(user.avatar),
                contentDescription = null,
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .then(avatarClickModifier)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    .then(avatarClickModifier),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = user?.name ?: "未登录",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LabelValue("硬币", user?.coins?.let { if (it == 0.0) "--" else it.toString() } ?: "--")
                LabelValue("等级", "Lv${user?.level ?: 0}")
            }
        }
    }
}

@Composable
private fun LabelValue(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "$label ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun SocialStatRow(user: User?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem(user?.dynamic?.toString() ?: "--", "动态")
            StatItem(user?.following?.toString() ?: "--", "关注")
            StatItem(user?.follower?.toString() ?: "--", "粉丝")
        }
    }
}

@Composable
private fun StatItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun FeatureEntryRow() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            FeatureEntry(Icons.Default.Refresh, "离线缓存")
            FeatureEntry(Icons.Default.DateRange, "历史记录")
            FeatureEntry(Icons.Default.FavoriteBorder, "收藏")
            FeatureEntry(Icons.Default.Star, "稍后再看")
        }
    }
}

@Composable
private fun FeatureEntry(icon: ImageVector, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(28.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center
        )
    }
}
