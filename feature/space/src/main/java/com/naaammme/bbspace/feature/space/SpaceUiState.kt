package com.naaammme.bbspace.feature.space

import androidx.compose.runtime.Immutable
import com.naaammme.bbspace.core.model.DynamicItem
import com.naaammme.bbspace.core.model.SpaceOrderOption
import com.naaammme.bbspace.core.model.SpaceProfile
import com.naaammme.bbspace.core.model.SpaceVideo

internal const val SPACE_DEFAULT_ORDER = "pubdate"

private val DEFAULT_ORDERS = listOf(
    SpaceOrderOption(title = "最新发布", value = SPACE_DEFAULT_ORDER),
    SpaceOrderOption(title = "最多播放", value = "click")
)

@Immutable
data class SpaceUiState(
    val header: SpaceHeaderUiState? = null,
    val archive: SpaceArchiveUiState = SpaceArchiveUiState(),
    val dynamics: SpaceDynamicUiState = SpaceDynamicUiState(),
    val selectedSection: SpaceSection = SpaceSection.VIDEO
) {
    val title: String
        get() = header?.profile?.name ?: "个人空间"
}

enum class SpaceSection {
    VIDEO,
    DYNAMIC
}

@Immutable
data class SpaceHeaderUiState(
    val profile: SpaceProfile,
    val bannerUrl: String?,
    val isLogin: Boolean = false,
    val isSelf: Boolean = false
)

@Immutable
data class SpaceArchiveUiState(
    val videos: List<SpaceVideo> = emptyList(),
    val orders: List<SpaceOrderOption> = DEFAULT_ORDERS,
    val selectedOrder: String = SPACE_DEFAULT_ORDER,
    val hasMore: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val message: String? = null,
    val loadMoreError: String? = null
) {
    val canLoadMore: Boolean
        get() = hasMore &&
                !isRefreshing &&
                !isLoadingMore &&
                loadMoreError.isNullOrBlank() &&
                videos.isNotEmpty()

    val showEmpty: Boolean
        get() = videos.isEmpty() &&
                !isRefreshing &&
                message.isNullOrBlank()
}

@Immutable
data class SpaceDynamicUiState(
    val items: List<DynamicItem> = emptyList(),
    val historyOffset: String = "",
    val page: Int = 1,
    val hasMore: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val message: String? = null,
    val loadMoreError: String? = null
) {
    val canLoadMore: Boolean
        get() = hasMore &&
                !isRefreshing &&
                !isLoadingMore &&
                loadMoreError.isNullOrBlank() &&
                items.isNotEmpty()

    val showEmpty: Boolean
        get() = items.isEmpty() &&
                !isRefreshing &&
                message.isNullOrBlank()
}

internal data class SpaceOrderState(
    val orders: List<SpaceOrderOption>,
    val selectedOrder: String
)

internal fun resolveSpaceOrderState(
    nextOrders: List<SpaceOrderOption>,
    preferred: String
): SpaceOrderState {
    val orders = nextOrders.ifEmpty { DEFAULT_ORDERS }
    return SpaceOrderState(
        orders = orders,
        selectedOrder = orders.firstOrNull { it.value == preferred }?.value
            ?: orders.first().value
    )
}
