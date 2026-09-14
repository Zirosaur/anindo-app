package com.ziro.anindo.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey
    val id: String, // Unique download task ID
    val animeId: String,
    val animeTitle: String,
    val animePosterUrl: String,
    val episodeTitle: String,
    val episodeUrl: String,
    val downloadUrl: String,
    val localFilePath: String = "",
    val quality: String = "720p",
    val status: String = "QUEUED", // QUEUED, DOWNLOADING, COMPLETED, FAILED, PAUSED
    val progress: Int = 0, // 0 - 100
    val totalBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
