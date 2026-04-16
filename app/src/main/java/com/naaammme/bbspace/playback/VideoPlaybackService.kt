package com.naaammme.bbspace.playback

import android.annotation.SuppressLint
import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.ui.PlayerNotificationManager
import com.naaammme.bbspace.R
import com.naaammme.bbspace.core.data.player.VideoPlaybackControllerImpl
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@AndroidEntryPoint
@UnstableApi
class VideoPlaybackService : Service() {
    @Inject
    lateinit var playbackController: VideoPlaybackControllerImpl

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var mediaSession: MediaSession? = null
    private var notificationManager: PlayerNotificationManager? = null
    private var isForeground = false

    override fun onCreate() {
        super.onCreate()
        notificationManager = buildNotificationManager()
        scope.launch {
            playbackController.playerState.collect(::bindPlayer)
        }
        scope.launch {
            combine(
                playbackController.sessionState,
                playbackController.pageMeta
            ) { _, _ -> Unit }.collect {
                mediaSession?.setSessionActivity(
                    PlaybackLaunchIntents.createContentIntent(
                        context = this@VideoPlaybackService,
                        route = playbackController.currentRoute()
                    )
                )
                notificationManager?.invalidate()
            }
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        if (!isForeground && !playbackController.playbackState.value.isPlaying) {
            val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setContentTitle(
                    playbackController.pageMeta.value?.title
                        ?.takeIf(String::isNotBlank)
                        ?: "视频播放"
                )
                .setContentText("后台待播")
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_LOW)
            PlaybackLaunchIntents.createContentIntent(
                context = this,
                route = playbackController.currentRoute()
            )?.let(builder::setContentIntent)
            startForeground(NOTIFICATION_ID, builder.build())
            isForeground = true
        }
        notificationManager?.invalidate()
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!playbackController.playbackState.value.isPlaying) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        notificationManager?.setPlayer(null)
        notificationManager = null
        mediaSession?.release()
        mediaSession = null
        if (isForeground) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            isForeground = false
        }
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun bindPlayer(player: Player?) {
        if (player == null) {
            notificationManager?.setPlayer(null)
            mediaSession?.release()
            mediaSession = null
            return
        }
        val currentSession = mediaSession
        val contentIntent = PlaybackLaunchIntents.createContentIntent(
            context = this,
            route = playbackController.currentRoute()
        )
        if (currentSession == null) {
            val builder = MediaSession.Builder(this, player)
            if (contentIntent != null) {
                builder.setSessionActivity(contentIntent)
            }
            mediaSession = builder.build()
        } else {
            currentSession.setPlayer(player)
            currentSession.setSessionActivity(contentIntent)
        }
        notificationManager?.setMediaSessionToken(checkNotNull(mediaSession).platformToken)
        notificationManager?.setPlayer(player)
    }

    private fun buildNotificationManager(): PlayerNotificationManager {
        return PlayerNotificationManager.Builder(
            this,
            NOTIFICATION_ID,
            NOTIFICATION_CHANNEL_ID
        )
            .setChannelNameResourceId(R.string.playback_notification_channel_name)
            .setChannelDescriptionResourceId(R.string.playback_notification_channel_desc)
            .setMediaDescriptionAdapter(NotificationTextAdapter())
            .setNotificationListener(NotificationListener())
            .setSmallIconResourceId(R.drawable.ic_launcher_monochrome)
            .build()
            .apply {
                setUseNextAction(false)
                setUsePreviousAction(false)
                setUseFastForwardAction(true)
                setUseRewindAction(true)
                setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                setPriority(NotificationCompat.PRIORITY_LOW)
                setUseChronometer(true)
            }
    }

    private inner class NotificationTextAdapter : PlayerNotificationManager.MediaDescriptionAdapter {
        override fun getCurrentContentTitle(player: Player): CharSequence {
            return playbackController.pageMeta.value?.title
                ?.takeIf(String::isNotBlank)
                ?: "视频播放"
        }

        override fun createCurrentContentIntent(player: Player) =
            PlaybackLaunchIntents.createContentIntent(
                context = this@VideoPlaybackService,
                route = playbackController.currentRoute()
            )

        override fun getCurrentContentText(player: Player): CharSequence? {
            val meta = playbackController.pageMeta.value
            val owner = meta?.ownerName?.takeIf(String::isNotBlank)
            val part = meta?.partTitle?.takeIf(String::isNotBlank)
            val text = listOfNotNull(owner, part).joinToString(" · ")
            return text.takeIf { it.isNotBlank() }
        }

        override fun getCurrentSubText(player: Player): CharSequence? {
            return if (playbackController.playbackState.value.isPlaying) {
                "后台播放中"
            } else {
                "后台待播"
            }
        }

        override fun getCurrentLargeIcon(
            player: Player,
            callback: PlayerNotificationManager.BitmapCallback
        ) = null
    }

    private inner class NotificationListener : PlayerNotificationManager.NotificationListener {
        @SuppressLint("MissingPermission")
        override fun onNotificationPosted(
            notificationId: Int,
            notification: Notification,
            ongoing: Boolean
        ) {
            if (ongoing) {
                startForeground(notificationId, notification)
                isForeground = true
                return
            }
            if (isForeground) {
                ServiceCompat.stopForeground(this@VideoPlaybackService, ServiceCompat.STOP_FOREGROUND_DETACH)
                isForeground = false
            }
            NotificationManagerCompat.from(this@VideoPlaybackService).notify(
                notificationId,
                notification
            )
        }

        override fun onNotificationCancelled(
            notificationId: Int,
            dismissedByUser: Boolean
        ) {
            if (isForeground) {
                ServiceCompat.stopForeground(this@VideoPlaybackService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                isForeground = false
            }
            if (dismissedByUser) {
                playbackController.stopPlayback()
            }
            stopSelf()
        }
    }

    private companion object {
        const val NOTIFICATION_ID = 1001
        const val NOTIFICATION_CHANNEL_ID = "video_playback"
    }
}
