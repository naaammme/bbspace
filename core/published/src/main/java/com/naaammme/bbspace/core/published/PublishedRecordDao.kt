package com.naaammme.bbspace.core.published

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.naaammme.bbspace.core.model.PublishedRecord

@Dao
interface PublishedRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: PublishedRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<PublishedRecordEntity>)

    @RawQuery
    suspend fun getPage(query: SupportSQLiteQuery): List<PublishedRecordEntity>

    @Query("SELECT * FROM published_record ORDER BY ctime DESC, item_id DESC")
    suspend fun getAll(): List<PublishedRecordEntity>

    @Query("SELECT COUNT(*) FROM published_record")
    suspend fun getCount(): Int

    @Query("DELETE FROM published_record WHERE key = :key")
    suspend fun deleteByKey(key: String): Int
}

internal fun buildPublishedRecordPageQuery(
    keyword: String,
    sortDesc: Boolean,
    limit: Int,
    lastItem: PublishedRecord? = null
): SupportSQLiteQuery {
    val normalizedKeyword = keyword.trim()
    val hasKeyword = normalizedKeyword.isNotBlank()
    val order = if (sortDesc) "DESC" else "ASC"
    val comparator = if (sortDesc) "<" else ">"
    val args = ArrayList<Any>(5)
    val sql = StringBuilder("SELECT * FROM published_record")
    var hasWhere = false
    if (hasKeyword) {
        sql.append(" WHERE content LIKE '%' || ? || '%' ESCAPE '\\'")
        args += escapePublishedRecordLikeKeyword(normalizedKeyword)
        hasWhere = true
    }
    lastItem?.let { item ->
        sql.append(if (hasWhere) " AND " else " WHERE ")
        sql.append("(ctime $comparator ? OR (ctime = ? AND item_id $comparator ?))")
        args += item.ctime
        args += item.ctime
        args += item.itemId
    }
    sql.append(" ORDER BY ctime $order, item_id $order LIMIT ?")
    args += limit
    return SimpleSQLiteQuery(sql.toString(), args.toTypedArray())
}

private fun escapePublishedRecordLikeKeyword(keyword: String): String {
    return buildString(keyword.length) {
        keyword.forEach { ch ->
            when (ch) {
                '%', '_', '\\' -> {
                    append('\\')
                    append(ch)
                }
                else -> append(ch)
            }
        }
    }
}
