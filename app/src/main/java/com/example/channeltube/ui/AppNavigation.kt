package com.example.channeltube.ui

import com.example.channeltube.data.local.VideoEntity

/**
 * Screen destinations supported by ChannelTube navigation.
 */
sealed interface Screen {
    data object Home : Screen
    data class ChannelDetail(val channelId: String) : Screen
    data class Player(val video: VideoEntity) : Screen
}

