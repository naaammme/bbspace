package com.naaammme.bbspace.infra.network

import com.naaammme.bbspace.core.common.BiliConstants
import com.naaammme.bbspace.infra.crypto.DeviceIdentity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * RESTful 公共参数构建器
 *
 * 这里构建稳定公参，业务自己的字段继续在调用点 put
 */
@Singleton
class BiliRestParamBuilder @Inject constructor(
    private val deviceIdentity: DeviceIdentity
) {
    /**
     * 构建普通 app 侧 REST 公参
     */
    fun app(
        profile: BiliRestProfile,
        ts: Long,
        accessKey: String = ""
    ): Map<String, String> {
        return buildMap {
            put("build", profile.build)
            put("c_locale", LOCALE)
            put("channel", BiliConstants.CHANNEL)
            put("disable_rcmd", ZERO)
            put("mobi_app", profile.mobiApp)
            put("platform", BiliConstants.PLATFORM)
            put("s_locale", LOCALE)
            put("statistics", profile.statistics)
            put("ts", ts.toString())
            if (accessKey.isNotBlank()) {
                put("access_key", accessKey)
            }
        }
    }

    /**
     * 构建 passport 侧公参
     *
     * 在 app 公参基础上补充登录注册链路需要的设备字段
     */
    fun passport(
        profile: BiliRestProfile,
        ts: Long,
        accessKey: String = ""
    ): Map<String, String> {
        return buildMap {
            putAll(app(profile, ts, accessKey))
            put("bili_local_id", deviceIdentity.fp)
            put("buvid", deviceIdentity.buvid)
            put("device", PHONE)
            put("device_id", deviceIdentity.fp)
            put("device_name", "${deviceIdentity.brand}${deviceIdentity.model}")
            put("device_platform", "Android${deviceIdentity.osVer}${deviceIdentity.brand}${deviceIdentity.model}")
            put("local_id", deviceIdentity.buvid)
        }
    }

    private companion object {
        const val LOCALE = "zh-Hans_CN"
        const val PHONE = "phone"
        const val ZERO = "0"
    }
}
