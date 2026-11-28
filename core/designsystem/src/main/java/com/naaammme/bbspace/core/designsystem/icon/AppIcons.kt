package com.naaammme.bbspace.core.designsystem.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object AppIcons {
    private val blackFill = SolidColor(Color.Black)

    private fun icon(name: String, vw: Float = 24f, vh: Float = 24f, build: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, vw, vh).apply(build).build()

    val Pause: ImageVector by lazy {
        icon("Pause", 256f, 256f) {
            path(fill = blackFill) {
                moveTo(168f, 40f)
                horizontalLineTo(192f)
                arcTo(16f, 16f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 208f, y1 = 56f)
                verticalLineTo(200f)
                arcTo(16f, 16f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 192f, y1 = 216f)
                horizontalLineTo(168f)
                arcTo(16f, 16f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 152f, y1 = 200f)
                verticalLineTo(56f)
                arcTo(16f, 16f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 168f, y1 = 40f)
                close()
            }
            path(fill = blackFill) {
                moveTo(64f, 40f)
                horizontalLineTo(88f)
                arcTo(16f, 16f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 104f, y1 = 56f)
                verticalLineTo(200f)
                arcTo(16f, 16f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 88f, y1 = 216f)
                horizontalLineTo(64f)
                arcTo(16f, 16f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 48f, y1 = 200f)
                verticalLineTo(56f)
                arcTo(16f, 16f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 64f, y1 = 40f)
                close()
            }
        }
    }

    val ThumbUpFilled: ImageVector by lazy { thumbUpIcon("ThumbUpFilled", filled = true) }
    val ThumbUpOutline: ImageVector by lazy { thumbUpIcon("ThumbUpOutline", filled = false) }

    private fun thumbUpIcon(name: String, filled: Boolean): ImageVector = icon(name) {
        path(
            fill = if (filled) blackFill else null,
            stroke = blackFill,
            strokeLineWidth = 1.6f,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(2.75f, 10.5f)
            horizontalLineTo(6f)
            verticalLineTo(20f)
            horizontalLineTo(2.75f)
            close()
            moveTo(8.5f, 10.5f)
            lineTo(13.1f, 4.65f)
            curveTo(13.55f, 4.07f, 14.5f, 4.4f, 14.5f, 5.15f)
            verticalLineTo(9f)
            horizontalLineTo(19.5f)
            curveTo(20.7f, 9f, 21.6f, 10.15f, 21.3f, 11.3f)
            lineTo(19.5f, 18.5f)
            curveTo(19.28f, 19.38f, 18.5f, 20f, 17.6f, 20f)
            horizontalLineTo(8.5f)
            close()
        }
    }

}
