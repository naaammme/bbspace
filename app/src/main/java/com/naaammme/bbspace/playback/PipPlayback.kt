package com.naaammme.bbspace.playback

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.naaammme.bbspace.R

/**
 * 原生画中画（PiP）当前是否处于激活状态。
 * 由 MainActivity 的 onPictureInPictureModeChanged 回调写入，PlaybackHost 读取，
 * 激活时整个窗口只渲染播放器。
 */
object PipPlayback {
    const val MIN_ASPECT = 0.41841f
    const val MAX_ASPECT = 2.39f

    var isInPip by mutableStateOf(false)
        private set

    fun updateInPip(inPip: Boolean) {
        isInPip = inPip
    }

    fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

    /**
     * 按当前视频宽高构建 PiP 参数；宽高无效时回退 16:9，
     * 比例超出系统允许范围时收敛到边界。
     * Android 12+ 提供 [actions] 时，系统 PiP 菜单会用它们替代媒体会话的
     * 上一个/下一个等按钮。
     */
    fun buildParams(
        context: Context,
        videoWidth: Int,
        videoHeight: Int,
        actions: List<RemoteAction> = emptyList()
    ): PictureInPictureParams? {
        if (!isSupported()) return null
        val builder = PictureInPictureParams.Builder()
        var w = videoWidth
        var h = videoHeight
        if (w <= 0 || h <= 0) {
            w = 16
            h = 9
        }
        val ratio = w.toFloat() / h.toFloat()
        val rational = when {
            ratio > MAX_ASPECT -> Rational(239, 100)
            ratio < MIN_ASPECT -> Rational(41841, 100000)
            else -> Rational(w, h)
        }
        return try {
            builder.setAspectRatio(rational)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && actions.isNotEmpty()) {
                builder.setActions(ArrayList(actions))
            }
            builder.build()
        } catch (_: IllegalArgumentException) {
            builder.setAspectRatio(Rational(16, 9)).build()
        }
    }

    /**
     * 构建 PiP 菜单的自定义按钮：快退/快进（时长同步设置）+ 暂停/播放。
     * 直播没有可跳转的进度，只提供暂停/播放。
     */
    fun buildActions(
        context: Context,
        isPlaying: Boolean,
        isLive: Boolean,
        seekMs: Long
    ): List<RemoteAction> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return emptyList()
        val actions = mutableListOf<RemoteAction>()
        if (!isLive && seekMs > 0) {
            val seconds = seekMs / 1000
            actions += remoteAction(
                context = context,
                action = PipActionReceiver.ACTION_REWIND,
                requestCode = PipActionReceiver.REQUEST_REWIND,
                iconRes = R.drawable.ic_pip_rewind,
                title = "快退",
                description = "快退 $seconds 秒",
                seekMs = -seekMs
            )
        }
        actions += remoteAction(
            context = context,
            action = PipActionReceiver.ACTION_TOGGLE_PLAY,
            requestCode = PipActionReceiver.REQUEST_TOGGLE_PLAY,
            iconRes = if (isPlaying) R.drawable.ic_pip_pause else R.drawable.ic_pip_play,
            title = if (isPlaying) "暂停" else "播放",
            description = if (isPlaying) "暂停" else "播放"
        )
        if (!isLive && seekMs > 0) {
            val seconds = seekMs / 1000
            actions += remoteAction(
                context = context,
                action = PipActionReceiver.ACTION_FAST_FORWARD,
                requestCode = PipActionReceiver.REQUEST_FAST_FORWARD,
                iconRes = R.drawable.ic_pip_fast_forward,
                title = "快进",
                description = "快进 $seconds 秒",
                seekMs = seekMs
            )
        }
        // 系统 PiP 菜单最多展示约 3 个自定义按钮
        return actions.take(3)
    }

    private fun remoteAction(
        context: Context,
        action: String,
        requestCode: Int,
        iconRes: Int,
        title: String,
        description: String,
        seekMs: Long? = null
    ): RemoteAction {
        val intent = Intent(context, PipActionReceiver::class.java)
            .setAction(action)
            .putExtra(PipActionReceiver.EXTRA_SEEK_MS, seekMs ?: 0L)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return RemoteAction(
            Icon.createWithResource(context, iconRes),
            title,
            description,
            pendingIntent
        )
    }
}
