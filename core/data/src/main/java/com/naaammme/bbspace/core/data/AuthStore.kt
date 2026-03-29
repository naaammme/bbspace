package com.naaammme.bbspace.core.data

import android.content.Context
import com.naaammme.bbspace.core.model.Cookie
import com.naaammme.bbspace.core.model.LoginCredential
import com.naaammme.bbspace.core.model.User
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 账号凭证存储
 * 管理登录凭证、多账号
 */
@Singleton
class AuthStore @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    companion object {
        private const val KEY_HD_KEY_PREFIX = "hd_key_"
        private const val KEY_HD_EXP_PREFIX = "hd_exp_"
    }

    private val prefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE)

    // 当前凭证

    var guestMode: Boolean
        get() = prefs.getBoolean("guest_mode", false)
        set(value) = prefs.edit().putBoolean("guest_mode", value).apply()

    val mid: Long get() = if (guestMode) 0L else prefs.getLong("mid", 0)
    val accessToken: String get() = if (guestMode) "" else prefs.getString("access_token", "") ?: ""
    val refreshToken: String get() = if (guestMode) "" else prefs.getString("refresh_token", "") ?: ""

    fun saveHdAccessKey(mid: Long, key: String, expiresIn: Long) {
        if (mid == 0L || key.isEmpty()) return
        val expiresAt = System.currentTimeMillis() + expiresIn * 1000
        prefs.edit().apply {
            putString(hdKeyKey(mid), key)
            putLong(hdExpKey(mid), expiresAt)
            apply()
        }
    }

    fun getHdAccessKeyForCurrent(): String {
        if (guestMode) return ""
        val currentMid = mid
        if (currentMid == 0L) return ""
        val key = prefs.getString(hdKeyKey(currentMid), "") ?: ""
        if (key.isEmpty()) return ""
        val expiresAt = prefs.getLong(hdExpKey(currentMid), 0)
        if (expiresAt > 0 && System.currentTimeMillis() >= expiresAt) {
            clearHdAccessKeyFor(currentMid)
            return ""
        }
        return key
    }

    fun hasHdAccessKeyForCurrent(): Boolean = getHdAccessKeyForCurrent().isNotEmpty()

    fun clearHdAccessKey() {
        val currentMid = mid
        if (currentMid == 0L) return
        clearHdAccessKeyFor(currentMid)
    }

    private fun clearHdAccessKeyFor(mid: Long) {
        prefs.edit().apply {
            remove(hdKeyKey(mid))
            remove(hdExpKey(mid))
            apply()
        }
    }

    private fun hdKeyKey(mid: Long): String = "$KEY_HD_KEY_PREFIX$mid"

    private fun hdExpKey(mid: Long): String = "$KEY_HD_EXP_PREFIX$mid"

    fun saveCredential(credential: LoginCredential) {
        prefs.edit().apply {
            putLong("mid", credential.mid)
            putString("access_token", credential.accessToken)
            putString("refresh_token", credential.refreshToken)
            putLong("expires_in", credential.expiresIn)
            putLong("login_time", System.currentTimeMillis())
            putString("cookies_json", cookiesToJson(credential.cookies))
            apply()
        }
        saveToAccountList(credential)
    }

    fun getSavedCredential(): LoginCredential? {
        if (guestMode) return null
        val mid = prefs.getLong("mid", 0)
        if (mid == 0L) return null
        return LoginCredential(
            mid = mid,
            accessToken = prefs.getString("access_token", "") ?: "",
            refreshToken = prefs.getString("refresh_token", "") ?: "",
            expiresIn = prefs.getLong("expires_in", 0),
            cookies = cookiesFromJson(prefs.getString("cookies_json", null))
        )
    }

    fun clearCredential() {
        val currentMid = prefs.getLong("mid", 0)
        val keys = listOf("mid", "access_token", "refresh_token", "expires_in", "login_time", "cookies_json")
        prefs.edit().apply {
            keys.forEach { remove(it) }
            apply()
        }
        if (currentMid != 0L) {
            clearHdAccessKeyFor(currentMid)
        }
    }

    // 多账号

    private fun saveToAccountList(credential: LoginCredential) {
        prefs.edit()
            .putString("account_${credential.mid}", credentialToJson(credential))
            .apply()
    }

    fun getAllAccounts(): List<LoginCredential> {
        return prefs.all.entries
            .filter { it.key.startsWith("account_") }
            .mapNotNull { credentialFromJson(it.value as? String) }
    }

    fun switchAccount(mid: Long): LoginCredential? {
        val json = prefs.getString("account_$mid", null) ?: return null
        val credential = credentialFromJson(json) ?: return null
        saveCredential(credential)
        return credential
    }

    fun removeAccount(mid: Long) {
        prefs.edit().remove("account_$mid").apply()
        clearHdAccessKeyFor(mid)
        if (this.mid == mid) {
            clearCredential()
            clearHdAccessKey()
        }
    }

    fun exportAllAccounts(): String {
        val arr = JSONArray()
        getAllAccounts().forEach { arr.put(JSONObject(credentialToJson(it))) }
        return arr.toString(2)
    }

    fun importAccounts(json: String): List<LoginCredential> {
        val arr = JSONArray(json)
        val result = mutableListOf<LoginCredential>()
        for (i in 0 until arr.length()) {
            val credential = credentialFromJson(arr.getJSONObject(i).toString())
            if (credential != null) {
                saveToAccountList(credential)
                result.add(credential)
            }
        }
        return result
    }

    fun exportCredential(): String? {
        val credential = getSavedCredential() ?: return null
        return credentialToJson(credential)
    }

    fun importCredential(json: String): LoginCredential? {
        val credential = credentialFromJson(json) ?: return null
        saveCredential(credential)
        return credential
    }


    private fun credentialToJson(credential: LoginCredential): String {
        return JSONObject().apply {
            put("mid", credential.mid)
            put("access_token", credential.accessToken)
            put("refresh_token", credential.refreshToken)
            put("expires_in", credential.expiresIn)
            put("cookies", JSONArray(cookiesToJson(credential.cookies)))
            put("export_time", System.currentTimeMillis())
        }.toString()
    }

    private fun credentialFromJson(json: String?): LoginCredential? {
        if (json.isNullOrEmpty()) return null
        return try {
            val obj = JSONObject(json)
            LoginCredential(
                mid = obj.getLong("mid"),
                accessToken = obj.getString("access_token"),
                refreshToken = obj.getString("refresh_token"),
                expiresIn = obj.optLong("expires_in", 0),
                cookies = cookiesFromJson(obj.optJSONArray("cookies")?.toString())
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun cookiesToJson(cookies: List<Cookie>): String {
        val arr = JSONArray()
        cookies.forEach { cookie ->
            arr.put(JSONObject().apply {
                put("name", cookie.name)
                put("value", cookie.value)
                put("http_only", cookie.httpOnly)
                put("expires", cookie.expires)
                put("secure", cookie.secure)
            })
        }
        return arr.toString()
    }

    private fun cookiesFromJson(json: String?): List<Cookie> {
        if (json.isNullOrEmpty()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                Cookie(
                    name = obj.getString("name"),
                    value = obj.getString("value"),
                    httpOnly = obj.optBoolean("http_only", false),
                    expires = obj.optLong("expires", 0),
                    secure = obj.optBoolean("secure", false)
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    // 用户信息
    fun saveUserInfo(user: User) {
        prefs.edit().apply {
            putLong("user_mid", user.mid)
            putString("user_name", user.name)
            putString("user_avatar", user.avatar)
            putString("user_sign", user.sign)
            putInt("user_level", user.level)
            putFloat("user_coins", user.coins.toFloat())
            putInt("user_sex", user.sex)
            putString("user_birthday", user.birthday)
            putInt("user_vip_type", user.vipType)
            putInt("user_vip_status", user.vipStatus)
            putBoolean("user_email_verified", user.emailVerified)
            putBoolean("user_phone_verified", user.phoneVerified)
            putInt("user_official_role", user.officialRole)
            putBoolean("user_silence", user.silence)
            putInt("user_dynamic", user.dynamic)
            putInt("user_following", user.following)
            putInt("user_follower", user.follower)
            putString("uinfo_${user.mid}", userToJson(user))
            apply()
        }
    }

    fun getAllUserInfos(): Map<Long, User> {
        return prefs.all.entries
            .filter { it.key.startsWith("uinfo_") }
            .mapNotNull { entry ->
                val mid = entry.key.removePrefix("uinfo_").toLongOrNull() ?: return@mapNotNull null
                val user = userFromJson(entry.value as? String) ?: return@mapNotNull null
                mid to user
            }
            .toMap()
    }

    fun getUserInfo(): User? {
        val mid = prefs.getLong("user_mid", 0)
        if (mid == 0L) return null
        return User(
            mid = mid,
            name = prefs.getString("user_name", "") ?: "",
            avatar = prefs.getString("user_avatar", "") ?: "",
            sign = prefs.getString("user_sign", "") ?: "",
            level = prefs.getInt("user_level", 0),
            coins = prefs.getFloat("user_coins", 0f).toDouble(),
            sex = prefs.getInt("user_sex", 0),
            birthday = prefs.getString("user_birthday", "") ?: "",
            vipType = prefs.getInt("user_vip_type", 0),
            vipStatus = prefs.getInt("user_vip_status", 0),
            emailVerified = prefs.getBoolean("user_email_verified", false),
            phoneVerified = prefs.getBoolean("user_phone_verified", false),
            officialRole = prefs.getInt("user_official_role", 0),
            silence = prefs.getBoolean("user_silence", false),
            dynamic = prefs.getInt("user_dynamic", 0),
            following = prefs.getInt("user_following", 0),
            follower = prefs.getInt("user_follower", 0)
        )
    }

    fun clearUserInfo() {
        val keys = listOf(
            "user_mid", "user_name", "user_avatar", "user_sign", "user_level",
            "user_coins", "user_sex", "user_birthday", "user_vip_type", "user_vip_status",
            "user_email_verified", "user_phone_verified", "user_official_role", "user_silence",
            "user_dynamic", "user_following", "user_follower"
        )
        prefs.edit().apply {
            keys.forEach { remove(it) }
            apply()
        }
    }

    private fun userToJson(user: User): String = JSONObject().apply {
        put("mid", user.mid)
        put("name", user.name)
        put("avatar", user.avatar)
        put("sign", user.sign)
        put("level", user.level)
        put("coins", user.coins)
        put("sex", user.sex)
        put("birthday", user.birthday)
        put("vip_type", user.vipType)
        put("vip_status", user.vipStatus)
        put("email_verified", user.emailVerified)
        put("phone_verified", user.phoneVerified)
        put("official_role", user.officialRole)
        put("silence", user.silence)
        put("dynamic", user.dynamic)
        put("following", user.following)
        put("follower", user.follower)
    }.toString()

    private fun userFromJson(json: String?): User? {
        if (json.isNullOrEmpty()) return null
        return try {
            val o = JSONObject(json)
            User(
                mid = o.getLong("mid"),
                name = o.optString("name"),
                avatar = o.optString("avatar"),
                sign = o.optString("sign"),
                level = o.optInt("level"),
                coins = o.optDouble("coins"),
                sex = o.optInt("sex"),
                birthday = o.optString("birthday"),
                vipType = o.optInt("vip_type"),
                vipStatus = o.optInt("vip_status"),
                emailVerified = o.optBoolean("email_verified"),
                phoneVerified = o.optBoolean("phone_verified"),
                officialRole = o.optInt("official_role"),
                silence = o.optBoolean("silence"),
                dynamic = o.optInt("dynamic"),
                following = o.optInt("following"),
                follower = o.optInt("follower")
            )
        } catch (_: Exception) {
            null
        }
    }
}
