package com.ziro.anindo.core.data.repository

import com.ziro.anindo.core.data.local.AppDatabase
import com.ziro.anindo.core.data.local.entity.AnimeEntity
import com.ziro.anindo.core.data.local.entity.EpisodeProgressEntity
import com.ziro.anindo.core.model.Anime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AnimeRepository(
    private val database: AppDatabase
) {
    private val animeDao = database.animeDao()
    private val progressDao = database.episodeProgressDao()

    fun getBookmarkedAnime(): Flow<List<Anime>> {
        return animeDao.getBookmarkedAnime().map { list ->
            list.map { it.toAnime() }
        }
    }

    fun isBookmarked(animeId: String): Flow<Boolean> {
        return animeDao.observeAnimeById(animeId).map { it?.isBookmarked == true }
    }

    suspend fun toggleBookmark(anime: Anime) {
        val existing = animeDao.getAnimeById(anime.computedId)
        if (existing == null) {
            val entity = AnimeEntity.fromAnime(anime, isBookmarked = true)
            animeDao.upsertAnime(entity)
        } else {
            val newStatus = !existing.isBookmarked
            animeDao.setBookmark(anime.computedId, newStatus)
        }
    }


    suspend fun saveEpisodeProgress(
        animeId: String,
        animeTitle: String,
        animePosterUrl: String,
        episodeUrl: String,
        episodeTitle: String,
        episodeNumber: String,
        positionMs: Long,
        durationMs: Long
    ) {
        val isCompleted = durationMs > 0L && (positionMs.toFloat() / durationMs.toFloat()) >= 0.90f
        val progress = EpisodeProgressEntity(
            episodeUrl = episodeUrl,
            animeId = animeId,
            animeTitle = animeTitle,
            animePosterUrl = animePosterUrl,
            episodeTitle = episodeTitle,
            episodeNumber = episodeNumber,
            playbackPositionMs = positionMs,
            durationMs = durationMs,
            isCompleted = isCompleted,
            lastWatchedAt = System.currentTimeMillis()
        )
        progressDao.upsertProgress(progress)
    }

    suspend fun getEpisodeProgress(episodeUrl: String): EpisodeProgressEntity? {
        return progressDao.getProgress(episodeUrl)
    }

    fun getProgressForAnime(animeId: String): Flow<List<EpisodeProgressEntity>> {
        return progressDao.observeProgressForAnime(animeId)
    }

    fun getRecentHistory(): Flow<List<EpisodeProgressEntity>> {
        return progressDao.observeRecentHistory()
    }

    suspend fun clearHistory() {
        progressDao.clearHistory()
    }
}
