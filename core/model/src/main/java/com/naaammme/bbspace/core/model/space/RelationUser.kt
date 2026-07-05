package com.naaammme.bbspace.core.model

import androidx.compose.runtime.Immutable

@Immutable
data class RelationUser(
    val mid: Long,
    val uname: String,
    val face: String,
    val sign: String,
    val isVip: Boolean,
    val vipLabel: String?
)
