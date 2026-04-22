package com.naaammme.bbspace.feature.space.model

import androidx.compose.runtime.Immutable
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
    val isPageLoading: Boolean = false,
    val pageErrorMessage: String? = null
) {
    val title: String
        get() = header?.profile?.name ?: "个人空间"
}

@Immutable
data class SpaceHeaderUiState(
    val profile: SpaceProfile,
    val bannerUrl: String?
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
