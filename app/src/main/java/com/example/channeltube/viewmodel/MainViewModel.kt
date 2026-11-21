package com.example.channeltube.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.example.channeltube.BuildConfig
import com.example.channeltube.data.local.AppDatabase
import com.example.channeltube.data.local.ChannelEntity
import com.example.channeltube.data.local.CustomPlaylistEntity
import com.example.channeltube.data.local.CustomPlaylistWithDetails
import com.example.channeltube.data.local.VideoEntity
import com.example.channeltube.data.remote.ChannelSearchResult
import com.example.channeltube.data.remote.PlaylistEntity
import com.example.channeltube.data.remote.YouTubeApi
import com.example.channeltube.data.repository.ChannelRepository
import com.example.channeltube.domain.FilterVideosUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val database = Room.databaseBuilder(
        application,
        AppDatabase::class.java,
        "channel_tube.db"
    ).addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3).fallbackToDestructiveMigration().build()

    private val repository = ChannelRepository(
        api = YouTubeApi(BuildConfig.YOUTUBE_API_KEY),
        dao = database.appDao()
    )
    private val filterVideos = FilterVideosUseCase()

    val channels: StateFlow<List<ChannelEntity>> = repository.observeChannels()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val videos: StateFlow<List<VideoEntity>> = combine(
        repository.observeVideos(),
        repository.observeChannels()
    ) { allVideos, savedChannels ->
        val selectedIds = savedChannels.map { it.channelId }.toSet()
        filterVideos(allVideos, selectedIds)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _shorts = MutableStateFlow<List<VideoEntity>>(emptyList())
    val shorts: StateFlow<List<VideoEntity>> = _shorts

    private val _isRefreshingShorts = MutableStateFlow(false)
    val isRefreshingShorts: StateFlow<Boolean> = _isRefreshingShorts

    private val _searchResults = MutableStateFlow<List<ChannelSearchResult>>(emptyList())
    val searchResults: StateFlow<List<ChannelSearchResult>> = _searchResults

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore

    private val _workingChannelId = MutableStateFlow<String?>(null)
    val workingChannelId: StateFlow<String?> = _workingChannelId

    private val _videoSearchQuery = MutableStateFlow("")
    val videoSearchQuery: StateFlow<String> = _videoSearchQuery

    private val _isSearchingVideos = MutableStateFlow(false)
    val isSearchingVideos: StateFlow<Boolean> = _isSearchingVideos

    private val _videoSearchResults = MutableStateFlow<List<VideoEntity>>(emptyList())
    val videoSearchResults: StateFlow<List<VideoEntity>> = _videoSearchResults

    private val _isVideoSearchSubmitted = MutableStateFlow(false)
    val isVideoSearchSubmitted: StateFlow<Boolean> = _isVideoSearchSubmitted

    private var videoSearchJob: Job? = null
    private var channelSearchJob: Job? = null

    private val _currentPlaylist = MutableStateFlow<PlaylistEntity?>(null)
    val currentPlaylist: StateFlow<PlaylistEntity?> = _currentPlaylist

    private val _playlistVideos = MutableStateFlow<List<VideoEntity>>(emptyList())
    val playlistVideos: StateFlow<List<VideoEntity>> = _playlistVideos

    private val _channelPlaylists = MutableStateFlow<List<PlaylistEntity>>(emptyList())
    val channelPlaylists: StateFlow<List<PlaylistEntity>> = _channelPlaylists

    private val _isLoadingPlaylists = MutableStateFlow(false)
    val isLoadingPlaylists: StateFlow<Boolean> = _isLoadingPlaylists

    // Custom User Playlists
    private val _customPlaylists = MutableStateFlow<List<CustomPlaylistWithDetails>>(emptyList())
    val customPlaylists: StateFlow<List<CustomPlaylistWithDetails>> = _customPlaylists.asStateFlow()

    private val _selectedCustomPlaylist = MutableStateFlow<CustomPlaylistEntity?>(null)
    val selectedCustomPlaylist: StateFlow<CustomPlaylistEntity?> = _selectedCustomPlaylist.asStateFlow()

    private val _selectedCustomPlaylistVideos = MutableStateFlow<List<VideoEntity>>(emptyList())
    val selectedCustomPlaylistVideos: StateFlow<List<VideoEntity>> = _selectedCustomPlaylistVideos.asStateFlow()

    private val _videoToAddToPlaylist = MutableStateFlow<VideoEntity?>(null)
    val videoToAddToPlaylist: StateFlow<VideoEntity?> = _videoToAddToPlaylist.asStateFlow()

    private val _activeVideoPlaylistIds = MutableStateFlow<Set<String>>(emptySet())
    val activeVideoPlaylistIds: StateFlow<Set<String>> = _activeVideoPlaylistIds.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    init {
        refreshFeed(showMessage = false)
        refreshShorts(showMessage = false)
        loadCustomPlaylists()
        viewModelScope.launch {
            repository.backfillMissingDurations()
        }
    }

    fun loadMoreFeedVideos() {
        if (_isLoadingMore.value || _isRefreshing.value) return
        val currentChannels = channels.value
        if (currentChannels.isEmpty()) return

        viewModelScope.launch {
            _isLoadingMore.value = true
            try {
                repository.loadMoreVideosForFeed(currentChannels)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    fun onChannelSearchQueryChange(query: String) {
        channelSearchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            _searchResults.value = emptyList()
            _isSearching.value = false
            return
        }

        channelSearchJob = viewModelScope.launch {
            delay(500)
            searchChannelsInternal(trimmed)
        }
    }

    fun searchChannels(query: String) {
        channelSearchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            _searchResults.value = emptyList()
            _isSearching.value = false
            return
        }

        channelSearchJob = viewModelScope.launch {
            searchChannelsInternal(trimmed)
        }
    }

    private suspend fun searchChannelsInternal(query: String) {
        _isSearching.value = true
        _message.value = null
        try {
            _searchResults.value = repository.searchChannels(query)
            if (_searchResults.value.isEmpty()) {
                _message.value = "No channels found. Try a different name or channel URL."
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            _message.value = error.message ?: "Could not search channels."
        } finally {
            _isSearching.value = false
        }
    }

    fun onVideoSearchQueryChange(query: String) {
        _videoSearchQuery.value = query
        videoSearchJob?.cancel()

        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            _videoSearchResults.value = emptyList()
            _isVideoSearchSubmitted.value = false
            _isSearchingVideos.value = false
            return
        }

        videoSearchJob = viewModelScope.launch {
            delay(500)
            searchAnyVideoInternal(trimmed)
        }
    }

    fun searchAnyVideo(query: String? = null) {
        val q = (query ?: _videoSearchQuery.value).trim()
        videoSearchJob?.cancel()
        if (q.isBlank()) {
            _videoSearchResults.value = emptyList()
            _isVideoSearchSubmitted.value = false
            _isSearchingVideos.value = false
            return
        }

        _videoSearchQuery.value = q
        videoSearchJob = viewModelScope.launch {
            searchAnyVideoInternal(q)
        }
    }

    private suspend fun searchAnyVideoInternal(q: String) {
        _isSearchingVideos.value = true
        _isVideoSearchSubmitted.value = true
        _message.value = null
        try {
            val results = repository.searchVideos(q, maxResults = 25)
            _videoSearchResults.value = results
            if (results.isEmpty()) {
                _message.value = "No videos found for \"$q\"."
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            _message.value = error.message ?: "Could not search videos."
        } finally {
            _isSearchingVideos.value = false
        }
    }

    fun clearVideoSearch() {
        videoSearchJob?.cancel()
        _videoSearchQuery.value = ""
        _videoSearchResults.value = emptyList()
        _isVideoSearchSubmitted.value = false
        _isSearchingVideos.value = false
    }

    fun selectVideoWithPlaylist(
        video: VideoEntity,
        playlist: PlaylistEntity? = null,
        videos: List<VideoEntity> = emptyList()
    ) {
        _currentPlaylist.value = playlist
        _playlistVideos.value = videos
        if (playlist == null && videos.isEmpty()) {
            loadPlaylistsForChannel(video.channelId, currentVideo = video)
        }
    }

    fun loadPlaylistsForChannel(channelId: String, currentVideo: VideoEntity? = null) {
        if (channelId.isBlank()) return
        viewModelScope.launch {
            _isLoadingPlaylists.value = true
            try {
                val playlists = repository.getChannelPlaylists(channelId)
                _channelPlaylists.value = playlists

                // If no playlist is explicitly set, attempt smart matching or load the top playlist
                if (_currentPlaylist.value == null && playlists.isNotEmpty() && currentVideo != null) {
                    val vTitle = currentVideo.title.lowercase()
                    val matching = playlists.find { pl ->
                        val pTitle = pl.title.lowercase()
                        vTitle.contains(pTitle) || pTitle.contains(vTitle.take(20)) ||
                            (vTitle.contains("unit") && pTitle.contains("unit")) ||
                            (vTitle.contains("one shot") && pTitle.contains("one shot"))
                    } ?: playlists.firstOrNull()

                    if (matching != null) {
                        loadPlaylistVideos(matching)
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            } finally {
                _isLoadingPlaylists.value = false
            }
        }
    }

    fun loadPlaylistVideos(playlist: PlaylistEntity) {
        _currentPlaylist.value = playlist
        viewModelScope.launch {
            _isLoadingPlaylists.value = true
            try {
                val videos = repository.getPlaylistVideos(
                    playlistId = playlist.playlistId,
                    channelName = playlist.channelTitle,
                    channelId = playlist.channelId
                )
                _playlistVideos.value = videos
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            } finally {
                _isLoadingPlaylists.value = false
            }
        }
    }

    fun clearCurrentPlaylist() {
        _currentPlaylist.value = null
        _playlistVideos.value = emptyList()
        _channelPlaylists.value = emptyList()
    }

    // ── Custom User Playlists ───────────────────────────────────────────────────

    fun loadCustomPlaylists() {
        viewModelScope.launch {
            try {
                // Ensure default watch later exists
                database.appDao().insertCustomPlaylist(
                    CustomPlaylistEntity(
                        playlistId = "watch_later",
                        title = "Watch Later"
                    )
                )
                val list = repository.getCustomPlaylistsWithDetails()
                _customPlaylists.value = list
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    fun openAddToPlaylistDialog(video: VideoEntity) {
        _videoToAddToPlaylist.value = video
        viewModelScope.launch {
            try {
                val containing = repository.getPlaylistsForVideo(video.videoId).toSet()
                _activeVideoPlaylistIds.value = containing
                loadCustomPlaylists()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    fun closeAddToPlaylistDialog() {
        _videoToAddToPlaylist.value = null
        _activeVideoPlaylistIds.value = emptySet()
    }

    fun toggleVideoInCustomPlaylist(playlistId: String, video: VideoEntity) {
        viewModelScope.launch {
            try {
                val added = repository.toggleVideoInPlaylist(playlistId, video)
                val current = _activeVideoPlaylistIds.value.toMutableSet()
                val plTitle = _customPlaylists.value.find { it.playlist.playlistId == playlistId }?.playlist?.title ?: "Playlist"
                if (added) {
                    current.add(playlistId)
                    _message.value = "Added to $plTitle"
                } else {
                    current.remove(playlistId)
                    _message.value = "Removed from $plTitle"
                }
                _activeVideoPlaylistIds.value = current
                loadCustomPlaylists()

                // If currently viewing this custom playlist, refresh its videos
                if (_selectedCustomPlaylist.value?.playlistId == playlistId) {
                    loadCustomPlaylistVideos(playlistId)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _message.value = e.message ?: "Could not update playlist"
            }
        }
    }

    fun createCustomPlaylist(title: String, initialVideo: VideoEntity? = null) {
        val cleanTitle = title.trim()
        if (cleanTitle.isBlank()) return
        viewModelScope.launch {
            try {
                val pl = repository.createCustomPlaylist(cleanTitle, initialVideo)
                _message.value = if (initialVideo != null) "Created playlist '${pl.title}' and added video" else "Created playlist '${pl.title}'"
                if (initialVideo != null) {
                    val current = _activeVideoPlaylistIds.value.toMutableSet()
                    current.add(pl.playlistId)
                    _activeVideoPlaylistIds.value = current
                }
                loadCustomPlaylists()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _message.value = e.message ?: "Could not create playlist"
            }
        }
    }

    fun deleteCustomPlaylist(playlistId: String) {
        viewModelScope.launch {
            try {
                val plTitle = _customPlaylists.value.find { it.playlist.playlistId == playlistId }?.playlist?.title ?: "Playlist"
                repository.deleteCustomPlaylist(playlistId)
                if (_selectedCustomPlaylist.value?.playlistId == playlistId) {
                    _selectedCustomPlaylist.value = null
                    _selectedCustomPlaylistVideos.value = emptyList()
                }
                _message.value = "Deleted '$plTitle'"
                loadCustomPlaylists()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    fun openCustomPlaylist(playlist: CustomPlaylistEntity) {
        _selectedCustomPlaylist.value = playlist
        loadCustomPlaylistVideos(playlist.playlistId)
    }

    fun closeCustomPlaylist() {
        _selectedCustomPlaylist.value = null
        _selectedCustomPlaylistVideos.value = emptyList()
    }

    private fun loadCustomPlaylistVideos(playlistId: String) {
        viewModelScope.launch {
            try {
                val videos = repository.getCustomPlaylistVideos(playlistId)
                _selectedCustomPlaylistVideos.value = videos
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    fun removeVideoFromCustomPlaylist(playlistId: String, videoId: String) {
        viewModelScope.launch {
            try {
                repository.removeVideoFromPlaylist(playlistId, videoId)
                loadCustomPlaylistVideos(playlistId)
                loadCustomPlaylists()
                _message.value = "Video removed from playlist"
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    fun addChannel(result: ChannelSearchResult) {
        viewModelScope.launch {
            _workingChannelId.value = result.channelId
            _message.value = null
            try {
                repository.addChannel(result)
                _searchResults.value = _searchResults.value.filterNot {
                    it.channelId == result.channelId
                }
                _message.value = "${result.channelName} added to My Channels."
                refreshFeed(showMessage = false)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _message.value = error.message ?: "Could not add this channel."
            } finally {
                _workingChannelId.value = null
            }
        }
    }

    fun removeChannel(channel: ChannelEntity) {
        viewModelScope.launch {
            try {
                repository.removeChannel(channel.channelId)
                _message.value = "${channel.channelName} removed. Its videos are hidden from the feed."
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _message.value = error.message ?: "Could not remove this channel."
            }
        }
    }

    fun refreshFeed(showMessage: Boolean = true) {
        if (_isRefreshing.value) return

        viewModelScope.launch {
            _isRefreshing.value = true
            var updatedChannels = 0
            var updatedVideos = 0
            val errors = mutableListOf<String>()

            try {
                val savedChannels = repository.getChannels()
                if (savedChannels.isEmpty()) {
                    if (showMessage) _message.value = "Add one or more channels to build your feed."
                    return@launch
                }

                // Query all channels concurrently in parallel with minimum network latency
                val results = coroutineScope {
                    savedChannels.map { channel ->
                        async {
                            try {
                                val count = repository.refreshChannel(channel)
                                Pair(channel.channelName, Result.success(count))
                            } catch (error: Exception) {
                                if (error is CancellationException) throw error
                                Pair(channel.channelName, Result.failure<Int>(error))
                            }
                        }
                    }.awaitAll()
                }

                for ((channelName, result) in results) {
                    result.fold(
                        onSuccess = { count ->
                            updatedVideos += count
                            updatedChannels++
                        },
                        onFailure = {
                            errors.add(channelName)
                        }
                    )
                }

                _message.value = when {
                    errors.isNotEmpty() && updatedChannels == 0 ->
                        "Could not refresh: ${errors.joinToString()}"
                    errors.isNotEmpty() ->
                        "Updated $updatedChannels channel(s). Could not refresh: ${errors.joinToString()}"
                    showMessage -> "Feed updated: $updatedVideos video(s) from $updatedChannels channel(s)."
                    else -> null
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _message.value = error.message ?: "Could not refresh the feed."
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun refreshShorts(showMessage: Boolean = false) {
        if (_isRefreshingShorts.value) return
        viewModelScope.launch {
            _isRefreshingShorts.value = true
            try {
                val savedChannels = repository.getChannels()
                if (savedChannels.isEmpty()) {
                    _shorts.value = emptyList()
                    return@launch
                }

                // Query all channel shorts in parallel
                val results = coroutineScope {
                    savedChannels.map { channel ->
                        async {
                            try {
                                repository.getChannelShorts(channel.channelId, limit = 10)
                            } catch (_: Exception) {
                                emptyList()
                            }
                        }
                    }.awaitAll()
                }

                _shorts.value = results.flatten()
                    .distinctBy { it.videoId }
                    .sortedByDescending { it.publishedAt }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (showMessage) _message.value = error.message ?: "Could not load shorts."
            } finally {
                _isRefreshingShorts.value = false
            }
        }
    }

    fun dismissMessage() {
        _message.value = null
    }
}
