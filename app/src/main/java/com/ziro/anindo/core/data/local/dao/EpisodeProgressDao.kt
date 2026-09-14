package com.ziro.anindo.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ziro.anindo.core.data.local.entity.EpisodeProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EpisodeProgressDao {
    @Query("SELECT * FROM episode_progress WHERE episodeUrl = :episodeUrl LIMIT 1")
    suspend fun getProgress(episodeUrl: String): EpisodeProgressEntity?

    @Query("SELECT * FROM episode_progress WHERE animeId = :animeId")
    fun observeProgressForAnime(animeId: String): Flow<List<EpisodeProgressEntity>>

    @Query("SELECT * FROM episode_progress ORDER BY lastWatchedAt DESC LIMIT 50")
    fun observeRecentHistory(): Flow<List<EpisodeProgressEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgress(progress: EpisodeProgressEntity)

    @Query("DELETE FROM episode_progress WHERE episodeUrl = :episodeUrl")
    suspend fun deleteProgress(episodeUrl: String)

    @Query("DELETE FROM episode_progress")
    suspend fun clearHistory()
}
