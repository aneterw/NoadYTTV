package com.noadytv.python

import com.chaquo.python.Python
import com.chaquo.python.PyObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicReference
import android.util.Log

data class VideoSearchResult(
    val id: String,
    val title: String,
    val duration: Long?,
    val thumbnail: String?,
    val channel: String?,
    val channel_id: String?,
    val view_count: Long?,
    val url: String,
    val upload_date: String? = null   // 格式：yyyyMMdd，如 "20241112"
)

data class VideoFormat(
    val format_id: String,
    val ext: String,
    val resolution: String?,
    val width: Int?,
    val height: Int?,
    val fps: Double?,
    val vcodec: String?,
    val acodec: String?,
    val url: String?,
    val protocol: String?,
    val filesize: Long?,
    val tbr: Double?,
    val language: String?,
    val manifest_url: String?  // DASH/HLS manifest URL for adaptive streaming
)

data class SubtitleTrack(
    val language: String,
    val name: String,
    val url: String?,
    val ext: String,
    val is_auto: Boolean,
    val local_path: String? = null  // 本地快取路徑（若 Python 端已下載）
)

data class VideoInfo(
    val id: String,
    val title: String,
    val description: String?,
    val duration: Long?,
    val thumbnail: String?,
    val channel: String?,
    val channel_id: String?,
    val view_count: Long?,
    val like_count: Long?,
    val upload_date: String?,
    val formats: List<VideoFormat>,
    val subtitles: List<SubtitleTrack>,
    val is_live: Boolean,
    val resolvedLanguage: String? = null
)

data class PlaylistVideo(
    val id: String,
    val title: String,
    val duration: Long?,
    val thumbnail: String?
)

/**
 * 播放資訊，支援 DASH (VOD) 與 HLS (Live) 自適應串流。
 * - VOD 1080p+：通常使用 DASH (MPD manifest)
 * - Live：通常使用 HLS (m3u8 manifest)
 */
data class PlaybackInfo(
    val videoUrl: String?,
    val audioUrl: String?,
    val videoFormatId: String?,
    val audioFormatId: String?,
    val videoHeight: Int?,
    val isDash: Boolean,           // true = DASH (MPD), false = HLS (m3u8) 或 progressive
    val singleFileUrl: String?,    // 單一檔案漸進式格式
    val manifestUrl: String?       // MPD (DASH) 或 m3u8 (HLS) manifest URL
)

class YouTubePythonBridge private constructor() {

    private val python = Python.getInstance()
    private val module: PyObject = python.getModule("youtube_extractor")

    companion object {
        private val INSTANCE = AtomicReference<YouTubePythonBridge?>(null)
        @JvmField var _lastTargetVideoId: String = ""   // 供 getTargetVideoUrlWeb 使用

        fun getInstance(): YouTubePythonBridge {
            return INSTANCE.getAndSet(null)?.also { INSTANCE.set(it) }
                ?: YouTubePythonBridge().also { INSTANCE.set(it) }
        }
    }

