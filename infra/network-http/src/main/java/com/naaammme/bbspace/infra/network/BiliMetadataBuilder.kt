package com.naaammme.bbspace.infra.network

import bilibili.metadata.device.device
import bilibili.metadata.fawkes.fawkesReq
import bilibili.metadata.locale.locale
import bilibili.metadata.locale.localeIds
import bilibili.metadata.metadata
import bilibili.metadata.network.NetworkOuterClass
import bilibili.metadata.network.network
import com.naaammme.bbspace.infra.crypto.DeviceIdentity
import com.naaammme.bbspace.core.common.BiliConstants
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Binary Metadata 构建器
 * HTTP 和 gRPC 共用
 */
@Singleton
class BiliMetadataBuilder @Inject constructor(
    private val deviceIdentity: DeviceIdentity
) {
    fun buildMetadata(accessKey: String = ""): ByteArray {
        return metadata {
            if (accessKey.isNotEmpty()) {
                this.accessKey = accessKey
            }
            mobiApp = BiliConstants.MOBI_APP
            device = ""
            build = BiliConstants.BUILD
            channel = BiliConstants.CHANNEL
            buvid = deviceIdentity.buvid
            platform = BiliConstants.PLATFORM
        }.toByteArray()
    }

    fun buildDevice(): ByteArray {
        val fts = System.currentTimeMillis() / 1000 - (30 * 24 * 3600)
        return device {
            appId = BiliConstants.APP_ID
            build = BiliConstants.BUILD
            buvid = deviceIdentity.buvid
            mobiApp = BiliConstants.MOBI_APP
            platform = BiliConstants.PLATFORM
            device = ""
            channel = BiliConstants.CHANNEL
            brand = deviceIdentity.brand
            model = deviceIdentity.model
            osver = deviceIdentity.osVer
            fpLocal = deviceIdentity.fp
            fpRemote = deviceIdentity.fp
            versionName = BiliConstants.VERSION
            fp = deviceIdentity.fp
            this.fts = fts
        }.toByteArray()
    }

    fun buildNetwork(): ByteArray { // TODO 动态化 type用ConnectivityManager检测WiFi或蜂窝 oid用TelephonyManager.getNetworkOperator读运营商 cellular读蜂窝代数 tf等免流模块实现后接入
        return network {
            type = NetworkOuterClass.NetworkType.WIFI
            tf = NetworkOuterClass.TFType.TF_UNKNOWN
            oid = "46000"
            quality = NetworkOuterClass.NetQuality.newBuilder()
                .setSuccessRate(-1.0f)
                .build()
        }.toByteArray()
    }

    fun buildLocale(): ByteArray { // locale就硬编码吧
        return locale {
            cLocale = localeIds {
                language = "zh"
                script = "Hans"
                region = "CN"
            }
            sLocale = localeIds {
                language = "zh"
                script = "Hans"
                region = "CN"
            }
            // simCode = ""
            timezone = "Asia/Shanghai"
            utcOffset = "+08:00"
            // is_daylight_time = 0 // 是否夏令时
            // always_translate = 0 // 是否始终翻译

        }.toByteArray()
    }

    fun buildFawkes(): ByteArray {
        val sessionId = UUID.randomUUID().toString().replace("-", "").substring(0, 8)
        return fawkesReq {
            appkey = BiliConstants.MOBI_APP
            env = BiliConstants.ENV
            this.sessionId = sessionId
        }.toByteArray()
    }
}
