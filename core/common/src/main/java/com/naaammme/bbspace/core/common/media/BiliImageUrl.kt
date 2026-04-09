package com.naaammme.bbspace.core.common.media

private val thumbReg = Regex(
    pattern = """(@(\d+[a-z]_?)*)(\..*)?$""",
    options = setOf(RegexOption.IGNORE_CASE)
)

private const val defQ = 15

fun thumbnailUrl(src: String?, q: Int = defQ): String? {
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
