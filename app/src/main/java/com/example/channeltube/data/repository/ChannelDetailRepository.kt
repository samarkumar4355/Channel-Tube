package com.example.channeltube.data.repository

import com.example.channeltube.data.local.AppDao
import com.example.channeltube.data.local.ChannelEntity
import com.example.channeltube.data.local.VideoEntity
import com.example.channeltube.data.remote.ChannelProfile
import com.example.channeltube.data.remote.PaginatedVideos
import com.example.channeltube.data.remote.YouTubeApi
import kotlinx.coroutines.flow.Flow

class ChannelDetailRepository(
    private val api: YouTubeApi,
    private val dao: AppDao
) {
    suspend fun getCachedChannel(channelId: String): ChannelEntity? {
        return dao.getChannel(channelId)
    }

    fun observeChannel(channelId: String): Flow<ChannelEntity?> {
        return dao.observeChannel(channelId)
    }

    suspend fun getCachedVideos(channelId: String): List<VideoEntity> {
        return dao.getVideosForChannel(channelId)
    }

    fun observeVideos(channelId: String): Flow<List<VideoEntity>> {
        return dao.observeVideosForChannel(channelId)
    }

    suspend fun getChannelProfile(channelId: String): ChannelProfile {
        val profile = api.getChannelProfile(channelId)
        // Also update local channel record if it was previously saved
        val existing = dao.getChannel(channelId)
        if (existing != null) {
            dao.insertChannel(
                existing.copy(
                    channelName = profile.channelName,
                    description = profile.description,
                    thumbnailUrl = profile.thumbnailUrl,
                    uploadsPlaylistId = profile.uploadsPlaylistId
                )
            )
        }
        return profile
    }

    suspend fun getUploadsPage(
        uploadsPlaylistId: String,
        pageToken: String? = null,
        channelId: String = "",
        channelName: String = ""
    ): PaginatedVideos {
        val result = api.getUploadsPage(
            playlistId = uploadsPlaylistId,
            pageToken = pageToken,
            maxResults = 50,
            fallbackChannelName = channelName,
            channelId = channelId
        )
        // Cache fetched videos in Room if channel is saved locally
        if (result.videos.isNotEmpty() && channelId.isNotBlank()) {
            val isChannelSaved = dao.getChannel(channelId) != null
            if (isChannelSaved) {
                dao.insertVideos(result.videos)
            }
        }
        return result
    }

    suspend fun searchVideos(
        channelId: String,
        query: String,
        pageToken: String? = null,
        channelName: String = ""
    ): PaginatedVideos {
        val result = api.searchChannelVideos(
            channelId = channelId,
            query = query,
            pageToken = pageToken,
            maxResults = 20,
            fallbackChannelName = channelName
        )
        // Cache search videos in Room if channel is saved locally
        if (result.videos.isNotEmpty()) {
            val isChannelSaved = dao.getChannel(channelId) != null
            if (isChannelSaved) {
                dao.insertVideos(result.videos)
            }
        }
        return result
    }
}

