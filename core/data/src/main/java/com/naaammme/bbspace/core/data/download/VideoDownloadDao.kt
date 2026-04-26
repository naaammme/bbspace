package com.naaammme.bbspace.core.data.download

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoDownloadDao {
    @Query("SELECT * FROM video_download_task ORDER BY created_at_ms DESC, id DESC")
    fun observeAll(): Flow<List<VideoDownloadEntity>>

    @Query("SELECT * FROM video_download_task WHERE id = :taskId")
    fun observe(taskId: Long): Flow<VideoDownloadEntity?>

    @Query("SELECT * FROM video_download_task WHERE id = :taskId")
    suspend fun find(taskId: Long): VideoDownloadEntity?

    @Query(
        "SELECT * FROM video_download_task " +
                "WHERE status = :status " +
                "ORDER BY created_at_ms ASC, id ASC " +
                "LIMIT 1"
    )
    suspend fun findFirstByStatus(status: String): VideoDownloadEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(task: VideoDownloadEntity): Long

    @Query(
        "SELECT id FROM video_download_task " +
                "WHERE biz = :biz " +
                "AND aid = :aid " +
                "AND cid = :cid " +
                "AND ep_id = :epId " +
                "AND season_id = :seasonId " +
                "AND kind = :kind " +
                "LIMIT 1"
    )
    suspend fun findExistingId(
        biz: String,
        aid: Long,
        cid: Long,
        epId: Long,
        seasonId: Long,
        kind: String
    ): Long?

    @Update
    suspend fun update(task: VideoDownloadEntity)

    @Query("DELETE FROM video_download_task WHERE id = :taskId")
    suspend fun delete(taskId: Long)

    @Query(
        "UPDATE video_download_task " +
                "SET status = :pausedStatus, error = :message, video_path = NULL, audio_path = NULL, duration_ms = 0 " +
                "WHERE status = :runningStatus"
    )
    suspend fun pauseRunningTasks(
        runningStatus: String,
        pausedStatus: String,
        message: String
    )
}
