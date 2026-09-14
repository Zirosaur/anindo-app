package com.ziro.anindo.core.model

data class WatchHistory(
    val animeTitle: String,
    val animeUrl: String,
    val lastEpTitle: String,
    val lastEpNum: Float,
    val lastEpUrl: String,
    val provider: String,
    val timePosSeconds: Long = 0,
    val durationSeconds: Long = 0,
    val percent: Int = 0,
    val status: String = "completed", // "in_progress" or "completed"
    val updatedAt: Long = System.currentTimeMillis()
)
