package com.example.channeltube.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.example.channeltube.BuildConfig
import com.example.channeltube.data.local.AppDatabase
import com.example.channeltube.data.local.VideoEntity
import com.example.channeltube.data.remote.ChannelProfile
import com.example.channeltube.data.remote.YouTubeApi
import com.example.channeltube.data.repository.ChannelDetailRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChannelDetailUiState(
    val profile: ChannelProfile? = null,
    val uploadsVideos: List<VideoEntity> = emptyList(),
    val uploadsNextPageToken: String? = null,
    val searchVideos: List<VideoEntity> = emptyList(),
    val searchNextPageToken: String? = null,
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val searchErrorMessage: String? = null
) {
    val isSearchMode: Boolean get() = searchQuery.isNotBlank()
    val displayedVideos: List<VideoEntity> get() = if (isSearchMode) searchVideos else uploadsVideos
    val hasNextPage: Boolean get() = if (isSearchMode) searchNextPageToken != null else uploadsNextPageToken != null
}

class ChannelDetailViewModel(
    application: Application,
    private val channelId: String
) : AndroidViewModel(application) {

    private val database = Room.databaseBuilder(
        application,
        AppDatabase::class.java,
        "channel_tube.db"
    ).build()

    private val repository = ChannelDetailRepository(
        api = YouTubeApi(BuildConfig.YOUTUBE_API_KEY),
        dao = database.appDao()
    )

    private val _uiState = MutableStateFlow(ChannelDetailUiState())
    val uiState: StateFlow<ChannelDetailUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var uploadsPlaylistId: String? = null

    init {
        loadInitial()
    }

    fun loadInitial(isRefresh: Boolean = false) {
        viewModelScope.launch {
            if (isRefresh) {
                _uiState.value = _uiState.value.copy(isRefreshing = true, errorMessage = null)
            } else {
                _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            }

            // Load cached channel and videos immediately for fast display
            try {
                val cachedChannel = repository.getCachedChannel(channelId)
                val cachedVideos = repository.getCachedVideos(channelId)
                if (cachedChannel != null && _uiState.value.profile == null) {
                    _uiState.value = _uiState.value.copy(
                        profile = ChannelProfile(
                            channelId = cachedChannel.channelId,
                            channelName = cachedChannel.channelName,
                            description = cachedChannel.description,
                            thumbnailUrl = cachedChannel.thumbnailUrl,
                            uploadsPlaylistId = cachedChannel.uploadsPlaylistId
                        ),
                        uploadsVideos = cachedVideos
                    )
                    uploadsPlaylistId = cachedChannel.uploadsPlaylistId
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
            }

            try {
                // Fetch fresh profile from YouTube API
                val profile = repository.getChannelProfile(channelId)
                uploadsPlaylistId = profile.uploadsPlaylistId
                _uiState.value = _uiState.value.copy(profile = profile)

                // Fetch first page of uploads (50 videos)
                if (profile.uploadsPlaylistId.isNotBlank()) {
                    val page = repository.getUploadsPage(
                        uploadsPlaylistId = profile.uploadsPlaylistId,
                        pageToken = null,
                        channelId = channelId,
                        channelName = profile.channelName
                    )
                    _uiState.value = _uiState.value.copy(
                        uploadsVideos = page.videos.distinctBy { it.videoId },
                        uploadsNextPageToken = page.nextPageToken,
                        errorMessage = null
                    )
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                // If we don't have any videos loaded, show error
                if (_uiState.value.uploadsVideos.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = error.message ?: "Could not load channel profile."
                    )
                }
            } finally {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isRefreshing = false
                )
            }
        }
    }

    fun loadNextPage() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || !state.hasNextPage) return

        if (state.isSearchMode) {
            loadNextSearchPage()
        } else {
            loadNextUploadsPage()
        }
    }

    private fun loadNextUploadsPage() {
        val playlistId = uploadsPlaylistId ?: return
        val nextToken = _uiState.value.uploadsNextPageToken ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingMore = true)
            try {
                val page = repository.getUploadsPage(
                    uploadsPlaylistId = playlistId,
                    pageToken = nextToken,
                    channelId = channelId,
                    channelName = _uiState.value.profile?.channelName.orEmpty()
                )
                val existingIds = _uiState.value.uploadsVideos.map { it.videoId }.toSet()
                val newVideos = page.videos.filter { it.videoId !in existingIds }
                _uiState.value = _uiState.value.copy(
                    uploadsVideos = _uiState.value.uploadsVideos + newVideos,
                    uploadsNextPageToken = page.nextPageToken
                )
            } catch (error: Exception) {
                if (error is CancellationException) throw error
            } finally {
                _uiState.value = _uiState.value.copy(isLoadingMore = false)
            }
        }
    }

    private fun loadNextSearchPage() {
        val nextToken = _uiState.value.searchNextPageToken ?: return
        val query = _uiState.value.searchQuery.trim()
        if (query.isBlank()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingMore = true)
            try {
                val page = repository.searchVideos(
                    channelId = channelId,
                    query = query,
                    pageToken = nextToken,
                    channelName = _uiState.value.profile?.channelName.orEmpty()
                )
                val existingIds = _uiState.value.searchVideos.map { it.videoId }.toSet()
                val newVideos = page.videos.filter { it.videoId !in existingIds }
                _uiState.value = _uiState.value.copy(
                    searchVideos = _uiState.value.searchVideos + newVideos,
                    searchNextPageToken = page.nextPageToken
                )
            } catch (error: Exception) {
                if (error is CancellationException) throw error
            } finally {
                _uiState.value = _uiState.value.copy(isLoadingMore = false)
            }
        }
    }

    fun onSearchQueryChange(newQuery: String) {
        _uiState.value = _uiState.value.copy(searchQuery = newQuery)

        searchJob?.cancel()

        if (newQuery.isBlank()) {
            _uiState.value = _uiState.value.copy(
                searchVideos = emptyList(),
                searchNextPageToken = null,
                isSearching = false,
                searchErrorMessage = null
            )
            return
        }

        // Debounce search by 500ms
        searchJob = viewModelScope.launch {
            delay(500)
            executeSearch(newQuery.trim())
        }
    }

    fun submitSearch() {
        searchJob?.cancel()
        val query = _uiState.value.searchQuery.trim()
        if (query.isBlank()) {
            clearSearch()
            return
        }
        searchJob = viewModelScope.launch {
            executeSearch(query)
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _uiState.value = _uiState.value.copy(
            searchQuery = "",
            searchVideos = emptyList(),
            searchNextPageToken = null,
            isSearching = false,
            searchErrorMessage = null
        )
    }

    private suspend fun executeSearch(query: String) {
        _uiState.value = _uiState.value.copy(
            isSearching = true,
            searchErrorMessage = null
        )
        try {
            val page = repository.searchVideos(
                channelId = channelId,
                query = query,
                pageToken = null,
                channelName = _uiState.value.profile?.channelName.orEmpty()
            )
            _uiState.value = _uiState.value.copy(
                searchVideos = page.videos.distinctBy { it.videoId },
                searchNextPageToken = page.nextPageToken,
                isSearching = false,
                searchErrorMessage = if (page.videos.isEmpty()) "No videos found for \"$query\" on this channel." else null
            )
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            _uiState.value = _uiState.value.copy(
                isSearching = false,
                searchErrorMessage = error.message ?: "Search failed. Please try again."
            )
        }
    }

    fun retry() {
        if (_uiState.value.isSearchMode) {
            submitSearch()
        } else {
            loadInitial()
        }
    }
}

class ChannelDetailViewModelFactory(
    private val application: Application,
    private val channelId: String
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ChannelDetailViewModel(application, channelId) as T
    }
}

