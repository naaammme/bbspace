package com.naaammme.bbspace.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun <T> CapsuleGroup(
    items: List<T>,
    modifier: Modifier = Modifier,
    gap: Dp = 2.dp,
    itemContent: @Composable (item: T, index: Int, shape: Shape) -> Unit
) {
    val shapes = MaterialTheme.shapes

    Column(modifier = modifier) {
        items.forEachIndexed { index, item ->
            itemContent(item, index, capsuleGroupShape(index, items.size, shapes))
            if (index < items.lastIndex) {
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(gap)
                )
            }
        }
    }
}

@Composable
fun CapsuleListItem(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String? = null,
    shape: Shape = MaterialTheme.shapes.medium,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 84.dp)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingContent != null) {
                Box(
                    modifier = Modifier.padding(end = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    leadingContent()
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (trailingContent != null) {
                Box(
                    modifier = Modifier.padding(start = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    trailingContent()
                }
            }
        }
    }
}

private fun capsuleGroupShape(index: Int, count: Int, shapes: Shapes): Shape {
    val outer = shapes.large
    val inner = shapes.extraSmall
    return when {
        count <= 1 -> outer
        index == 0 -> outer.copy(
            bottomStart = inner.bottomStart,
            bottomEnd = inner.bottomEnd
        )
        index == count - 1 -> outer.copy(
            topStart = inner.topStart,
            topEnd = inner.topEnd
        )
        else -> inner
    }
}