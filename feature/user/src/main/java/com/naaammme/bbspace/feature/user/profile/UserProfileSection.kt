package com.naaammme.bbspace.feature.user.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.naaammme.bbspace.core.designsystem.component.AvatarImage
import com.naaammme.bbspace.core.model.SpaceRoute
import com.naaammme.bbspace.core.model.User

@Composable
fun UserProfileSection(
    user: User?,
    onOpenSpace: (SpaceRoute) -> Unit
) {
    UserInfoSection(
        user = user,
        onOpenSpace = onOpenSpace
    )

    Spacer(modifier = Modifier.height(20.dp))

    SocialStatRow(user = user)
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
        AvatarImage(
            url = user?.avatar?.takeIf(String::isNotBlank),
            contentDescription = user?.name ?: "未登录",
            modifier = Modifier
                .size(72.dp)
                .then(avatarClickModifier),
            fallbackContent = {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )

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
