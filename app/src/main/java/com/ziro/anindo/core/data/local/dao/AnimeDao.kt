package com.ziro.anindo.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ziro.anindo.core.data.local.entity.AnimeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AnimeDao {
    @Query("SELECT * FROM anime WHERE isBookmarked = 1 ORDER BY lastUpdated DESC")
    fun getBookmarkedAnime(): Flow<List<AnimeEntity>>

    @Query("SELECT * FROM anime WHERE id = :animeId LIMIT 1")
    suspend fun getAnimeById(animeId: String): AnimeEntity?

    @Query("SELECT * FROM anime WHERE id = :animeId LIMIT 1")
    fun observeAnimeById(animeId: String): Flow<AnimeEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAnime(anime: AnimeEntity)

    @Query("UPDATE anime SET isBookmarked = :isBookmarked, lastUpdated = :timestamp WHERE id = :animeId")
    suspend fun setBookmark(animeId: String, isBookmarked: Boolean, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM anime WHERE id = :animeId AND isBookmarked = 0")
    suspend fun deleteIfNotBookmarked(animeId: String)
}
