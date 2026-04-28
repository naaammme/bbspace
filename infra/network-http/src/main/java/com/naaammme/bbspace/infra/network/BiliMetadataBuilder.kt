package com.naaammme.bbspace.infra.network

import bilibili.metadata.MetadataOuterClass
import bilibili.metadata.device.DeviceOuterClass
import bilibili.metadata.fawkes.Fawkes
import bilibili.metadata.locale.LocaleOuterClass
import bilibili.metadata.network.NetworkOuterClass
import com.naaammme.bbspace.core.common.BiliConstants
import com.naaammme.bbspace.infra.crypto.DeviceIdentity
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BiliMetadataBuilder @Inject constructor(
    private val deviceIdentity: DeviceIdentity
) {
    fun buildMetadata(accessKey: String = ""): ByteArray {
        return MetadataOuterClass.Metadata.newBuilder().apply {
            if (accessKey.isNotEmpty()) {
                this.accessKey = accessKey
            }
            mobiApp = BiliConstants.MOBI_APP
            device = ""
            build = BiliConstants.BUILD
            channel = BiliConstants.CHANNEL
            buvid = deviceIdentity.buvid
            platform = BiliConstants.PLATFORM
        }.build().toByteArray()
    }

    fun buildDevice(): ByteArray {
        val fts = System.currentTimeMillis() / 1000 - (30 * 24 * 3600)
        return DeviceOuterClass.Device.newBuilder().apply {
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
        }.build().toByteArray()
    }
    // TODO 动态化 type用ConnectivityManager检测WiFi或蜂窝 oid用TelephonyManager.getNetworkOperator读运营商 cellular读蜂窝代数 tf等免流模块实现后接入
    fun buildNetwork(): ByteArray {
        val quality = NetworkOuterClass.NetQuality.newBuilder()
            .setSuccessRate(-1.0f)
            .build()

        return NetworkOuterClass.Network.newBuilder().apply {
            type = NetworkOuterClass.NetworkType.WIFI
            tf = NetworkOuterClass.TFType.TF_UNKNOWN
            oid = "46000"
            this.quality = quality
        }.build().toByteArray()
    }

    fun buildLocale(): ByteArray {
        val cLocale = LocaleOuterClass.LocaleIds.newBuilder()
            .setLanguage("zh")
            .setScript("Hans")
            .setRegion("CN")
            .build()
        val sLocale = LocaleOuterClass.LocaleIds.newBuilder()
            .setLanguage("zh")
            .setScript("Hans")
            .setRegion("CN")
            .build()

        return LocaleOuterClass.Locale.newBuilder().apply {
            this.cLocale = cLocale
            this.sLocale = sLocale
            // simCode = ""
            timezone = "Asia/Shanghai"
            utcOffset = "+08:00"
            // is_daylight_time = 0 // 是否夏令时
            // always_translate = 0 // 是否始终翻译

        }.build().toByteArray()
    }

    fun buildFawkes(): ByteArray {
        val sessionId = UUID.randomUUID().toString().replace("-", "").substring(0, 8)
        return Fawkes.FawkesReq.newBuilder().apply {
            appkey = BiliConstants.APP_KEY_NAME
            env = BiliConstants.ENV
            this.sessionId = sessionId
        }.build().toByteArray()
    }
}
