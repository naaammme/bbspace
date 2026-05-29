package com.naaammme.bbspace.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.ControllerInfo
import androidx.media3.session.MediaSessionService
import com.naaammme.bbspace.MainActivity
import com.naaammme.bbspace.R
import com.naaammme.bbspace.infra.player.PlayerEngine
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@AndroidEntryPoint
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    @Inject
    lateinit var playerEngine: PlayerEngine

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .setNotificationId(NOTIFICATION_ID)
            .setChannelId(NOTIFICATION_CHANNEL_ID)
            .setChannelName(R.string.playback_notification_channel_name)
            .build()
            .apply {
                setSmallIcon(R.drawable.ic_launcher_monochrome)
            }
        setMediaNotificationProvider(notificationProvider)
        scope.launch {
            playerEngine.player.collect(::bindPlayer)
        }
    }

    override fun onGetSession(controllerInfo: ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!isPlaybackOngoing) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        scope.cancel()
        super.onDestroy()
    }

    private fun bindPlayer(player: androidx.media3.common.Player?) {
        if (player == null) {
            mediaSession?.let {
                removeSession(it)
                it.release()
            }
            mediaSession = null
            return
        }
        val session = mediaSession
        if (session == null) {
            createSession(player)
            return
        }
        session.setPlayer(player)
        session.setSessionActivity(createContentIntent())
    }

    private fun createSession(player: androidx.media3.common.Player): MediaSession {
        return MediaSession.Builder(this, player)
            .setSessionActivity(createContentIntent())
            .build()
            .also {
                mediaSession = it
                addSession(it)
            }
    }

    private fun createContentIntent(): PendingIntent {
        return PendingIntent.getActivity(
            this,
            CONTENT_REQ_OPEN,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private companion object {
        const val NOTIFICATION_ID = 1001
        const val NOTIFICATION_CHANNEL_ID = "video_playback"
        const val CONTENT_REQ_OPEN = 1001
    }
}
