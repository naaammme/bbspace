package com.naaammme.bbspace.infra.network

import com.naaammme.bbspace.core.common.BiliConstants

/**
 * User-Agent 构建器
 * B站 API 使用两种不同格式的 UA
 */
object UserAgentBuilder {

    /**
     * gRPC 接口使用的 UA（如 ticket 接口）
     * 格式: Dalvik/2.1.0 (Linux; U; Android {osVer}; {model} Build/...) {version} os/android model/{model} ...
     */
    fun buildGrpcUserAgent(model: String, osVer: String): String {
        return "Dalvik/2.1.0 (Linux; U; Android $osVer; $model Build/PQ3A.190605.07021633) " +
                "${BiliConstants.VERSION} os/android model/$model mobi_app/${BiliConstants.MOBI_APP} " +
                "build/${BiliConstants.BUILD_STR} channel/${BiliConstants.CHANNEL} " +
                "innerVer/${BiliConstants.BUILD_STR} osVer/$osVer network/2"
    }

    /**
     * RESTful 接口使用的 UA（如二维码、轮询、guestid）
     * 格式: Mozilla/5.0 BiliDroid/{version} (bbcallen@gmail.com) os/android model/{model} ...
     */
    fun buildRestfulUserAgent(model: String, osVer: String): String {
        return "Mozilla/5.0 BiliDroid/${BiliConstants.VERSION} (bbcallen@gmail.com) " +
                "os/android model/$model mobi_app/${BiliConstants.MOBI_APP} " +
                "build/${BiliConstants.BUILD_STR} channel/${BiliConstants.CHANNEL} " +
                "innerVer/${BiliConstants.BUILD_STR} osVer/$osVer network/2"
    }
}
