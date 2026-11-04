package com.example.channeltube.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.media.AudioManager
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.FullscreenListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.channeltube.R
import com.example.channeltube.data.local.ChannelEntity
import com.example.channeltube.data.local.VideoEntity
import com.example.channeltube.data.remote.ChannelSearchResult
import com.example.channeltube.viewmodel.MainViewModel

private enum class MainTab(val label: String, val symbol: String) {
    FEED("Feed", "▶"),
    SHORTS("Shorts", "⚡"),
    CHANNELS("Channels", "▣"),
    SETTINGS("About", "⚙")
}

@Composable
fun ChannelTubeTheme(content: @Composable () -> Unit) {
    val colors = darkColorScheme(
        primary = Color(0xFFFF5252),
        onPrimary = Color.White,
        background = Color(0xFF101114),
        onBackground = Color(0xFFF4F4F5),
        surface = Color(0xFF1B1D22),
        onSurface = Color(0xFFF4F4F5),
        surfaceVariant = Color(0xFF292C33),
        onSurfaceVariant = Color(0xFFB9BBC2)
    )
    MaterialTheme(colorScheme = colors, content = content)
}

@Composable
fun ChannelTubeApp(viewModel: MainViewModel) {
    val channels by viewModel.channels.collectAsState()
    val videos by viewModel.videos.collectAsState()
    val message by viewModel.message.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()

    var selectedTab by rememberSaveable { mutableStateOf(MainTab.FEED.name) }
    var selectedVideo by remember { mutableStateOf<VideoEntity?>(null) }
    var selectedChannelId by remember { mutableStateOf<String?>(null) }

    if (selectedVideo != null) {
        PlayerScreen(
            video = selectedVideo!!,
            channels = channels,
            videos = videos,
            isLoadingMore = isLoadingMore,
            onLoadMore = { viewModel.loadMoreFeedVideos() },
            onVideoSelect = { selectedVideo = it },
            onChannelClick = {
                selectedVideo = null
                selectedChannelId = it
            },
            onBack = { selectedVideo = null }
        )
    } else if (selectedChannelId != null) {
        ChannelDetailScreen(
            channelId = selectedChannelId!!,
            onBack = { selectedChannelId = null },
            onVideoClick = { selectedVideo = it }
        )
    } else {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    MainTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = selectedTab == tab.name,
                            onClick = { selectedTab = tab.name },
                            icon = { Text(tab.symbol, fontSize = 18.sp) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (MainTab.valueOf(selectedTab) != MainTab.SHORTS) {
                    AppHeader(
                        isRefreshing = isRefreshing,
                        onRefresh = { viewModel.refreshFeed() }
                    )
                }

                when (MainTab.valueOf(selectedTab)) {
                    MainTab.FEED -> FeedScreen(
                        channels = channels,
                        videos = videos,
                        isLoadingMore = isLoadingMore,
                        onLoadMore = { viewModel.loadMoreFeedVideos() },
                        onVideoClick = { selectedVideo = it },
                        onChannelClick = { selectedChannelId = it },
                        onGoToChannels = { selectedTab = MainTab.CHANNELS.name }
                    )
                    MainTab.SHORTS -> ShortsScreen(
                        viewModel = viewModel,
                        onVideoClick = { selectedVideo = it },
                        onChannelClick = { selectedChannelId = it },
                        onGoToChannels = { selectedTab = MainTab.CHANNELS.name }
                    )
                    MainTab.CHANNELS -> ChannelsScreen(
                        viewModel = viewModel,
                        onChannelClick = { selectedChannelId = it }
                    )
                    MainTab.SETTINGS -> AboutScreen(channelCount = channels.size)
                }

                if (!message.isNullOrBlank()) {
                    MessageBanner(
                        message = message.orEmpty(),
                        onDismiss = { viewModel.dismissMessage() }
                    )
                }
            }
        }
    }
}

