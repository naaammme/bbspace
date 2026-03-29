package com.naaammme.bbspace.core.model

data class User(
    val mid: Long,
    val name: String,
    val avatar: String,
    val sign: String = "",
    val level: Int = 0,
    val coins: Double = 0.0,
    val sex: Int = 0,
    val birthday: String = "",
    val vipType: Int = 0,
    val vipStatus: Int = 0,
    val emailVerified: Boolean = false,
    val phoneVerified: Boolean = false,
    val officialRole: Int = 0,
    val silence: Boolean = false,
    val dynamic: Int = 0,
    val following: Int = 0,
    val follower: Int = 0
)
