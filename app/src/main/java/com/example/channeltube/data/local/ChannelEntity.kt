package com.example.channeltube.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "channels")
data class ChannelEntity(
    @PrimaryKey val channelId: String,
    val channelName: String,
    val description: String,
    val thumbnailUrl: String,
    val uploadsPlaylistId: String,
    val addedAt: Long = System.currentTimeMillis()
)
