package com.noadytv

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.noadytv.data.YouTubeRepository
import com.noadytv.player.PlayerController
import com.noadytv.python.PlaybackInfo
import com.noadytv.python.VideoSearchResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = YouTubeRepository.getInstance()
    private val playerController = PlayerController(application)

    // Search state
    private val _searchResults = MutableStateFlow<List<VideoSearchResult>>(emptyList())
    val searchResults: StateFlow<List<VideoSearchResult>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _searchError = MutableStateFlow<String?>(null)
    val searchError: StateFlow<String?> = _searchError.asStateFlow()

    private val _focusedIndex = MutableStateFlow(0)
    val focusedIndex: StateFlow<Int> = _focusedIndex.asStateFlow()

    // Navigation state - using Pair instead of custom class
    private val _navigateToPlayer = MutableStateFlow<Pair<String, List<String>>?>(null)
    val navigateToPlayer: StateFlow<Pair<String, List<String>>?> = _navigateToPlayer.asStateFlow()

    fun search(query: String) {
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }

        _isSearching.value = true
        _searchError.value = null

        viewModelScope.launch {
            val result = repository.search(query)
            _isSearching.value = false
            result.onSuccess { videos ->
                _searchResults.value = videos
                _focusedIndex.value = 0
            }.onFailure { error ->
                _searchError.value = error.message
            }
        }
    }

    fun onVideoClick(video: VideoSearchResult) {
        val playlist = _searchResults.value.map { it.id }
        _navigateToPlayer.value = video.id to playlist
    }

    // 獲取影片播放資訊 (支援 DASH 自適應串流)
    suspend fun getPlaybackInfo(videoId: String) = repository.getPlaybackInfo(videoId)

    /** @deprecated Use getPlaybackInfo instead */
    @Deprecated("Use getPlaybackInfo for proper DASH support")
    suspend fun getPlaybackUrl(videoId: String) = repository.getPlaybackUrl(videoId)

    fun onFocusChange(index: Int) {
        _focusedIndex.value = index
    }

    fun clearNavigation() {
        _navigateToPlayer.value = null
    }

    fun getPlayerController() = playerController
}