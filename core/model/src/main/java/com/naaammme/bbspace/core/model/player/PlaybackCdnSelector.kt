package com.naaammme.bbspace.core.model

private const val PROXY_TF_HOST = "proxy-tf-all-ws.bilivideo.com"
private val MCDN_RESOURCE_REGEX = Regex(
    """^https?://(?:(?:\d{1,3}\.){3}\d{1,3}|[^/]+\.mcdn\.bilivideo\.(?:com|cn|net))(?::\d{1,5})?/v\d/resource""",
    RegexOption.IGNORE_CASE
)
private val REWRITABLE_UPGCXCODE_REGEX = Regex(
    """^https?://(?:upos-\w+-(?!302)\w+|(?:upos|proxy)-tf-[^/]+)\.(?:bilivideo|akamaized)\.(?:com|net)/upgcxcode""",
    RegexOption.IGNORE_CASE
)

fun selectPlaybackCdn(
    stream: PlaybackStream?,
    audio: PlaybackAudio?,
    mode: VideoCdnMode
): PlaybackCdn? {
    val dash = stream as? PlaybackStream.Dash ?: return null
    val videoUrls = mergePlaybackUrls(dash.videoUrl, dash.videoBackupUrls)
    if (videoUrls.isEmpty()) return null
    return PlaybackCdn(
        videoUrl = selectPlaybackUrl(videoUrls, mode) ?: return null,
        audioUrl = selectPlaybackUrl(
            urls = mergePlaybackUrls(audio?.url, audio?.backupUrls.orEmpty()),
            mode = mode
        )
    )
}

private fun selectPlaybackUrl(
    urls: List<String>,
    mode: VideoCdnMode
): String? {
    if (urls.isEmpty()) return null
    return when (mode) {
        VideoCdnMode.BaseUrl -> urls.first()
        VideoCdnMode.Backup1 -> urls.getOrNull(1) ?: urls.first()
        VideoCdnMode.Backup2 -> urls.getOrNull(2) ?: urls.getOrNull(1) ?: urls.first()
        else -> rewritePlaybackUrl(urls, mode.host ?: return urls.first()) ?: urls.first()
    }
}

private fun rewritePlaybackUrl(
    urls: List<String>,
    targetHost: String
): String? {
    var mcdnResourceUrl: String? = null
    var mcdnUpgcxcodeUrl: String? = null
    for (url in urls) {
        if (REWRITABLE_UPGCXCODE_REGEX.containsMatchIn(url)) {
            val uri = runCatching { java.net.URI(url) }.getOrNull() ?: continue
            if (parseQueryParams(uri.rawQuery)["os"].equals("mcdn", ignoreCase = true)) {
                mcdnUpgcxcodeUrl = url
            } else {
                return rewriteUriHost(uri, targetHost)
            }
            continue
        }
        if (MCDN_RESOURCE_REGEX.containsMatchIn(url)) {
            mcdnResourceUrl = url
            continue
        }
        if (url.contains("/upgcxcode/", ignoreCase = true)) {
            mcdnUpgcxcodeUrl = url
            continue
        }
        if (url.contains("szbdyd.com", ignoreCase = true)) {
            val uri = runCatching { java.net.URI(url) }.getOrNull() ?: continue
            val xyUsource = parseQueryParams(uri.rawQuery)["xy_usource"]?.takeIf(String::isNotBlank)
            return rewriteUriHost(
                uri = uri,
                host = xyUsource ?: targetHost,
                scheme = "https",
                port = 443
            )
        }
    }
    mcdnUpgcxcodeUrl?.let {
        val uri = runCatching { java.net.URI(it) }.getOrNull() ?: return null
        return rewriteUriHost(uri, targetHost)
    }
    mcdnResourceUrl?.let {
        val encoded = java.net.URLEncoder.encode(it, "UTF-8")
        return "https://$PROXY_TF_HOST?url=$encoded"
    }
    return null
}

private fun rewriteUriHost(
    uri: java.net.URI,
    host: String,
    scheme: String? = uri.scheme,
    port: Int = uri.port
): String {
    return java.net.URI(
        scheme,
        uri.userInfo,
        host,
        port,
        uri.path,
        uri.query,
        uri.fragment
    ).toString()
}

private fun parseQueryParams(rawQuery: String?): Map<String, String> {
    if (rawQuery.isNullOrBlank()) return emptyMap()
    return rawQuery.split("&")
        .mapNotNull { part ->
            val idx = part.indexOf('=')
            when {
                idx < 1 -> null
                else -> {
                    val key = java.net.URLDecoder.decode(part.substring(0, idx), "UTF-8")
                    val value = java.net.URLDecoder.decode(part.substring(idx + 1), "UTF-8")
                    key to value
                }
            }
        }
        .toMap()
}

private fun mergePlaybackUrls(primaryUrl: String?, backupUrls: List<String>): List<String> {
    return (listOfNotNull(primaryUrl) + backupUrls)
        .filter(String::isNotBlank)
        .distinct()
}
