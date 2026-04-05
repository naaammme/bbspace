package com.naaammme.bbspace.feature.video

import android.graphics.Color
import androidx.media3.common.Player
import com.naaammme.bbspace.core.model.DanmakuElem
import master.flame.danmaku.api.DanmakuItemMapper
import master.flame.danmaku.api.DanmakuItemUtils
import master.flame.danmaku.api.PlayerTimeProvider
import master.flame.danmaku.danmaku.model.BaseDanmaku
import master.flame.danmaku.danmaku.model.IDisplayer
import master.flame.danmaku.danmaku.model.android.DanmakuContext

internal fun createDanmakuContext(): DanmakuContext {
    return DanmakuContext.create()
        .setDanmakuStyle(IDisplayer.DANMAKU_STYLE_STROKEN, 3f)
        .setDuplicateMergingEnabled(false)
        .setScrollSpeedFactor(1.1f)
        .setScaleTextSize(1f)
        .setMaximumLines(
            hashMapOf(
                BaseDanmaku.TYPE_SCROLL_RL to 6,
                BaseDanmaku.TYPE_SCROLL_LR to 6
            )
        )
        .preventOverlapping(
            hashMapOf(
                BaseDanmaku.TYPE_SCROLL_RL to true,
                BaseDanmaku.TYPE_SCROLL_LR to true,
                BaseDanmaku.TYPE_FIX_TOP to true,
                BaseDanmaku.TYPE_FIX_BOTTOM to true
            )
        )
        .setDanmakuMargin(24)
}

internal class BbspaceDanmakuMapper(
    private val density: Float
) : DanmakuItemMapper<DanmakuElem> {

    override fun map(
        item: DanmakuElem,
        danmakuContext: DanmakuContext
    ): BaseDanmaku? {
        val text = item.content.trim()
        if (text.isEmpty()) {
            return null
        }

        val danmaku = DanmakuItemUtils.createTextDanmaku(
            danmakuContext,
            mapDanmakuType(item.mode),
            item.progressMs.toLong().coerceAtLeast(0L),
            text
        ) ?: return null

        danmaku.textColor = normalizeDanmakuColor(item.color)
        danmaku.textShadowColor = Color.BLACK
        danmaku.textSize = resolveDanmakuTextSize(item.fontSize, density)
        danmaku.priority = if (item.pool != 0) 1 else 0
        return danmaku
    }
}

internal class PlayerSessionTimeProvider(
    private val playerProvider: () -> Player
) : PlayerTimeProvider {

    override fun getCurrentTimeMs(): Long {
        return playerProvider().currentPosition.coerceAtLeast(0L)
    }

    override fun isPlaying(): Boolean {
        return playerProvider().isPlaying
    }

    override fun getSyncThresholdTimeMs(): Long {
        return DANMAKU_SEEK_SYNC_THRESHOLD_MS
    }
}

private fun mapDanmakuType(mode: Int): Int {
    return when (mode) {
        BaseDanmaku.TYPE_FIX_BOTTOM -> BaseDanmaku.TYPE_FIX_BOTTOM
        BaseDanmaku.TYPE_FIX_TOP -> BaseDanmaku.TYPE_FIX_TOP
        BaseDanmaku.TYPE_SCROLL_LR -> BaseDanmaku.TYPE_SCROLL_LR
        else -> BaseDanmaku.TYPE_SCROLL_RL
    }
}

private fun normalizeDanmakuColor(color: Int): Int {
    return if (color ushr 24 == 0) {
        color or 0xFF000000.toInt()
    } else {
        color
    }
}

private fun resolveDanmakuTextSize(
    fontSize: Int,
    density: Float
): Float {
    val baseSize = fontSize.coerceIn(18, 36).toFloat()
    return baseSize * (density - 0.6f).coerceAtLeast(1f)
}

private const val DANMAKU_SEEK_SYNC_THRESHOLD_MS = 2_000L
