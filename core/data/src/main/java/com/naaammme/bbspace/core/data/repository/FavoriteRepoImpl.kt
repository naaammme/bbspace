package com.naaammme.bbspace.core.data.repository

import com.naaammme.bbspace.core.common.BiliConstants
import com.naaammme.bbspace.core.data.AuthStore
import com.naaammme.bbspace.core.domain.favorite.FavoriteRepository
import com.naaammme.bbspace.core.model.FavoriteContentCursor
import com.naaammme.bbspace.core.model.FavoriteContentItem
import com.naaammme.bbspace.core.model.FavoriteContentPage
import com.naaammme.bbspace.core.model.FavoriteContentTarget
import com.naaammme.bbspace.core.model.FavoriteFolder
import com.naaammme.bbspace.core.model.FavoritePage
import com.naaammme.bbspace.core.model.VideoTarget
import com.naaammme.bbspace.core.model.VideoTargetTool
import com.naaammme.bbspace.infra.network.BiliRestClient
import com.naaammme.bbspace.infra.network.BiliRestParamBuilder
import com.naaammme.bbspace.infra.network.BiliRestProfile
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Singleton
class FavoriteRepoImpl @Inject constructor(
    private val restClient: BiliRestClient,
    private val restParamBuilder: BiliRestParamBuilder,
    private val authStore: AuthStore
) : FavoriteRepository {

    override suspend fun fetchMyFavorites(): FavoritePage {
        val accessToken = authStore.accessToken
        check(accessToken.isNotBlank()) { "请先登录" }
        val ts = System.currentTimeMillis() / 1000
        val json = restClient.getSigned(
            url = "${BiliConstants.BASE_URL_API}$MY_FAV_ENDPOINT",
            params = restParamBuilder.app(BiliRestProfile.APP, ts, accessToken),
            profile = BiliRestProfile.APP
        )
        return withContext(Dispatchers.Default) {
            val data = json.getJSONObject("data")
            FavoritePage(
                folders = buildList {
                    val list = data.optJSONArray("list") ?: return@buildList
                    for (i in 0 until list.length()) {
                        mapFolder(list.optJSONObject(i))?.let(::add)
                    }
                }
            )
        }
    }

    override suspend fun fetchFavoriteContents(cursor: FavoriteContentCursor): FavoriteContentPage {
        val accessToken = authStore.accessToken
        check(accessToken.isNotBlank()) { "请先登录" }
        val ts = System.currentTimeMillis() / 1000
        val json = restClient.getSigned(
            url = "${BiliConstants.BASE_URL_API}$FAV_CONTENT_ENDPOINT",
            params = restParamBuilder.app(BiliRestProfile.APP, ts, accessToken) + mapOf(
                "start_oid" to cursor.startOid.toString(),
                "start_otype" to cursor.startOtype.toString(),
                "tab_id" to ALL_FAVORITE_TAB_ID.toString(),
                "start_score" to cursor.startScore.toString(),
                "page_type" to PAGE_TYPE.toString()
            ),
            profile = BiliRestProfile.APP
        )
        return withContext(Dispatchers.Default) {
            val data = json.getJSONObject("data")
            FavoriteContentPage(
                items = buildList {
                    val list = data.optJSONArray("list") ?: return@buildList
                    for (i in 0 until list.length()) {
                        mapContentItem(list.optJSONObject(i), cursor.startScore, i)?.let(::add)
                    }
                },
                cursor = FavoriteContentCursor(
                    startOid = data.optLong("next_oid"),
                    startOtype = data.optInt("next_otype"),
                    startScore = data.optLong("next_score")
                ),
                hasMore = data.optBoolean("has_more")
            )
        }
    }

    private fun mapFolder(item: JSONObject?): FavoriteFolder? {
        item ?: return null
        val fid = item.optLong("fid")
        val id = item.optLong("id")
        val title = item.optString("title").blankToNull() ?: return null
        return FavoriteFolder(
            id = id,
            fid = fid,
            title = title,
            cover = item.optString("cover").toHttps(),
            attrDesc = item.optString("attr_desc").cleanAttrDesc(),
            mediaCount = item.optInt("media_count"),
            createdAtSec = item.optLong("ctime"),
            isTop = item.optBoolean("is_top")
        )
    }

    private fun mapContentItem(
        item: JSONObject?,
        pageScore: Long,
        index: Int
    ): FavoriteContentItem? {
        item ?: return null
        val oid = item.optLong("oid")
        val otype = item.optInt("otype")
        val title = item.optString("title").blankToNull() ?: return null
        val upper = item.optJSONObject("upper")
        val cntInfo = item.optJSONObject("cnt_info")
        return FavoriteContentItem(
            key = "$otype:$oid:$pageScore:$index",
            oid = oid,
            otype = otype,
            title = title,
            cover = item.optString("cover").toHttps(),
            ownerName = upper?.optString("name").blankToNull(),
            viewText = cntInfo?.optString("view_text_1").blankToNull(),
            danmakuText = item.optString("right_text").blankToNull(),
            playbackDesc = item.optString("playback_desc").blankToNull(),
            typeDesc = item.optString("otype_desc").blankToNull(),
            isInvalid = item.optBoolean("is_invalid"),
            target = buildTarget(item, oid, otype)
        )
    }

    private fun buildTarget(
        item: JSONObject,
        oid: Long,
        otype: Int
    ): FavoriteContentTarget? {
        if (item.optBoolean("is_invalid")) return null
        return when (otype) {
            TYPE_UGC -> {
                val aid = oid.takeIf { it > 0L } ?: return null
                val cid = item.optJSONObject("ugc")?.optLong("first_cid")?.takeIf { it > 0L }
                    ?: return null
                FavoriteContentTarget.Video(
                    VideoTarget.Ugc(
                        aid = aid,
                        cid = cid,
                        src = VideoTargetTool.favorite()
                    )
                )
            }

            TYPE_PGC -> {
                val epId = oid.takeIf { it > 0L } ?: return null
                val ogv = item.optJSONObject("ogv")
                FavoriteContentTarget.Video(
                    VideoTarget.Pgc(
                        epId = epId,
                        seasonId = ogv?.optLong("season_id")?.takeIf { it > 0L },
                        subType = ogv?.optInt("type_id")?.takeIf { it > 0 },
                        src = VideoTargetTool.favorite()
                    )
                )
            }

            TYPE_OPUS -> {
                val opusId = oid.takeIf { it > 0L }?.toString() ?: return null
                FavoriteContentTarget.DynamicDetail(opusId = opusId)
            }

            else -> null
        }
    }

    private fun String?.blankToNull(): String? = this?.takeIf { it.isNotBlank() }

    private fun String?.toHttps(): String? = this?.replace("http://", "https://")?.blankToNull()

    private fun String?.cleanAttrDesc(): String? {
        return this?.trim()
            ?.trim('·')
            ?.trim()
            .blankToNull()
    }

    private companion object {
        const val MY_FAV_ENDPOINT = "/x/v3/fav/tab/my_fav"
        const val FAV_CONTENT_ENDPOINT = "/x/v3/fav/tab/fav"
        const val ALL_FAVORITE_TAB_ID = 101
        const val PAGE_TYPE = 1
        const val TYPE_UGC = 2
        const val TYPE_PGC = 42
        const val TYPE_OPUS = 302
    }
}
