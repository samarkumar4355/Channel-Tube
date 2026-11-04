package com.example.channeltube.ui

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.channeltube.data.local.VideoEntity
import com.example.channeltube.data.remote.ChannelProfile
import com.example.channeltube.viewmodel.ChannelDetailViewModel
import com.example.channeltube.viewmodel.ChannelDetailViewModelFactory
import com.example.channeltube.viewmodel.ChannelTab
import java.text.NumberFormat
import java.util.Locale

@Composable
fun ChannelDetailScreen(
    channelId: String,
    onBack: () -> Unit,
    onVideoClick: (VideoEntity) -> Unit
) {
    val context = LocalContext.current
    val viewModel: ChannelDetailViewModel = viewModel(
        key = channelId,
        factory = ChannelDetailViewModelFactory(context.applicationContext as Application, channelId)
    )
    val uiState by viewModel.uiState.collectAsState()

    BackHandler(onBack = onBack)

    val listState = rememberLazyListState()

    // Detect when user scrolls near the end to trigger automatic pagination
    LaunchedEffect(listState) {
        snapshotFlow {
            val total = listState.layoutInfo.totalItemsCount
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && lastVisible >= total - 4
        }.distinctUntilChanged().collect { shouldLoadMore ->
            if (shouldLoadMore && uiState.hasNextPage && !uiState.isLoadingMore && !uiState.isLoading) {
                viewModel.loadNextPage()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Navigation Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text("← Back to feed", fontSize = 14.sp)
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header: Channel Profile Info
            item(key = "header") {
                ChannelHeader(
                    profile = uiState.profile,
                    channelId = channelId
                )
            }

            // Search Bar for channel-specific videos
            item(key = "search_bar") {
                ChannelVideoSearchBar(
                    query = uiState.searchQuery,
                    isSearching = uiState.isSearching,
                    onQueryChange = { viewModel.onSearchQueryChange(it) },
                    onSearchSubmit = { viewModel.submitSearch() },
                    onClear = { viewModel.clearSearch() }
                )
            }

            // Section Title & Tabs
            item(key = "section_title") {
                if (uiState.isSearchMode) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val count = uiState.displayedVideos.size
                        Text(
                            text = "SEARCH RESULTS ($count)",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        TextButton(
                            onClick = { viewModel.clearSearch() },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                        ) {
                            Text("Show all uploads", fontSize = 12.sp)
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = uiState.selectedTab == ChannelTab.VIDEOS,
                            onClick = { viewModel.selectTab(ChannelTab.VIDEOS) },
                            label = { Text("Videos (${uiState.uploadsVideos.size})") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                        FilterChip(
                            selected = uiState.selectedTab == ChannelTab.SHORTS,
                            onClick = { viewModel.selectTab(ChannelTab.SHORTS) },
                            label = {
                                val countText = if (uiState.shortsVideos.isNotEmpty()) " (${uiState.shortsVideos.size})" else ""
                                Text("⚡ Shorts / Clips$countText")
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }
            }

            // Initial Loading Indicator
            if (uiState.isLoading && uiState.displayedVideos.isEmpty()) {
                item(key = "loading_state") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp),
                            strokeWidth = 3.dp
                        )
                    }
                }
            }

            // Searching indicator
            if (uiState.isSearching && uiState.displayedVideos.isEmpty()) {
                item(key = "searching_state") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp),
                                strokeWidth = 3.dp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "Searching channel uploads...",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // Error State
            if (uiState.errorMessage != null && uiState.displayedVideos.isEmpty()) {
                item(key = "error_state") {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF2C1B1B)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Could not load channel videos",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF8A80)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                uiState.errorMessage.orEmpty(),
                                color = Color(0xFFCFCFCF),
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(onClick = { viewModel.retry() }) {
                                Text("Retry")
                            }
                        }
                    }
                }
            }

            // Search Empty / Error State
            if (uiState.isSearchMode && !uiState.isSearching && uiState.displayedVideos.isEmpty()) {
                item(key = "search_empty_state") {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "No matching videos",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "No videos matching \"${uiState.searchQuery}\" were found on this channel.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedButton(onClick = { viewModel.clearSearch() }) {
                                Text("Clear search")
                            }
                        }
                    }
                }
            }

            // Shorts Loading / Empty State
            if (uiState.selectedTab == ChannelTab.SHORTS && !uiState.isSearchMode) {
                if (uiState.isLoadingShorts && uiState.shortsVideos.isEmpty()) {
                    item(key = "shorts_loading") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 36.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp),
                                    strokeWidth = 3.dp
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    "Loading channel Shorts...",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                } else if (!uiState.isLoadingShorts && uiState.shortsVideos.isEmpty()) {
                    item(key = "shorts_empty") {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("No Shorts found", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    "This channel doesn't appear to have recent Shorts or short clips.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }

            // Videos / Shorts List
            if (uiState.selectedTab == ChannelTab.SHORTS && !uiState.isSearchMode) {
                val chunkedShorts = uiState.shortsVideos.chunked(2)
                items(
                    items = chunkedShorts,
                    key = { chunk -> "shorts_row_" + chunk.first().videoId },
                    contentType = { "shorts_row" }
                ) { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        row.forEach { shortVideo ->
                            Box(modifier = Modifier.weight(1f)) {
                                ShortCard(
                                    video = shortVideo,
                                    channelAvatarUrl = uiState.profile?.thumbnailUrl,
                                    onVideoClick = { onVideoClick(shortVideo) },
                                    onChannelClick = { /* Already on this channel */ }
                                )
                            }
                        }
                        if (row.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            } else {
                items(
                    items = uiState.displayedVideos,
                    key = { it.videoId },
                    contentType = { "video_card" }
                ) { video ->
                    VideoCard(
                        video = video,
                        channelAvatarUrl = uiState.profile?.thumbnailUrl,
                        onVideoClick = { onVideoClick(video) },
                        onChannelClick = { /* Already on this channel's screen */ }
                    )
                }
            }

            // Pagination Loading Indicator
            if (uiState.isLoadingMore) {
                item(key = "pagination_indicator") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelHeader(
    profile: ChannelProfile?,
    channelId: String
) {
    var isDescriptionExpanded by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!profile?.thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = profile?.thumbnailUrl,
                        contentDescription = "Channel avatar",
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Surface(
                        modifier = Modifier.size(68.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("YT", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profile?.channelName ?: "YouTube Channel",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    val customUrl = profile?.customUrl
                    if (!customUrl.isNullOrBlank()) {
                        Text(
                            text = customUrl,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    } else {
                        Text(
                            text = channelId,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    val stats = buildList {
                        profile?.subscriberCount?.let { add(formatSubscriberCount(it)) }
                        profile?.videoCount?.let { add(formatVideoCount(it)) }
                    }.joinToString(" • ")

                    if (stats.isNotBlank()) {
                        Text(
                            text = stats,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            val description = profile?.description?.trim().orEmpty()
            if (description.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    maxLines = if (isDescriptionExpanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { isDescriptionExpanded = !isDescriptionExpanded }
                )
                Text(
                    text = if (isDescriptionExpanded) "Show less" else "...more",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .clickable { isDescriptionExpanded = !isDescriptionExpanded }
                )
            }
        }
    }
}

@Composable
private fun ChannelVideoSearchBar(
    query: String,
    isSearching: Boolean,
    onQueryChange: (String) -> Unit,
    onSearchSubmit: () -> Unit,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            label = { Text("Search this channel's videos") },
            placeholder = { Text("e.g. Python, Tutorial, Review") },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearchSubmit() }),
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
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clickable { onClear() }
                            .padding(8.dp)
                    )
                }
            }
        )

        Spacer(modifier = Modifier.width(8.dp))

        Button(
            onClick = onSearchSubmit,
            enabled = query.isNotBlank() && !isSearching,
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("Search")
        }
    }
}

private fun formatSubscriberCount(count: Long): String {
    return when {
        count >= 1_000_000_000 -> String.format(Locale.US, "%.1fB", count / 1_000_000_000.0).replace(".0B", "B") + " subscribers"
        count >= 1_000_000 -> String.format(Locale.US, "%.1fM", count / 1_000_000.0).replace(".0M", "M") + " subscribers"
        count >= 1_000 -> String.format(Locale.US, "%.1fK", count / 1_000.0).replace(".0K", "K") + " subscribers"
        else -> "$count subscribers"
    }
}

private fun formatVideoCount(count: Long): String {
    return NumberFormat.getNumberInstance(Locale.US).format(count) + " videos"
}

