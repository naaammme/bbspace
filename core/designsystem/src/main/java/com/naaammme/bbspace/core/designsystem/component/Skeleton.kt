package com.naaammme.bbspace.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun SkeletonBlock(
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    color: Color = Color.Unspecified
) {
    val blockColor = if (color == Color.Unspecified) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        color
    }
    Box(modifier = modifier.background(color = blockColor, shape = shape))
}

@Composable
fun VideoGridCardSkeleton(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        SkeletonBlock(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 10f)
        )
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            SkeletonBlock(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .height(16.dp),
                shape = MaterialTheme.shapes.extraSmall
            )
            Spacer(modifier = Modifier.height(6.dp))
            SkeletonBlock(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(14.dp),
                shape = MaterialTheme.shapes.extraSmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SkeletonBlock(
                    modifier = Modifier
                        .width(60.dp)
                        .height(12.dp),
                    shape = MaterialTheme.shapes.extraSmall
                )
                SkeletonBlock(
                    modifier = Modifier
                        .width(60.dp)
                        .height(12.dp),
                    shape = MaterialTheme.shapes.extraSmall
                )
            }
        }
    }
}

@Composable
fun VideoListCardSkeleton(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.large
            )
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SkeletonBlock(
            modifier = Modifier
                .weight(0.38f)
                .aspectRatio(16f / 10f),
            shape = MaterialTheme.shapes.medium
        )
        Column(
            modifier = Modifier.weight(0.62f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SkeletonBlock(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .height(18.dp),
                shape = MaterialTheme.shapes.extraSmall
            )
            SkeletonBlock(
                modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .height(18.dp),
                shape = MaterialTheme.shapes.extraSmall
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SkeletonBlock(
                    modifier = Modifier
                        .width(72.dp)
                        .height(12.dp),
                    shape = MaterialTheme.shapes.extraSmall
                )
                SkeletonBlock(
                    modifier = Modifier
                        .width(58.dp)
                        .height(12.dp),
                    shape = MaterialTheme.shapes.extraSmall
                )
            }
        }
    }
}

@Composable
fun CommentHeaderSkeleton(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        SkeletonBlock(
            modifier = Modifier
                .width(104.dp)
                .height(20.dp),
            shape = MaterialTheme.shapes.extraSmall
        )
        SkeletonBlock(
            modifier = Modifier
                .width(60.dp)
                .height(32.dp),
            shape = MaterialTheme.shapes.extraLarge
        )
    }
}

@Composable
fun CommentCardSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.large
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            SkeletonBlock(
                modifier = Modifier.size(44.dp),
                shape = CircleShape
            )
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SkeletonBlock(
                        modifier = Modifier
                            .fillMaxWidth(0.34f)
                            .height(18.dp),
                        shape = MaterialTheme.shapes.extraSmall
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SkeletonChip(width = 52.dp)
                        SkeletonChip(width = 68.dp)
                    }
                }
                SkeletonChip(width = 56.dp)
            }
        }
        Column(
            modifier = Modifier.padding(start = 56.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SkeletonBlock(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .height(16.dp),
                shape = MaterialTheme.shapes.extraSmall
            )
            SkeletonBlock(
                modifier = Modifier
                    .fillMaxWidth(0.66f)
                    .height(16.dp),
                shape = MaterialTheme.shapes.extraSmall
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SkeletonBlock(
                        modifier = Modifier
                            .width(56.dp)
                            .height(14.dp),
                        shape = MaterialTheme.shapes.extraSmall
                    )
                    SkeletonBlock(
                        modifier = Modifier
                            .width(52.dp)
                            .height(14.dp),
                        shape = MaterialTheme.shapes.extraSmall
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                SkeletonBlock(
                    modifier = Modifier
                        .width(32.dp)
                        .height(18.dp),
                    shape = MaterialTheme.shapes.extraSmall
                )
            }
        }
    }
}

@Composable
fun VideoDetailInfoSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = MaterialTheme.shapes.extraLarge
                )
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SkeletonBlock(
                modifier = Modifier
                    .width(72.dp)
                    .aspectRatio(1f),
                shape = MaterialTheme.shapes.large
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SkeletonBlock(
                    modifier = Modifier
                        .fillMaxWidth(0.4f)
                        .height(20.dp),
                    shape = MaterialTheme.shapes.extraSmall
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SkeletonChip(width = 76.dp)
                    SkeletonChip(width = 88.dp)
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = MaterialTheme.shapes.extraLarge
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SkeletonBlock(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .height(22.dp),
                shape = MaterialTheme.shapes.extraSmall
            )
            SkeletonBlock(
                modifier = Modifier
                    .fillMaxWidth(0.58f)
                    .height(22.dp),
                shape = MaterialTheme.shapes.extraSmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SkeletonChip(width = 64.dp)
                SkeletonChip(width = 92.dp)
                SkeletonChip(width = 84.dp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SkeletonChip(width = 78.dp)
                SkeletonChip(width = 70.dp)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = MaterialTheme.shapes.extraLarge
                )
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ActionSkeletonChip()
            ActionSkeletonChip()
            ActionSkeletonChip()
        }

        DetailEntryCardSkeleton()
        DetailEntryCardSkeleton()
    }
}

@Composable
fun VideoRelateCardSkeleton(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.large
            )
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        SkeletonBlock(
            modifier = Modifier
                .weight(0.38f)
                .aspectRatio(16f / 10f),
            shape = MaterialTheme.shapes.large
        )
        Column(
            modifier = Modifier.weight(0.62f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SkeletonBlock(
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    .height(18.dp),
                shape = MaterialTheme.shapes.extraSmall
            )
            SkeletonBlock(
                modifier = Modifier
                    .fillMaxWidth(0.76f)
                    .height(18.dp),
                shape = MaterialTheme.shapes.extraSmall
            )
            SkeletonBlock(
                modifier = Modifier
                    .fillMaxWidth(0.46f)
                    .height(14.dp),
                shape = MaterialTheme.shapes.extraSmall
            )
        }
    }
}

@Composable
private fun SkeletonChip(width: Dp) {
    SkeletonBlock(
        modifier = Modifier
            .width(width)
            .height(28.dp),
        shape = MaterialTheme.shapes.extraLarge
    )
}

@Composable
private fun ActionSkeletonChip() {
    SkeletonBlock(
        modifier = Modifier
            .width(84.dp)
            .height(34.dp),
        shape = MaterialTheme.shapes.extraLarge
    )
}

@Composable
private fun DetailEntryCardSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.large
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SkeletonBlock(
            modifier = Modifier
                .fillMaxWidth(0.28f)
                .height(20.dp),
            shape = MaterialTheme.shapes.extraSmall
        )
        SkeletonBlock(
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .height(18.dp),
            shape = MaterialTheme.shapes.extraSmall
        )
        SkeletonBlock(
            modifier = Modifier
                .fillMaxWidth(0.46f)
                .height(16.dp),
            shape = MaterialTheme.shapes.extraSmall
        )
    }
}
