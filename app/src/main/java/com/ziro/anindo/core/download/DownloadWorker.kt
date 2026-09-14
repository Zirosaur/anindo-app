package com.ziro.anindo.core.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.ziro.anindo.AnindoApp
import com.ziro.anindo.core.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.util.concurrent.TimeUnit

class DownloadWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "DownloadWorker"
        const val CHANNEL_ID = "anindo_downloads"
        const val KEY_DOWNLOAD_ID = "download_id"
        const val KEY_URL = "download_url"
        const val KEY_TITLE = "download_title"
        const val KEY_FILENAME = "download_filename"
        const val KEY_REFERER = "download_referer"
        const val MIN_VALID_FILE_SIZE = 100_000L // Minimum 100 KB to be a valid video
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    // Dedicated client with extended timeouts for large video file downloading
    private val downloadClient: OkHttpClient = NetworkClient.client.newBuilder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

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
                setForeground(ForegroundInfo(notificationId, buildNotification(title, 0).build()))
            }
        } catch (_: Throwable) {
            // Foreground service display failure shouldn't abort download
        }

        // Determine destination file & prepare output stream
        val (targetFile, outputStream) = getDestinationOutputStream(filename) ?: run {
            Log.e(TAG, "Failed to create destination file output stream for: $filename")
            downloadDao.updateProgress(downloadId, 0, 0, 0, "FAILED")
            return@withContext Result.failure()
        }

        downloadDao.updateProgress(downloadId, 0, 0, 0, "DOWNLOADING")

        val isHls = isHlsUrl(url)
        Log.d(TAG, "Starting download task $downloadId: isHls=$isHls, title=$title, url=$url")

        val success = try {
            if (isHls) {
                downloadHlsStream(
                    masterUrl = url,
                    referer = referer,
                    targetFile = targetFile,
                    outputStream = outputStream,
                    downloadId = downloadId,
                    title = title,
                    notificationId = notificationId
                )
            } else {
                downloadDirectStream(
                    url = url,
                    referer = referer,
                    targetFile = targetFile,
                    outputStream = outputStream,
                    downloadId = downloadId,
                    title = title,
                    notificationId = notificationId
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download execution exception: ${e.message}", e)
            false
        }

        if (isStopped) {
            targetFile.delete()
            downloadDao.updateProgress(downloadId, 0, 0, 0, "CANCELLED")
            return@withContext Result.failure()
        }

        val finalSize = targetFile.length()
        if (success && finalSize >= MIN_VALID_FILE_SIZE) {
            // Register with Android Media Scanner so it appears in video players and files apps
            try {
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(targetFile.absolutePath),
                    arrayOf("video/mp4", "video/mp2t"),
                    null
                )
            } catch (_: Throwable) {}

            val existing = downloadDao.getDownloadById(downloadId)
            if (existing != null) {
                downloadDao.updateDownload(
                    existing.copy(
                        status = "COMPLETED",
                        progress = 100,
                        downloadedBytes = finalSize,
                        totalBytes = finalSize,
                        localFilePath = targetFile.absolutePath
                    )
                )
            }

            // Notification on complete
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

            Log.i(TAG, "Download finished successfully: $title ($finalSize bytes) at ${targetFile.absolutePath}")
            Result.success()
        } else {
            Log.e(TAG, "Download failed or downloaded file too small: $finalSize bytes (expected >= $MIN_VALID_FILE_SIZE)")
            targetFile.delete()
            downloadDao.updateProgress(downloadId, 0, 0, 0, "FAILED")
            Result.failure()
        }
    }

    private fun getDestinationOutputStream(filename: String): Pair<File, FileOutputStream>? {
        // 1. Try public Download/Anindo directory
        try {
            val publicDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "Anindo"
            )
            if (!publicDir.exists()) publicDir.mkdirs()
            val file = File(publicDir, filename)
            val stream = FileOutputStream(file)
            return Pair(file, stream)
        } catch (_: Throwable) {}

        // 2. Guaranteed fallback: app-specific external storage directory (no permissions required)
        try {
            val appDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
            if (!appDir.exists()) appDir.mkdirs()
            val file = File(appDir, filename)
            val stream = FileOutputStream(file)
            return Pair(file, stream)
        } catch (_: Throwable) {}

        return null
    }

    private suspend fun downloadDirectStream(
        url: String,
        referer: String,
        targetFile: File,
        outputStream: FileOutputStream,
        downloadId: String,
        title: String,
        notificationId: Int
    ): Boolean = withContext(Dispatchers.IO) {
        val reqBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", NetworkClient.USER_AGENT)

        if (referer.isNotBlank()) {
            reqBuilder.header("Referer", referer)
        } else if (url.contains("desustream") || url.contains("odcdn") || url.contains("odcloud")) {
            reqBuilder.header("Referer", "https://desustream.net/")
        }

        val response = downloadClient.newCall(reqBuilder.build()).execute()
        if (!response.isSuccessful || response.body == null) {
            outputStream.close()
            return@withContext false
        }

        val body = response.body!!
        val contentLength = body.contentLength()
        val totalBytes = if (contentLength > 0) contentLength else 0L

        outputStream.use { out ->
            body.byteStream().use { input ->
                val buffer = ByteArray(32 * 1024)
                var downloadedBytes = 0L
                var read: Int
                var lastReportedProgress = -1

                while (input.read(buffer).also { read = it } != -1) {
                    if (isStopped) return@withContext false
                    out.write(buffer, 0, read)
                    downloadedBytes += read

                    val progress = if (totalBytes > 0) {
                        ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 99)
                    } else {
                        0
                    }

                    if (progress != lastReportedProgress) {
                        lastReportedProgress = progress
                        AnindoApp.instance.database.downloadDao().updateProgress(
                            id = downloadId,
                            progress = progress,
                            downloadedBytes = downloadedBytes,
                            totalBytes = if (totalBytes > 0) totalBytes else downloadedBytes,
                            status = "DOWNLOADING"
                        )
                        try {
                            notificationManager.notify(
                                notificationId,
                                buildNotification(title, progress).build()
                            )
                        } catch (_: Throwable) {}
                    }
                }
                out.flush()
            }
        }
        true
    }

    private suspend fun downloadHlsStream(
        masterUrl: String,
        referer: String,
        targetFile: File,
        outputStream: FileOutputStream,
        downloadId: String,
        title: String,
        notificationId: Int
    ): Boolean = withContext(Dispatchers.IO) {
        val downloadDao = AnindoApp.instance.database.downloadDao()

        // 1. Fetch playlist content
        val reqBuilder = Request.Builder()
            .url(masterUrl)
            .header("User-Agent", NetworkClient.USER_AGENT)
        if (referer.isNotBlank()) {
            reqBuilder.header("Referer", referer)
        }

        val masterContent = downloadClient.newCall(reqBuilder.build()).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext false
            resp.body?.string() ?: return@withContext false
        }

        // 2. If it's a master playlist with variants, pick the highest quality variant
        var mediaPlaylistUrl = masterUrl
        var mediaLines = masterContent.lines()

        if (masterContent.contains("#EXT-X-STREAM-INF")) {
            var selectedVariantUri: String? = null
            var bestBandwidth = -1L
            var currentBandwidth = 0L

            for (line in mediaLines) {
                val trimmed = line.trim()
                if (trimmed.startsWith("#EXT-X-STREAM-INF")) {
                    val bwMatch = Regex("""BANDWIDTH=(\d+)""").find(trimmed)
                    currentBandwidth = bwMatch?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                } else if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                    if (currentBandwidth >= bestBandwidth) {
                        bestBandwidth = currentBandwidth
                        selectedVariantUri = trimmed
                    }
                }
            }

            if (selectedVariantUri != null) {
                mediaPlaylistUrl = resolveUrl(masterUrl, selectedVariantUri)
                val subReq = Request.Builder()
                    .url(mediaPlaylistUrl)
                    .header("User-Agent", NetworkClient.USER_AGENT)
                    .apply { if (referer.isNotBlank()) header("Referer", referer) }
                    .build()

                val subContent = downloadClient.newCall(subReq).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext false
                    resp.body?.string() ?: return@withContext false
                }
                mediaLines = subContent.lines()
            }
        }

        // 3. Extract segment URLs
        val segmentUrls = mutableListOf<String>()
        for (line in mediaLines) {
            val trimmed = line.trim()
            if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                segmentUrls.add(resolveUrl(mediaPlaylistUrl, trimmed))
            }
        }

        if (segmentUrls.isEmpty()) {
            Log.e(TAG, "No HLS segments found in playlist: $mediaPlaylistUrl")
            outputStream.close()
            return@withContext false
        }

        val totalSegments = segmentUrls.size
        Log.d(TAG, "Found $totalSegments HLS segments to download for $title")

        outputStream.use { out ->
            var downloadedBytes = 0L
            var lastReportedProgress = -1

            for (i in 0 until totalSegments) {
                if (isStopped) return@withContext false

                val segUrl = segmentUrls[i]
                var success = false
                var attempts = 0

                while (!success && attempts < 3) {
                    attempts++
                    try {
                        val segReq = Request.Builder()
                            .url(segUrl)
                            .header("User-Agent", NetworkClient.USER_AGENT)
                            .apply { if (referer.isNotBlank()) header("Referer", referer) }
                            .build()

                        downloadClient.newCall(segReq).execute().use { resp ->
                            if (resp.isSuccessful && resp.body != null) {
                                val bytes = resp.body!!.bytes()
                                out.write(bytes)
                                downloadedBytes += bytes.size
                                success = true
                            }
                        }
                    } catch (e: Exception) {
                        if (attempts >= 3) {
                            Log.e(TAG, "Failed to download segment $i ($segUrl): ${e.message}")
                            return@withContext false
                        }
                    }
                }

                val progress = (((i + 1) * 100) / totalSegments).coerceIn(0, 99)
                if (progress != lastReportedProgress) {
                    lastReportedProgress = progress
                    val estimatedTotal = (downloadedBytes / (i + 1)) * totalSegments
                    downloadDao.updateProgress(
                        id = downloadId,
                        progress = progress,
                        downloadedBytes = downloadedBytes,
                        totalBytes = estimatedTotal,
                        status = "DOWNLOADING"
                    )
                    try {
                        notificationManager.notify(
                            notificationId,
                            buildNotification(title, progress).build()
                        )
                    } catch (_: Throwable) {}
                }
            }
            out.flush()
        }
        true
    }

    private fun isHlsUrl(url: String): Boolean {
        val clean = url.lowercase()
        return clean.contains(".m3u8") || clean.contains("/api/hls") || clean.contains("putarin")
    }

    private fun resolveUrl(baseUrl: String, relativeUrl: String): String {
        val trimmed = relativeUrl.trim()
        if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            return trimmed
        }
        if (trimmed.startsWith("//")) {
            val proto = if (baseUrl.startsWith("http://", ignoreCase = true)) "http:" else "https:"
            return "$proto$trimmed"
        }
        return try {
            URI(baseUrl).resolve(trimmed).toString()
        } catch (_: Exception) {
            val baseWithoutFile = if (baseUrl.contains("/")) baseUrl.substringBeforeLast("/") + "/" else baseUrl
            baseWithoutFile + trimmed.trimStart('/')
        }
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
