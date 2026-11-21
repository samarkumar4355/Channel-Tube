package com.example.channeltube.download

data class DownloadQualityOption(
    val id: String,
    val title: String,
    val resolutionLabel: String,
    val extension: String,
    val mimeType: String,
    val videoUrl: String?,
    val audioUrl: String?,
    val isAudioOnly: Boolean,
    val isMuxedAlready: Boolean = false,
    val approximateSizeMb: Double? = null
)

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    PROCESSING,
    COMPLETED,
    FAILED
}

data class ActiveDownload(
    val videoId: String,
    val title: String,
    val status: DownloadStatus,
    val progressPercent: Int = 0,
    val message: String = "",
    val outputFilePath: String? = null
)

