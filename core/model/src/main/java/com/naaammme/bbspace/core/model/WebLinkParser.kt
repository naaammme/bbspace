package com.naaammme.bbspace.core.model

import java.net.URI

object WebLinkParser {

    fun parse(url: String): WebLinkTarget {
        val uri = runCatching { URI(url) }.getOrNull() ?: return WebLinkTarget.Stay
        val host = uri.host ?: return WebLinkTarget.Stay
        return when {
            host.endsWith("bilibili.com") -> parseBilibili(uri)
            host.endsWith("b23.tv") -> WebLinkTarget.Stay
            else -> WebLinkTarget.Stay
        }
    }

    private fun parseBilibili(uri: URI): WebLinkTarget {
        val host = uri.host.orEmpty()
        val path = uri.path.orEmpty().trimEnd('/')
        val rawUrl = uri.toString()

        when {
            host.startsWith("space") -> {
                val mid = path.substringAfterLast('/').toLongOrNull()
                if (mid != null && mid > 0) return WebLinkTarget.ToSpace(mid)
            }

            host.startsWith("live") -> {
                val roomId = path.substringAfterLast('/').toLongOrNull()
                if (roomId != null && roomId > 0) return WebLinkTarget.ToLive(roomId)
            }

            path.startsWith("/video/") -> {
                val aid = VideoTargetTool.aid(rawUrl)
                val bvid = VideoTargetTool.bvid(rawUrl)
                val cid = VideoTargetTool.cid(rawUrl) ?: 0L
                if (aid != null || bvid != null) {
                    return WebLinkTarget.ToVideo(
                        VideoTarget.Ugc(
                            aid = aid ?: 0L,
                            cid = cid,
                            bvid = bvid,
                            src = VideoTargetTool.feed()
                        )
                    )
                }
            }

            path.startsWith("/bangumi/play/") -> {
                val epId = VideoTargetTool.epId(rawUrl)
                if (epId != null) {
                    return WebLinkTarget.ToVideo(
                        VideoTarget.Pgc(epId = epId, src = VideoTargetTool.feed())
                    )
                }
            }
        }
        return WebLinkTarget.Stay
    }
}
