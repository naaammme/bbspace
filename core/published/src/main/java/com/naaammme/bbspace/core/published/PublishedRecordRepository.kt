package com.naaammme.bbspace.core.published

import android.content.Context
import androidx.room.Room
import com.naaammme.bbspace.core.model.PublishedRecord
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class PublishedRecordRepository @Inject constructor(
    private val dao: PublishedRecordDao
) {

    suspend fun save(record: PublishedRecord) {
        dao.upsert(record.toEntity())
    }

    suspend fun getRecords(
        keyword: String,
        sortDesc: Boolean,
        limit: Int,
        lastItem: PublishedRecord? = null
    ): List<PublishedRecord> {
        return dao.getPage(
            buildPublishedRecordPageQuery(
                keyword = keyword,
                sortDesc = sortDesc,
                limit = limit,
                lastItem = lastItem
            )
        ).map(PublishedRecordEntity::toModel)
    }

    suspend fun getCount(): Int {
        return dao.getCount()
    }

    suspend fun delete(key: String): Boolean {
        return dao.deleteByKey(key) > 0
    }

    suspend fun exportJson(): String {
        val items = dao.getAll()
        return withContext(Dispatchers.Default) {
            JSONArray().apply {
                items.forEach { item ->
                    put(
                        JSONObject().apply {
                            put("key", item.key)
                            put("kind", item.kind)
                            put("item_id", item.itemId)
                            put("target_id", item.targetId)
                            put("target_type", item.targetType)
                            put("sender_mid", item.senderMid)
                            put("sender_name", item.senderName)
                            put("sender_avatar", item.senderAvatar)
                            put("content", item.content)
                            put("ctime", item.ctime)
                            put("root_id", item.rootId)
                            put("parent_id", item.parentId)
                            put("image_list_json", item.imageListJson ?: JSONObject.NULL)
                        }
                    )
                }
            }.toString()
        }
    }

    suspend fun importJson(json: String): Int {
        val items = withContext(Dispatchers.Default) {
            val array = JSONArray(json)
            buildList(array.length()) {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    add(
                        PublishedRecord(
                            key = item.getString("key"),
                            kind = item.getInt("kind"),
                            itemId = item.getLong("item_id"),
                            targetId = item.getLong("target_id"),
                            targetType = item.getLong("target_type"),
                            senderMid = item.getLong("sender_mid"),
                            senderName = item.getString("sender_name"),
                            senderAvatar = item.getString("sender_avatar"),
                            content = item.getString("content"),
                            ctime = item.getLong("ctime"),
                            rootId = item.optLong("root_id"),
                            parentId = item.optLong("parent_id"),
                            imageListJson = item.takeIf {
                                it.has("image_list_json") && !it.isNull("image_list_json")
                            }?.getString("image_list_json")
                        ).toEntity()
                    )
                }
            }
        }
        if (items.isEmpty()) return 0
        dao.upsertAll(items)
        return items.size
    }
}

@Module
@InstallIn(SingletonComponent::class)
object PublishedModule {
    @Provides
    @Singleton
    fun providePublishedRecordDb(
        @ApplicationContext context: Context
    ): PublishedRecordDb {
        return Room.databaseBuilder(
            context,
            PublishedRecordDb::class.java,
            "published_record.db"
        )
            .fallbackToDestructiveMigration(true)
            .build()
    }

    @Provides
    fun providePublishedRecordDao(db: PublishedRecordDb): PublishedRecordDao {
        return db.dao()
    }
}
