package com.naaammme.bbspace.infra.crypto

import android.content.Context

/**
 * legal region 持久缓存
 * 仅在登录响应头更新 退出登录时清除
 */
class LegalRegionCache(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun get(): String = prefs.getString(KEY_REGION, "") ?: ""

    fun set(region: String) {
        if (region.isEmpty()) {
            clear()
            return
        }
        prefs.edit().putString(KEY_REGION, region).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_REGION).apply()
    }

    private companion object {
        const val PREFS_NAME = "legal_region"
        const val KEY_REGION = "region"
    }
}
