package com.example.channeltube.domain

import com.example.channeltube.data.local.VideoEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class FilterVideosUseCaseTest {
    @Test
    fun onlySelectedChannelVideosAreReturned() {
        val videos = listOf(
            video("v1", "channel-a"),
            video("v2", "channel-b"),
            video("v3", "channel-c")
        )

        val result = FilterVideosUseCase()(videos, setOf("channel-a", "channel-c"))

        assertEquals(listOf("v1", "v3"), result.map { it.videoId })
    }

    @Test
    fun emptySelectionReturnsNoVideos() {
        val result = FilterVideosUseCase()(listOf(video("v1", "channel-a")), emptySet())
        assertEquals(emptyList<String>(), result.map { it.videoId })
    }

    private fun video(videoId: String, channelId: String) = VideoEntity(
        videoId = videoId,
        channelId = channelId,
        channelName = "Channel",
        title = "Video",
        description = "",
        thumbnailUrl = "",
        publishedAt = "2026-01-01T00:00:00Z"
    )
}
