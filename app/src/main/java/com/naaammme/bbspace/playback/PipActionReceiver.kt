package com.naaammme.bbspace.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.naaammme.bbspace.core.playback.StreamPlaybackSession
import com.naaammme.bbspace.core.model.StreamPlaybackTarget
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 系统 PiP 菜单自定义按钮（快退/快进/暂停播放）的回调接收器。
 */
@AndroidEntryPoint
class PipActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var playbackSession: StreamPlaybackSession

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_REWIND -> seekBy(-intent.getLongExtra(EXTRA_SEEK_MS, 0L))
            ACTION_FAST_FORWARD -> seekBy(intent.getLongExtra(EXTRA_SEEK_MS, 0L))
            ACTION_TOGGLE_PLAY -> togglePlay()
        }
    }

    private fun seekBy(deltaMs: Long) {
        if (deltaMs == 0L) return
        val state = playbackSession.sessionState.value
        if (state.target is StreamPlaybackTarget.Live) return
        val player = playbackSession.player.value ?: return
        val duration = player.duration.takeIf { it > 0L }
        val target = player.currentPosition + deltaMs
        val clamped = when {
            duration != null -> target.coerceIn(0L, duration)
            else -> target.coerceAtLeast(0L)
        }
        player.seekTo(clamped)
    }

    private fun togglePlay() {
        if (playbackSession.sessionState.value.playWhenReady) {
            playbackSession.pause()
        } else {
            playbackSession.play()
        }
    }

    companion object {
        const val ACTION_REWIND = "com.naaammme.bbspace.pip.action.REWIND"
        const val ACTION_FAST_FORWARD = "com.naaammme.bbspace.pip.action.FAST_FORWARD"
        const val ACTION_TOGGLE_PLAY = "com.naaammme.bbspace.pip.action.TOGGLE_PLAY"
        const val EXTRA_SEEK_MS = "seek_ms"

        const val REQUEST_REWIND = 2001
        const val REQUEST_FAST_FORWARD = 2002
        const val REQUEST_TOGGLE_PLAY = 2003
    }
}
