package com.example.channeltube.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "playlist_items",
    primaryKeys = ["playlistId", "videoId"],
    foreignKeys = [
        ForeignKey(
            entity = CustomPlaylistEntity::class,
            parentColumns = ["playlistId"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("playlistId"), Index("videoId")]
)
data class PlaylistItemEntity(
    val playlistId: String,
    val videoId: String,
    val channelId: String,
    val channelName: String,
    val title: String,
    val description: String = "",
    val thumbnailUrl: String = "",
    val publishedAt: String = "",
    val duration: String = "",
    val addedAt: Long = System.currentTimeMillis()
) {
    fun toVideoEntity(): VideoEntity = VideoEntity(
        videoId = videoId,
        channelId = channelId,
        channelName = channelName,
        title = title,
        description = description,
        thumbnailUrl = thumbnailUrl,
        publishedAt = publishedAt,
        duration = duration
    )
}

fun VideoEntity.toPlaylistItemEntity(playlistId: String): PlaylistItemEntity = PlaylistItemEntity(
    playlistId = playlistId,
    videoId = videoId,
    channelId = channelId,
    channelName = channelName,
    title = title,
    description = description,
    thumbnailUrl = thumbnailUrl,
    publishedAt = publishedAt,
    duration = duration
)
