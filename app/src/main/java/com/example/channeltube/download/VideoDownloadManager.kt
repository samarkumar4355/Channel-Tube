package com.example.channeltube.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object VideoDownloadManager {
    private const val TAG = "VideoDownloadManager"
    private const val CHANNEL_ID = "channeltube_downloads"
    private val isInitialized = AtomicBoolean(false)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val downloadScope = CoroutineScope(Dispatchers.IO)

    private val _activeDownloads = MutableStateFlow<Map<String, ActiveDownload>>(emptyMap())
    val activeDownloads: StateFlow<Map<String, ActiveDownload>> = _activeDownloads.asStateFlow()

    fun initNewPipe() {
        if (isInitialized.compareAndSet(false, true)) {
            try {
                NewPipe.init(NewPipeDownloader())
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize NewPipeExtractor", e)
            }
        }
    }

    /**
     * Extracts download options for a video or Short given its 11-char videoId.
     */
    suspend fun extractDownloadOptions(videoId: String): Result<List<DownloadQualityOption>> = withContext(Dispatchers.IO) {
        initNewPipe()
        try {
            val url = "https://www.youtube.com/watch?v=$videoId"
            val info = StreamInfo.getInfo(url)
            val duration = info.duration

            val options = mutableListOf<DownloadQualityOption>()

            // Best audio stream (preferably M4A for universal compatibility)
            val audioStreams = info.audioStreams.sortedByDescending { it.averageBitrate }
            val bestM4aAudio = audioStreams.firstOrNull { it.format == MediaFormat.M4A || it.format?.suffix == "m4a" }
                ?: audioStreams.firstOrNull()
            val bestAudioUrl = bestM4aAudio?.content
            val bestAudioBitrate = if (bestM4aAudio != null && bestM4aAudio.bitrate > 0) {
                bestM4aAudio.bitrate.toLong()
            } else if (bestM4aAudio != null && bestM4aAudio.averageBitrate > 0) {
                bestM4aAudio.averageBitrate.toLong() * 1000L
            } else 128_000L

            // 1. Check progressive muxed streams first (contains both video and audio)
            val muxedStreams = info.videoStreams.filter { it.content?.isNotBlank() == true }
            for (ms in muxedStreams) {
                val res = ms.resolution ?: "${ms.height}p"
                val sizeMb = calculateStreamSizeMb(ms.content, ms.bitrate.toLong(), null, 0L, duration)
                options.add(
                    DownloadQualityOption(
                        id = "muxed_${ms.itag}",
                        title = "$res HD Video (.mp4)",
                        resolutionLabel = res,
                        extension = "mp4",
                        mimeType = "video/mp4",
                        videoUrl = ms.content,
                        audioUrl = null,
                        isAudioOnly = false,
                        isMuxedAlready = true,
                        approximateSizeMb = sizeMb
                    )
                )
            }

            // 2. Adaptive video streams (need muxing with audio)
            // Filter to MP4 video streams to allow fast hardware MediaMuxer into standard MP4
            val mp4VideoOnly = info.videoOnlyStreams
                .filter { (it.format == MediaFormat.MPEG_4 || it.format?.suffix == "mp4") && it.content?.isNotBlank() == true }

            val standardResolutions = listOf("1080p", "720p", "480p", "360p", "240p")
            val addedResolutions = mutableSetOf<String>()

            // Mark any resolution already covered by progressive streams
            options.forEach { addedResolutions.add(it.resolutionLabel) }

            for (resTarget in standardResolutions) {
                if (addedResolutions.contains(resTarget)) continue
                val match = mp4VideoOnly.firstOrNull { it.resolution?.startsWith(resTarget) == true || "${it.height}p" == resTarget }
                if (match != null && bestAudioUrl != null) {
                    addedResolutions.add(resTarget)
                    val label = when (resTarget) {
                        "1080p" -> "1080p Full HD"
                        "720p" -> "720p HD"
                        "480p" -> "480p Standard"
                        "360p" -> "360p Medium"
                        else -> resTarget
                    }
                    val sizeMb = calculateStreamSizeMb(match.content, match.bitrate.toLong(), bestAudioUrl, bestAudioBitrate, duration)
                    options.add(
                        DownloadQualityOption(
                            id = "adaptive_${match.itag}",
                            title = "$label Video (.mp4)",
                            resolutionLabel = resTarget,
                            extension = "mp4",
                            mimeType = "video/mp4",
                            videoUrl = match.content,
                            audioUrl = bestAudioUrl,
                            isAudioOnly = false,
                            isMuxedAlready = false,
                            approximateSizeMb = sizeMb
                        )
                    )
                }
            }

            // If no standard MP4 video streams matched, add whatever top video stream exists
            if (options.isEmpty()) {
                val fallbackStream = info.videoOnlyStreams.firstOrNull { it.content?.isNotBlank() == true }
                if (fallbackStream != null) {
                    val res = fallbackStream.resolution ?: "${fallbackStream.height}p"
                    val sizeMb = calculateStreamSizeMb(fallbackStream.content, fallbackStream.bitrate.toLong(), bestAudioUrl, bestAudioBitrate, duration)
                    options.add(
                        DownloadQualityOption(
                            id = "fallback_${fallbackStream.itag}",
                            title = "$res Video (.mp4)",
                            resolutionLabel = res,
                            extension = "mp4",
                            mimeType = "video/mp4",
                            videoUrl = fallbackStream.content,
                            audioUrl = bestAudioUrl,
                            isAudioOnly = false,
                            isMuxedAlready = false,
                            approximateSizeMb = sizeMb
                        )
                    )
                }
            }

            // 3. Audio Only Option (M4A)
            if (bestM4aAudio != null && bestM4aAudio.content?.isNotBlank() == true) {
                val bitrate = if (bestM4aAudio.averageBitrate > 0) "${bestM4aAudio.averageBitrate} kbps" else "High Quality"
                val sizeMb = calculateStreamSizeMb(null, 0L, bestM4aAudio.content, bestAudioBitrate, duration)
                options.add(
                    DownloadQualityOption(
                        id = "audio_${bestM4aAudio.itag}",
                        title = "Audio Only ($bitrate .m4a)",
                        resolutionLabel = bitrate,
                        extension = "m4a",
                        mimeType = "audio/mp4",
                        videoUrl = null,
                        audioUrl = bestM4aAudio.content,
                        isAudioOnly = true,
                        isMuxedAlready = true,
                        approximateSizeMb = sizeMb
                    )
                )
            }

            if (options.isEmpty()) {
                Result.failure(Exception("No downloadable video or audio streams found for this video."))
            } else {
                Result.success(options)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting streams for $videoId", e)
            Result.failure(e)
        }
    }

    /**
     * Starts background download of the selected video/audio stream into phone public storage.
     */
    fun startDownload(
        context: Context,
        videoId: String,
        videoTitle: String,
        option: DownloadQualityOption
    ) {
        val appContext = context.applicationContext
        createNotificationChannel(appContext)

        val notificationId = (videoId.hashCode() and 0x7FFFFFFF)
        val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Sanitize filename
        val cleanTitle = videoTitle
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(65)
            .ifBlank { "ChannelTube_Video_${videoId.take(6)}" }
        val fileName = "$cleanTitle.${option.extension}"

        showToast(appContext, "Downloading '$cleanTitle' in background...")

        _activeDownloads.update { current ->
            current + (videoId to ActiveDownload(
                videoId = videoId,
                title = cleanTitle,
                status = DownloadStatus.DOWNLOADING,
                progressPercent = 0,
                message = "Starting download..."
            ))
        }

        downloadScope.launch {
            val cacheDir = appContext.cacheDir
            val tempVideoFile = File(cacheDir, "dl_v_${videoId}_${System.currentTimeMillis()}.tmp")
            val tempAudioFile = File(cacheDir, "dl_a_${videoId}_${System.currentTimeMillis()}.tmp")
            val tempMuxedFile = File(cacheDir, "dl_mux_${videoId}_${System.currentTimeMillis()}.mp4")

            try {
                updateNotification(
                    context = appContext,
                    notificationManager = notificationManager,
                    notificationId = notificationId,
                    title = cleanTitle,
                    content = "Downloading ${option.resolutionLabel}...",
                    progress = 5,
                    isOngoing = true
                )

                if (option.isAudioOnly) {
                    // Audio Only: download directly to tempAudioFile
                    val audioUrl = option.audioUrl ?: throw IllegalStateException("Audio URL is missing")
                    downloadUrlToFile(audioUrl, tempAudioFile) { progress ->
                        val percent = (progress * 100).toInt().coerceIn(0, 100)
                        updateDownloadProgress(videoId, cleanTitle, percent, "Downloading audio ($percent%)")
                        updateNotification(appContext, notificationManager, notificationId, cleanTitle, "Downloading audio: $percent%", percent, true)
                    }

                    // Save directly to public Downloads
                    val savedUri = saveFileToPublicDownloads(appContext, tempAudioFile, fileName, option.mimeType)
                    onDownloadComplete(appContext, notificationManager, notificationId, videoId, cleanTitle, fileName, savedUri, option.mimeType)
                } else if (option.isMuxedAlready || option.audioUrl == null) {
                    // Progressive muxed stream: download video directly
                    val videoUrl = option.videoUrl ?: throw IllegalStateException("Video URL is missing")
                    downloadUrlToFile(videoUrl, tempVideoFile) { progress ->
                        val percent = (progress * 100).toInt().coerceIn(0, 100)
                        updateDownloadProgress(videoId, cleanTitle, percent, "Downloading video ($percent%)")
                        updateNotification(appContext, notificationManager, notificationId, cleanTitle, "Downloading video: $percent%", percent, true)
                    }

                    val savedUri = saveFileToPublicDownloads(appContext, tempVideoFile, fileName, option.mimeType)
                    onDownloadComplete(appContext, notificationManager, notificationId, videoId, cleanTitle, fileName, savedUri, option.mimeType)
                } else {
                    // Adaptive stream: Download video (0-80%) + audio (80-95%) -> Mux (95-100%)
                    val videoUrl = option.videoUrl ?: throw IllegalStateException("Video URL is missing")
                    val audioUrl = option.audioUrl

                    // 1. Download video track
                    downloadUrlToFile(videoUrl, tempVideoFile) { progress ->
                        val percent = (progress * 80).toInt().coerceIn(0, 80)
                        updateDownloadProgress(videoId, cleanTitle, percent, "Downloading video ($percent%)")
                        updateNotification(appContext, notificationManager, notificationId, cleanTitle, "Downloading video: $percent%", percent, true)
                    }

                    // 2. Download audio track
                    downloadUrlToFile(audioUrl, tempAudioFile) { progress ->
                        val percent = (80 + progress * 15).toInt().coerceIn(80, 95)
                        updateDownloadProgress(videoId, cleanTitle, percent, "Downloading audio...")
                        updateNotification(appContext, notificationManager, notificationId, cleanTitle, "Downloading audio...", percent, true)
                    }

                    // 3. Hardware Mux video and audio into standard MP4
                    updateDownloadProgress(videoId, cleanTitle, 96, "Saving final MP4 video...")
                    updateNotification(appContext, notificationManager, notificationId, cleanTitle, "Processing video...", 96, true)

                    val muxSuccess = MediaMuxerHelper.mux(tempVideoFile, tempAudioFile, tempMuxedFile)
                    val finalFileToSave = if (muxSuccess && tempMuxedFile.exists() && tempMuxedFile.length() > 0) {
                        tempMuxedFile
                    } else {
                        Log.w(TAG, "Muxing failed or empty, falling back to video stream")
                        tempVideoFile
                    }

                    val savedUri = saveFileToPublicDownloads(appContext, finalFileToSave, fileName, option.mimeType)
                    onDownloadComplete(appContext, notificationManager, notificationId, videoId, cleanTitle, fileName, savedUri, option.mimeType)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download failed for $videoId", e)
                _activeDownloads.update { current ->
                    current + (videoId to ActiveDownload(
                        videoId = videoId,
                        title = cleanTitle,
                        status = DownloadStatus.FAILED,
                        progressPercent = 0,
                        message = "Download failed: ${e.message}"
                    ))
                }
                showToast(appContext, "Download failed: ${e.message?.take(50)}")
                updateNotification(
                    context = appContext,
                    notificationManager = notificationManager,
                    notificationId = notificationId,
                    title = cleanTitle,
                    content = "Download failed",
                    progress = 0,
                    isOngoing = false
                )
            } finally {
                tempVideoFile.delete()
                tempAudioFile.delete()
                tempMuxedFile.delete()
            }
        }
    }

    private fun downloadUrlToFile(url: String, destination: File, onProgress: (Float) -> Unit) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception("HTTP ${response.code}: ${response.message}")
        }

        val body = response.body ?: throw Exception("Empty response body")
        val contentLength = body.contentLength()

        var bytesDownloaded = 0L
        val buffer = ByteArray(64 * 1024)

        body.byteStream().use { input ->
            FileOutputStream(destination).use { output ->
                var read: Int
                var lastProgressReport = 0L
                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    bytesDownloaded += read

                    val now = System.currentTimeMillis()
                    if (now - lastProgressReport > 200 || bytesDownloaded == contentLength) {
                        lastProgressReport = now
                        if (contentLength > 0) {
                            onProgress(bytesDownloaded.toFloat() / contentLength.toFloat())
                        }
                    }
                }
            }
        }
    }

    /**
     * Saves a local file into the public Downloads/ChannelTube folder.
     * Uses MediaStore on Android 10+ (API 29+) and direct File APIs on Android 9 and below.
     */
    private fun saveFileToPublicDownloads(
        context: Context,
        sourceFile: File,
        displayName: String,
        mimeType: String
    ): Uri? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/ChannelTube")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                ?: throw Exception("Failed to create MediaStore entry in Downloads")

            resolver.openOutputStream(uri)?.use { out ->
                FileInputStream(sourceFile).use { input ->
                    input.copyTo(out)
                }
            } ?: throw Exception("Failed to open output stream to MediaStore")

            contentValues.clear()
            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)

            return uri
        } else {
            // Android 9 and below
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val channelTubeDir = File(downloadsDir, "ChannelTube")
            if (!channelTubeDir.exists()) {
                channelTubeDir.mkdirs()
            }
            val destFile = File(channelTubeDir, displayName)
            FileInputStream(sourceFile).use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            MediaScannerConnection.scanFile(context, arrayOf(destFile.absolutePath), arrayOf(mimeType), null)
            return Uri.fromFile(destFile)
        }
    }

    private fun onDownloadComplete(
        context: Context,
        notificationManager: NotificationManager,
        notificationId: Int,
        videoId: String,
        title: String,
        fileName: String,
        savedUri: Uri?,
        mimeType: String
    ) {
        _activeDownloads.update { current ->
            current + (videoId to ActiveDownload(
                videoId = videoId,
                title = title,
                status = DownloadStatus.COMPLETED,
                progressPercent = 100,
                message = "Saved to Downloads/ChannelTube",
                outputFilePath = fileName
            ))
        }

        showToast(context, "Saved to Downloads/ChannelTube: $fileName")

        // Notification on completion with intent to open file
        val contentIntent = savedUri?.let { uri ->
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            PendingIntent.getActivity(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Download Complete")
            .setContentText("$fileName\nSaved in Downloads/ChannelTube")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setOngoing(false)

        contentIntent?.let { builder.setContentIntent(it) }

        notificationManager.notify(notificationId, builder.build())
    }

    private fun updateDownloadProgress(videoId: String, title: String, percent: Int, message: String) {
        _activeDownloads.update { current ->
            current + (videoId to ActiveDownload(
                videoId = videoId,
                title = title,
                status = DownloadStatus.DOWNLOADING,
                progressPercent = percent,
                message = message
            ))
        }
    }

    private fun updateNotification(
        context: Context,
        notificationManager: NotificationManager,
        notificationId: Int,
        title: String,
        content: String,
        progress: Int,
        isOngoing: Boolean
    ) {
        try {
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(title)
                .setContentText(content)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(isOngoing)

            if (isOngoing) {
                builder.setProgress(100, progress, false)
            } else {
                builder.setProgress(0, 0, false)
            }

            notificationManager.notify(notificationId, builder.build())
        } catch (_: Exception) {}
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ChannelTube Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress and completion of video downloads"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun extractClenBytes(url: String?): Long? {
        if (url.isNullOrBlank()) return null
        val match = Regex("[&?]clen=([0-9]+)").find(url)
        return match?.groupValues?.get(1)?.toLongOrNull()
    }

    private fun calculateStreamSizeMb(
        videoUrl: String?,
        videoBitrate: Long,
        audioUrl: String?,
        audioBitrate: Long,
        durationSeconds: Long
    ): Double? {
        val videoClen = extractClenBytes(videoUrl)
        val audioClen = extractClenBytes(audioUrl)

        var totalBytes = 0L

        if (videoClen != null && videoClen > 0) {
            totalBytes += videoClen
        } else if (videoBitrate > 0 && durationSeconds > 0) {
            totalBytes += (videoBitrate * durationSeconds) / 8
        }

        if (audioClen != null && audioClen > 0) {
            totalBytes += audioClen
        } else if (audioBitrate > 0 && durationSeconds > 0) {
            totalBytes += (audioBitrate * durationSeconds) / 8
        }

        if (totalBytes <= 0) return null
        return totalBytes / (1024.0 * 1024.0)
    }

    private fun showToast(context: Context, text: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, text, Toast.LENGTH_LONG).show()
        }
    }
}

