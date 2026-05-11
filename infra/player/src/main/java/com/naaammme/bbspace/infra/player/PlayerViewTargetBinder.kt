package com.naaammme.bbspace.infra.player

import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import java.util.IdentityHashMap

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
            return
        }
        PlayerView.switchTargetView(player, oldView, view)
        boundViews[player] = view
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
}
