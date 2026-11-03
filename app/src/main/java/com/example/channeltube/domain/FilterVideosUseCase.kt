package com.example.channeltube.domain

import com.example.channeltube.data.local.VideoEntity

/** Keeps the home feed limited to channels the user currently follows. */
class FilterVideosUseCase {
    operator fun invoke(
        videos: List<VideoEntity>,
        selectedChannelIds: Set<String>
    ): List<VideoEntity> {
        return videos.filter { video -> selectedChannelIds.contains(video.channelId) }
    }
}
