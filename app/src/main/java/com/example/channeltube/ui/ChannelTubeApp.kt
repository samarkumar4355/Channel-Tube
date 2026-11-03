package com.example.channeltube.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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

    var selectedTab by rememberSaveable { mutableStateOf(MainTab.FEED.name) }
    var selectedVideo by remember { mutableStateOf<VideoEntity?>(null) }
    var selectedChannelId by remember { mutableStateOf<String?>(null) }

    if (selectedVideo != null) {
        PlayerScreen(
            video = selectedVideo!!,
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
                AppHeader(
                    isRefreshing = isRefreshing,
                    onRefresh = { viewModel.refreshFeed() }
                )

                when (MainTab.valueOf(selectedTab)) {
                    MainTab.FEED -> FeedScreen(
                        channels = channels,
                        videos = videos,
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

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                "YOUR FEED · ${channels.size} CHANNEL(S)",
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
private fun EmptyState(
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

private enum class PlayerLoadState { LOADING, PLAYING, ERROR }

@Composable
private fun PlayerScreen(video: VideoEntity, onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity

    var loadState by remember { mutableStateOf(PlayerLoadState.LOADING) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isFullscreen by remember { mutableStateOf(false) }

    // References kept for fullscreen toggling and lifecycle cleanup.
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var customViewRef by remember { mutableStateOf<View?>(null) }
    var customViewCallbackRef by remember {
        mutableStateOf<WebChromeClient.CustomViewCallback?>(null)
    }

    // Exit fullscreen when the hardware back button is pressed.
    BackHandler(enabled = isFullscreen) {
        customViewCallbackRef?.onCustomViewHidden()
        customViewRef = null
        customViewCallbackRef = null
        isFullscreen = false
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    // Regular back (not fullscreen) → return to feed.
    BackHandler(enabled = !isFullscreen) {
        onBack()
    }

    // Pause WebView playback and clean up when the composable leaves.
    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.onPause()
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Fullscreen overlay – drawn on top of everything when active.
    if (isFullscreen && customViewRef != null) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            factory = { ctx ->
                FrameLayout(ctx).apply {
                    addView(
                        customViewRef,
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    )
                }
            }
        )
        return // Don't render the normal player UI underneath.
    }

    // Normal (non-fullscreen) player layout.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ── Top bar ──
        TextButton(
            onClick = onBack,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp)
        ) {
            Text("← Back to feed")
        }

        // ── Embedded player ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
            contentAlignment = Alignment.Center
        ) {
            // Show thumbnail underneath while loading.
            if (loadState == PlayerLoadState.LOADING && video.thumbnailUrl.isNotBlank()) {
                AsyncImage(
                    model = video.thumbnailUrl,
                    contentDescription = "Video thumbnail",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            // The WebView player itself.
            YouTubeEmbeddedPlayer(
                videoId = video.videoId,
                onWebViewCreated = { webViewRef = it },
                onPageStarted = { loadState = PlayerLoadState.LOADING },
                onPageFinished = {
                    if (loadState != PlayerLoadState.ERROR) {
                        loadState = PlayerLoadState.PLAYING
                    }
                },
                onError = { description ->
                    loadState = PlayerLoadState.ERROR
                    errorMessage = description
                },
                onShowCustomView = { view, callback ->
                    customViewRef = view
                    customViewCallbackRef = callback
                    isFullscreen = true
                    activity?.requestedOrientation =
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                },
                onHideCustomView = {
                    customViewCallbackRef?.onCustomViewHidden()
                    customViewRef = null
                    customViewCallbackRef = null
                    isFullscreen = false
                    activity?.requestedOrientation =
                        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
            )

            // Spinner overlay while the iframe is loading.
            if (loadState == PlayerLoadState.LOADING) {
                CircularProgressIndicator(
                    modifier = Modifier.size(36.dp),
                    strokeWidth = 3.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // ── Video metadata ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                video.title,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                video.channelName,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 14.sp
            )
            if (video.publishedAt.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    video.publishedAt.take(10),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }

            // Error banner – shows inside the player screen without redirecting.
            if (loadState == PlayerLoadState.ERROR) {
                Spacer(modifier = Modifier.height(14.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF2C1B1B)
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            "Playback unavailable",
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFFF8A80)
                        )
                        Text(
                            errorMessage
                                ?: "This video cannot be played in the embedded player. The video may be restricted, unavailable, or require viewing on YouTube directly.",
                            color = Color(0xFFCFCFCF),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                "Playback is provided by YouTube's embedded player. YouTube controls branding, ads, and playback restrictions.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                textAlign = TextAlign.Start
            )
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// YouTube IFrame WebView Composable
// ──────────────────────────────────────────────────────────────────────────────

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun YouTubeEmbeddedPlayer(
    videoId: String,
    onWebViewCreated: (WebView) -> Unit = {},
    onPageStarted: () -> Unit = {},
    onPageFinished: () -> Unit = {},
    onError: (String?) -> Unit = {},
    onShowCustomView: (View, WebChromeClient.CustomViewCallback) -> Unit = { _, _ -> },
    onHideCustomView: () -> Unit = {}
) {
    // IDs come from the YouTube API; keep the value URL-safe before placing it in HTML.
    val safeVideoId = remember(videoId) {
        videoId.filter { it.isLetterOrDigit() || it == '_' || it == '-' }
    }
    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f),
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(android.graphics.Color.BLACK)

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    allowFileAccess = false

                    // Remove '; wv' from user agent so YouTube does not restrict the embedded player
                    val defaultUa = userAgentString
                    if (defaultUa.contains("; wv")) {
                        userAgentString = defaultUa.replace("; wv", "")
                    }
                }

                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        onPageStarted()
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        onPageFinished()
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?
                    ) {
                        super.onReceivedError(view, request, error)
                        // Only report main-frame errors, not sub-resource loads.
                        if (request?.isForMainFrame == true) {
                            val description = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                error?.description?.toString()
                            } else {
                                "A network error occurred."
                            }
                            onError(description ?: "Failed to load the video player.")
                        }
                    }

                    // Only intercept top-level (main frame) navigation attempts away from the player.
                    // Sub-frame and iframe resources must NEVER be blocked, or YouTube internal scripts fail.
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        if (request?.isForMainFrame != true) {
                            return false
                        }
                        val url = request.url?.toString().orEmpty()
                        if (url.contains("youtube-nocookie.com") ||
                            url.contains("youtube.com/embed") ||
                            url.startsWith("data:") ||
                            url.startsWith("about:")
                        ) {
                            return false
                        }
                        // Block top-level external navigation (e.g. clicking YouTube logo/links)
                        return true
                    }
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                        if (view != null && callback != null) {
                            onShowCustomView(view, callback)
                        }
                    }

                    override fun onHideCustomView() {
                        onHideCustomView()
                    }
                }

                val html = """
                    <!doctype html>
                    <html>
                    <head>
                      <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                      <style>
                        * { margin: 0; padding: 0; box-sizing: border-box; }
                        html, body { width: 100%; height: 100%; background: #000; overflow: hidden; }
                        iframe { position: absolute; top: 0; left: 0; width: 100%; height: 100%; border: 0; }
                      </style>
                    </head>
                    <body>
                      <iframe
                        id="ytplayer"
                        src="https://www.youtube-nocookie.com/embed/$safeVideoId?autoplay=1&playsinline=1&rel=0&modestbranding=1"
                        title="YouTube video player"
                        allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share; fullscreen"
                        referrerpolicy="strict-origin-when-cross-origin"
                        allowfullscreen>
                      </iframe>
                    </body>
                    </html>
                """.trimIndent()
                loadDataWithBaseURL(
                    "https://www.youtube-nocookie.com",
                    html,
                    "text/html",
                    "UTF-8",
                    null
                )

                onWebViewCreated(this)
            }
        }
    )
}
