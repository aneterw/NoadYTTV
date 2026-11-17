package com.noadytv.channel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.noadytv.python.VideoSearchResult
import com.noadytv.channel.data.ChannelSearchResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 頻道搜尋 ViewModel — 完全獨立於 MainViewModel
 * Layer 1：關鍵字 → 頻道列表（ChannelSearchResult）
 * Layer 2：點選頻道 → 該頻道影片列表（VideoSearchResult，與影片搜尋同格式）
 */
class ChannelSearchViewModel(application: Application) : AndroidViewModel(application) {

    private val bridge = ChannelSearchBridge.getInstance()

    // ── Layer 1：頻道列表 ───────────────────────────────
    private val _channelResults = MutableStateFlow<List<ChannelSearchResult>>(emptyList())
    val channelResults: StateFlow<List<ChannelSearchResult>> = _channelResults.asStateFlow()

    private val _isSearchingChannels = MutableStateFlow(false)
    val isSearchingChannels: StateFlow<Boolean> = _isSearchingChannels.asStateFlow()

    private val _channelSearchError = MutableStateFlow<String?>(null)
    val channelSearchError: StateFlow<String?> = _channelSearchError.asStateFlow()

    // ── Layer 2：頻道內影片 ─────────────────────────────
    private val _videoResults = MutableStateFlow<List<VideoSearchResult>>(emptyList())
    val videoResults: StateFlow<List<VideoSearchResult>> = _videoResults.asStateFlow()

    private val _isSearchingVideos = MutableStateFlow(false)
    val isSearchingVideos: StateFlow<Boolean> = _isSearchingVideos.asStateFlow()

    private val _videoSearchError = MutableStateFlow<String?>(null)
    val videoSearchError: StateFlow<String?> = _videoSearchError.asStateFlow()

    // 當前選取的頻道資訊（layer 2 時使用）
    var currentChannelUrl: String = ""
    var currentChannelTitle: String = ""

    // ── 方法 ───────────────────────────────────────────

    /** 搜尋頻道（Layer 1） */
    fun searchChannels(query: String, maxResults: Int) {
        if (query.isBlank()) {
            _channelResults.value = emptyList()
            return
        }
        _isSearchingChannels.value = true
        _channelSearchError.value = null
        viewModelScope.launch {
            val result = bridge.searchChannels(query, maxResults)
            _isSearchingChannels.value = false
            result.onSuccess { rawChannels ->
                // 篩選：至少 5000 訂閱者，若全數低於門檻則保留全部結果
                val filtered = rawChannels.filter { ch ->
                    ch.follower_count == null || ch.follower_count!! >= 5000
                }.takeIf { it.isNotEmpty() } ?: rawChannels
                _channelResults.value = filtered
            }.onFailure { error ->
                _channelSearchError.value = error.message
            }
        }
    }

    /** 進入指定頻道並載入其影片（Layer 2） */
    fun loadChannelVideos(channelUrl: String, channelTitle: String, maxVideos: Int) {
        currentChannelUrl = channelUrl
        currentChannelTitle = channelTitle
        _isSearchingVideos.value = true
        _videoSearchError.value = null
        viewModelScope.launch {
            val result = bridge.getChannelVideos(channelUrl, maxVideos)
            _isSearchingVideos.value = false
            result.onSuccess { videos ->
                _videoResults.value = videos
            }.onFailure { error ->
                _videoSearchError.value = error.message
            }
        }
    }

    /** 清除頻道列表，返回 Layer 1 */
    fun clearVideoResults() {
        _videoResults.value = emptyList()
        _isSearchingVideos.value = false
    }

    /** 清除所有結果 */
    fun clearAll() {
        _channelResults.value = emptyList()
        _videoResults.value = emptyList()
        _isSearchingChannels.value = false
        _isSearchingVideos.value = false
        currentChannelUrl = ""
        currentChannelTitle = ""
    }
}
