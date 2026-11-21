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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
import com.example.channeltube.data.remote.PlaylistEntity
import com.example.channeltube.viewmodel.MainViewModel

private enum class MainTab(val label: String, val symbol: String) {
    FEED("Feed", "▶"),
    SHORTS("Shorts", "⚡"),
    CHANNELS("Channels", "▣"),
    PLAYLISTS("Playlists", "☰"),
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

    val currentPlaylist by viewModel.currentPlaylist.collectAsState()
    val playlistVideos by viewModel.playlistVideos.collectAsState()
    val channelPlaylists by viewModel.channelPlaylists.collectAsState()
    val isLoadingPlaylists by viewModel.isLoadingPlaylists.collectAsState()

    val customPlaylists by viewModel.customPlaylists.collectAsState()
    val videoToAddToPlaylist by viewModel.videoToAddToPlaylist.collectAsState()
    val activeVideoPlaylistIds by viewModel.activeVideoPlaylistIds.collectAsState()

    val videoSearchQuery by viewModel.videoSearchQuery.collectAsState()
    val isSearchingVideos by viewModel.isSearchingVideos.collectAsState()
    val videoSearchResults by viewModel.videoSearchResults.collectAsState()
    val isVideoSearchSubmitted by viewModel.isVideoSearchSubmitted.collectAsState()

    var selectedTab by rememberSaveable { mutableStateOf(MainTab.FEED.name) }
    var selectedVideo by remember { mutableStateOf<VideoEntity?>(null) }
    var selectedChannelId by remember { mutableStateOf<String?>(null) }

    // Pre-cue the first feed video so top video playback is instantaneous (< 50ms)
    LaunchedEffect(videos) {
        if (videos.isNotEmpty() && WarmPlayerManager.currentMainVideoId == null) {
            WarmPlayerManager.cueMainVideo(videos.first().videoId)
        }
    }

