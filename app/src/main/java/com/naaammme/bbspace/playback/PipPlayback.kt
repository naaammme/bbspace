package com.naaammme.bbspace.playback

import android.app.PictureInPictureParams
import android.content.Context
import android.os.Build
import android.util.Rational
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

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
     */
    fun buildParams(context: Context, videoWidth: Int, videoHeight: Int): PictureInPictureParams? {
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
            builder.setAspectRatio(rational).build()
        } catch (_: IllegalArgumentException) {
            builder.setAspectRatio(Rational(16, 9)).build()
        }
    }
}
