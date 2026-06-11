package com.naaammme.bbspace.feature.user.collage

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShoppingCart

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import com.naaammme.bbspace.core.model.SpaceRoute
import com.naaammme.bbspace.core.model.User
import com.naaammme.bbspace.feature.user.UserDest

private object UserCollageShapes {
    val octagon = PolygonShape(sides = 8, rotationDeg = 22f, cornerScale = 0.14f)
    val hexagon = PolygonShape(sides = 6, rotationDeg = -18f, cornerScale = 0.2f)
    val statHexagon = PolygonShape(sides = 6, rotationDeg = -30f, cornerScale = 0.2f)
    val watchLaterStar = StarShape(points = 5, innerScale = 0.62f)
    val followerStar = StarShape(points = 6, innerScale = 0.78f, rotationDeg = -30f, cornerScale = 0.82f)
}

private object UserCollageFrames {
    val avatar = UserCollageFrame("avatar", 0.29f, 0.34f, Alignment.TopStart, 0.04f, 0.05f, -8f)
    val name = UserCollageFrame("name", 0.41f, 0.41f, Alignment.TopEnd, -0.03f, 0.05f, 7f)
    val dynamic = UserCollageFrame("dynamic", 0.29f, 0.29f, Alignment.CenterEnd, -0.05f, -0.11f, -9f)
    val following = UserCollageFrame("following", 0.31f, 0.20f, Alignment.CenterStart, 0.06f, 0.03f, -12f)
    val follower = UserCollageFrame("follower", 0.23f, 0.23f, Alignment.CenterEnd, -0.05f, 0.09f, 10f)
    val offline = UserCollageFrame("offline", 0.31f, 0.31f, Alignment.BottomStart, 0.03f, -0.24f, -7f)
    val history = UserCollageFrame("history", 0.31f, 0.31f, Alignment.BottomCenter, -0.02f, -0.25f, 13f)
    val favorite = UserCollageFrame("favorite", 0.31f, 0.31f, Alignment.BottomEnd, -0.03f, -0.24f, -10f)
    val watchLater = UserCollageFrame("watchLater", 0.35f, 0.35f, Alignment.BottomStart, 0.13f, -0.09f, 6f)
    val bbSpace = UserCollageFrame("bbSpace", 0.46f, 0.20f, Alignment.BottomEnd, -0.04f, -0.11f, -6f)
}

private data class UserCollageFrame(
    val key: String,
    val widthScale: Float,
    val heightScale: Float,
    val alignment: Alignment,
    val offsetXScale: Float,
    val offsetYScale: Float,
    val rotationZ: Float
)

