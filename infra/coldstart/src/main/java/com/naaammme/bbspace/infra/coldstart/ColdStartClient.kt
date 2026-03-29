package com.naaammme.bbspace.infra.coldstart

import bilibili.app.coldstart.v1.Coldstart
import bilibili.app.wall.v1.WallOuterClass
import com.google.protobuf.Any
import com.naaammme.bbspace.infra.crypto.RegionCodeCache
import com.naaammme.bbspace.core.common.log.Logger
import com.naaammme.bbspace.core.model.ColdStartData
import com.naaammme.bbspace.core.model.FreeFlowRules
import com.naaammme.bbspace.core.model.IpInfo
import com.naaammme.bbspace.core.model.RuleInfo
import com.naaammme.bbspace.infra.grpc.BiliGrpcClient
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ColdStartClient @Inject constructor(
    private val regionCodeCache: RegionCodeCache,
    private val grpcClient: dagger.Lazy<BiliGrpcClient>
) {
    companion object {
        private const val TAG = "ColdStartClient"
        private const val ENDPOINT = "bilibili.app.coldstart.v1.ColdStart/GetColdStartDeferredData"
        private const val BIZ_KEY_IP = "app.bilibili.com/x/resource/ip"
        private const val BIZ_KEY_RULE = "grpc.biliapi.net/bilibili.app.wall.v1.Wall/RuleInfo"

        private val COUNTRY_CODE_MAP = mapOf(
            86 to "CN",
            852 to "HK",
            853 to "MO",
            886 to "TW",
            1 to "US",
            81 to "JP",
            82 to "KR",
            65 to "SG",
            60 to "MY",
            66 to "TH"
        )
    }

    fun clearCache() {
        regionCodeCache.clear()
        Logger.d(TAG) { "IP 缓存已清除" }
    }

    suspend fun getColdStartData(): ColdStartData {
        try {
            val request = Coldstart.GetColdStartDataReq.newBuilder()
                .addReqList(
                    Coldstart.ColdStartBizReq.newBuilder()
                        .setBizKey(BIZ_KEY_IP)
                        .build()
                )
                .addReqList(
                    Coldstart.ColdStartBizReq.newBuilder()
                        .setBizKey(BIZ_KEY_RULE)
                        .build()
                )
                .build()

            val coldStartResp = grpcClient.get().call(
                endpoint = ENDPOINT,
                requestBytes = request.toByteArray(),
                parser = Coldstart.GetColdStartDataResp.parser()
            )

            var ipInfo: IpInfo? = null
            var freeFlowRules: FreeFlowRules? = null

            coldStartResp.respListList.forEach { resp ->
                when (resp.bizKey) {
                    BIZ_KEY_IP -> ipInfo = parseIpInfo(resp.bizResp)
                    BIZ_KEY_RULE -> freeFlowRules = parseFreeFlowRules(resp.bizResp)
                }
            }

            ipInfo?.let { info ->
                regionCodeCache.set(info.regionCode)
                Logger.d(TAG) { "IP 区域已缓存: ${info.regionCode}" }
            }

            return ColdStartData(ipInfo, freeFlowRules)
        } catch (e: Exception) {
            Logger.e(TAG, e) { "获取冷启动数据失败" }
            return ColdStartData(null, null)
        }
    }

    private fun parseIpInfo(any: Any): IpInfo? {
        return try {
            val httpResp = Coldstart.HttpJsonBizResp.parseFrom(any.value)
            val json = JSONObject(httpResp.data)
            val data = json.getJSONObject("data")

            val countryCode = data.getInt("country_code")
            val regionCode = COUNTRY_CODE_MAP[countryCode] ?: "CN"

            IpInfo(
                addr = data.getString("addr"),
                country = data.getString("country"),
                province = data.optString("province", ""),
                city = data.optString("city", ""),
                isp = data.getString("isp"),
                zoneId = data.getLong("zone_id"),
                countryCode = countryCode,
                regionCode = regionCode
            )
        } catch (e: Exception) {
            Logger.e(TAG, e) { "解析 IP 信息失败" }
            null
        }
    }

    private fun parseFreeFlowRules(any: Any): FreeFlowRules? {
        return try {
            val rulesReply = WallOuterClass.RulesReply.parseFrom(any.value)
            val rulesMap = rulesReply.rulesInfoMap

            FreeFlowRules(
                cm = rulesMap["cm"]?.rulesInfoList?.map { it.toRuleInfo() } ?: emptyList(),
                ct = rulesMap["ct"]?.rulesInfoList?.map { it.toRuleInfo() } ?: emptyList(),
                cu = rulesMap["cu"]?.rulesInfoList?.map { it.toRuleInfo() } ?: emptyList(),
                hashValue = rulesReply.hashValue
            )
        } catch (e: Exception) {
            Logger.e(TAG, e) { "解析免流规则失败" }
            null
        }
    }

    private fun WallOuterClass.RuleInfo.toRuleInfo() = RuleInfo(
        tf = this.tf,
        mode = this.m,
        action = this.a,
        pattern = this.p,
        actionBackup = this.aBackupList
    )
}
