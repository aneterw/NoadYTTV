package com.noadytv.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.noadytv.data.YouTubeRepository
import com.noadytv.python.PlaybackInfo
import com.noadytv.python.VideoInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PlayerController(
    private val context: Context,
    private val repository: YouTubeRepository = YouTubeRepository.getInstance()
) {
    private val exoPlayer: ExoPlayer = ExoPlayer.Builder(context)
        .setTrackSelector(DefaultTrackSelector(context))
        .build()

    private var scope = CoroutineScope(Dispatchers.IO + Job())

    // State flows
    private val _currentVideo = MutableStateFlow<String?>(null)
    val currentVideo: StateFlow<String?> = _currentVideo.asStateFlow()

    private val _playbackInfo = MutableStateFlow<PlaybackInfo?>(null)
    val playbackInfo: StateFlow<PlaybackInfo?> = _playbackInfo.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentTime = MutableStateFlow(0L)
    val currentTime: StateFlow<Long> = _currentTime.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _showControls = MutableStateFlow(false)
    val showControls: StateFlow<Boolean> = _showControls.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // Playlist
    private var playlist: List<String> = emptyList()
    private var currentIndex = -1

    init {
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    _duration.value = exoPlayer.duration
                    _currentTime.value = exoPlayer.currentPosition
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                _error.value = "播放錯誤: ${error.errorCode} - ${error.message}"
            }
        })
    }

    fun playVideo(videoId: String, videoPlaylist: List<String> = emptyList(), startIndex: Int = 0) {
        this.playlist = videoPlaylist
        this.currentIndex = startIndex
        _showControls.value = false
        _error.value = null

        scope.launch {
            _currentVideo.value = videoId
            try {
                val result = repository.getVideoInfo(videoId)
                result.onSuccess { videoInfo ->
                    val playbackResult = repository.getPlaybackInfo(videoId)
                    playbackResult.onSuccess { playbackInfo ->
                        _playbackInfo.value = playbackInfo
                        loadMedia(playbackInfo, videoInfo.title)
                    }.onFailure { err ->
                        _error.value = err.message ?: "Failed to load playback info"
                    }
                }.onFailure { err ->
                    _error.value = err.message ?: "Failed to load video"
                }
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    private fun loadMedia(playbackInfo: PlaybackInfo, title: String) {
        val mediaItem = if (playbackInfo.manifestUrl != null) {
            // 自適應串流 (DASH/HLS/m3u8_native)：使用 manifest URL
            // 不設 mimeType，讓 ExoPlayer 自動根據 Manifest 類型偵測
            MediaItem.Builder()
                .setUri(playbackInfo.manifestUrl!!)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(title)
                        .build()
                )
                .build()
        } else if (playbackInfo.singleFileUrl != null) {
            // 單一檔案漸進式格式
            MediaItem.Builder()
                .setUri(playbackInfo.singleFileUrl!!)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(title)
                        .build()
                )
                .build()
        } else if (playbackInfo.videoUrl != null && playbackInfo.audioUrl != null) {
            // DASH：分開的影片+音訊 URL（合併播放）
            MediaItem.Builder()
                .setUri(playbackInfo.videoUrl!!)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(title)
                        .build()
                )
                .build()
        } else if (playbackInfo.videoUrl != null) {
            // 僅有影片 URL
            MediaItem.Builder()
                .setUri(playbackInfo.videoUrl!!)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(title)
                        .build()
                )
                .build()
        } else {
            // 最後手段
            val url = playbackInfo.audioUrl ?: playbackInfo.videoUrl
            MediaItem.Builder()
                .setUri(url!!)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(title)
                        .build()
                )
                .build()
        }

        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    fun togglePlayPause() {
        exoPlayer.playWhenReady = !exoPlayer.playWhenReady
    }

    fun stop() {
        exoPlayer.stop()
    }

    fun seekTo(position: Long) {
        exoPlayer.seekTo(position)
    }

    fun seekForward(seconds: Long = 10) {
        exoPlayer.seekTo(exoPlayer.currentPosition + seconds * 1000)
    }

    fun seekBackward(seconds: Long = 10) {
        exoPlayer.seekTo(maxOf(0, exoPlayer.currentPosition - seconds * 1000))
    }

    fun playNext() {
        if (currentIndex < playlist.size - 1) {
            currentIndex++
            playVideo(playlist[currentIndex], playlist, currentIndex)
        }
    }

    fun playPrevious() {
        if (currentIndex > 0) {
            currentIndex--
            playVideo(playlist[currentIndex], playlist, currentIndex)
        }
    }

    fun toggleControls() {
        _showControls.value = !_showControls.value
    }

    fun setAutoPlayNext(enabled: Boolean) {}

    fun getExoPlayer(): ExoPlayer = exoPlayer

    fun release() {
        exoPlayer.release()
        scope.coroutineContext.cancel()
    }
}