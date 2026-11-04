package com.example.channeltube.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.example.channeltube.BuildConfig
import com.example.channeltube.data.local.AppDatabase
import com.example.channeltube.data.local.ChannelEntity
import com.example.channeltube.data.local.VideoEntity
import com.example.channeltube.data.remote.ChannelSearchResult
import com.example.channeltube.data.remote.YouTubeApi
import com.example.channeltube.data.repository.ChannelRepository
import com.example.channeltube.domain.FilterVideosUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val database = Room.databaseBuilder(
        application,
        AppDatabase::class.java,
        "channel_tube.db"
    ).build()

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

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    init {
        refreshFeed(showMessage = false)
        refreshShorts(showMessage = false)
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

    fun searchChannels(query: String) {
        if (query.isBlank()) {
            _message.value = "Enter a channel name, channel URL, or @handle."
            return
        }

        viewModelScope.launch {
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
    }

    fun onVideoSearchQueryChange(query: String) {
        _videoSearchQuery.value = query
        if (query.isBlank()) {
            _videoSearchResults.value = emptyList()
            _isVideoSearchSubmitted.value = false
        }
    }

    fun searchAnyVideo(query: String? = null) {
        val q = (query ?: _videoSearchQuery.value).trim()
        if (q.isBlank()) {
            _message.value = "Please enter a search term."
            return
        }

        _videoSearchQuery.value = q
        viewModelScope.launch {
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
    }

    fun clearVideoSearch() {
        _videoSearchQuery.value = ""
        _videoSearchResults.value = emptyList()
        _isVideoSearchSubmitted.value = false
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