@Composable
private fun AppHeader(isRefreshing: Boolean, onRefresh: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = R.drawable.app_logo),
            contentDescription = "ChannelTube logo",
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(10.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("ChannelTube", fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Text(
                "Your channels. Your feed.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }
        if (isRefreshing) {
            CircularProgressIndicator(modifier = Modifier.size(25.dp), strokeWidth = 2.dp)
        } else {
            TextButton(onClick = onRefresh) {
                Text("Refresh")
            }
        }
    }
}

@Composable
private fun FeedScreen(
    channels: List<ChannelEntity>,
    videos: List<VideoEntity>,
    isLoadingMore: Boolean = false,
    onLoadMore: () -> Unit = {},
    onVideoClick: (VideoEntity) -> Unit,
    onChannelClick: (String) -> Unit,
    onGoToChannels: () -> Unit
) {
    if (channels.isEmpty()) {
        EmptyState(
            title = "Your feed is empty",
            description = "Add the YouTube channels you want to follow. Only their videos will appear here.",
            buttonText = "Add channels",
            onButtonClick = onGoToChannels
        )
        return
    }

    if (videos.isEmpty()) {
        EmptyState(
            title = "No videos loaded yet",
            description = "Refresh your feed to load recent uploads from your selected channels.",
            buttonText = "Go to My Channels",
            onButtonClick = onGoToChannels
        )
        return
    }

    val channelMap = remember(channels) { channels.associateBy { it.channelId } }
    val listState = rememberLazyListState()

    val shouldLoadMore = remember {
        derivedStateOf {
            val totalItems = listState.layoutInfo.totalItemsCount
            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleIndex >= totalItems - 4
        }
    }
    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value && !isLoadingMore) {
            onLoadMore()
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                "YOUR FEED · ${channels.size} CHANNEL(S) · ${videos.size} VIDEOS",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        items(videos, key = { it.videoId }) { video ->
            val channel = channelMap[video.channelId]
            VideoCard(
                video = video,
                channelAvatarUrl = channel?.thumbnailUrl,
                onVideoClick = { onVideoClick(video) },
                onChannelClick = onChannelClick
            )
        }
        if (isLoadingMore) {
            item(key = "loading_more_feed_indicator") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Loading more videos...",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VideoCard(
    video: VideoEntity,
    channelAvatarUrl: String? = null,
    onVideoClick: () -> Unit,
    onChannelClick: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onVideoClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            if (video.thumbnailUrl.isNotBlank()) {
                AsyncImage(
                    model = video.thumbnailUrl,
                    contentDescription = "Thumbnail for ${video.title}",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(195.dp),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(195.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No thumbnail", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = video.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!channelAvatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = channelAvatarUrl,
                            contentDescription = "Channel avatar for ${video.channelName}",
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .clickable { onChannelClick(video.channelId) },
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Surface(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .clickable { onChannelClick(video.channelId) },
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = CircleShape
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = video.channelName.take(1).uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = video.channelName,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable { onChannelClick(video.channelId) }
                        )
                        if (video.publishedAt.isNotBlank()) {
                            Text(
                                text = video.publishedAt.take(10),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelsScreen(
    viewModel: MainViewModel,
    onChannelClick: (String) -> Unit
) {
    val channels by viewModel.channels.collectAsState()
    val results by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val workingChannelId by viewModel.workingChannelId.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Text("Add a channel", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(
            "Search by channel name, paste a channel URL, or enter an @handle.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                label = { Text("Channel name or URL") },
                singleLine = true
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { viewModel.searchChannels(query) },
                enabled = query.isNotBlank() && !isSearching
            ) {
                if (isSearching) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Search")
                }
            }
        }

        if (results.isNotEmpty()) {
            Text(
                "SEARCH RESULTS",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 18.dp, bottom = 8.dp)
            )
            LazyColumn(
                modifier = Modifier.height(220.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(results, key = { it.channelId }) { result ->
                    SearchResultRow(
                        result = result,
                        isAdding = workingChannelId == result.channelId,
                        alreadyAdded = channels.any { it.channelId == result.channelId },
                        onAdd = { viewModel.addChannel(result) }
                    )
                }
            }
        }

        Text(
            "MY CHANNELS · ${channels.size}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 18.dp, bottom = 8.dp)
        )

        if (channels.isEmpty()) {
            Text(
                "No channels saved yet.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 18.dp)
            ) {
                items(channels, key = { it.channelId }) { channel ->
                    SavedChannelRow(
                        channel = channel,
                        onClick = { onChannelClick(channel.channelId) },
                        onRemove = { viewModel.removeChannel(channel) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    result: ChannelSearchResult,
    isAdding: Boolean,
    alreadyAdded: Boolean,
    onAdd: () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ChannelThumbnail(url = result.thumbnailUrl)
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(result.channelName, fontWeight = FontWeight.SemiBold, maxLines = 2)
                Text(
                    result.channelId,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    maxLines = 1
                )
            }
            when {
                alreadyAdded -> Text("Added", color = MaterialTheme.colorScheme.primary)
                isAdding -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                else -> OutlinedButton(onClick = onAdd) { Text("Add") }
            }
        }
    }
}

@Composable
private fun SavedChannelRow(
    channel: ChannelEntity,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ChannelThumbnail(url = channel.thumbnailUrl)
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(channel.channelName, fontWeight = FontWeight.SemiBold)
                Text(
                    "Selected channel",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
            TextButton(onClick = onRemove) { Text("Remove") }
        }
    }
}

@Composable
private fun ChannelThumbnail(url: String) {
    if (url.isNotBlank()) {
        AsyncImage(
            model = url,
            contentDescription = "Channel thumbnail",
            modifier = Modifier.size(48.dp),
            contentScale = ContentScale.Crop
        )
    } else {
        Surface(
            modifier = Modifier.size(48.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.small
        ) {
            Box(contentAlignment = Alignment.Center) { Text("YT") }
        }
    }
}

@Composable
private fun AboutScreen(channelCount: Int) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 6.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.app_logo),
                contentDescription = "ChannelTube logo",
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(14.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text("ChannelTube", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Your YouTube Channels. In One App.",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        Text("About ChannelTube", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "ChannelTube builds a focused feed from the channels you choose. It does not use YouTube's general recommendation feed.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Feed rules", fontWeight = FontWeight.Bold)
                Text("• Only saved channel IDs are allowed in the home feed.")
                Text("• Removing a channel also removes its cached videos.")
                Text("• Recent uploads are cached locally for the feed.")
            }
        }
        Text("Selected channels: $channelCount")
        Text(
            "Playback uses YouTube's embedded player. YouTube may display its branding, controls, ads, or restrictions. This app does not download or extract video files.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp
        )
        Text("ChannelTube · MVP 1.0.0", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    }
}

@Composable
fun EmptyState(
    title: String,
    description: String,
    buttonText: String,
    onButtonClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 18.dp)
            )
            Button(onClick = onButtonClick) { Text(buttonText) }
        }
    }
}

@Composable
private fun MessageBanner(message: String, onDismiss: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(message, modifier = Modifier.weight(1f), fontSize = 12.sp)
            TextButton(onClick = onDismiss) { Text("OK") }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// In-App Video Player Screen
// ──────────────────────────────────────────────────────────────────────────────

private tailrec fun android.content.Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private enum class PlayerLoadState { LOADING, PLAYING, ERROR }

@Composable
private fun PlayerScreen(
    video: VideoEntity,
    channels: List<ChannelEntity>,
    videos: List<VideoEntity>,
    isLoadingMore: Boolean = false,
    onLoadMore: () -> Unit = {},
    onVideoSelect: (VideoEntity) -> Unit = {},
    onChannelClick: (String) -> Unit = {},
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

    var loadState by remember(video.videoId) { mutableStateOf(PlayerLoadState.LOADING) }
    var errorMessage by remember(video.videoId) { mutableStateOf<String?>(null) }
    var isFullscreen by remember { mutableStateOf(false) }

    var playerRef by remember { mutableStateOf<YouTubePlayer?>(null) }
    var currentSecond by remember(video.videoId) { mutableFloatStateOf(0f) }
    var totalDuration by remember(video.videoId) { mutableFloatStateOf(0f) }
    var isPlaying by remember(video.videoId) { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    var userDraggingValue by remember(video.videoId) { mutableStateOf<Float?>(null) }

    var fullscreenViewRef by remember { mutableStateOf<View?>(null) }
    var exitFullscreenFn by remember { mutableStateOf<(() -> Unit)?>(null) }

    // Ensure device media volume is not muted or zero so audio is clearly audible
    LaunchedEffect(Unit) {
        audioManager?.let { am ->
            val currentVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (currentVol == 0) {
                val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                am.setStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    (maxVol * 0.75f).toInt().coerceAtLeast(1),
                    AudioManager.FLAG_SHOW_UI
                )
            }
        }
    }

    fun exitFullscreenInternal() {
        val rootLayout = activity?.findViewById<FrameLayout>(android.R.id.content)
        fullscreenViewRef?.let { rootLayout?.removeView(it) }
        fullscreenViewRef = null
        exitFullscreenFn = null
        isFullscreen = false
        activity?.window?.let { window ->
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        }
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    // Exit fullscreen when back button is pressed
    BackHandler(enabled = isFullscreen) {
        exitFullscreenFn?.invoke()
        exitFullscreenInternal()
    }

    // Regular back → return to feed
    BackHandler(enabled = !isFullscreen) {
        onBack()
    }

    DisposableEffect(Unit) {
        onDispose {
            exitFullscreenInternal()
        }
    }

    // Normal player layout (kept composed even when in fullscreen so player is never destroyed)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ── 1. FIXED TOP PLAYER SECTION ──────────────────────────────────
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Top header bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onBack,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "← Back to feed",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        "Now Playing",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                }

                // Embedded YouTube player container (16:9)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f),
                    contentAlignment = Alignment.Center
                ) {
                    if (loadState == PlayerLoadState.LOADING && video.thumbnailUrl.isNotBlank()) {
                        AsyncImage(
                            model = video.thumbnailUrl,
                            contentDescription = "Video thumbnail",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }

                    YouTubeEmbeddedPlayer(
                        videoId = video.videoId,
                        onPlayerReady = { player ->
                            playerRef = player
                            player.unMute()
                            player.setVolume(100)
                            isMuted = false
                            if (loadState != PlayerLoadState.ERROR) {
                                loadState = PlayerLoadState.PLAYING
                            }
                        },
                        onCurrentSecond = { sec ->
                            currentSecond = sec
                            if (loadState == PlayerLoadState.LOADING) {
                                loadState = PlayerLoadState.PLAYING
                            }
                        },
                        onVideoDuration = { dur ->
                            totalDuration = dur
                        },
                        onStateChange = { state ->
                            isPlaying = (state == PlayerConstants.PlayerState.PLAYING)
                            if (state == PlayerConstants.PlayerState.PLAYING ||
                                state == PlayerConstants.PlayerState.PAUSED
                            ) {
                                if (loadState != PlayerLoadState.ERROR) {
                                    loadState = PlayerLoadState.PLAYING
                                }
                            }
                        },
                        onError = { description ->
                            loadState = PlayerLoadState.ERROR
                            errorMessage = description
                        },
                        onEnterFullscreen = { view, exitFs ->
                            fullscreenViewRef = view
                            exitFullscreenFn = exitFs
                            isFullscreen = true

                            val rootLayout = activity?.findViewById<FrameLayout>(android.R.id.content)
                            (view.parent as? ViewGroup)?.removeView(view)
                            view.setBackgroundColor(android.graphics.Color.BLACK)
                            rootLayout?.addView(
                                view,
                                FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            )

                            activity?.window?.let { window ->
                                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                                insetsController.systemBarsBehavior =
                                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                                insetsController.hide(WindowInsetsCompat.Type.systemBars())
                            }

                            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        },
                        onExitFullscreen = {
                            exitFullscreenInternal()
                        }
                    )
                }

                // ── Interactive Scrubber / Slider (Random Click to Seek) ──
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                        val displaySecond = userDraggingValue ?: currentSecond
                        Slider(
                            value = displaySecond.coerceIn(0f, totalDuration.coerceAtLeast(1f)),
                            onValueChange = { newValue ->
                                userDraggingValue = newValue
                            },
                            onValueChangeFinished = {
                                userDraggingValue?.let { target ->
                                    playerRef?.seekTo(target)
                                    playerRef?.unMute()
                                    playerRef?.setVolume(100)
                                    playerRef?.play()
                                    currentSecond = target
                                    isPlaying = true
                                    isMuted = false
                                }
                                userDraggingValue = null
                            },
                            valueRange = 0f..totalDuration.coerceAtLeast(1f),
                            enabled = totalDuration > 0f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFFFF0000),
                                activeTrackColor = Color(0xFFFF0000),
                                inactiveTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${formatDuration(displaySecond)} / ${formatDuration(totalDuration)}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Sound / Mute Toggle Button
                                Surface(
                                    onClick = {
                                        if (isMuted) {
                                            playerRef?.unMute()
                                            playerRef?.setVolume(100)
                                            isMuted = false
                                            audioManager?.let { am ->
                                                val currentVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                                                if (currentVol == 0) {
                                                    val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                                    am.setStreamVolume(
                                                        AudioManager.STREAM_MUSIC,
                                                        (maxVol * 0.75f).toInt().coerceAtLeast(1),
                                                        AudioManager.FLAG_SHOW_UI
                                                    )
                                                }
                                            }
                                        } else {
                                            playerRef?.mute()
                                            isMuted = true
                                        }
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isMuted) Color(0xFF444444) else MaterialTheme.colorScheme.surface
                                ) {
                                    Text(
                                        text = if (isMuted) "🔇 Muted" else "🔊 Sound",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isMuted) Color.LightGray else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                    )
                                }

                                // Rewind 10s
                                Surface(
                                    onClick = {
                                        val target = (currentSecond - 10f).coerceAtLeast(0f)
                                        playerRef?.seekTo(target)
                                        playerRef?.unMute()
                                        playerRef?.setVolume(100)
                                        playerRef?.play()
                                        currentSecond = target
                                        isPlaying = true
                                        isMuted = false
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.surface
                                ) {
                                    Text(
                                        text = "⏪ 10s",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                    )
                                }

                                // Play / Pause Toggle
                                Surface(
                                    onClick = {
                                        if (isPlaying) {
                                            playerRef?.pause()
                                            isPlaying = false
                                        } else {
                                            playerRef?.unMute()
                                            playerRef?.setVolume(100)
                                            playerRef?.play()
                                            isPlaying = true
                                            isMuted = false
                                        }
                                    },
                                    shape = CircleShape,
                                    color = Color(0xFFFF0000)
                                ) {
                                    Text(
                                        text = if (isPlaying) "❚❚" else "▶",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                                    )
                                }

                                // Fast Forward 10s
                                Surface(
                                    onClick = {
                                        val target = (currentSecond + 10f).coerceAtMost(totalDuration)
                                        playerRef?.seekTo(target)
                                        playerRef?.unMute()
                                        playerRef?.setVolume(100)
                                        playerRef?.play()
                                        currentSecond = target
                                        isPlaying = true
                                        isMuted = false
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.surface
                                ) {
                                    Text(
                                        text = "10s ⏩",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Compact Title & Channel Information
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = video.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onChannelClick(video.channelId) }
                            .padding(vertical = 2.dp)
                    ) {
                        val currentChannel = channels.find { it.channelId == video.channelId }
                        if (!currentChannel?.thumbnailUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = currentChannel!!.thumbnailUrl,
                                contentDescription = "Channel avatar",
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Surface(
                                modifier = Modifier.size(28.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = video.channelName.take(1).uppercase(),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = video.channelName,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (video.publishedAt.isNotBlank()) {
                                Text(
                                    text = video.publishedAt.take(10),
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Text(
                            text = "Channel Profile →",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    if (loadState == PlayerLoadState.ERROR) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF2C1B1B))) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    "Playback unavailable",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    color = Color(0xFFFF8A80)
                                )
                                Text(
                                    errorMessage ?: "This video cannot be played in the embedded player.",
                                    color = Color(0xFFCFCFCF),
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── 2. SCROLLABLE ADDED VIDEO FEED BELOW THE FIXED PLAYER ──────────
        val feedVideos = remember(videos, video.videoId) {
            videos.filter { it.videoId != video.videoId }
        }
        val channelMap = remember(channels) {
            channels.associateBy { it.channelId }
        }
        val listState = rememberLazyListState()

        val shouldLoadMore = remember {
            derivedStateOf {
                val totalItems = listState.layoutInfo.totalItemsCount
                val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                totalItems > 0 && lastVisibleIndex >= totalItems - 4
            }
        }
        LaunchedEffect(shouldLoadMore.value) {
            if (shouldLoadMore.value && !isLoadingMore) {
                onLoadMore()
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "MORE FROM YOUR CHANNELS",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${feedVideos.size} videos",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }

            items(feedVideos, key = { it.videoId }) { feedVideo ->
                val channel = channelMap[feedVideo.channelId]
                VideoCard(
                    video = feedVideo,
                    channelAvatarUrl = channel?.thumbnailUrl,
                    onVideoClick = { onVideoSelect(feedVideo) },
                    onChannelClick = onChannelClick
                )
            }

            if (isLoadingMore) {
                item(key = "loading_more_player_feed_indicator") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "Loading more videos...",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

fun formatDuration(seconds: Float): String {
    val totalSec = seconds.toInt().coerceAtLeast(0)
    val hrs = totalSec / 3600
    val mins = (totalSec % 3600) / 60
    val secs = totalSec % 60
    return if (hrs > 0) {
        String.format("%d:%02d:%02d", hrs, mins, secs)
    } else {
        String.format("%02d:%02d", mins, secs)
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// YouTube IFrame Player Composable
// ──────────────────────────────────────────────────────────────────────────────

@Composable
fun YouTubeEmbeddedPlayer(
    videoId: String,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .aspectRatio(16f / 9f),
    isForShorts: Boolean = false,
    onPlayerReady: (YouTubePlayer) -> Unit = {},
    onError: (String?) -> Unit = {},
    onCurrentSecond: (Float) -> Unit = {},
    onVideoDuration: (Float) -> Unit = {},
    onStateChange: (PlayerConstants.PlayerState) -> Unit = {},
    onEnterFullscreen: (View, () -> Unit) -> Unit = { _, _ -> },
    onExitFullscreen: () -> Unit = {}
) {
    val safeVideoId = remember(videoId) {
        videoId.filter { it.isLetterOrDigit() || it == '_' || it == '-' }
    }
    var currentLoadedVideoId by remember { mutableStateOf("") }

    // Register active screen callbacks with WarmPlayerManager
    DisposableEffect(isForShorts) {
        val callback = object : PlayerEventCallback {
            override fun onPlayerReady(player: YouTubePlayer) {
                onPlayerReady(player)
            }
            override fun onCurrentSecond(second: Float) {
                onCurrentSecond(second)
            }
            override fun onVideoDuration(duration: Float) {
                onVideoDuration(duration)
            }
            override fun onStateChange(state: PlayerConstants.PlayerState) {
                onStateChange(state)
            }
            override fun onError(description: String?) {
                onError(description)
            }
            override fun onEnterFullscreen(view: View, exitFullscreen: () -> Unit) {
                onEnterFullscreen(view, exitFullscreen)
            }
            override fun onExitFullscreen() {
                onExitFullscreen()
            }
        }

        if (isForShorts) {
            WarmPlayerManager.shortsCallback = callback
            if (WarmPlayerManager.isShortsReady) {
                WarmPlayerManager.shortsPlayer?.let { onPlayerReady(it) }
            }
        } else {
            WarmPlayerManager.mainCallback = callback
            if (WarmPlayerManager.isMainReady) {
                WarmPlayerManager.mainPlayer?.let { onPlayerReady(it) }
            }
        }

        onDispose {
            if (isForShorts) {
                WarmPlayerManager.detachShortsPlayer()
            } else {
                WarmPlayerManager.detachMainPlayer()
            }
        }
    }

    // Instant video load and playback on the warm player
    LaunchedEffect(safeVideoId) {
        if (safeVideoId.isNotBlank() && safeVideoId != currentLoadedVideoId) {
            currentLoadedVideoId = safeVideoId
            val player = if (isForShorts) WarmPlayerManager.shortsPlayer else WarmPlayerManager.mainPlayer
            player?.loadVideo(safeVideoId, 0f)
            player?.unMute()
            player?.setVolume(100)
            player?.play()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            if (isForShorts) {
                WarmPlayerManager.getShortsPlayerView(ctx)
            } else {
                WarmPlayerManager.getMainPlayerView(ctx)
            }
        },
        update = {
            val player = if (isForShorts) WarmPlayerManager.shortsPlayer else WarmPlayerManager.mainPlayer
            if (safeVideoId.isNotBlank() && safeVideoId != currentLoadedVideoId) {
                currentLoadedVideoId = safeVideoId
                player?.loadVideo(safeVideoId, 0f)
                player?.unMute()
                player?.setVolume(100)
                player?.play()
            }
        }
    )
}
