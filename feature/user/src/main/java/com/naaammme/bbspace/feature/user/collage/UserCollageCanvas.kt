package com.naaammme.bbspace.feature.user.collage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.math.sin

@Composable
internal fun UserCollageTile(
    modifier: Modifier = Modifier,
    shape: Shape,
    color: Color,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    if (color == Color.Transparent) {
        Box(
            modifier = modifier
                .clip(shape)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
            content = { content() }
        )
    } else if (onClick == null) {
        Surface(
            modifier = modifier,
            shape = shape,
            color = color,
            content = content
        )
    } else {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            color = color,
            content = content
        )
    }
}

@Immutable
internal data class UserCollagePalette(
    val canvasTop: Color,
    val canvasMid: Color,
    val canvasBottom: Color,
    val tilePrimary: Color,
    val tileSecondary: Color,
    val tileHighlight: Color,
    val tileSurface: Color,
    val tileSurfaceStrong: Color
)

private const val ALPHA_CANVAS_TOP = 0.86f
private const val ALPHA_CANVAS_MID = 0.96f
private const val ALPHA_CANVAS_BOTTOM = 0.88f
private const val ALPHA_TILE_CONTAINER = 0.98f
private const val ALPHA_TILE_SURFACE = 0.96f
private const val ALPHA_TILE_SURFACE_STRONG = 0.98f

@Composable
internal fun rememberUserCollagePalette(): UserCollagePalette {
    val colors = MaterialTheme.colorScheme
    return remember(
        colors.primaryContainer,
        colors.surfaceContainer,
        colors.secondaryContainer,
        colors.tertiaryContainer,
        colors.surface,
        colors.surfaceContainerHigh
    ) {
        UserCollagePalette(
            canvasTop = colors.primaryContainer.copy(alpha = ALPHA_CANVAS_TOP),
            canvasMid = colors.surfaceContainer.copy(alpha = ALPHA_CANVAS_MID),
            canvasBottom = colors.secondaryContainer.copy(alpha = ALPHA_CANVAS_BOTTOM),
            tilePrimary = colors.primaryContainer.copy(alpha = ALPHA_TILE_CONTAINER),
            tileSecondary = colors.secondaryContainer.copy(alpha = ALPHA_TILE_CONTAINER),
            tileHighlight = colors.tertiaryContainer.copy(alpha = ALPHA_TILE_CONTAINER),
            tileSurface = colors.surface.copy(alpha = ALPHA_TILE_SURFACE),
            tileSurfaceStrong = colors.surfaceContainerHigh.copy(alpha = ALPHA_TILE_SURFACE_STRONG)
        )
    }
}

@Composable
internal fun rememberUserCollageBackgroundBrush(palette: UserCollagePalette): Brush {
    return remember(palette.canvasTop, palette.canvasMid, palette.canvasBottom) {
        Brush.verticalGradient(listOf(palette.canvasTop, palette.canvasMid, palette.canvasBottom))
    }
}

@Immutable
internal data class PolygonShape(
    val sides: Int,
    val rotationDeg: Float = 0f,
    val cornerScale: Float = 0f
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        return Outline.Generic(buildPolygonPath(size, sides, rotationDeg, cornerScale))
    }
}

@Immutable
internal data class StarShape(
    val points: Int,
    val innerScale: Float = 0.55f,
    val rotationDeg: Float = -90f,
    val cornerScale: Float = 0.26f
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        return Outline.Generic(buildStarPath(size, points, innerScale, rotationDeg, cornerScale))
    }
}

private fun buildPolygonPath(
    size: Size,
    sides: Int,
    rotationDeg: Float,
    cornerScale: Float
): Path {
    val path = Path()
    if (sides < 3) return path

    val radius = min(size.width, size.height) / 2f
    val centerX = size.width / 2f
    val centerY = size.height / 2f
    val clampedScale = cornerScale.coerceIn(0f, 0.5f)

    fun vertex(index: Int): Offset {
        val theta = rotationDeg + index * 360f / sides
        val angle = theta * PI.toFloat() / 180f
        return Offset(centerX + radius * cos(angle), centerY + radius * sin(angle))
    }

    if (clampedScale <= 0f) {
        for (index in 0 until sides) {
            val point = vertex(index)
            if (index == 0) path.moveTo(point.x, point.y)
            else path.lineTo(point.x, point.y)
        }
        path.close()
        return path
    }

    val cornerRadius = radius * clampedScale
    var prev = vertex(sides - 1)
    var current = vertex(0)
    for (index in 0 until sides) {
        val next = vertex((index + 1) % sides)
        val start = insetVertex(current.x, current.y, prev.x, prev.y, cornerRadius)
        val end = insetVertex(current.x, current.y, next.x, next.y, cornerRadius)
        if (index == 0) path.moveTo(start.x, start.y)
        else path.lineTo(start.x, start.y)
        path.quadraticTo(current.x, current.y, end.x, end.y)
        prev = current
        current = next
    }
    path.close()
    return path
}

private fun buildStarPath(
    size: Size,
    points: Int,
    innerScale: Float,
    rotationDeg: Float,
    cornerScale: Float
): Path {
    val path = Path()
    val outerRadius = min(size.width, size.height) / 2f
    val innerRadius = outerRadius * innerScale
    val cornerRadius = (outerRadius - innerRadius) * cornerScale
    val centerX = size.width / 2f
    val centerY = size.height / 2f
    val totalVertices = points * 2

    fun vertex(i: Int): Offset {
        val theta = rotationDeg + i * 360f / totalVertices
        val angle = theta * PI.toFloat() / 180f
        val r = if (i % 2 == 0) outerRadius else innerRadius
        return Offset(centerX + r * cos(angle), centerY + r * sin(angle))
    }

    var prev = vertex(totalVertices - 1)
    var current = vertex(0)
    for (i in 0 until totalVertices) {
        val next = vertex((i + 1) % totalVertices)
        val start = insetVertex(current.x, current.y, prev.x, prev.y, cornerRadius)
        val end = insetVertex(current.x, current.y, next.x, next.y, cornerRadius)
        if (i == 0) path.moveTo(start.x, start.y) else path.lineTo(start.x, start.y)
        path.quadraticTo(current.x, current.y, end.x, end.y)
        prev = current
        current = next
    }
    path.close()
    return path
}

private fun insetVertex(
    ox: Float,
    oy: Float,
    tx: Float,
    ty: Float,
    distance: Float
): Offset {
    val dx = tx - ox
    val dy = ty - oy
    val edgeLen = sqrt(dx * dx + dy * dy)
    if (edgeLen == 0f) return Offset(ox, oy)
    val scale = min(0.5f, distance / edgeLen)
    return Offset(ox + dx * scale, oy + dy * scale)
}