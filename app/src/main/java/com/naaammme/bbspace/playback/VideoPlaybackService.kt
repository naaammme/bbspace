package com.naaammme.bbspace.playback

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
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
import com.naaammme.bbspace.MainActivity
import com.naaammme.bbspace.R
import com.naaammme.bbspace.core.data.player.VideoPlaybackControllerImpl
import com.naaammme.bbspace.core.domain.live.LivePlaybackController
import com.naaammme.bbspace.infra.player.EngineSource
import com.naaammme.bbspace.infra.player.PlayerEngine
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

    @Inject
    lateinit var livePlaybackController: LivePlaybackController

    @Inject
    lateinit var playerEngine: PlayerEngine

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var mediaSession: MediaSession? = null
    private var notificationManager: PlayerNotificationManager? = null
    private var isForeground = false

    override fun onCreate() {
        super.onCreate()
        notificationManager = buildNotificationManager()
        scope.launch {
            playerEngine.player.collect(::bindPlayer)
        }
        scope.launch {
            combine(
                playerEngine.currentSource,
                playbackController.sessionState,
                playbackController.pageMeta,
                livePlaybackController.state
            ) { _, _, _, _ -> Unit }.collect {
                mediaSession?.setSessionActivity(createContentIntent())
                updateActionMode()
                notificationManager?.invalidate()
            }
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        if (!isForeground && !playerEngine.snapshot.value.isPlaying) {
            val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setContentTitle(currentTitle())
                .setContentText(currentSubText())
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_LOW)
            createContentIntent()?.let(builder::setContentIntent)
            startForeground(NOTIFICATION_ID, builder.build())
            isForeground = true
        }
        notificationManager?.invalidate()
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!playerEngine.snapshot.value.isPlaying) {
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
        val contentIntent = createContentIntent()
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
            return currentTitle()
        }

        override fun createCurrentContentIntent(player: Player) = createContentIntent()

        override fun getCurrentContentText(player: Player): CharSequence? {
            if (isLivePlayback()) {
                return livePlaybackController.state.value.playbackSource?.currentDescription
            }
            player.currentMediaItem
                ?.mediaMetadata
                ?.artist
                ?.toString()
                ?.takeIf(String::isNotBlank)
                ?.let { return it }
            val meta = playbackController.pageMeta.value
            val text = listOfNotNull(
                meta?.ownerName?.takeIf(String::isNotBlank),
                meta?.partTitle?.takeIf(String::isNotBlank)
            ).joinToString(" · ")
            return text.takeIf { it.isNotBlank() }
        }

        override fun getCurrentSubText(player: Player): CharSequence? {
            return currentSubText()
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
                if (isLivePlayback()) {
                    livePlaybackController.release()
                } else {
                    playbackController.stopPlayback()
                }
            }
            stopSelf()
        }
    }

    private fun createContentIntent(): PendingIntent? {
        if (isLivePlayback()) {
            return PendingIntent.getActivity(
                this,
                LIVE_REQ_OPEN,
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        return PendingIntent.getActivity(
            this,
            VIDEO_REQ_OPEN,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun currentTitle(): String {
        if (isLivePlayback()) {
            val roomId = livePlaybackController.state.value.playbackSource?.roomId
            return roomId?.let { "直播间 $it" } ?: "直播播放"
        }
        playerEngine.player.value
            ?.currentMediaItem
            ?.mediaMetadata
            ?.title
            ?.toString()
            ?.takeIf(String::isNotBlank)
            ?.let { return it }
        return playbackController.pageMeta.value?.title
            ?.takeIf(String::isNotBlank)
            ?: "视频播放"
    }

    private fun currentSubText(): String {
        val isPlaying = playerEngine.snapshot.value.isPlaying
        return if (isLivePlayback()) {
            if (isPlaying) "后台直播中" else "后台待播"
        } else {
            playerEngine.player.value
                ?.currentMediaItem
                ?.mediaMetadata
                ?.artist
                ?.toString()
                ?.takeIf(String::isNotBlank)
                ?.let { return it }
            if (isPlaying) "后台播放中" else "后台待播"
        }
    }

    private fun updateActionMode() {
        val isLive = isLivePlayback()
        notificationManager?.setUseFastForwardAction(!isLive)
        notificationManager?.setUseRewindAction(!isLive)
        notificationManager?.setUseChronometer(!isLive)
    }

    private fun isLivePlayback(): Boolean {
        return playerEngine.currentSource.value is EngineSource.LiveFlv
    }

    private companion object {
        const val NOTIFICATION_ID = 1001
        const val NOTIFICATION_CHANNEL_ID = "video_playback"
        const val VIDEO_REQ_OPEN = 1001
        const val LIVE_REQ_OPEN = 1002
    }
}
