package com.naaammme.bbspace.core.model

data class IpInfo(
    val addr: String,
    val country: String,
    val province: String,
    val city: String,
    val isp: String,
    val zoneId: Long,
    val countryCode: Int,
    val regionCode: String
)

data class FreeFlowRules(
    val cm: List<RuleInfo>,
    val ct: List<RuleInfo>,
    val cu: List<RuleInfo>,
    val hashValue: String
)

data class RuleInfo(
    val tf: Boolean,
    val mode: String,
    val action: String,
    val pattern: String,
    val actionBackup: List<String>
)

data class ColdStartData(
    val ipInfo: IpInfo?,
    val freeFlowRules: FreeFlowRules?
)