    suspend fun searchVideos(query: String, maxResults: Int = 60): Result<List<VideoSearchResult>> {
        return withContext(Dispatchers.IO) {
            try {
                val result = module.callAttr("search_videos", query, maxResults).toString()
                val jsonArray = JSONArray(result)
                val videos = mutableListOf<VideoSearchResult>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    videos.add(
                        VideoSearchResult(
                            id = obj.optString("id"),
                            title = obj.optString("title"),
                            duration = if (obj.has("duration") && !obj.isNull("duration")) obj.optLong("duration", -1).takeIf { it > 0 } else null,
                            thumbnail = obj.optString("thumbnail"),
                            channel = obj.optString("channel"),
                            channel_id = obj.optString("channel_id"),
                            view_count = if (obj.has("view_count") && !obj.isNull("view_count")) obj.optLong("view_count", -1).takeIf { it > 0 } else null,
                            url = obj.optString("url"),
                            upload_date = if (obj.has("upload_date") && !obj.isNull("upload_date")) obj.optString("upload_date") else null
                        )
                    )
                }
                Result.success(videos)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getVideoInfo(videoId: String, subtitlePref: String = "none"): Result<VideoInfo> {
        return withContext(Dispatchers.IO) {
            try {
                val result = module.callAttr("get_video_info", videoId, subtitlePref).toString()
                val json = JSONObject(result)

                val formats = JSONArray(json.optString("formats", "[]")).let { fmtArray ->
                    val list = mutableListOf<VideoFormat>()
                    for (i in 0 until fmtArray.length()) {
                        val f = fmtArray.getJSONObject(i)
                        list.add(
                            VideoFormat(
                                format_id = f.optString("format_id"),
                                ext = f.optString("ext"),
                                resolution = f.optString("resolution"),
                                width = if (f.has("width") && !f.isNull("width")) f.getInt("width") else null,
                                height = if (f.has("height") && !f.isNull("height")) f.getInt("height") else null,
                                fps = if (f.has("fps") && !f.isNull("fps")) f.getDouble("fps") else null,
                                vcodec = f.optString("vcodec"),
                                acodec = f.optString("acodec"),
                                url = f.optString("url"),
                                protocol = f.optString("protocol"),
                                filesize = if (f.has("filesize") && !f.isNull("filesize")) f.getLong("filesize") else null,
                                tbr = if (f.has("tbr") && !f.isNull("tbr")) f.getDouble("tbr") else null,
                                language = f.optString("language"),
                                manifest_url = f.optString("manifest_url")
                            )
                        )
                    }
                    list
                }

                val subtitles = JSONArray(json.optString("subtitles", "[]")).let { subArray ->
                    val list = mutableListOf<SubtitleTrack>()
                    for (i in 0 until subArray.length()) {
                        val s = subArray.getJSONObject(i)
                        list.add(
                            SubtitleTrack(
                                language = s.optString("language"),
                                name = s.optString("name"),
                                url = s.optString("url"),
                                ext = s.optString("ext"),
                                is_auto = false,
                                local_path = if (s.has("local_path") && !s.isNull("local_path")) s.optString("local_path") else null
                            )
                        )
                    }
                    list
                }

                // 處理 duration：直播時 JSON 可能為 null 或缺少該欄位
                val duration: Long? = if (json.has("duration") && !json.isNull("duration")) {
                    try {
                        json.getLong("duration")
                    } catch (e: Exception) {
                        null
                    }
                } else {
                    null
                }

                val videoInfo = VideoInfo(
                    id = json.optString("id"),
                    title = json.optString("title"),
                    description = json.optString("description"),
                    duration = duration,
                    thumbnail = json.optString("thumbnail"),
                    channel = json.optString("channel"),
                    channel_id = json.optString("channel_id"),
                    view_count = if (json.has("view_count") && !json.isNull("view_count")) json.getLong("view_count") else null,
                    like_count = if (json.has("like_count") && !json.isNull("like_count")) json.getLong("like_count") else null,
                    upload_date = json.optString("upload_date"),
                    formats = formats,
                    subtitles = subtitles,
                    is_live = json.optBoolean("is_live", false),
                    resolvedLanguage = json.optString("resolved_language").takeIf { it.isNotEmpty() }
                )
                Result.success(videoInfo)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * 取得最佳播放資訊，支援 VOD (DASH) 與 Live (HLS) 自適應串流。
     * 優先順序：
     *   VOD: 1080p+ DASH > 720p+ DASH > Progressive
     *   Live: 1080p+ HLS > 720p+ HLS > Progressive
     */
    fun getBestPlaybackInfo(videoInfo: VideoInfo): PlaybackInfo {
        val isLive = videoInfo.is_live
        
        // 過濾有效的格式 (URL 存在即可，不限定協定)
        val validFormats = videoInfo.formats.filter { it.url != null && it.url!!.isNotEmpty() }

        // 單一檔案漸進式格式 (影片+音訊合一) - 通常 <= 720p
        val progressiveFormats = validFormats.filter { format ->
            format.vcodec != null && format.vcodec != "none" &&
            format.acodec != null && format.acodec != "none"
        }.sortedByDescending { it.height ?: 0 }

        // 僅影片的格式 (需要獨立音訊) - 可能是 DASH 或 HLS
        val videoOnlyFormats = validFormats.filter { format ->
            format.vcodec != null && format.vcodec != "none" &&
            (format.acodec == null || format.acodec == "none")
        }.sortedByDescending { it.height ?: 0 }

        // 僅音訊的格式
        val audioOnlyFormats = validFormats.filter { format ->
            format.acodec != null && format.acodec != "none" &&
            (format.vcodec == null || format.vcodec == "none")
        }.sortedByDescending { it.tbr ?: 0.0 }

        // 分離 DASH 和 HLS 格式
        val dashVideoFormats = videoOnlyFormats.filter { it.protocol?.contains("dash", true) == true }
        val hlsVideoFormats = videoOnlyFormats.filter { 
            it.protocol?.contains("m3u8", true) == true || it.protocol?.contains("hls", true) == true 
        }
        val dashAudioFormats = audioOnlyFormats.filter { it.protocol?.contains("dash", true) == true }
        // m3u8_native 是 YouTube Android client 的自適應串流協定，等同於 HLS/DASH manifest
        val hlsAudioFormats = audioOnlyFormats.filter {
            it.protocol?.contains("m3u8", true) == true || it.protocol?.contains("hls", true) == true
        }

        // 🔍 DEBUG: 列出所有可用格式（含 m3u8_native）
        Log.d("YouTubePythonBridge", "=== 可用格式列表 (${validFormats.size}) ===")
        validFormats.forEach { f ->
            val isDash = f.protocol?.contains("dash", true) == true
            val isHls = f.protocol?.contains("m3u8", true) == true || f.protocol?.contains("hls", true) == true
            Log.d("YouTubePythonBridge", "  fmt=${f.format_id} h=${f.height} tbr=${f.tbr} vcodec=${f.vcodec} proto=${f.protocol} dash=$isDash hls=$isHls")
        }

        if (isLive) {
            // ===== 直播優先使用 HLS (m3u8/m3u8_native) =====
            
            // 策略 1：1080p+ HLS
            val hls1080p = hlsVideoFormats.firstOrNull { it.height != null && it.height!! >= 1080 }
            if (hls1080p != null) {
                val bestAudio = hlsAudioFormats.maxByOrNull { it.tbr ?: 0.0 }
                    ?: audioOnlyFormats.maxByOrNull { it.tbr ?: 0.0 }
                Log.d("YouTubePythonBridge", "選擇: 1080p+ HLS fmt=${hls1080p.format_id} h=${hls1080p.height} tbr=${hls1080p.tbr} vcodec=${hls1080p.vcodec} audio=${bestAudio?.format_id}")
                return PlaybackInfo(
                    videoUrl = hls1080p.url,
                    audioUrl = bestAudio?.url,
                    videoFormatId = hls1080p.format_id,
                    audioFormatId = bestAudio?.format_id,
                    videoHeight = hls1080p.height,
                    isDash = false,  // HLS
                    singleFileUrl = null,
                    manifestUrl = hls1080p.manifest_url ?: hls1080p.url  // HLS 用 m3u8 URL
                )
            }

            // 策略 2：720p+ HLS
            val hls720p = hlsVideoFormats.firstOrNull { it.height != null && it.height!! >= 720 }
            if (hls720p != null) {
                val bestAudio = hlsAudioFormats.maxByOrNull { it.tbr ?: 0.0 }
                    ?: audioOnlyFormats.maxByOrNull { it.tbr ?: 0.0 }
                Log.d("YouTubePythonBridge", "選擇: 720p+ HLS fmt=${hls720p.format_id} h=${hls720p.height} tbr=${hls720p.tbr} vcodec=${hls720p.vcodec} audio=${bestAudio?.format_id}")
                return PlaybackInfo(
                    videoUrl = hls720p.url,
                    audioUrl = bestAudio?.url,
                    videoFormatId = hls720p.format_id,
                    audioFormatId = bestAudio?.format_id,
                    videoHeight = hls720p.height,
                    isDash = false,  // HLS
                    singleFileUrl = null,
                    manifestUrl = hls720p.manifest_url ?: hls720p.url
                )
            }

            // 策略 3：任意 HLS
            val anyHls = hlsVideoFormats.firstOrNull()
            if (anyHls != null) {
                val bestAudio = hlsAudioFormats.maxByOrNull { it.tbr ?: 0.0 }
                    ?: audioOnlyFormats.maxByOrNull { it.tbr ?: 0.0 }
                Log.d("YouTubePythonBridge", "選擇: 任意 HLS fmt=${anyHls.format_id} h=${anyHls.height} tbr=${anyHls.tbr} vcodec=${anyHls.vcodec} audio=${bestAudio?.format_id}")
                return PlaybackInfo(
                    videoUrl = anyHls.url,
                    audioUrl = bestAudio?.url,
                    videoFormatId = anyHls.format_id,
                    audioFormatId = bestAudio?.format_id,
                    videoHeight = anyHls.height,
                    isDash = false,
                    singleFileUrl = null,
                    manifestUrl = anyHls.manifest_url ?: anyHls.url
                )
            }
        } else {
            // ===== 一般影片 (VOD) 優先使用 DASH，其次 m3u8_native（YouTube Android 自適應） =====
            
            // 策略 1：1080p+ DASH
            val dash1080p = dashVideoFormats.firstOrNull { it.height != null && it.height!! >= 1080 }
            if (dash1080p != null && (dashAudioFormats.isNotEmpty() || audioOnlyFormats.isNotEmpty())) {
                val bestAudio = dashAudioFormats.maxByOrNull { it.tbr ?: 0.0 }
                    ?: audioOnlyFormats.maxByOrNull { it.tbr ?: 0.0 }
                Log.d("YouTubePythonBridge", "選擇: 1080p+ DASH fmt=${dash1080p.format_id} h=${dash1080p.height} tbr=${dash1080p.tbr} vcodec=${dash1080p.vcodec} audio=${bestAudio?.format_id}")
                return PlaybackInfo(
                    videoUrl = dash1080p.url,
                    audioUrl = bestAudio?.url,
                    videoFormatId = dash1080p.format_id,
                    audioFormatId = bestAudio?.format_id,
                    videoHeight = dash1080p.height,
                    isDash = true,
                    singleFileUrl = null,
                    manifestUrl = dash1080p.manifest_url
                )
            }

            // 策略 2：720p+ DASH
            val dash720p = dashVideoFormats.firstOrNull { it.height != null && it.height!! >= 720 }
            if (dash720p != null && (dashAudioFormats.isNotEmpty() || audioOnlyFormats.isNotEmpty())) {
                val bestAudio = dashAudioFormats.maxByOrNull { it.tbr ?: 0.0 }
                    ?: audioOnlyFormats.maxByOrNull { it.tbr ?: 0.0 }
                Log.d("YouTubePythonBridge", "選擇: 720p+ DASH fmt=${dash720p.format_id} h=${dash720p.height} tbr=${dash720p.tbr} vcodec=${dash720p.vcodec} audio=${bestAudio?.format_id}")
                return PlaybackInfo(
                    videoUrl = dash720p.url,
                    audioUrl = bestAudio?.url,
                    videoFormatId = dash720p.format_id,
                    audioFormatId = bestAudio?.format_id,
                    videoHeight = dash720p.height,
                    isDash = true,
                    singleFileUrl = null,
                    manifestUrl = dash720p.manifest_url
                )
            }

            // 策略 3 (VOD fallback)：1080p+ m3u8_native（YouTube Android 自適應串流非 DASH 格式）
            val vodHls1080p = hlsVideoFormats.firstOrNull { it.height != null && it.height!! >= 1080 }
            if (vodHls1080p != null && (hlsAudioFormats.isNotEmpty() || dashAudioFormats.isNotEmpty() || audioOnlyFormats.isNotEmpty())) {
                val bestAudio = hlsAudioFormats.maxByOrNull { it.tbr ?: 0.0 }
                    ?: dashAudioFormats.maxByOrNull { it.tbr ?: 0.0 }
                    ?: audioOnlyFormats.maxByOrNull { it.tbr ?: 0.0 }
                Log.d("YouTubePythonBridge", "選擇: VOD m3u8_native 1080p+ fmt=${vodHls1080p.format_id} h=${vodHls1080p.height} tbr=${vodHls1080p.tbr} vcodec=${vodHls1080p.vcodec} audio=${bestAudio?.format_id}")
                return PlaybackInfo(
                    videoUrl = vodHls1080p.url,
                    audioUrl = bestAudio?.url,
                    videoFormatId = vodHls1080p.format_id,
                    audioFormatId = bestAudio?.format_id,
                    videoHeight = vodHls1080p.height,
                    isDash = false,
                    singleFileUrl = null,
                    manifestUrl = vodHls1080p.manifest_url ?: vodHls1080p.url
                )
            }

            // 策略 4 (VOD fallback)：720p+ m3u8_native
            val vodHls720p = hlsVideoFormats.firstOrNull { it.height != null && it.height!! >= 720 }
            if (vodHls720p != null && (hlsAudioFormats.isNotEmpty() || dashAudioFormats.isNotEmpty() || audioOnlyFormats.isNotEmpty())) {
                val bestAudio = hlsAudioFormats.maxByOrNull { it.tbr ?: 0.0 }
                    ?: dashAudioFormats.maxByOrNull { it.tbr ?: 0.0 }
                    ?: audioOnlyFormats.maxByOrNull { it.tbr ?: 0.0 }
                Log.d("YouTubePythonBridge", "選擇: VOD m3u8_native 720p+ fmt=${vodHls720p.format_id} h=${vodHls720p.height} tbr=${vodHls720p.tbr} vcodec=${vodHls720p.vcodec} audio=${bestAudio?.format_id}")
                return PlaybackInfo(
                    videoUrl = vodHls720p.url,
                    audioUrl = bestAudio?.url,
                    videoFormatId = vodHls720p.format_id,
                    audioFormatId = bestAudio?.format_id,
                    videoHeight = vodHls720p.height,
                    isDash = false,
                    singleFileUrl = null,
                    manifestUrl = vodHls720p.manifest_url ?: vodHls720p.url
                )
            }
        }

        // 策略 3 (共通)：回退到最佳漸進式格式 (單一檔案含影片+音訊)
        val bestProgressive = progressiveFormats.firstOrNull()
        if (bestProgressive != null) {
            Log.d("YouTubePythonBridge", "選擇: 漸進式 fmt=${bestProgressive.format_id} h=${bestProgressive.height} tbr=${bestProgressive.tbr} vcodec=${bestProgressive.vcodec}")
            return PlaybackInfo(
                videoUrl = null,
                audioUrl = null,
                videoFormatId = null,
                audioFormatId = null,
                videoHeight = bestProgressive.height,
                isDash = false,
                singleFileUrl = bestProgressive.url,
                manifestUrl = null
            )
        }

        // 策略 4 (共通)：任意影片格式搭配任意音訊
        val anyVideo = videoOnlyFormats.firstOrNull()
        if (anyVideo != null && audioOnlyFormats.isNotEmpty()) {
            val bestAudio = audioOnlyFormats.maxByOrNull { it.tbr ?: 0.0 }
            val isDash = anyVideo.protocol?.contains("dash", true) == true
            Log.d("YouTubePythonBridge", "選擇: 任意影片 fmt=${anyVideo.format_id} h=${anyVideo.height} tbr=${anyVideo.tbr} vcodec=${anyVideo.vcodec} dash=$isDash audio=${bestAudio?.format_id}")
            return PlaybackInfo(
                videoUrl = anyVideo.url,
                audioUrl = bestAudio?.url,
                videoFormatId = anyVideo.format_id,
                audioFormatId = bestAudio?.format_id,
                videoHeight = anyVideo.height,
                isDash = isDash,
                singleFileUrl = null,
                manifestUrl = anyVideo.manifest_url ?: (if (isDash) anyVideo.url else null)
            )
        }

        // 最後手段：任意有 URL 的格式
        val anyFormat = validFormats.firstOrNull { it.url != null }
        Log.d("YouTubePythonBridge", "選擇: 最後手段 fmt=${anyFormat?.format_id} h=${anyFormat?.height}")
        return PlaybackInfo(
            videoUrl = if (anyFormat != null && anyFormat.vcodec != "none") anyFormat.url else null,
            audioUrl = if (anyFormat != null && anyFormat.acodec != "none") anyFormat.url else null,
            videoFormatId = anyFormat?.format_id,
            audioFormatId = anyFormat?.format_id,
            videoHeight = anyFormat?.height,
            isDash = false,
            singleFileUrl = anyFormat?.url,
            manifestUrl = anyFormat?.manifest_url
        )
    }

    /** @deprecated 改用 getBestPlaybackInfo 以正確支援 DASH/HLS */
    @Deprecated("Use getBestPlaybackInfo for proper DASH/HLS support")
    fun getBestPlaybackUrl(videoInfo: VideoInfo): String? {
        val info = getBestPlaybackInfo(videoInfo)
        return info.singleFileUrl ?: info.videoUrl ?: info.audioUrl
    }

    suspend fun downloadSubtitleToFilePath(videoId: String, lang: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val result = module.callAttr("download_subtitle_to_file", videoId, lang).toString()
                Result.success(result)
            } catch (e: Exception) {
                Log.e("YouTubePythonBridge", "downloadSubtitleToFilePath error: ${e.message}")
                Result.failure(e)
            }
        }
    }

    /** 用 yt-dlp format selector 精確取得目標高度影片 URL */
    suspend fun getTargetVideoUrl(videoId: String, maxHeight: Int): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val url = module.callAttr("get_target_video_url", videoId, maxHeight).toString().trim()
                Log.d("YouTubePythonBridge", "getTargetVideoUrl: $videoId h=$maxHeight url_len=${url.length}")
                if (url.isNotEmpty()) Result.success(url) else Result.failure(IllegalStateException("No URL found for height $maxHeight"))
            } catch (e: Exception) {
                Log.e("YouTubePythonBridge", "getTargetVideoUrl error: ${e.message}")
                Result.failure(e)
            }
        }
    }

    /** 用 web client 精確取得指定高度的 progressive URL（當 android_tv_embedded 只給 adaptive manifest 時 fallback） */
    suspend fun getTargetVideoUrlWeb(): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val id = Companion._lastTargetVideoId
                if (id.isEmpty()) return@withContext Result.failure(IllegalStateException("No target video ID set"))
                val url = module.callAttr("get_target_video_url_web").toString().trim()
                Log.d("YouTubePythonBridge", "getTargetVideoUrlWeb: $id url_len=${url.length}")
                if (url.isNotEmpty()) Result.success(url) else Result.failure(IllegalStateException("No progressive URL from web client"))
            } catch (e: Exception) {
                Log.e("YouTubePythonBridge", "getTargetVideoUrlWeb error: ${e.message}")
                Result.failure(e)
            }
        }
    }

    /** 在呼叫 getTargetVideoUrl 前設定，供 web client fallback 使用 */
    fun setLastTargetVideoId(id: String) {
        Companion._lastTargetVideoId = id
        Log.d("YouTubePythonBridge", "setLastTargetVideoId: $id")
    }
}

