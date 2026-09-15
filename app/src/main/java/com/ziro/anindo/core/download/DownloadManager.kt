package com.ziro.anindo.core.download

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.ziro.anindo.AnindoApp
import com.ziro.anindo.core.data.local.entity.DownloadEntity
import java.io.File
import java.util.UUID

class AppDownloadManager(private val context: Context) {
    private val workManager = WorkManager.getInstance(context)
    private val downloadDao = AnindoApp.instance.database.downloadDao()

    suspend fun enqueueDownload(
        animeId: String,
        animeTitle: String,
        posterUrl: String,
        episodeTitle: String,
        episodeUrl: String,
        videoStreamUrl: String,
        referer: String = "",
        quality: String = "720p"
    ): String {
        val downloadId = UUID.randomUUID().toString()
        val safeAnimeName = animeTitle.replace("[^a-zA-Z0-9]".toRegex(), "_")
        val safeEpTitle = episodeTitle.replace("[^a-zA-Z0-9]".toRegex(), "_")
        val filename = "${safeAnimeName}_${safeEpTitle}_${quality}.mp4"

        val entity = DownloadEntity(
            id = downloadId,
            animeId = animeId,
            animeTitle = animeTitle,
            animePosterUrl = posterUrl,
            episodeTitle = episodeTitle,
            episodeUrl = episodeUrl,
            downloadUrl = videoStreamUrl,
            quality = quality,
            status = "QUEUED",
            progress = 0
        )
        downloadDao.insertDownload(entity)

        val inputData = Data.Builder()
            .putString(DownloadWorker.KEY_DOWNLOAD_ID, downloadId)
            .putString(DownloadWorker.KEY_URL, videoStreamUrl)
            .putString(DownloadWorker.KEY_TITLE, "$animeTitle - $episodeTitle")
            .putString(DownloadWorker.KEY_FILENAME, filename)
            .putString(DownloadWorker.KEY_REFERER, referer)
            .build()

        val wifiOnly = try {
            AnindoApp.instance.settingsManager.downloadWifiOnly.value
        } catch (_: Throwable) {
            false
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()

        val downloadRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(constraints)
            .setInputData(inputData)
            .addTag("download_$downloadId")
            .build()

        workManager.enqueue(downloadRequest)
        return downloadId
    }

    fun cancelDownload(downloadId: String) {
        workManager.cancelAllWorkByTag("download_$downloadId")
    }

    suspend fun deleteDownload(downloadId: String, filePath: String = "") {
        cancelDownload(downloadId)
        if (filePath.isNotBlank()) {
            try {
                val file = File(filePath)
                if (file.exists()) {
                    file.delete()
                }
            } catch (_: Exception) {}
        }
        downloadDao.deleteDownload(downloadId)
    }
}
