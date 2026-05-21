package com.naaammme.bbspace.core.domain.favorite

import com.naaammme.bbspace.core.model.FavoriteContentCursor
import com.naaammme.bbspace.core.model.FavoriteContentPage
import com.naaammme.bbspace.core.model.FavoritePage

interface FavoriteRepository {
    suspend fun fetchMyFavorites(): FavoritePage

    suspend fun fetchFavoriteContents(cursor: FavoriteContentCursor): FavoriteContentPage
}
