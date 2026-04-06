package com.naaammme.bbspace.feature.video

import android.os.SystemClock
import android.graphics.Color
import com.naaammme.bbspace.core.model.DanmakuElem
import com.naaammme.bbspace.feature.video.model.VideoDanmakuConfig
import master.flame.danmaku.api.DanmakuItemMapper
import master.flame.danmaku.api.DanmakuItemUtils
import master.flame.danmaku.api.PlayerTimeProvider
import master.flame.danmaku.danmaku.model.BaseDanmaku
import master.flame.danmaku.danmaku.model.IDisplayer
import master.flame.danmaku.danmaku.model.android.DanmakuContext

internal fun createDanmakuContext(): DanmakuContext {
    return DanmakuContext.create()
        .setDanmakuStyle(IDisplayer.DANMAKU_STYLE_STROKEN, 3f)
        .setDanmakuMargin(24)
}

internal class BbspaceDanmakuMapper : DanmakuItemMapper<DanmakuElem> {

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
        danmaku.textSize = resolveDanmakuTextSize(
            fontSize = item.fontSize,
            density = danmakuContext.displayer.density
        )
        danmaku.priority = if (item.pool != 0) 1 else 0
        return danmaku
    }
}

internal class PlayerSessionTimeProvider(
    positionMs: Long = 0L,
    isPlaying: Boolean = false,
    speed: Float = 1f
) : PlayerTimeProvider {
    @Volatile
    private var posMs = positionMs.coerceAtLeast(0L)

    @Volatile
    private var playing = isPlaying

    @Volatile
    private var playSpd = speed.coerceAtLeast(0f)

    @Volatile
    private var updateAtMs = SystemClock.elapsedRealtime()

    fun update(
        positionMs: Long,
        isPlaying: Boolean,
        speed: Float
    ) {
        posMs = positionMs.coerceAtLeast(0L)
        playing = isPlaying
        playSpd = speed.coerceAtLeast(0f)
        updateAtMs = SystemClock.elapsedRealtime()
    }

    override fun getCurrentTimeMs(): Long {
        if (!playing) return posMs
        val deltaMs = (SystemClock.elapsedRealtime() - updateAtMs).coerceAtLeast(0L)
        return posMs + (deltaMs * playSpd).toLong()
    }

    override fun isPlaying(): Boolean {
        return playing
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
    return fontSize.coerceIn(18, 36).toFloat() * (density - 0.6f).coerceAtLeast(1f)
}

internal fun DanmakuContext.applyConfig(
    config: VideoDanmakuConfig,
    playbackSpeed: Float
) {
    val spd = playbackSpeed.coerceIn(0.25f, 3f)
    setDanmakuTransparency(config.opacity)
    setScaleTextSize(config.textScale.coerceIn(0.5f, 2f) * 0.6f)
    setScrollSpeedFactor(2f / (config.speed.coerceIn(0.5f, 2f) * spd))
    setDuplicateMergingEnabled(config.mergeDuplicates)
    setMaximumVisibleSizeInScreen(config.maximumVisibleSize)
    setMaximumLines(config.maximumLines)
    preventOverlapping(config.overlappingRules)
    setR2LDanmakuVisibility(config.showScrollRl)
    setL2RDanmakuVisibility(true)
    setFTDanmakuVisibility(config.showTop)
    setFBDanmakuVisibility(config.showBottom)
}

private val VideoDanmakuConfig.maximumVisibleSize: Int
    get() = when (densityLevel) {
        0 -> 20
        2 -> 0
        else -> -1
    }

private val VideoDanmakuConfig.maximumLines: Map<Int, Int>
    get() {
        val lines = when (areaPercent) {
            25 -> 3
            50 -> 6
            75 -> 9
            else -> 12
        }
        return hashMapOf(
            BaseDanmaku.TYPE_SCROLL_RL to lines,
            BaseDanmaku.TYPE_SCROLL_LR to lines
        )
    }

private val VideoDanmakuConfig.overlappingRules: Map<Int, Boolean>?
    get() = when (densityLevel) {
        0 -> hashMapOf(
            BaseDanmaku.TYPE_SCROLL_RL to true,
            BaseDanmaku.TYPE_SCROLL_LR to true,
            BaseDanmaku.TYPE_FIX_TOP to true,
            BaseDanmaku.TYPE_FIX_BOTTOM to true
        )

        1 -> hashMapOf(
            BaseDanmaku.TYPE_FIX_TOP to true,
            BaseDanmaku.TYPE_FIX_BOTTOM to true
        )

        else -> null
    }

internal const val DANMAKU_SEEK_SYNC_THRESHOLD_MS = 1_000L
