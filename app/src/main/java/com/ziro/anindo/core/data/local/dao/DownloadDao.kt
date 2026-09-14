package com.ziro.anindo.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ziro.anindo.core.data.local.entity.DownloadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAllDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id LIMIT 1")
    suspend fun getDownloadById(id: String): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE episodeUrl = :episodeUrl LIMIT 1")
    suspend fun getDownloadByEpisodeUrl(episodeUrl: String): DownloadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(download: DownloadEntity)

    @Update
    suspend fun updateDownload(download: DownloadEntity)

    @Query("UPDATE downloads SET progress = :progress, downloadedBytes = :downloadedBytes, totalBytes = :totalBytes, status = :status WHERE id = :id")
    suspend fun updateProgress(id: String, progress: Int, downloadedBytes: Long, totalBytes: Long, status: String)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteDownload(id: String)
}