@Composable
internal fun UserCollageSection(
    user: User?,
    collageOffsets: Map<String, Offset>,
    onCollageOffsetChange: (String, Offset) -> Unit,
    onOpenSpace: (SpaceRoute) -> Unit,
    onNavigateToBbSpace: () -> Unit,
    onNavigate: (UserDest) -> Unit,
    onNavigateToDownload: () -> Unit,
    palette: UserCollagePalette,
    modifier: Modifier = Modifier
) {
    val spaceRoute = user?.takeUnless { it.mid <= 0L && it.name.isBlank() }?.let {
        SpaceRoute(
            mid = it.mid,
            name = it.name.takeIf(String::isNotBlank)
        )
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
        val base = minOf(maxWidth, maxHeight)
        val extraLarge = MaterialTheme.shapes.extraLarge

        @Composable
        fun BoxScope.CollageItem(
            frame: UserCollageFrame,
            shape: Shape,
            color: Color,
            onClick: (() -> Unit)? = null,
            content: @Composable () -> Unit
        ) {
            UserCollageItem(
                frame = frame,
                base = base,
                shape = shape,
                color = color,
                collageOffsets = collageOffsets,
                onOffsetChange = onCollageOffsetChange,
                onClick = onClick,
                content = content
            )
        }

        CollageItem(
            frame = UserCollageFrames.avatar,
            shape = CircleShape,
            color = Color.Transparent,
            onClick = spaceRoute?.let { { onOpenSpace(it) } }
        ) {
            UserAvatarTileContent(user = user)
        }
        CollageItem(
            frame = UserCollageFrames.name,
            shape = UserCollageShapes.octagon,
            color = palette.tileHighlight
        ) {
            UserNameTileContent(user = user)
        }
        CollageItem(
            frame = UserCollageFrames.dynamic,
            shape = UserCollageShapes.statHexagon,
            color = palette.tilePrimary
        ) {
            UserStatTileContent(
                title = "动态",
                value = user?.dynamic?.toString() ?: "--"
            )
        }
        CollageItem(
            frame = UserCollageFrames.following,
            shape = extraLarge,
            color = palette.tileSurface
        ) {
            UserStatTileContent(
                title = "关注",
                value = user?.following?.toString() ?: "--"
            )
        }
        CollageItem(
            frame = UserCollageFrames.follower,
            shape = UserCollageShapes.followerStar,
            color = palette.tileSecondary
        ) {
            UserStatTileContent(
                title = "粉丝",
                value = user?.follower?.toString() ?: "--"
            )
        }
        CollageItem(
            frame = UserCollageFrames.offline,
            shape = extraLarge,
            color = palette.tilePrimary,
            onClick = onNavigateToDownload
        ) {
            UserEntryTileContent(
                icon = Icons.Default.Refresh,
                title = "离线缓存",
                subtitle = "本地视频"
            )
        }
        CollageItem(
            frame = UserCollageFrames.history,
            shape = UserCollageShapes.octagon,
            color = palette.tileSurface,
            onClick = { onNavigate(UserDest.History) }
        ) {
            UserEntryTileContent(
                icon = Icons.Default.DateRange,
                title = "历史记录",
                subtitle = "继续看"
            )
        }
        CollageItem(
            frame = UserCollageFrames.favorite,
            shape = UserCollageShapes.hexagon,
            color = palette.tileSecondary,
            onClick = { onNavigate(UserDest.Favorite) }
        ) {
            UserEntryTileContent(
                icon = Icons.Default.FavoriteBorder,
                title = "收藏",
                subtitle = "我的合集"
            )
        }
        CollageItem(
            frame = UserCollageFrames.watchLater,
            shape = UserCollageShapes.watchLaterStar,
            color = palette.tileHighlight,
            onClick = { onNavigate(UserDest.WatchLater) }
        ) {
            UserEntryTileContent(
                icon = null,
                title = "稍后再看",
                subtitle = "待看清单",
                centered = true
            )
        }
        CollageItem(
            frame = UserCollageFrames.bbSpace,
            shape = extraLarge,
            color = palette.tileSurfaceStrong,
            onClick = onNavigateToBbSpace
        ) {
            UserEntryTileContent(
                icon = Icons.Default.ShoppingCart,
                title = "bb空间",
                subtitle = "扩展入口"
            )
        }
    }
}

@Composable
private fun BoxScope.UserCollageItem(
    frame: UserCollageFrame,
    base: Dp,
    shape: Shape,
    color: Color,
    collageOffsets: Map<String, Offset>,
    onOffsetChange: (String, Offset) -> Unit,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val savedOffset = collageOffsets[frame.key] ?: Offset.Zero
    val latestSavedOffset by rememberUpdatedState(savedOffset)
    val latestOffsetChange by rememberUpdatedState(onOffsetChange)

    var dragOffsetX by remember(frame.key) { mutableFloatStateOf(0f) }
    var dragOffsetY by remember(frame.key) { mutableFloatStateOf(0f) }

    UserCollageTile(
        modifier = Modifier
            .width(base * frame.widthScale)
            .height(base * frame.heightScale)
            .align(frame.alignment)
            .offset(x = base * frame.offsetXScale, y = base * frame.offsetYScale)
            .offset { IntOffset(savedOffset.x.roundToInt(), savedOffset.y.roundToInt()) }
            .graphicsLayer {
                rotationZ = frame.rotationZ
                translationX = dragOffsetX
                translationY = dragOffsetY
            }
            .pointerInput(frame.key) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        dragOffsetX = 0f
                        dragOffsetY = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragOffsetX += dragAmount.x
                        dragOffsetY += dragAmount.y
                    },
                    onDragEnd = {
                        val finalOffset = Offset(dragOffsetX, dragOffsetY)
                        if (finalOffset != Offset.Zero) {
                            latestOffsetChange(frame.key, latestSavedOffset + finalOffset)
                        }
                        dragOffsetX = 0f
                        dragOffsetY = 0f
                    },
                    onDragCancel = {
                        dragOffsetX = 0f
                        dragOffsetY = 0f
                    }
                )
            },
        shape = shape,
        color = color,
        onClick = onClick,
        content = content
    )
}
