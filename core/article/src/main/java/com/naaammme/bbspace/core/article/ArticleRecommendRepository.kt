package com.naaammme.bbspace.core.article

import android.text.format.DateFormat
import com.naaammme.bbspace.core.common.BiliConstants
import com.naaammme.bbspace.core.auth.AuthStore
import com.naaammme.bbspace.core.model.article.ArticleRecommendItem
import com.naaammme.bbspace.core.model.article.ArticleRecommendPage
import com.naaammme.bbspace.infra.network.BiliRestClient
import com.naaammme.bbspace.infra.network.BiliRestParamBuilder
import com.naaammme.bbspace.infra.network.BiliRestProfile
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class ArticleRecommendRepository @Inject constructor(
    private val restClient: BiliRestClient,
    private val restParamBuilder: BiliRestParamBuilder,
    private val authStore: AuthStore
) {

    suspend fun fetchHomePage(
        page: Int,
        aids: String?
    ): ArticleRecommendPage {
        val ts = System.currentTimeMillis() / 1000L
        val json = restClient.getSigned(
            url = "${BiliConstants.BASE_URL_API}$ARTICLE_RECOMMEND_PLUS_ENDPOINT",
            params = restParamBuilder.app(BiliRestProfile.APP, ts, authStore.accessToken) + buildMap {
                put("cid", HOME_CATEGORY_ID.toString())
                put("pn", page.coerceAtLeast(1).toString())
                put("ps", PAGE_SIZE.toString())
                put("sort", DEFAULT_SORT.toString())
                put("from", HOME_FROM.toString())
                aids?.takeIf { it.isNotBlank() }?.let { put("aids", it) }
            },
            profile = BiliRestProfile.APP
        )
        return withContext(Dispatchers.Default) {
            parsePage(json)
        }
    }

    private fun parsePage(json: JSONObject): ArticleRecommendPage {
        val data = json.optJSONObject("data")
        return ArticleRecommendPage(
            items = parseItems(data?.optJSONArray("articles")),
            aidsLength = json.optInt("aids_len").coerceAtLeast(0)
        )
    }

    private fun parseItems(arr: JSONArray?): List<ArticleRecommendItem> {
        if (arr == null || arr.length() == 0) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                parseItem(item)?.let(::add)
            }
        }
    }

    private fun parseItem(obj: JSONObject): ArticleRecommendItem? {
        val id = obj.optLongCompat("id").takeIf { it > 0L } ?: return null
        val title = obj.optString("title").blankToNull() ?: return null
        val author = obj.optJSONObject("author")
        val stats = obj.optJSONObject("stats")
        return ArticleRecommendItem(
            id = id,
            title = title,
            summary = obj.optString("summary").blankToNull(),
            cover = obj.optString("banner_url").toHttps()
                ?: obj.optJSONArray("image_urls")?.firstStringOrNull()?.toHttps(),
            authorMid = author?.optLongCompat("mid")?.takeIf { it > 0L },
            authorName = author?.optString("name").blankToNull(),
            authorFace = author?.optString("face").toHttps(),
            categoryName = obj.optJSONObject("category")?.optString("name").blankToNull(),
            publishTimeText = obj.optLong("publish_time")
                .takeIf { it > 0L }
                ?.let(::formatPubDate),
            viewCount = stats?.optLongCompat("view") ?: 0L,
            likeCount = stats?.optLongCompat("like") ?: 0L,
            replyCount = stats?.optLongCompat("reply") ?: 0L
        )
    }

    private fun formatPubDate(ts: Long): String {
        return DateFormat.format("yyyy-MM-dd", ts * 1000).toString()
    }

    private fun JSONArray.firstStringOrNull(): String? {
        if (length() == 0) return null
        return optString(0).blankToNull()
    }

    private fun JSONObject.optLongCompat(key: String): Long {
        return optLong(key).takeIf { it != 0L }
            ?: optString(key).toLongOrNull()
            ?: 0L
    }

    private fun String?.blankToNull(): String? {
        return this?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun String?.toHttps(): String? {
        return this?.replace("http://", "https://")?.trim()?.takeIf { it.isNotBlank() }
    }

    private companion object {
        const val ARTICLE_RECOMMEND_PLUS_ENDPOINT = "/x/article/recommends/plus"
        const val HOME_CATEGORY_ID = 0L
        const val PAGE_SIZE = 20
        const val DEFAULT_SORT = 0
        const val HOME_FROM = 2
    }
}