    if (selectedVideo != null) {
        PlayerScreen(
            video = selectedVideo!!,
            channels = channels,
            videos = videos,
            currentPlaylist = currentPlaylist,
            playlistVideos = playlistVideos,
            channelPlaylists = channelPlaylists,
            isLoadingPlaylists = isLoadingPlaylists,
            isLoadingMore = isLoadingMore,
            onLoadMore = { viewModel.loadMoreFeedVideos() },
            onVideoSelect = {
                WarmPlayerManager.playMainVideo(it.videoId)
                selectedVideo = it
            },
            onSelectPlaylist = { playlist -> viewModel.loadPlaylistVideos(playlist) },
            onClearPlaylist = { viewModel.clearCurrentPlaylist() },
            onSaveToPlaylist = { viewModel.openAddToPlaylistDialog(it) },
            onChannelClick = {
                selectedVideo = null
                selectedChannelId = it
            },
            onBack = {
                selectedVideo = null
                viewModel.clearCurrentPlaylist()
            }
        )
    } else if (selectedChannelId != null) {
        ChannelDetailScreen(
            channelId = selectedChannelId!!,
            onBack = { selectedChannelId = null },
            onVideoClick = { video, playlist, pVideos ->
                WarmPlayerManager.playMainVideo(video.videoId)
                viewModel.selectVideoWithPlaylist(video, playlist, pVideos)
                selectedVideo = video
            },
            onSaveToPlaylist = { viewModel.openAddToPlaylistDialog(it) }
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
                        onVideoClick = {
                            WarmPlayerManager.playMainVideo(it.videoId)
                            viewModel.selectVideoWithPlaylist(it)
                            selectedVideo = it
                        },
                        onChannelClick = { selectedChannelId = it },
                        onGoToChannels = { selectedTab = MainTab.CHANNELS.name },
                        searchQuery = videoSearchQuery,
                        isSearching = isSearchingVideos,
                        searchResults = videoSearchResults,
                        isSearchSubmitted = isVideoSearchSubmitted,
                        onSearchQueryChange = { viewModel.onVideoSearchQueryChange(it) },
                        onSearchSubmit = { viewModel.searchAnyVideo() },
                        onClearSearch = { viewModel.clearVideoSearch() },
                        onSaveToPlaylist = { viewModel.openAddToPlaylistDialog(it) }
                    )
                    MainTab.SHORTS -> ShortsScreen(
                        viewModel = viewModel,
                        onVideoClick = {
                            WarmPlayerManager.playMainVideo(it.videoId)
                            viewModel.selectVideoWithPlaylist(it)
                            selectedVideo = it
                        },
                        onChannelClick = { selectedChannelId = it },
                        onGoToChannels = { selectedTab = MainTab.CHANNELS.name }
                    )
                    MainTab.CHANNELS -> ChannelsScreen(
                        viewModel = viewModel,
                        onChannelClick = { selectedChannelId = it }
                    )
                    MainTab.PLAYLISTS -> PlaylistsScreen(
                        viewModel = viewModel,
                        onVideoClick = { video, playlist, pVideos ->
                            WarmPlayerManager.playMainVideo(video.videoId)
                            viewModel.selectVideoWithPlaylist(video, playlist, pVideos)
                            selectedVideo = video
                        }
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

    if (videoToAddToPlaylist != null) {
        SaveToPlaylistDialog(
            video = videoToAddToPlaylist!!,
            playlists = customPlaylists,
            activePlaylistIds = activeVideoPlaylistIds,
            onToggle = { plId -> viewModel.toggleVideoInCustomPlaylist(plId, videoToAddToPlaylist!!) },
            onCreatePlaylist = { title -> viewModel.createCustomPlaylist(title, videoToAddToPlaylist) },
            onDismiss = { viewModel.closeAddToPlaylistDialog() }
        )
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
    onGoToChannels: () -> Unit,
    searchQuery: String,
    isSearching: Boolean,
    searchResults: List<VideoEntity>,
    isSearchSubmitted: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onSearchSubmit: () -> Unit,
    onClearSearch: () -> Unit,
    onSaveToPlaylist: (VideoEntity) -> Unit = {}
) {
    if (isSearchSubmitted || searchQuery.isNotBlank()) {
        BackHandler {
            onClearSearch()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Pinned Search Bar for any video at the top of the feed page
        FeedVideoSearchBar(
            query = searchQuery,
            isSearching = isSearching,
            onQueryChange = onSearchQueryChange,
            onSearchSubmit = onSearchSubmit,
            onClear = onClearSearch
        )

        if (isSearchSubmitted) {
            // YouTube global search results mode
            if (isSearching && searchResults.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp
                        )
                        Text(
                            "Searching YouTube for \"$searchQuery\"...",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (searchResults.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "No videos found",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "No YouTube videos matched \"$searchQuery\". Try a different search term.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(onClick = onClearSearch) {
                            Text("← Back to Feed")
                        }
                    }
                }
            } else {
                val channelMap = remember(channels) { channels.associateBy { it.channelId } }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item(key = "search_results_header", contentType = "header") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "SEARCH RESULTS · ${searchResults.size} VIDEOS",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            TextButton(
                                onClick = onClearSearch,
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                            ) {
                                Text("Back to feed", fontSize = 12.sp)
                            }
                        }
                    }
                    items(
                        items = searchResults,
                        key = { it.videoId },
                        contentType = { "video_card" }
                    ) { video ->
                        val channel = channelMap[video.channelId]
                        VideoCard(
                            video = video,
                            channelAvatarUrl = channel?.thumbnailUrl,
                            onVideoClick = { onVideoClick(video) },
                            onChannelClick = onChannelClick,
                            onSaveToPlaylist = { onSaveToPlaylist(video) }
                        )
                    }
                }
            }
        } else if (searchQuery.isNotBlank()) {
            // Typing mode: Real-time instant feed filter + one-tap YouTube search banner
            val matchingFeedVideos = remember(videos, searchQuery) {
                val clean = searchQuery.trim().lowercase()
                videos.filter {
                    it.title.lowercase().contains(clean) || it.channelName.lowercase().contains(clean)
                }
            }
            val channelMap = remember(channels) { channels.associateBy { it.channelId } }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // One-tap shortcut card to search all of YouTube
                item(key = "search_all_youtube_banner", contentType = "banner") {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSearchSubmit() },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🔍", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Search YouTube for \"$searchQuery\"",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Tap to find any video across YouTube",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text("→", fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                if (matchingFeedVideos.isNotEmpty()) {
                    item(key = "matching_feed_header", contentType = "header") {
                        Text(
                            "FROM YOUR CHANNELS (${matchingFeedVideos.size})",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    items(
                        items = matchingFeedVideos,
                        key = { it.videoId },
                        contentType = { "video_card" }
                    ) { video ->
                        val channel = channelMap[video.channelId]
                        VideoCard(
                            video = video,
                            channelAvatarUrl = channel?.thumbnailUrl,
                            onVideoClick = { onVideoClick(video) },
                            onChannelClick = onChannelClick,
                            onSaveToPlaylist = { onSaveToPlaylist(video) }
                        )
                    }
                } else {
                    item(key = "no_matching_feed", contentType = "empty") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No videos in your feed match \"$searchQuery\".\nTap the button above to search all of YouTube.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        } else {
            // Normal feed mode
            if (channels.isEmpty()) {
                EmptyState(
                    title = "Your feed is empty",
                    description = "Add the YouTube channels you want to follow, or use the search bar above to watch any video.",
                    buttonText = "Add channels",
                    onButtonClick = onGoToChannels
                )
            } else if (videos.isEmpty()) {
                EmptyState(
                    title = "No videos loaded yet",
                    description = "Refresh your feed to load recent uploads, or search any video above.",
                    buttonText = "Go to My Channels",
                    onButtonClick = onGoToChannels
                )
            } else {
                val channelMap = remember(channels) { channels.associateBy { it.channelId } }
                val listState = rememberLazyListState()

                LaunchedEffect(listState) {
                    snapshotFlow {
                        val totalItems = listState.layoutInfo.totalItemsCount
                        val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                        totalItems > 0 && lastVisibleIndex >= totalItems - 4
                    }.distinctUntilChanged().collect { shouldLoad ->
                        if (shouldLoad && !isLoadingMore) {
                            onLoadMore()
                        }
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item(key = "feed_header", contentType = "header") {
                        Text(
                            "YOUR FEED · ${channels.size} CHANNEL(S) · ${videos.size} VIDEOS",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    items(
                        items = videos,
                        key = { it.videoId },
                        contentType = { "video_card" }
                    ) { video ->
                        val channel = channelMap[video.channelId]
                        VideoCard(
                            video = video,
                            channelAvatarUrl = channel?.thumbnailUrl,
                            onVideoClick = { onVideoClick(video) },
                            onChannelClick = onChannelClick,
                            onSaveToPlaylist = { onSaveToPlaylist(video) }
                        )
                    }
                    if (isLoadingMore) {
                        item(key = "loading_more_feed_indicator", contentType = "loader") {
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
    }
}

@Composable
private fun FeedVideoSearchBar(
    query: String,
    isSearching: Boolean,
    onQueryChange: (String) -> Unit,
    onSearchSubmit: () -> Unit,
    onClear: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    "Search any video...",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            leadingIcon = {
                Text(
                    "🔍",
                    fontSize = 14.sp,
                    modifier = Modifier.padding(start = 6.dp)
                )
            },
            trailingIcon = {
                if (isSearching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else if (query.isNotBlank()) {
                    Text(
                        text = "✕",
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clickable { onClear() }
                            .padding(8.dp)
                    )
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearchSubmit() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}

@Composable
fun VideoCard(
    video: VideoEntity,
    channelAvatarUrl: String? = null,
    onVideoClick: () -> Unit,
    onChannelClick: (String) -> Unit,
    onSaveToPlaylist: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onVideoClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(195.dp)
            ) {
                if (video.thumbnailUrl.isNotBlank()) {
                    AsyncImage(
                        model = video.thumbnailUrl,
                        contentDescription = "Thumbnail for ${video.title}",
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No thumbnail", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                if (video.duration.isNotBlank()) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 8.dp, bottom = 8.dp)
                    ) {
                        Text(
                            text = video.duration,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
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

                    if (onSaveToPlaylist != null) {
                        Surface(
                            onClick = onSaveToPlaylist,
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("➕", fontSize = 11.sp)
                                Text(
                                    text = "Save",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
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
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    viewModel.onChannelSearchQueryChange(it)
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        "Search channel name, URL, or @handle...",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                leadingIcon = {
                    Text(
                        "🔍",
                        fontSize = 14.sp,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                },
                trailingIcon = {
                    if (isSearching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else if (query.isNotBlank()) {
                        Text(
                            text = "✕",
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clickable {
                                    query = ""
                                    viewModel.onChannelSearchQueryChange("")
                                }
                                .padding(8.dp)
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.searchChannels(query) }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
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
    currentPlaylist: PlaylistEntity? = null,
    playlistVideos: List<VideoEntity> = emptyList(),
    channelPlaylists: List<PlaylistEntity> = emptyList(),
    isLoadingPlaylists: Boolean = false,
    isLoadingMore: Boolean = false,
    onLoadMore: () -> Unit = {},
    onVideoSelect: (VideoEntity) -> Unit = {},
    onSelectPlaylist: (PlaylistEntity) -> Unit = {},
    onClearPlaylist: () -> Unit = {},
    onSaveToPlaylist: (VideoEntity) -> Unit = {},
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
    val overlayAlpha by animateFloatAsState(
        targetValue = if (loadState == PlayerLoadState.LOADING) 1f else 0f,
        animationSpec = tween(durationMillis = 150),
        label = "player_thumbnail_alpha"
    )
    var isFullscreen by remember { mutableStateOf(false) }

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
                    // Base: Warm YouTube player
                    YouTubeEmbeddedPlayer(
                        videoId = video.videoId,
                        onPlayerReady = { player ->
                            player.unMute()
                            player.setVolume(100)
                            player.play()
                        },
                        onCurrentSecond = { sec ->
                            if (sec > 0f && loadState == PlayerLoadState.LOADING) {
                                loadState = PlayerLoadState.PLAYING
                            }
                        },
                        onVideoDuration = { },
                        onStateChange = { state ->
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

                    // Overlay: YouTube-style instant high-res thumbnail cover
                    // Stays seamlessly visible until the video actually plays, preventing black flicker!
                    if (overlayAlpha > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = overlayAlpha }
                        ) {
                            if (video.thumbnailUrl.isNotBlank()) {
                                AsyncImage(
                                    model = video.thumbnailUrl,
                                    contentDescription = "Video thumbnail",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black)
                                )
                            }
                            // YouTube-style red loading bar at bottom edge
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .align(Alignment.BottomCenter),
                                color = Color(0xFFFF0000),
                                trackColor = Color.Transparent
                            )
                        }
                    }
                }
            }
        }

        // ── 2. SCROLLABLE VIDEO FEED OR PLAYLIST QUEUE BELOW THE FIXED PLAYER ──
        val feedVideos = remember(videos, video.videoId) {
            videos.filter { it.videoId != video.videoId }
        }
        val channelMap = remember(channels) {
            channels.associateBy { it.channelId }
        }
        val listState = rememberLazyListState()

        // Scroll back to top when a new video is loaded so its details are visible
        LaunchedEffect(video.videoId) {
            listState.scrollToItem(0)
        }

        LaunchedEffect(listState) {
            snapshotFlow {
                val totalItems = listState.layoutInfo.totalItemsCount
                val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                totalItems > 0 && lastVisibleIndex >= totalItems - 4
            }.distinctUntilChanged().collect { shouldLoad ->
                if (shouldLoad && !isLoadingMore && currentPlaylist == null) {
                    onLoadMore()
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ── VIDEO DETAILS (Title, Channel, Description, Save, Playlist chips) ──
            // Scrolls up seamlessly with the feed videos below the fixed 16:9 player, like real YouTube
            item(key = "playing_video_details_${video.videoId}", contentType = "video_details") {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = video.title,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(6.dp))
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
                                        .size(32.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Surface(
                                    modifier = Modifier.size(32.dp),
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = video.channelName.take(1).uppercase(),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = video.channelName,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
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

                        // Video Description
                        var isDescriptionExpanded by remember(video.videoId) { mutableStateOf(false) }
                        val descriptionText = video.description.trim()
                        if (descriptionText.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { isDescriptionExpanded = !isDescriptionExpanded },
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Description",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = if (isDescriptionExpanded) "Show less ▲" else "...more ▼",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = descriptionText,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 15.sp,
                                        maxLines = if (isDescriptionExpanded) Int.MAX_VALUE else 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = if (isDescriptionExpanded) {
                                            Modifier
                                                .fillMaxWidth()
                                                .heightIn(max = 140.dp)
                                                .verticalScroll(rememberScrollState())
                                        } else {
                                            Modifier.fillMaxWidth()
                                        }
                                    )
                                }
                            }
                        }

                        // Save to Playlist Action Button
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                onClick = { onSaveToPlaylist(video) },
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.clip(RoundedCornerShape(20.dp))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text("➕", fontSize = 12.sp)
                                    Text(
                                        text = "Save to Playlist",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        // Channel Playlists Chips Selector
                        if (channelPlaylists.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "PLAYLISTS:",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    item {
                                        FilterChip(
                                            selected = currentPlaylist == null,
                                            onClick = onClearPlaylist,
                                            label = { Text("Feed Videos", fontSize = 11.sp) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                            )
                                        )
                                    }
                                    items(channelPlaylists, key = { it.playlistId }) { pl ->
                                        FilterChip(
                                            selected = currentPlaylist?.playlistId == pl.playlistId,
                                            onClick = { onSelectPlaylist(pl) },
                                            label = {
                                                val count = if (pl.itemCount > 0) " (${pl.itemCount})" else ""
                                                Text(
                                                    text = "${pl.title.take(18)}$count",
                                                    fontSize = 11.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                            )
                                        )
                                    }
                                }
                            }
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
            if (currentPlaylist != null) {
                // Active Playlist Queue Banner
                item(key = "playlist_banner", contentType = "playlist_banner") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "📋 PLAYLIST: ${currentPlaylist.title}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val currentIndex = playlistVideos.indexOfFirst { it.videoId == video.videoId }
                                val indexText = if (currentIndex >= 0) "Video ${currentIndex + 1} of ${playlistVideos.size}" else "${playlistVideos.size} videos"
                                Text(
                                    text = "${currentPlaylist.channelTitle} • $indexText",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(
                                onClick = onClearPlaylist,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Exit Queue", fontSize = 11.sp)
                            }
                        }
                    }
                }

                if (isLoadingPlaylists && playlistVideos.isEmpty()) {
                    item(key = "playlist_loading", contentType = "loader") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
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
                                    "Loading playlist videos...",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Sequential playlist items
                itemsIndexed(
                    items = playlistVideos,
                    key = { idx, item -> "pl_${item.videoId}_$idx" },
                    contentType = { _, _ -> "playlist_item" }
                ) { idx, plVideo ->
                    val isNowPlaying = plVideo.videoId == video.videoId
                    PlaylistItemCard(
                        index = idx + 1,
                        video = plVideo,
                        isPlaying = isNowPlaying,
                        onClick = { onVideoSelect(plVideo) },
                        onSaveToPlaylist = { onSaveToPlaylist(plVideo) }
                    )
                }

                // Section divider to feed
                item(contentType = "more_divider") {
                    Spacer(modifier = Modifier.height(6.dp))
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

                items(
                    items = feedVideos,
                    key = { "feed_${it.videoId}" },
                    contentType = { "video_card" }
                ) { feedVideo ->
                    val channel = channelMap[feedVideo.channelId]
                    VideoCard(
                        video = feedVideo,
                        channelAvatarUrl = channel?.thumbnailUrl,
                        onVideoClick = { onVideoSelect(feedVideo) },
                        onChannelClick = onChannelClick,
                        onSaveToPlaylist = { onSaveToPlaylist(feedVideo) }
                    )
                }
            } else {
                // Normal Feed Videos List
                item(contentType = "more_header") {
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

                items(
                    items = feedVideos,
                    key = { it.videoId },
                    contentType = { "video_card" }
                ) { feedVideo ->
                    val channel = channelMap[feedVideo.channelId]
                    VideoCard(
                        video = feedVideo,
                        channelAvatarUrl = channel?.thumbnailUrl,
                        onVideoClick = { onVideoSelect(feedVideo) },
                        onChannelClick = onChannelClick,
                        onSaveToPlaylist = { onSaveToPlaylist(feedVideo) }
                    )
                }

                if (isLoadingMore) {
                    item(key = "loading_more_player_feed_indicator", contentType = "loader") {
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
}

@Composable
private fun PlaylistItemCard(
    index: Int,
    video: VideoEntity,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onSaveToPlaylist: (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        color = if (isPlaying) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        border = if (isPlaying) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.width(28.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isPlaying) {
                    Text("▶", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                } else {
                    Text(
                        text = "$index",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            Box(
                modifier = Modifier
                    .width(112.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF22242A))
            ) {
                if (video.thumbnailUrl.isNotBlank()) {
                    AsyncImage(
                        model = video.thumbnailUrl,
                        contentDescription = "Thumbnail",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                if (video.duration.isNotBlank()) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(3.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 4.dp, bottom = 4.dp)
                    ) {
                        Text(
                            text = video.duration,
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                if (isPlaying) {
                    Text(
                        text = "▶ NOW PLAYING",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }
                Text(
                    text = video.title,
                    fontSize = 12.sp,
                    fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = video.channelName,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (onSaveToPlaylist != null) {
                Surface(
                    onClick = onSaveToPlaylist,
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(start = 4.dp)
                ) {
                    Text(
                        text = "➕",
                        fontSize = 12.sp,
                        modifier = Modifier.padding(6.dp)
                    )
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

    // Instant video load and playback coordinated via WarmPlayerManager
    LaunchedEffect(safeVideoId) {
        if (safeVideoId.isNotBlank()) {
            if (isForShorts) {
                WarmPlayerManager.playShortsVideo(safeVideoId)
            } else {
                WarmPlayerManager.playMainVideo(safeVideoId)
            }
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
            // Playback state is coordinated via WarmPlayerManager.playMainVideo / playShortsVideo
        }
    )
}
