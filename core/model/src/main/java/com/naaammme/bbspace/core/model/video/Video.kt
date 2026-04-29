package com.naaammme.bbspace.core.model

/**
 * 视频模型
 */
data class Video(
    val bvid: String,
    val aid: Long,
    val title: String,
    val cover: String,
    val author: User,
    val duration: Int,
    val playCount: Long,
    val danmakuCount: Int,
    val publishTime: Long,
    val description: String = ""
)
