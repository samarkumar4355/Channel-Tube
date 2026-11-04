package com.example.channeltube.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.channeltube.data.local.ChannelEntity
import com.example.channeltube.data.local.VideoEntity
import com.example.channeltube.viewmodel.MainViewModel
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ShortsScreen(
    viewModel: MainViewModel,
    onVideoClick: (VideoEntity) -> Unit,
    onChannelClick: (String) -> Unit,
    onGoToChannels: () -> Unit
) {
    val channels by viewModel.channels.collectAsState()
    val shorts by viewModel.shorts.collectAsState()
    val isRefreshing by viewModel.isRefreshingShorts.collectAsState()

    var selectedChannelId by remember { mutableStateOf<String?>(null) }
    var isVerticalScrollMode by remember { mutableStateOf(true) }
    var initialScrollIndex by remember { mutableIntStateOf(0) }
    var openedFromGrid by remember { mutableStateOf(false) }

    val channelMap = remember(channels) { channels.associateBy { it.channelId } }

    val filteredShorts = remember(shorts, selectedChannelId) {
        if (selectedChannelId == null) {
            shorts
        } else {
            shorts.filter { it.channelId == selectedChannelId }
        }
    }

    // Handle back button when in vertical scroll mode opened from grid
    BackHandler(enabled = isVerticalScrollMode && openedFromGrid) {
        isVerticalScrollMode = false
        openedFromGrid = false
    }

    when {
        channels.isEmpty() -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    title = "No channels followed",
                    description = "Add your favorite YouTube channels to explore and swipe through their Shorts here.",
                    buttonText = "Add Channels",
                    onButtonClick = onGoToChannels
                )
            }
        }

        isRefreshing && shorts.isEmpty() -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp),
                        strokeWidth = 3.dp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Loading Shorts from your channels...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
            }
        }

        filteredShorts.isEmpty() -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    title = "No Shorts found",
                    description = if (selectedChannelId != null) {
                        "This channel hasn't uploaded any Shorts recently."
                    } else {
                        "None of your followed channels have uploaded Shorts recently. Try refreshing."
                    },
                    buttonText = "Refresh Shorts",
                    onButtonClick = { viewModel.refreshShorts(showMessage = true) }
                )
            }
        }

        isVerticalScrollMode -> {
            // ── Primary View: High-Performance Reused Player with Vertical Paging ──
            ShortsVerticalPager(
                shorts = filteredShorts,
                channels = channels,
                channelMap = channelMap,
                initialIndex = initialScrollIndex,
                selectedChannelId = selectedChannelId,
                onSelectChannel = { channelId ->
                    selectedChannelId = channelId
                    initialScrollIndex = 0
                },
                isRefreshing = isRefreshing,
                onRefresh = { viewModel.refreshShorts(showMessage = true) },
                onChannelClick = onChannelClick,
                onToggleGrid = {
                    isVerticalScrollMode = false
                }
            )
        }

        else -> {
            // ── Secondary View: 2-Column Grid View ──
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // Header with View Switcher
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Shorts & Clips",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${filteredShorts.size} clips available",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }

                    Surface(
                        onClick = {
                            initialScrollIndex = 0
                            openedFromGrid = false
                            isVerticalScrollMode = true
                        },
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFFFF0000),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("⚡ Shorts Feed", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (isRefreshing) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        TextButton(onClick = { viewModel.refreshShorts(showMessage = true) }) {
                            Text("Refresh")
                        }
                    }
                }

                // Channel filter row
                ShortsChannelFilterRow(
                    channels = channels,
                    shorts = shorts,
                    selectedChannelId = selectedChannelId,
                    onSelectChannel = { selectedChannelId = it }
                )

                // Grid content
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredShorts, key = { it.videoId }) { shortVideo ->
                        val channel = channelMap[shortVideo.channelId]
                        ShortCard(
                            video = shortVideo,
                            channelAvatarUrl = channel?.thumbnailUrl,
                            onVideoClick = {
                                val clickedIdx = filteredShorts.indexOfFirst { it.videoId == shortVideo.videoId }
                                initialScrollIndex = if (clickedIdx >= 0) clickedIdx else 0
                                openedFromGrid = true
                                isVerticalScrollMode = true
                            },
                            onChannelClick = onChannelClick
                        )
                    }
                }
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Ultra-Fast Persistent Player Vertical Feed for Shorts
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun ShortsVerticalPager(
    shorts: List<VideoEntity>,
    channels: List<ChannelEntity>,
    channelMap: Map<String, ChannelEntity>,
    initialIndex: Int,
    selectedChannelId: String?,
    onSelectChannel: (String?) -> Unit,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onChannelClick: (String) -> Unit,
    onToggleGrid: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, (shorts.size - 1).coerceAtLeast(0)),
        pageCount = { shorts.size }
    )

    var playerRef by remember { mutableStateOf<YouTubePlayer?>(null) }
    var isPlaying by remember { mutableStateOf(true) }
    var isMuted by remember { mutableStateOf(false) }
    var currentSecond by remember { mutableFloatStateOf(0f) }
    var totalDuration by remember { mutableFloatStateOf(0f) }
    var isUserSeeking by remember { mutableStateOf(false) }
    var userSeekValue by remember { mutableFloatStateOf(0f) }
    var showPlayIconFeedback by remember { mutableStateOf(false) }

    val activeVideo = remember(pagerState.currentPage, shorts) {
        if (shorts.isNotEmpty() && pagerState.currentPage in shorts.indices) {
            shorts[pagerState.currentPage]
        } else null
    }

    // Sync mute state
    LaunchedEffect(isMuted, playerRef) {
        playerRef?.let { p ->
            if (isMuted) {
                p.mute()
            } else {
                p.unMute()
                p.setVolume(100)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        // ── 1. The Single Persistent Warm YouTube Player ──
        // Stays warm in memory; videos switch via loadVideoById in ~200ms!
        if (activeVideo != null) {
            YouTubeEmbeddedPlayer(
                videoId = activeVideo.videoId,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(9f / 16f),
                isForShorts = true,
                onPlayerReady = { player ->
                    playerRef = player
                    if (isMuted) {
                        player.mute()
                    } else {
                        player.unMute()
                        player.setVolume(100)
                    }
                    player.play()
                    isPlaying = true
                },
                onCurrentSecond = { sec ->
                    currentSecond = sec
                },
                onVideoDuration = { dur ->
                    totalDuration = dur
                },
                onStateChange = { state ->
                    isPlaying = (state == PlayerConstants.PlayerState.PLAYING)
                }
            )
        }

        // ── 2. Vertical Pager (Gesture & Thumbnails) ──
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            key = { page -> if (page < shorts.size) shorts[page].videoId else page }
        ) { page ->
            if (page in shorts.indices) {
                val shortVideo = shorts[page]
                val isCurrent = pagerState.currentPage == page
                val isScrolling = pagerState.isScrollInProgress
                val channel = channelMap[shortVideo.channelId]

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
                    // Show thumbnail while swiping or for non-current pages
                    if (!isCurrent || isScrolling) {
                        AsyncImage(
                            model = shortVideo.thumbnailUrl,
                            contentDescription = "Short preview",
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(9f / 16f),
                            contentScale = ContentScale.Crop
                        )
                    }

                    // Click layer to toggle play / pause
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable {
                                if (isPlaying) {
                                    playerRef?.pause()
                                    isPlaying = false
                                } else {
                                    playerRef?.play()
                                    isPlaying = true
                                }
                                showPlayIconFeedback = true
                            }
                    )

                    // Bottom Gradient Scrim
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color(0x99000000), Color(0xF5000000))
                                )
                            )
                    )

                    // Bottom Left Video Details
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 16.dp, end = 72.dp, bottom = 32.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onChannelClick(shortVideo.channelId) }
                                .padding(vertical = 4.dp)
                        ) {
                            if (!channel?.thumbnailUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = channel?.thumbnailUrl,
                                    contentDescription = "Channel thumbnail",
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Surface(
                                    modifier = Modifier.size(38.dp),
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            shortVideo.channelName.take(1).uppercase(),
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = shortVideo.channelName,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Followed Channel • View Profile",
                                    color = Color(0xFFFF5252),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = shortVideo.title,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 18.sp
                        )

                        if (shortVideo.publishedAt.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = shortVideo.publishedAt.take(10),
                                color = Color.LightGray,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Floating Play/Pause Action Button
                    Surface(
                        onClick = {
                            if (isPlaying) {
                                playerRef?.pause()
                                isPlaying = false
                            } else {
                                playerRef?.play()
                                isPlaying = true
                            }
                        },
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.6f),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = 48.dp)
                            .size(46.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = if (isPlaying) "❚❚" else "▶",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Bottom Seekbar (Random click anywhere to seek)
                    if (isCurrent && totalDuration > 0f) {
                        val displaySec = if (isUserSeeking) userSeekValue else currentSecond
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 2.dp)
                        ) {
                            Slider(
                                value = displaySec.coerceIn(0f, totalDuration.coerceAtLeast(1f)),
                                onValueChange = { newVal ->
                                    isUserSeeking = true
                                    userSeekValue = newVal
                                },
                                onValueChangeFinished = {
                                    playerRef?.seekTo(userSeekValue)
                                    playerRef?.play()
                                    currentSecond = userSeekValue
                                    isPlaying = true
                                    isUserSeeking = false
                                },
                                valueRange = 0f..totalDuration.coerceAtLeast(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.Red,
                                    activeTrackColor = Color.Red,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.35f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }

        // Tap Feedback Animation Overlay
        AnimatedVisibility(
            visible = showPlayIconFeedback,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            LaunchedEffect(showPlayIconFeedback) {
                if (showPlayIconFeedback) {
                    delay(500)
                    showPlayIconFeedback = false
                }
            }
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.65f),
                modifier = Modifier.size(68.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = if (isPlaying) "▶" else "❚❚",
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // ── 3. Top Translucent Header: Title, Channel Filters, Grid Toggle ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xCC000000), Color(0x77000000), Color.Transparent)
                    )
                )
                .padding(top = 10.dp, bottom = 12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFFF0000)
                    ) {
                        Text(
                            text = "⚡ SHORTS",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Swipe up for next",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Mute / Unmute Button
                    Surface(
                        onClick = { isMuted = !isMuted },
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.5f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = if (isMuted) "🔇" else "🔊",
                                fontSize = 14.sp
                            )
                        }
                    }

                    // Grid Toggle Button
                    Surface(
                        onClick = onToggleGrid,
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "⊞ Grid",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }

                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "🔄",
                            fontSize = 14.sp,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { onRefresh() }
                                .padding(6.dp)
                        )
                    }
                }
            }

            // Channel Filter Chips
            if (channels.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = {
                            onSelectChannel(null)
                            coroutineScope.launch { pagerState.scrollToPage(0) }
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = if (selectedChannelId == null) Color(0xFFFF0000) else Color.White.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "All (${shorts.size})",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }

                    channels.forEach { channel ->
                        val count = shorts.count { it.channelId == channel.channelId }
                        val isSelected = selectedChannelId == channel.channelId
                        Surface(
                            onClick = {
                                val newId = if (isSelected) null else channel.channelId
                                onSelectChannel(newId)
                                coroutineScope.launch { pagerState.scrollToPage(0) }
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) Color(0xFFFF0000) else Color.White.copy(alpha = 0.2f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                if (channel.thumbnailUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = channel.thumbnailUrl,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text(
                                    text = "${channel.channelName} ($count)",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Helper: Channel Filter Chips Row for Grid Mode
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun ShortsChannelFilterRow(
    channels: List<ChannelEntity>,
    shorts: List<VideoEntity>,
    selectedChannelId: String?,
    onSelectChannel: (String?) -> Unit
) {
    if (channels.isEmpty()) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(
            selected = selectedChannelId == null,
            onClick = { onSelectChannel(null) },
            label = { Text("All (${shorts.size})") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primary,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
            )
        )

        channels.forEach { channel ->
            val channelShortsCount = shorts.count { it.channelId == channel.channelId }
            FilterChip(
                selected = selectedChannelId == channel.channelId,
                onClick = {
                    onSelectChannel(if (selectedChannelId == channel.channelId) null else channel.channelId)
                },
                leadingIcon = {
                    if (channel.thumbnailUrl.isNotBlank()) {
                        AsyncImage(
                            model = channel.thumbnailUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }
                },
                label = { Text("${channel.channelName} ($channelShortsCount)") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Short Card for Grid Mode
// ──────────────────────────────────────────────────────────────────────────────

@Composable
fun ShortCard(
    video: VideoEntity,
    channelAvatarUrl: String? = null,
    onVideoClick: () -> Unit,
    onChannelClick: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onVideoClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(9f / 16f)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .background(Color.Black)
            ) {
                if (video.thumbnailUrl.isNotBlank()) {
                    AsyncImage(
                        model = video.thumbnailUrl,
                        contentDescription = "Short thumbnail",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color(0x99000000), Color(0xEE000000)),
                                startY = 200f
                            )
                        )
                )

                Surface(
                    color = Color(0xCCFF0000),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.padding(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "⚡ SHORTS",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp)
                ) {
                    Text(
                        text = video.title,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 17.sp
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!channelAvatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = channelAvatarUrl,
                        contentDescription = "Channel avatar",
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .clickable { onChannelClick(video.channelId) },
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Surface(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .clickable { onChannelClick(video.channelId) },
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = video.channelName.take(1).uppercase(),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = video.channelName,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { onChannelClick(video.channelId) }
                    )
                    if (video.publishedAt.isNotBlank()) {
                        Text(
                            text = video.publishedAt.take(10),
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
