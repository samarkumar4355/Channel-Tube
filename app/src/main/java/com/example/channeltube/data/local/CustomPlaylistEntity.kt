package com.example.channeltube.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "custom_playlists")
data class CustomPlaylistEntity(
    @PrimaryKey val playlistId: String = UUID.randomUUID().toString(),
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class CustomPlaylistWithDetails(
    val playlist: CustomPlaylistEntity,
    val itemCount: Int,
    val coverThumbnailUrl: String? = null
)

