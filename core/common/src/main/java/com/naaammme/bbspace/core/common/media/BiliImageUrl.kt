package com.naaammme.bbspace.core.common.media

private val thumbReg = Regex(
    pattern = """(@(\d+[a-z]_?)*)(\..*)?$""",
    options = setOf(RegexOption.IGNORE_CASE)
)

const val BILI_IMAGE_DEFAULT_Q = 15
private const val AVATAR_IMAGE_SUFFIX = "@120w_120h_85q_!widget-layer-avatar.webp"
private const val COVER_IMAGE_SUFFIX = "@575w_360h_1e_1c_85q.webp"

fun coverThumbnailUrl(src: String?): String? {
    if (src.isNullOrBlank()) return src
    val url = src.toHttps()
    val queryIdx = url.indexOf('?')
    val body = if (queryIdx >= 0) url.substring(0, queryIdx) else url
    val query = if (queryIdx >= 0) url.substring(queryIdx) else ""
    return body.substringBefore('@') + COVER_IMAGE_SUFFIX + query
}

fun thumbnailUrl(src: String?, q: Int = BILI_IMAGE_DEFAULT_Q): String? {
    if (src.isNullOrBlank()) return src
    var matched = false
    val url = src.toHttps().replace(thumbReg) { match ->
        matched = true
        val suffix = match.groupValues[3].ifEmpty { ".webp" }
        "${match.groupValues[1]}_${q}q$suffix"
    }
    return if (matched) {
        url
    } else {
        "${url}@${q}q.webp"
    }
}

fun avatarThumbnailUrl(src: String?): String? {
    if (src.isNullOrBlank()) return src
    val url = src.toHttps()
    val queryIdx = url.indexOf('?')
    val body = if (queryIdx >= 0) url.substring(0, queryIdx) else url
    val query = if (queryIdx >= 0) url.substring(queryIdx) else ""
    return body.substringBefore('@') + AVATAR_IMAGE_SUFFIX + query
}

fun originImageUrl(src: String?): String? {
    if (src.isNullOrBlank()) return src
    val url = src.toHttps()
    val queryIdx = url.indexOf('?')
    val body = if (queryIdx >= 0) url.substring(0, queryIdx) else url
    val query = if (queryIdx >= 0) url.substring(queryIdx) else ""
    return body.replace(thumbReg, "") + query
}

private fun String.toHttps(): String {
    return replace("http://", "https://")
}
