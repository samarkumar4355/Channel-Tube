package com.example.channeltube.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "videos",
    foreignKeys = [
        ForeignKey(
            entity = ChannelEntity::class,
            parentColumns = ["channelId"],
            childColumns = ["channelId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("channelId")]
)
data class VideoEntity(
    @PrimaryKey val videoId: String,
    val channelId: String,
    val channelName: String,
    val title: String,
    val description: String,
    val thumbnailUrl: String,
    val publishedAt: String
)
