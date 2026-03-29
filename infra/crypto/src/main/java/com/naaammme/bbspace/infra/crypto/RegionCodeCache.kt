package com.naaammme.bbspace.infra.crypto

/**
 * 区域码内存缓存
 * 每次冷启动由 ColdStartClient 重新获取并设值
 */
class RegionCodeCache {

    @Volatile
    private var cached: String = DEFAULT_REGION

    fun get(): String = cached

    fun set(regionCode: String) {
        cached = regionCode
    }

    fun clear() {
        cached = ""
    }

    companion object {
        private const val DEFAULT_REGION = "CN"
    }
}
