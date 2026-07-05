package com.naaammme.bbspace.playback

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.os.IBinder
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.ui.PlayerNotificationManager
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.toBitmap
import com.naaammme.bbspace.MainActivity
import com.naaammme.bbspace.R
import com.naaammme.bbspace.core.common.media.coverThumbnailUrl
import com.naaammme.bbspace.core.playback.StreamPlaybackSession
import com.naaammme.bbspace.core.model.StreamPlaybackTarget
import com.naaammme.bbspace.infra.player.EngineSource
import com.naaammme.bbspace.infra.player.PlayerEngine
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@AndroidEntryPoint
@OptIn(UnstableApi::class)
class PlaybackService : Service() {
    @Inject
    lateinit var playbackSession: StreamPlaybackSession

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
            playbackSession.sessionState
                .distinctUntilChanged { old, new ->
                    old.target == new.target &&
                        old.title == new.title &&
                        old.subtitle == new.subtitle &&
                        old.cover == new.cover &&
                        old.isPlaying == new.isPlaying
                }
                .collect {
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
        bindPlayer(playerEngine.player.value)
        updateActionMode()
        notificationManager?.invalidate()
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        playbackSession.close()
        stopSelf()
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
                val subtitle = playbackSession.sessionState.value.subtitle
                subtitle?.takeIf(String::isNotBlank)?.let { return it }
                return player.currentMediaItem
                    ?.mediaMetadata
                    ?.artist
                    ?.toString()
                    ?.takeIf(String::isNotBlank)
            }
            val subtitle = playbackSession.sessionState.value.subtitle
            subtitle?.takeIf(String::isNotBlank)?.let { return it }
            return player.currentMediaItem
                ?.mediaMetadata
                ?.artist
                ?.toString()
                ?.takeIf(String::isNotBlank)
        }

        override fun getCurrentSubText(player: Player): CharSequence {
            return currentSubText()
        }

        override fun getCurrentLargeIcon(
            player: Player,
            callback: PlayerNotificationManager.BitmapCallback
        ): Bitmap? {
            val coverUrl = currentLargeIconUrl() ?: return null
            SingletonImageLoader.get(this@PlaybackService).enqueue(
                ImageRequest.Builder(this@PlaybackService)
                    .data(coverUrl)
                    .listener(
                        onSuccess = { _, result ->
                            callback.onBitmap(result.image.toBitmap())
                        },
                    )
                    .build()
            )
            return null
        }
    }

    private inner class NotificationListener : PlayerNotificationManager.NotificationListener {
        @SuppressLint("MissingPermission")
        override fun onNotificationPosted(
            notificationId: Int,
            notification: Notification,
            ongoing: Boolean
        ) {
            // 始终保持前台服务状态防止在失去音频焦点时退居后台而被系统直接杀死
            // Android 11+ 的媒体控制中心依然允许用户手取消划掉暂停状态的媒体卡片，不会影响体验
            if (!isForeground) {
                startForeground(notificationId, notification)
                isForeground = true
            }
        }

        override fun onNotificationCancelled(
            notificationId: Int,
            dismissedByUser: Boolean
        ) {
            if (isForeground) {
                ServiceCompat.stopForeground(this@PlaybackService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                isForeground = false
            }
            if (dismissedByUser) {
                playbackSession.close()
            }
            stopSelf()
        }
    }

    private fun createContentIntent(): PendingIntent? {
        return PendingIntent.getActivity(
            this,
            CONTENT_REQ_OPEN,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun currentTitle(): String {
        val liveTarget = playbackSession.sessionState.value.target as? StreamPlaybackTarget.Live
        if (liveTarget != null) {
            return liveTarget.route.title?.takeIf(String::isNotBlank)
                ?: "直播间 ${liveTarget.route.roomId}"
        }
        val title = playbackSession.sessionState.value.title
        title.takeIf(String::isNotBlank)?.let { return it }
        playerEngine.player.value
            ?.currentMediaItem
            ?.mediaMetadata
            ?.title
            ?.toString()
            ?.takeIf(String::isNotBlank)
            ?.let { return it }
        return "视频播放"
    }

    private fun currentSubText(): String {
        val isPlaying = playerEngine.playbackState.value.isPlaying
        return if (isLivePlayback()) {
            if (isPlaying) "后台直播中" else "后台待播"
        } else {
            if (isPlaying) "后台播放中" else "后台待播"
        }
    }

    private fun updateActionMode() {
        val isLive = isLivePlayback()
        notificationManager?.setUseFastForwardAction(!isLive)
        notificationManager?.setUseRewindAction(!isLive)
        notificationManager?.setUseChronometer(!isLive)
    }

    private fun currentLargeIconUrl(): String? {
        return when (val target = playbackSession.sessionState.value.target) {
            is StreamPlaybackTarget.Live -> coverThumbnailUrl(target.route.cover)
            is StreamPlaybackTarget.Video -> coverThumbnailUrl(playbackSession.sessionState.value.cover)
            null -> null
        }
    }

    private fun isLivePlayback(): Boolean {
        return playbackSession.sessionState.value.target is StreamPlaybackTarget.Live ||
            playerEngine.currentSource.value is EngineSource.LiveFlv
    }

    private companion object {
        const val NOTIFICATION_ID = 1001
        const val NOTIFICATION_CHANNEL_ID = "video_playback"
        const val CONTENT_REQ_OPEN = 1001
    }
}
