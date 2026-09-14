package com.ziro.anindo.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ziro.anindo.core.model.WatchHistory

@Entity(tableName = "episode_progress")
data class EpisodeProgressEntity(
    @PrimaryKey
    val episodeUrl: String,
    val animeId: String,
    val animeTitle: String,
    val animePosterUrl: String,
    val episodeTitle: String,
    val episodeNumber: String = "",
    val playbackPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isCompleted: Boolean = false,
    val lastWatchedAt: Long = System.currentTimeMillis()
) {
    fun toWatchHistory(): WatchHistory {
        return WatchHistory(
            animeId = animeId,
            animeTitle = animeTitle,
            episodeUrl = episodeUrl,
            episodeTitle = episodeTitle,
            positionMs = playbackPositionMs,
            durationMs = durationMs,
            lastWatchedTimestamp = lastWatchedAt
        )
    }

    val progressPercent: Float
        get() = if (durationMs > 0L) (playbackPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
}
