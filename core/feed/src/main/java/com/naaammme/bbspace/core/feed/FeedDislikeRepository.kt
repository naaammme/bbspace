package com.naaammme.bbspace.core.feed

import com.naaammme.bbspace.core.common.BiliConstants
import com.naaammme.bbspace.core.auth.AuthStore
import com.naaammme.bbspace.core.settings.PageActionTracker
import com.naaammme.bbspace.core.model.FeedDislikeContext
import com.naaammme.bbspace.core.model.ThreePointReason
import com.naaammme.bbspace.core.model.ThreePointReasonKind
import com.naaammme.bbspace.infra.network.BiliRestClient
import com.naaammme.bbspace.infra.network.BiliRestParamBuilder
import com.naaammme.bbspace.infra.network.BiliRestProfile
import javax.inject.Inject
import javax.inject.Singleton

data class FeedActionResult(
    val toast: String,
    val removed: Boolean = false
)

@Singleton
class FeedDislikeRepository @Inject constructor(
    private val restClient: BiliRestClient,
    private val restParamBuilder: BiliRestParamBuilder,
    private val authStore: AuthStore,
    private val pageActionTracker: PageActionTracker
) {

    suspend fun dislike(
        context: FeedDislikeContext,
        reason: ThreePointReason
    ): FeedActionResult {
        val profile = context.restProfile()
        val ts = System.currentTimeMillis() / 1000
        val params = restParamBuilder.app(profile, ts, context.accessKey()) + buildMap {
            putBaseContext(context)
            put("action_id", pageActionTracker.currentActionId())
            putReason(reason)
            reason.extra?.takeIf(String::isNotBlank)?.let { put("extra", it) }
        }
        restClient.getSigned(
            url = "${BiliConstants.BASE_URL_APP}$FEED_DISLIKE_ENDPOINT",
            params = params,
            profile = profile
        )
        return FeedActionResult(
            toast = reason.toast.ifBlank { "已提交" },
            removed = true
        )
    }

    suspend fun cancelDislike(context: FeedDislikeContext): FeedActionResult {
        val profile = context.restProfile()
        val ts = System.currentTimeMillis() / 1000
        val params = restParamBuilder.app(profile, ts, context.accessKey()) + buildMap {
            putBaseContext(context)
            put("action_id", pageActionTracker.currentActionId())
        }
        restClient.getSigned(
            url = "${BiliConstants.BASE_URL_APP}$FEED_DISLIKE_CANCEL_ENDPOINT",
            params = params,
            profile = profile
        )
        return FeedActionResult(toast = "已撤销")
    }

    private fun MutableMap<String, String>.putBaseContext(context: FeedDislikeContext) {
        put("id", context.id)
        put("goto", context.goto)
        put("is_light_panel", "false")
        put("from_spmid", context.fromSpmid)
        put("spmid", context.spmid)
        context.fromModule?.takeIf(String::isNotBlank)?.let { put("from_module", it) }
        context.trackId?.takeIf(String::isNotBlank)?.let { put("track_id", it) }
        context.reportData?.takeIf(String::isNotBlank)?.let { put("report_data", it) }
        context.mid?.takeIf { it > 0L }?.let { put("mid", it.toString()) }
        context.rid?.takeIf { it > 0L }?.let { put("rid", it.toString()) }
        context.tagId?.takeIf { it > 0L }?.let { put("tag_id", it.toString()) }
    }

    private fun MutableMap<String, String>.putReason(reason: ThreePointReason) {
        when (reason.kind) {
            ThreePointReasonKind.DISLIKE -> put("reason_id", reason.id.toString())
            ThreePointReasonKind.FEEDBACK -> put("feedback_id", reason.id.toString())
        }
    }

    private fun FeedDislikeContext.restProfile(): BiliRestProfile {
        return if (useHdProfile) BiliRestProfile.HD else BiliRestProfile.APP
    }

    private fun FeedDislikeContext.accessKey(): String {
        return if (useHdProfile) {
            authStore.getHdAccessKeyForCurrent()
        } else {
            authStore.accessToken
        }
    }

    private companion object {
        const val FEED_DISLIKE_ENDPOINT = "/x/feed/dislike"
        const val FEED_DISLIKE_CANCEL_ENDPOINT = "/x/feed/dislike/cancel"
    }
}
