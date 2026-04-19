package com.naaammme.bbspace.infra.network

import com.naaammme.bbspace.core.common.BiliConstants

/**
 * RESTful 签名 profile
 *
 * 不同接口使用的 appkey、appsec、build、mobi_app 并不完全相同，
 * 调用方必须显式选择 profile，不能靠拦截器在末尾偷偷改参数
 */
enum class BiliRestProfile(
    val appKey: String,
    val appSec: String,
    val build: String,
    val mobiApp: String,
    val statistics: String
) {
    APP(
        appKey = BiliConstants.APP_KEY,
        appSec = BiliConstants.APP_SEC,
        build = BiliConstants.BUILD_STR,
        mobiApp = BiliConstants.MOBI_APP,
        statistics = BiliConstants.STATISTICS_JSON
    ),
    HD(
        appKey = BiliConstants.APP_KEY_HD,
        appSec = BiliConstants.APP_SEC_HD,
        build = BiliConstants.BUILD_STR_HD,
        mobiApp = BiliConstants.MOBI_APP_HD,
        statistics = BiliConstants.STATISTICS_JSON_HD
    ),
    SMS(
        appKey = BiliConstants.APP_KEY_SMS,
        appSec = BiliConstants.APP_SEC_SMS,
        build = BiliConstants.BUILD_STR,
        mobiApp = BiliConstants.MOBI_APP,
        statistics = BiliConstants.STATISTICS_JSON
    )
}
