package com.noadytv.data

import com.noadytv.python.VideoInfo
import com.noadytv.python.VideoSearchResult
import com.noadytv.python.YouTubePythonBridge
import com.noadytv.python.PlaybackInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class YouTubeRepository private constructor() {
    private val bridge = YouTubePythonBridge.getInstance()

    // In-memory cache to avoid re-fetching same video info
    private val videoCache = mutableMapOf<String, VideoInfo>()

    suspend fun search(query: String, maxResults: Int = 60): Result<List<VideoSearchResult>> {
            return try {
                val result = bridge.searchVideos(query, maxResults)
                result
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun getVideoInfo(videoId: String, subtitlePref: String = "none"): Result<VideoInfo> {
        // Return cached result if available
        videoCache[videoId]?.let { return Result.success(it) }

        return try {
            val result = bridge.getVideoInfo(videoId, subtitlePref)
            result.onSuccess { videoInfo ->
                videoCache[videoId] = videoInfo
            }
            result
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get best playback info for adaptive streaming (DASH) - always fetch fresh from yt-dlp for latest token
     */
    suspend fun getPlaybackInfo(videoId: String, subtitlePref: String = "none"): Result<PlaybackInfo> {
        return getVideoInfo(videoId, subtitlePref).map { videoInfo ->
            val playbackInfo = bridge.getBestPlaybackInfo(videoInfo)
            if (playbackInfo.singleFileUrl == null && playbackInfo.videoUrl == null && playbackInfo.audioUrl == null) {
                throw IllegalStateException("No playable format found")
            }
            playbackInfo
        }
    }

    /** @deprecated Use getPlaybackInfo instead */
    @Deprecated("Use getPlaybackInfo for proper DASH support")
    suspend fun getPlaybackUrl(videoId: String): Result<String> {
        return getPlaybackInfo(videoId).map { info ->
            info.singleFileUrl ?: info.videoUrl ?: info.audioUrl
                ?: throw IllegalStateException("No playable format found")
        }
    }

    companion object {
        @Volatile
        private var instance: YouTubeRepository? = null

        fun getInstance(): YouTubeRepository {
            return instance ?: synchronized(this) {
                instance ?: YouTubeRepository().also { instance = it }
            }
        }
    }
}