package com.naaammme.bbspace.core.data.search

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class SearchHistoryDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insert(item: SearchHistoryEntity): Long

    @Query(
        """
        UPDATE search_history
        SET searchCount = searchCount + 1,
            updatedAt = :updatedAt
        WHERE keyword = :keyword
        """
    )
    abstract suspend fun bump(
        keyword: String,
        updatedAt: Long
    )

    @Transaction
    open suspend fun record(
        keyword: String,
        updatedAt: Long
    ) {
        val rowId = insert(
            SearchHistoryEntity(
                keyword = keyword,
                searchCount = 1,
                updatedAt = updatedAt
            )
        )
        if (rowId == -1L) {
            bump(keyword, updatedAt)
        }
    }

    @Query("DELETE FROM search_history WHERE keyword = :keyword")
    abstract suspend fun deleteByKeyword(keyword: String)

    @Query(
        """
        SELECT keyword
        FROM search_history
        ORDER BY updatedAt DESC, searchCount DESC, keyword ASC
        """
    )
    abstract fun observeTopKeywordsByTime(): Flow<List<String>>

    @Query(
        """
        SELECT keyword
        FROM search_history
        ORDER BY searchCount DESC, updatedAt DESC, keyword ASC
        """
    )
    abstract fun observeTopKeywordsByHot(): Flow<List<String>>
}
