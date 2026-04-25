package com.naaammme.bbspace.core.model

import androidx.compose.runtime.Immutable

@Immutable
data class VideoDownloadRequest(
    val route: VideoRoute,
    val kind: VideoDownloadKind,
    val videoQuality: Int,
    val audioQuality: Int,
    val title: String?
)

enum class VideoDownloadKind {
    VIDEO,
    AUDIO;

    companion object {
        fun from(raw: String?): VideoDownloadKind {
            return values().firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: VIDEO
        }
    }
}

@Immutable
data class VideoDownloadOption(
    val value: Int,
    val label: String
)

object VideoDownloadOptions {
    val videoQualities = listOf(
        VideoDownloadOption(16, "360P"),
        VideoDownloadOption(32, "480P"),
        VideoDownloadOption(64, "720P"),
        VideoDownloadOption(80, "1080P"),
        VideoDownloadOption(112, "1080P+"),
        VideoDownloadOption(116, "1080P 60"),
        VideoDownloadOption(120, "4K"),
        VideoDownloadOption(125, "HDR"),
        VideoDownloadOption(126, "杜比视界"),
        VideoDownloadOption(127, "8K")
    )

    val audioQualities = listOf(
        VideoDownloadOption(0, "自动"),
        VideoDownloadOption(30216, "64K"),
        VideoDownloadOption(30232, "132K"),
        VideoDownloadOption(30280, "192K"),
        VideoDownloadOption(30250, "杜比全景声"),
        VideoDownloadOption(30251, "Hi-Res")
    )

    fun videoLabel(quality: Int): String {
        return videoQualities.firstOrNull { it.value == quality }?.label ?: "画质 $quality"
    }

    fun audioLabel(quality: Int): String {
        return audioQualities.firstOrNull { it.value == quality }?.label ?: "音频 $quality"
    }
}

sealed interface VideoDownloadProgress {
    data object Preparing : VideoDownloadProgress
    data class Downloading(
        val label: String,
        val doneBytes: Long,
        val totalBytes: Long
    ) : VideoDownloadProgress
    data object Muxing : VideoDownloadProgress
    data class Done(
        val uri: String
    ) : VideoDownloadProgress
}
