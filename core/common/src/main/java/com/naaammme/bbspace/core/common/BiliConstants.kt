package com.naaammme.bbspace.core.common

/**
 * B站 API 常量
 *
 * 所有 Base URL 和应用级常量统一在此定义，各模块不得重复声明。
 * 端点路径按功能就近定义在各模块的 companion object 中。
 */
object BiliConstants {
    // 粉版 (android) 的 appkey
    const val APP_KEY = "1d8b6e7d45233436"
    const val APP_SEC = "560c52ccd288fed045859ed18bffd973"

    // TV 版 (android_hd) 的 appkey
    const val APP_KEY_HD = "dfca71928277209b"
    const val APP_SEC_HD = "b5475a8825547a4fc26c7d518eaaa02e"

    // 手机号登录专用 appkey
    const val APP_KEY_SMS = "783bbb7264451d82"
    const val APP_SEC_SMS = "2653583c8873dea268ab9386918b1d65"

    const val BUILD = 8620300
    const val BUILD_STR = "8620300"

    const val BUILD_HD = 2031100

    const val BUILD_STR_HD = "2031100"
    const val VERSION = "8.62.0"

    const val VERSION_HD = "2.3.1"
    const val MOBI_APP = "android"

    const val MOBI_APP_HD = "android_hd"
    const val CHANNEL = "bili"
    const val APP_ID = 1

    const val APP_ID_HD = 5
    const val PLATFORM = "android"
    const val ENV = "prod"

    // HMAC key for ticket
    const val HMAC_KEY = "Ezlc3tgtl"
    const val TICKET_KEY_ID = "ec01"

    // Base URLs
    const val BASE_URL_APP = "https://app.bilibili.com"
    const val BASE_URL_API = "https://api.bilibili.com"
    const val BASE_URL_PASSPORT = "https://passport.bilibili.com"

    // Statistics JSON
    const val STATISTICS_JSON = """{"appId":1,"platform":3,"version":8620300}"""

    const val STATISTICS_JSON_HD = """{"appId":5,"platform":3,"version":"2.3.1","abtest":""}"""

}
