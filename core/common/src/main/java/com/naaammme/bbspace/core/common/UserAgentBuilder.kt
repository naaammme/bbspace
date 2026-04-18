package com.naaammme.bbspace.core.common

/**
 * B站请求使用的 User-Agent 构建器
 */
object UserAgentBuilder {

    fun buildGrpcUserAgent(model: String, osVer: String): String {
        return "Dalvik/2.1.0 (Linux; U; Android $osVer; $model Build/PQ3A.190605.07021633) " +
                "${BiliConstants.VERSION} ${buildBiliAppTail(model, osVer)}"
    }

    fun buildRestfulUserAgent(model: String, osVer: String): String {
        return "Mozilla/5.0 BiliDroid/${BiliConstants.VERSION} (bbcallen@gmail.com) " +
                buildBiliAppTail(model, osVer)
    }

    fun buildPlayerUserAgent(): String {
        return "Bilibili Freedoooooom/MarkII"
    }

    private fun buildBiliAppTail(model: String, osVer: String): String {
        return "os/android model/$model mobi_app/${BiliConstants.MOBI_APP} " +
                "build/${BiliConstants.BUILD_STR} channel/${BiliConstants.CHANNEL} " +
                "innerVer/${BiliConstants.BUILD_STR} osVer/$osVer network/2"
    }
}
