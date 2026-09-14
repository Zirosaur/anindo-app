package com.ziro.anindo.core.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.ziro.anindo.AnindoApp
import com.ziro.anindo.core.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

class DownloadWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val CHANNEL_ID = "anindo_downloads"
        const val KEY_DOWNLOAD_ID = "download_id"
        const val KEY_URL = "download_url"
        const val KEY_TITLE = "download_title"
        const val KEY_FILENAME = "download_filename"
        const val KEY_REFERER = "download_referer"
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val downloadId = inputData.getString(KEY_DOWNLOAD_ID) ?: return@withContext Result.failure()
        val url = inputData.getString(KEY_URL) ?: return@withContext Result.failure()
        val title = inputData.getString(KEY_TITLE) ?: "Anime Episode"
        val filename = inputData.getString(KEY_FILENAME) ?: "episode_${System.currentTimeMillis()}.mp4"
        val referer = inputData.getString(KEY_REFERER) ?: ""

        val database = AnindoApp.instance.database
        val downloadDao = database.downloadDao()

        val notificationId = downloadId.hashCode()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                setForeground(
                    ForegroundInfo(
                        notificationId,
                        buildNotification(title, 0).build(),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                )
            } else {
                setForeground(createForegroundInfo(notificationId, title, 0))
            }
        } catch (_: Throwable) {
            // Background task continues even if foreground service fails
        }

        try {
            val reqBuilder = Request.Builder()
                .url(url)
                .header("User-Agent", NetworkClient.USER_AGENT)

            if (referer.isNotBlank()) {
                reqBuilder.header("Referer", referer)
            } else if (url.contains("desustream") || url.contains("odcdn") || url.contains("odcloud")) {
                reqBuilder.header("Referer", "https://desustream.net/")
            }

            val response = NetworkClient.client.newCall(reqBuilder.build()).execute()
            if (!response.isSuccessful || response.body == null) {
                downloadDao.updateProgress(downloadId, 0, 0, 0, "FAILED")
                return@withContext Result.failure()
            }

            val body = response.body!!
            val totalBytes = body.contentLength().coerceAtLeast(1L)

            // Save to public Downloads/Anindo so user can easily find it in gallery / files app
            val publicDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "Anindo"
            )
            val downloadDir = try {
                if (!publicDir.exists()) publicDir.mkdirs()
                if (publicDir.canWrite()) publicDir else (context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir)
            } catch (_: Exception) {
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
            }
            if (!downloadDir.exists()) downloadDir.mkdirs()

            val targetFile = File(downloadDir, filename)
            val inputStream = body.byteStream()
            val outputStream = FileOutputStream(targetFile)

            val buffer = ByteArray(16 * 1024)
            var downloadedBytes = 0L
            var read: Int
            var lastReportedProgress = -1

            downloadDao.updateProgress(downloadId, 0, 0, totalBytes, "DOWNLOADING")

            while (inputStream.read(buffer).also { read = it } != -1) {
                if (isStopped) {
                    outputStream.close()
                    inputStream.close()
                    targetFile.delete()
                    downloadDao.updateProgress(downloadId, 0, 0, totalBytes, "CANCELLED")
                    return@withContext Result.failure()
                }

                outputStream.write(buffer, 0, read)
                downloadedBytes += read

                val currentProgress = ((downloadedBytes * 100) / totalBytes).toInt()
                if (currentProgress != lastReportedProgress) {
                    lastReportedProgress = currentProgress
                    downloadDao.updateProgress(downloadId, currentProgress, downloadedBytes, totalBytes, "DOWNLOADING")
                    try {
                        notificationManager.notify(
                            notificationId,
                            buildNotification(title, currentProgress).build()
                        )
                    } catch (_: Throwable) {}
                }
            }

            outputStream.flush()
            outputStream.close()
            inputStream.close()

            // Register with Android Media Scanner
            try {
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(targetFile.absolutePath),
                    arrayOf("video/mp4"),
                    null
                )
            } catch (_: Throwable) {}

            // Update database with completed status
            val existing = downloadDao.getDownloadById(downloadId)
            if (existing != null) {
                downloadDao.updateDownload(
                    existing.copy(
                        status = "COMPLETED",
                        progress = 100,
                        downloadedBytes = totalBytes,
                        totalBytes = totalBytes,
                        localFilePath = targetFile.absolutePath
                    )
                )
            }

            // Show completion notification
            try {
                notificationManager.notify(
                    notificationId,
                    NotificationCompat.Builder(context, CHANNEL_ID)
                        .setContentTitle("Selesai Mengunduh")
                        .setContentText(title)
                        .setSmallIcon(android.R.drawable.stat_sys_download_done)
                        .setAutoCancel(true)
                        .build()
                )
            } catch (_: Throwable) {}

            Result.success()
        } catch (e: Exception) {
            downloadDao.updateProgress(downloadId, 0, 0, 0, "FAILED")
            Result.failure()
        }
    }

    private fun createForegroundInfo(id: Int, title: String, progress: Int): ForegroundInfo {
        return ForegroundInfo(id, buildNotification(title, progress).build())
    }

    private fun buildNotification(title: String, progress: Int): NotificationCompat.Builder {
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Mengunduh: $title")
            .setContentText("$progress%")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setProgress(100, progress, false)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Unduhan Anindo",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifikasi kemajuan pengunduhan anime offline"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
}
