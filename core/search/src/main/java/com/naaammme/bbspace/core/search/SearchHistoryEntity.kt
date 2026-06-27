package com.naaammme.bbspace.core.search

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "search_history",
    indices = [
        Index(value = ["updatedAt", "searchCount"]),
        Index(value = ["searchCount", "updatedAt"])
    ]
)
data class SearchHistoryEntity(
    @PrimaryKey val keyword: String,
    val searchCount: Int,
    val updatedAt: Long
)
