package com.example.channeltube.data.repository

import com.example.channeltube.data.local.AppDao
import com.example.channeltube.data.local.ChannelEntity
import com.example.channeltube.data.local.VideoEntity
import com.example.channeltube.data.remote.ChannelSearchResult
import com.example.channeltube.data.remote.YouTubeApi
import kotlinx.coroutines.flow.Flow

class ChannelRepository(
    private val api: YouTubeApi,
    private val dao: AppDao
) {
    fun observeChannels(): Flow<List<ChannelEntity>> = dao.observeChannels()

    fun observeVideos(): Flow<List<VideoEntity>> = dao.observeVideos()

    suspend fun getChannels(): List<ChannelEntity> = dao.getChannels()

    suspend fun searchChannels(query: String): List<ChannelSearchResult> {
        return api.searchChannels(query)
    }

    suspend fun addChannel(result: ChannelSearchResult) {
        val channel = api.getSavedChannel(result)
        dao.insertChannel(channel)
    }

    suspend fun removeChannel(channelId: String) {
        // The videos are removed explicitly as well as by the Room foreign-key rule.
        dao.deleteVideosForChannel(channelId)
        dao.deleteChannel(channelId)
    }

    suspend fun refreshChannel(channel: ChannelEntity): Int {
        val freshVideos = api.getUploads(channel)
        dao.deleteVideosForChannel(channel.channelId)
        dao.insertVideos(freshVideos)
        return freshVideos.size
    }
}
