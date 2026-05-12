package com.naaammme.bbspace.core.model

import androidx.compose.runtime.Immutable

@Immutable
data class UinterestResponse(
    val labels: List<InterestLabel>,
    val allLabels: List<InterestAreaLabels>,
    val pageMaterial: InterestPageMaterial?,
    val distributionMaterial: InterestDistributionMaterial?
)

@Immutable
data class InterestLabel(
    val name: String,
    val icon: String,
    val isFixed: Boolean,
    val areaName: String
)

@Immutable
data class InterestAreaLabels(
    val areaIcon: String,
    val areaName: String,
    val areaLabel: List<String>
)

@Immutable
data class InterestPageMaterial(
    val title: String,
    val subtitle: String,
    val myInterestTitle: String,
    val editButtonText: String,
    val backToDefaultButton: String,
    val moreInterestButton: String
)

@Immutable
data class InterestDistributionMaterial(
    val title: String,
    val subtitle: String,
    val areaList: List<DistributionAreaItem>
)

@Immutable
data class DistributionAreaItem(
    val name: String,
    val color: String,
    val count: Int
)
