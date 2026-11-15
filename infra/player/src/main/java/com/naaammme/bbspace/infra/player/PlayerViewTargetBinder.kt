package com.naaammme.bbspace.infra.player

import android.view.SurfaceView
import android.view.View
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import java.util.IdentityHashMap
import kotlin.math.abs

/** 判定 Surface 尺寸与视图尺寸不一致超过该阈值才需要修复（避免亚像素抖动反复触发） */
private const val SurfaceSizeFixTolerancePx = 2

/** Surface 尺寸短暂滞后属正常（异步重建），持续不一致才视为卡在旧尺寸 */
private const val SurfaceSizeFixDelayMs = 150L

@UnstableApi
object PlayerViewTargetBinder {

    private val boundViews = IdentityHashMap<Player, PlayerView>()

    fun bind(
        view: PlayerView,
        player: Player?
    ) {
        player ?: return unbind(view)
        removeViewEntry(view, keepPlayer = player)
        val oldView = boundViews[player]
        if (oldView === view) {
            if (view.player !== player) {
                view.player = player
            }
            scheduleStaleSurfaceFix(view)
            return
        }
        PlayerView.switchTargetView(player, oldView, view)
        boundViews[player] = view
        scheduleStaleSurfaceFix(view)
    }

    fun unbind(view: PlayerView) {
        val player = view.player
        if (player != null && boundViews[player] === view) {
            boundViews.remove(player)
            PlayerView.switchTargetView(player, view, null)
            return
        }
        removeViewEntry(view, keepPlayer = null)
        view.player = null
    }

    private fun removeViewEntry(
        view: PlayerView,
        keepPlayer: Player?
    ) {
        boundViews.entries.removeAll { it.value === view && it.key !== keepPlayer }
    }

    /**
     * 从画中画返回等场景下，SurfaceView 的 Surface 可能停留在旧窗口的小尺寸
     * （画面铺不满视图，右侧/下方出现黑边），且系统不会自行纠正。
     * 这里在绑定后检查 Surface 尺寸是否与视图持续不一致，是则强制重建 Surface。
     */
    private fun scheduleStaleSurfaceFix(view: PlayerView) {
        val surface = view.videoSurfaceView as? SurfaceView ?: return
        view.post {
            if (!surface.isAttachedToWindow || surface.visibility != View.VISIBLE) {
                return@post
            }
            val viewWidth = surface.width
            val viewHeight = surface.height
            if (viewWidth <= 0 || viewHeight <= 0) {
                return@post
            }
            if (!isSurfaceStale(surface, viewWidth, viewHeight)) {
                return@post
            }
            surface.postDelayed(
                {
                    if (
                        !surface.isAttachedToWindow ||
                        surface.visibility != View.VISIBLE ||
                        surface.width != viewWidth ||
                        surface.height != viewHeight ||
                        !isSurfaceStale(surface, viewWidth, viewHeight)
                    ) {
                        return@postDelayed
                    }
                    surface.visibility = View.GONE
                    surface.post { surface.visibility = View.VISIBLE }
                },
                SurfaceSizeFixDelayMs
            )
        }
    }

    private fun isSurfaceStale(surface: SurfaceView, viewWidth: Int, viewHeight: Int): Boolean {
        val frame = surface.holder.surfaceFrame
        if (frame.isEmpty) return false
        return abs(frame.width() - viewWidth) > SurfaceSizeFixTolerancePx ||
            abs(frame.height() - viewHeight) > SurfaceSizeFixTolerancePx
    }
}
