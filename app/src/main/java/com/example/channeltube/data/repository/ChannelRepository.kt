package com.example.channeltube.data.repository

import com.example.channeltube.data.local.AppDao
import com.example.channeltube.data.local.ChannelEntity
import com.example.channeltube.data.local.VideoEntity
import com.example.channeltube.data.remote.ChannelSearchResult
import com.example.channeltube.data.remote.YouTubeApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.ConcurrentHashMap

class ChannelRepository(
    private val api: YouTubeApi,
    private val dao: AppDao
) {
    // Pagination tokens for each channel's uploads playlist
    private val nextTokens = ConcurrentHashMap<String, String>()
    private val channelExhausted = ConcurrentHashMap.newKeySet<String>()

    fun observeChannels(): Flow<List<ChannelEntity>> = dao.observeChannels()

    fun observeVideos(): Flow<List<VideoEntity>> = dao.observeVideos()

    suspend fun getChannels(): List<ChannelEntity> = dao.getChannels()

    suspend fun searchChannels(query: String): List<ChannelSearchResult> {
        return api.searchChannels(query)
    }

    suspend fun searchVideos(query: String, maxResults: Int = 25): List<VideoEntity> {
        return api.searchVideos(query = query, maxResults = maxResults).videos
    }

    suspend fun addChannel(result: ChannelSearchResult) {
        val channel = api.getSavedChannel(result)
        dao.insertChannel(channel)
        refreshChannel(channel)
    }

    suspend fun removeChannel(channelId: String) {
        nextTokens.remove(channelId)
        channelExhausted.remove(channelId)
        dao.deleteVideosForChannel(channelId)
        dao.deleteChannel(channelId)
    }

    suspend fun refreshChannel(channel: ChannelEntity): Int {
        nextTokens.remove(channel.channelId)
        channelExhausted.remove(channel.channelId)
        val pageResult = api.getUploadsPage(
            playlistId = channel.uploadsPlaylistId,
            pageToken = null,
            maxResults = 50,
            fallbackChannelName = channel.channelName,
            channelId = channel.channelId
        )
        dao.deleteVideosForChannel(channel.channelId)
        dao.insertVideos(pageResult.videos)
        if (!pageResult.nextPageToken.isNullOrBlank()) {
            nextTokens[channel.channelId] = pageResult.nextPageToken
        } else {
            channelExhausted.add(channel.channelId)
        }
        return pageResult.videos.size
    }

    /**
     * Loads the next page of videos across all added channels in parallel with minimum latency.
     * Inserts new videos into Room database which automatically updates all feeds without duplicates.
     */
    suspend fun loadMoreVideosForFeed(channels: List<ChannelEntity>): Int = coroutineScope {
        val activeChannels = channels.filter {
            !channelExhausted.contains(it.channelId) && it.uploadsPlaylistId.isNotBlank()
        }
        if (activeChannels.isEmpty()) return@coroutineScope 0

        val deferredList = activeChannels.map { channel ->
            async {
                try {
                    val token = nextTokens[channel.channelId]
                    val pageResult = api.getUploadsPage(
                        playlistId = channel.uploadsPlaylistId,
                        pageToken = token,
                        maxResults = 50,
                        fallbackChannelName = channel.channelName,
                        channelId = channel.channelId
                    )
                    if (!pageResult.nextPageToken.isNullOrBlank()) {
                        nextTokens[channel.channelId] = pageResult.nextPageToken
                    } else {
                        channelExhausted.add(channel.channelId)
                    }
                    pageResult.videos
                } catch (e: Exception) {
                    emptyList()
                }
            }
        }
        val allVideos = deferredList.awaitAll().flatten()
        if (allVideos.isNotEmpty()) {
            dao.insertVideos(allVideos)
        }
        allVideos.size
    }

    suspend fun getChannelShorts(channelId: String, limit: Int = 15): List<VideoEntity> {
        val result = api.getChannelShorts(channelId = channelId, maxResults = limit)
        if (result.videos.isNotEmpty()) {
            dao.insertVideos(result.videos)
        }
        return result.videos
    }
}
