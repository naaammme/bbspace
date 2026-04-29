package com.naaammme.bbspace.core.model

import androidx.compose.runtime.Immutable

@Immutable
data class InterestChoose(
    val style: Int,
    val uniqueId: Int,
    val title: String,
    val subTitle: String,
    val confirmText: String,
    val genders: List<InterestGender>,
    val genderTitle: String,
    val ages: List<InterestAge>,
    val ageTitle: String,
    val items: List<InterestItem>
)

@Immutable
data class InterestItem(
    val id: Int,
    val name: String,
    val icon: String,
    val subItems: List<InterestSubItem>
)

@Immutable
data class InterestSubItem(
    val id: Int,
    val name: String
)

@Immutable
data class InterestGender(
    val id: Int,
    val title: String
)

@Immutable
data class InterestAge(
    val id: Int,
    val title: String
)
