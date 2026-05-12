package com.naaammme.bbspace.core.data.repository

import com.naaammme.bbspace.core.common.BiliConstants
import com.naaammme.bbspace.core.data.AuthStore
import com.naaammme.bbspace.core.domain.feed.InterestRepository
import com.naaammme.bbspace.core.model.DistributionAreaItem
import com.naaammme.bbspace.core.model.InterestAreaLabels
import com.naaammme.bbspace.core.model.InterestDistributionMaterial
import com.naaammme.bbspace.core.model.InterestLabel
import com.naaammme.bbspace.core.model.InterestPageMaterial
import com.naaammme.bbspace.core.model.UinterestResponse
import com.naaammme.bbspace.infra.network.BiliRestClient
import com.naaammme.bbspace.infra.network.BiliRestParamBuilder
import com.naaammme.bbspace.infra.network.BiliRestProfile
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Singleton
class InterestRepoImpl @Inject constructor(
    private val restClient: BiliRestClient,
    private val restParamBuilder: BiliRestParamBuilder,
    private val authStore: AuthStore
) : InterestRepository {

    override suspend fun fetchUinterest(): UinterestResponse {
        val accessToken = authStore.accessToken
        check(accessToken.isNotBlank()) { "请先登录" }
        val ts = System.currentTimeMillis() / 1000
        val json = restClient.getSigned(
            url = "${BiliConstants.BASE_URL_APP}$UINTEREST_ENDPOINT",
            params = restParamBuilder.app(BiliRestProfile.APP, ts, accessToken) + mapOf(
                "need_all_label" to "1"
            ),
            profile = BiliRestProfile.APP
        )
        return withContext(Dispatchers.Default) {
            val data = json.getJSONObject("data")
            UinterestResponse(
                labels = parseLabels(data),
                allLabels = parseAllLabels(data),
                pageMaterial = parsePageMaterial(data.optJSONObject("uinterest_page_material")),
                distributionMaterial = parseDistributionMaterial(
                    data.optJSONObject("uinterest_distribution_material")
                )
            )
        }
    }

    private fun parseLabels(data: JSONObject): List<InterestLabel> {
        val arr = data.optJSONArray("labels") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val obj = arr.optJSONObject(i) ?: return@mapNotNull null
            InterestLabel(
                name = obj.optString("name"),
                icon = obj.optString("icon"),
                isFixed = obj.optInt("is_fixed") == 1,
                areaName = obj.optString("area_name")
            )
        }
    }

    private fun parseAllLabels(data: JSONObject): List<InterestAreaLabels> {
        val arr = data.optJSONArray("all_labels") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val obj = arr.optJSONObject(i) ?: return@mapNotNull null
            val labelArr = obj.optJSONArray("area_label") ?: return@mapNotNull null
            InterestAreaLabels(
                areaIcon = obj.optString("area_icon"),
                areaName = obj.optString("area_name"),
                areaLabel = (0 until labelArr.length()).mapNotNull { j ->
                    labelArr.optString(j).takeIf { it.isNotBlank() }
                }
            )
        }
    }

    private fun parsePageMaterial(obj: JSONObject?): InterestPageMaterial? {
        obj ?: return null
        return InterestPageMaterial(
            title = obj.optString("title"),
            subtitle = obj.optString("subtitle"),
            myInterestTitle = obj.optString("my_interest_title"),
            editButtonText = obj.optString("edit_button_text"),
            backToDefaultButton = obj.optString("back_to_default_button"),
            moreInterestButton = obj.optString("more_interest_button")
        )
    }

    private fun parseDistributionMaterial(obj: JSONObject?): InterestDistributionMaterial? {
        obj ?: return null
        val arr = obj.optJSONArray("area_list") ?: return null
        return InterestDistributionMaterial(
            title = obj.optString("title"),
            subtitle = obj.optString("subtitle"),
            areaList = (0 until arr.length()).mapNotNull { i ->
                val item = arr.optJSONObject(i) ?: return@mapNotNull null
                DistributionAreaItem(
                    name = item.optString("name"),
                    color = item.optString("color"),
                    count = item.optInt("count")
                )
            }
        )
    }

    private companion object {
        const val UINTEREST_ENDPOINT = "/x/v2/feed/uinterest"
    }
}
