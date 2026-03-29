package com.naaammme.bbspace.infra.grpc

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import bilibili.gaia.gw.GwApi.DeviceAppList
import bilibili.gaia.gw.GwApi.EncryptType
import bilibili.gaia.gw.GwApi.FetchPublicKeyReply
import bilibili.gaia.gw.GwApi.GaiaEncryptMsgReq
import bilibili.gaia.gw.GwApi.GaiaMsgHeader
import bilibili.gaia.gw.GwApi.PayloadType
import bilibili.gaia.gw.GwApi.UploadAppListReply
import com.google.protobuf.ByteString
import com.google.protobuf.Empty
import com.naaammme.bbspace.core.common.log.Logger
import com.naaammme.bbspace.infra.crypto.GaiaEncryptor
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gaia 风控上报
 * 获取公钥 加密应用列表 上报 管理上报频率
 */
@Singleton
class GaiaReporter @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val grpcClient: BiliGrpcClient
) {
    companion object {
        private const val TAG = "GaiaReporter"
        private const val PREFS_NAME = "gaia_reporter"
        private const val KEY_LAST_UPLOAD = "last_upload_time"
        private const val KEY_FIRST_INSTALL = "first_install"
        private const val UPLOAD_INTERVAL = 24 * 60 * 60 * 1000L

        private const val FETCH_KEY_EP = "bilibili.gaia.gw.Gaia/ExGetAxe"
        private const val UPLOAD_EP = "bilibili.gaia.gw.Gaia/ExClimbAppleTrees"

        private val SENSITIVE_PREFIXES = listOf(
            "de.robv.android.xposed",
            "com.topjohnwu.magisk",
            "com.bly.dkplat",
            "com.lbe.parallel",
            "com.ludashi.superboost",
            "com.excelliance.dualaid"
        )

        private val MOCK_SYS_APPS = listOf(
            "com.android.settings",
            "com.android.phone",
            "com.android.camera",
            "com.android.gallery3d",
            "com.android.contacts",
            "com.android.mms",
            "com.android.calendar",
            "com.android.deskclock",
            "com.android.calculator2",
            "com.android.chrome",
            "com.google.android.gms",
            "com.google.android.gsf",
            "com.android.systemui",
            "com.android.launcher3",
            "com.android.inputmethod.latin",
            "com.android.bluetooth",
            "com.android.nfc",
            "com.android.wifi",
            "com.android.providers.media",
            "com.android.providers.contacts",
            "com.android.providers.calendar",
            "com.android.providers.downloads",
            "com.android.packageinstaller",
            "com.android.certinstaller",
            "com.android.keychain",
            "com.android.vpndialogs",
            "com.android.shell",
            "com.android.statementservice",
            "com.android.storagemanager",
            "com.android.externalstorage",
            "com.android.documentsui",
            "com.android.printspooler",
            "com.android.musicfx",
            "com.android.soundrecorder",
            "com.android.filemanager",
            "com.android.browser",
            "com.android.email",
            "com.android.exchange",
            "com.android.backupconfirm",
            "com.android.companiondevicemanager",
            "com.google.android.gsf.login",
            "com.google.android.syncadapters.contacts",
            "com.google.android.syncadapters.calendar",
            "com.google.android.backuptransport",
            "com.google.android.feedback",
            "com.google.android.onetimeinitializer",
            "com.google.android.partnersetup"
        )

        private val MOCK_USER_APPS = listOf(
            "tv.danmaku.bili",
            "com.tencent.mm",
            "com.eg.android.AlipayGphone",
            "com.tencent.mobileqq",
            "com.taobao.taobao",
            "com.jingdong.app.mall",
            "com.netease.cloudmusic",
            "com.ss.android.ugc.aweme",
            "com.sina.weibo",
            "com.zhihu.android",
            "com.baidu.BaiduMap",
            "com.dianping.v1",
            "com.meituan.android.takeaway",
            "com.autonavi.minimap",
            "com.xunmeng.pinduoduo",
            "me.ele"
        )

        private const val MIN_APP_COUNT = 10
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * 检查并执行风控上报
     * 首次安装或距上次上报超过24小时才会执行
     */
    suspend fun reportIfNeeded() {
        try {
            if (!shouldReport()) {
                Logger.d(TAG) { "跳过上报" }
                return
            }

            val isFirst = prefs.getBoolean(KEY_FIRST_INSTALL, true)
            Logger.d(TAG) { "开始风控上报 isFirst=$isFirst" }

            val keyReply = fetchPublicKey()
            Logger.d(TAG) { "公钥获取成功 version=${keyReply.version} deadline=${keyReply.deadline}" }

            if (keyReply.deadline < System.currentTimeMillis() / 1000) {
                Logger.w(TAG) { "公钥已过期" }
                return
            }

            val (sysApps, userApps) = collectApps()
            Logger.d(TAG) { "应用列表收集完成 sys=${sysApps.size} user=${userApps.size}" }

            val source = if (isFirst) "first_installation" else "first_open"
            val reply = upload(sysApps, userApps, source, keyReply.publicKey)

            Logger.i(TAG) { "上报成功 trace_id: ${reply.traceId}" }
            markUploaded()
        } catch (e: Exception) {
            Logger.e(TAG, e) { "上报失败" }
        }
    }

    private fun shouldReport(): Boolean {
        if (prefs.getBoolean(KEY_FIRST_INSTALL, true)) return true
        val last = prefs.getLong(KEY_LAST_UPLOAD, 0)
        return System.currentTimeMillis() - last >= UPLOAD_INTERVAL
    }

    private suspend fun fetchPublicKey(): FetchPublicKeyReply {
        return grpcClient.call(
            endpoint = FETCH_KEY_EP,
            requestBytes = Empty.getDefaultInstance().toByteArray(),
            parser = FetchPublicKeyReply.parser()
        )
    }

    private suspend fun upload(
        sysApps: List<String>,
        userApps: List<String>,
        source: String,
        publicKey: String
    ): UploadAppListReply {
        val appList = DeviceAppList.newBuilder()
            .setSource(source)
            .addAllSystemAppList(sysApps)
            .addAllUserAppList(userApps)
            .build()

        val (encKey, encPayload) = GaiaEncryptor.encrypt(appList.toByteArray(), publicKey)

        val req = GaiaEncryptMsgReq.newBuilder()
            .setHeader(
                GaiaMsgHeader.newBuilder()
                    .setEncodeType(EncryptType.SERVER_RSA_AES)
                    .setPayloadType(PayloadType.DEVICE_APP_LIST)
                    .setEncodedAesKey(ByteString.copyFrom(encKey))
                    .setTs(System.currentTimeMillis())
            )
            .setEncryptPayload(ByteString.copyFrom(encPayload))
            .build()

        return grpcClient.call(
            endpoint = UPLOAD_EP,
            requestBytes = req.toByteArray(),
            parser = UploadAppListReply.parser()
        )
    }

    private fun collectApps(): Pair<List<String>, List<String>> {
        try {
            val packages = context.packageManager
                .getInstalledApplications(PackageManager.GET_META_DATA)
            val sys = mutableListOf<String>()
            val user = mutableListOf<String>()

            for (app in packages) {
                val pkg = app.packageName
                if (SENSITIVE_PREFIXES.any { pkg.startsWith(it) }) continue
                if ((app.flags and ApplicationInfo.FLAG_SYSTEM) != 0) {
                    sys.add(pkg)
                } else {
                    user.add(pkg)
                }
            }

            if (sys.size + user.size < MIN_APP_COUNT) {
                Logger.w(TAG) { "应用列表过少(sys=${sys.size} user=${user.size}) 降级使用mock数据" }
                return Pair(MOCK_SYS_APPS, MOCK_USER_APPS)
            }

            return Pair(sys, user)
        } catch (e: Exception) {
            Logger.w(TAG) { "收集应用列表失败 使用mock数据" }
            return Pair(MOCK_SYS_APPS, MOCK_USER_APPS)
        }
    }

    private fun markUploaded() {
        prefs.edit()
            .putBoolean(KEY_FIRST_INSTALL, false)
            .putLong(KEY_LAST_UPLOAD, System.currentTimeMillis())
            .apply()
    }
}
