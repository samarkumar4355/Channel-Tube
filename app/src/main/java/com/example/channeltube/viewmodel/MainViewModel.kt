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

    private val _searchResults = MutableStateFlow<List<ChannelSearchResult>>(emptyList())
    val searchResults: StateFlow<List<ChannelSearchResult>> = _searchResults

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _workingChannelId = MutableStateFlow<String?>(null)
    val workingChannelId: StateFlow<String?> = _workingChannelId

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    init {
        refreshFeed(showMessage = false)
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

                for (channel in savedChannels) {
                    try {
                        updatedVideos += repository.refreshChannel(channel)
                        updatedChannels++
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        errors.add(channel.channelName)
                    }
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

    fun dismissMessage() {
        _message.value = null
    }
}
