package com.ziro.anindo.core.model

data class WatchHistory(
    val animeId: String,
    val animeTitle: String,
    val episodeUrl: String,
    val episodeTitle: String,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val lastWatchedTimestamp: Long = System.currentTimeMillis()
) {
    val progressPercent: Float
        get() = if (durationMs > 0L) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
}
