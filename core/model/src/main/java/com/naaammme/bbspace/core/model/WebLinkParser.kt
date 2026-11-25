package com.naaammme.bbspace.core.model

import java.net.URI

object WebLinkParser {

    fun parse(url: String): WebLinkTarget {
        val uri = runCatching { URI(url) }.getOrNull() ?: return WebLinkTarget.Stay
        val host = uri.host.orEmpty()
        val path = uri.path.orEmpty().trimEnd('/')
        return when {
            uri.scheme == "bilibili" && host == "video" -> parseVideo(uri, path)
            uri.scheme == "bilibili" && host == "bangumi" -> parseBangumi(uri, path)
            (uri.scheme == "bilibili" && host == "space") || host.startsWith("space") ->
                path.substringAfterLast('/').toLongOrNull()
                    ?.takeIf { it > 0 }
                    ?.let(WebLinkTarget::ToSpace)
                    ?: WebLinkTarget.Stay
            (uri.scheme == "bilibili" && host == "live") || host.startsWith("live") ->
                path.substringAfterLast('/').toLongOrNull()
                    ?.takeIf { it > 0 }
                    ?.let(WebLinkTarget::ToLive)
                    ?: WebLinkTarget.Stay
            host.endsWith("bilibili.com") && path.startsWith("/video/") -> parseVideo(uri, path)
            host.endsWith("bilibili.com") && path.startsWith("/bangumi/play/") -> parseBangumi(uri, path)
            host.endsWith("b23.tv") -> WebLinkTarget.Stay
            else -> WebLinkTarget.Stay
        }
    }

    private val BV_REGEX = Regex("BV[0-9A-Za-z]+")

    private fun parseVideo(
        uri: URI,
        path: String
    ): WebLinkTarget {
        val rawUrl = uri.toString()
        val lastSegment = path.substringAfterLast('/')
        val bvid = VideoTargetTool.bvid(rawUrl)
            ?: lastSegment.takeIf { BV_REGEX.matches(it) }
        val aid = VideoTargetTool.aid(rawUrl)
            ?: lastSegment.removePrefix("av").toLongOrNull()
        val cid = VideoTargetTool.cid(rawUrl) ?: 0L
        if (aid == null && bvid == null) return WebLinkTarget.Stay
        return WebLinkTarget.ToVideo(
            VideoTarget.Ugc(
                aid = aid ?: 0L,
                cid = cid,
                bvid = bvid,
                src = VideoTargetTool.default()
            )
        )
    }

    private fun parseBangumi(
        uri: URI,
        path: String
    ): WebLinkTarget {
        val epId = VideoTargetTool.epId(uri.toString())
        if (epId != null) {
            return WebLinkTarget.ToVideo(
                VideoTarget.Pgc(epId = epId, src = VideoTargetTool.default())
            )
        }
        val seasonId = path.substringAfterLast('/')
            .removePrefix("ss").toLongOrNull()
            ?: return WebLinkTarget.Stay
        return WebLinkTarget.ToVideo(
            VideoTarget.Pgc(
                seasonId = seasonId,
                src = VideoTargetTool.default()
            )
        )
    }
}
