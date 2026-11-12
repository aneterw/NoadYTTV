package com.noadytv

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModelProvider
import com.noadytv.player.PlayerController
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaItem.SubtitleConfiguration
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.ui.PlayerView
import androidx.media3.ui.CaptionStyleCompat
import androidx.annotation.Dimension
import android.graphics.Color
import com.noadytv.python.PlaybackInfo
import com.noadytv.python.SubtitleTrack
import com.noadytv.python.VideoFormat
import com.noadytv.python.VideoInfo
import com.noadytv.python.YouTubePythonBridge

class PlayerActivity : ComponentActivity() {

    private val viewModel: MainViewModel by lazy {
        ViewModelProvider(this)[MainViewModel::class.java]
    }

    private var playerController: PlayerController? = null
    private var playerView: PlayerView? = null
    private var currentVideoInfo: VideoInfo? = null
    private var currentPlaybackInfo: PlaybackInfo? = null

    // SharedPreferences 鍵（與 SettingsActivity 共用）
    private val PREF_FILE = "player_settings"
    private val KEY_SUBTITLE = "pref_subtitle"
    private val KEY_RESOLUTION = "pref_resolution"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        // 建立全螢幕播放器（使用內建控制器）
        playerView = PlayerView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            useController = true
        }
        setContentView(playerView)

        val videoId = intent.getStringExtra("videoId") ?: ""
        val videoTitle = intent.getStringExtra("videoTitle") ?: ""

        playerController = PlayerController(application)
        playerView?.player = playerController?.getExoPlayer()

        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                // 1. 取得完整影片資訊（含字幕、解析度列表）
                val videoInfoResult = viewModel.getVideoInfo(videoId)
                if (videoInfoResult.isSuccess) {
                    currentVideoInfo = videoInfoResult.getOrNull()
                }

                // 2. 取得播放資訊
                val result = viewModel.getPlaybackInfo(videoId)
                if (result.isSuccess) {
                    val playbackInfo = result.getOrNull()
                    if (playbackInfo != null) {
                        currentPlaybackInfo = playbackInfo
                        val prefs = getSharedPreferences(PREF_FILE, MODE_PRIVATE)
                        val savedSubtitle = prefs.getString(KEY_SUBTITLE, "none")
                        val savedResolution = prefs.getString(KEY_RESOLUTION, "auto") ?: "auto"
                        val title = videoTitle

                        // 3. 如果有字幕偏好設定，即時重新抓最新字幕 URL 再組建 MediaItem
                        var finalPlaybackInfo = playbackInfo
                        var subtitleTrackForMediaItem: com.noadytv.python.SubtitleTrack? = null
                        if (savedSubtitle != "none" && currentVideoInfo != null) {
                            val matched = currentVideoInfo!!.subtitles.firstOrNull { it.language == savedSubtitle }
                                ?: currentVideoInfo!!.subtitles.firstOrNull { sub ->
                                    sub.language.startsWith(savedSubtitle!!, ignoreCase = true) ||
                                        savedSubtitle!!.startsWith(sub.language, ignoreCase = true)
                                }
                            android.util.Log.d("NoadYTTV_Subtitle", "applySavedPrefs matching: saved=$savedSubtitle matched=${matched?.language} url=${matched?.url}")
                            if (matched != null) {
                                // 使用已抓到的即時 URL（非過期快取），直接交給 ExoPlayer
                                subtitleTrackForMediaItem = matched
                            }
                        }

                        // 4. 組建 MediaItem（帶字幕）
                        val mediaItem = if (subtitleTrackForMediaItem != null) {
                            buildMediaItemWithSubtitle(finalPlaybackInfo, title, subtitleTrackForMediaItem)
                        } else {
                            buildMediaItem(finalPlaybackInfo, title)
                        }

                        val player = playerController?.getExoPlayer()
                        player?.setMediaItem(mediaItem)
                        player?.prepare()
                        player?.play()
                        android.util.Log.d("NoadYTTV_Subtitle", "Player started with subtitle=${subtitleTrackForMediaItem?.language ?: "none"}")

                        // 確保文字軌道被啟用，設定優先字幕語言
                        val params = player?.trackSelector?.getParameters()?.buildUpon()
                            ?.setPreferredTextLanguage(savedSubtitle)
                            ?.build()
                        if (params != null) {
                            player?.trackSelector?.setParameters(params)
                        }
                        // 強制啟用文字/字幕軌道（用 TrackSelector）
                        val selector = player?.trackSelector as? androidx.media3.exoplayer.trackselection.DefaultTrackSelector
                        if (selector != null) {
                            val baseParams = selector.getParameters()
                            val newParams = baseParams.buildUpon()
                                .setPreferredTextLanguage(savedSubtitle)
                                .build()
                            selector.setParameters(newParams)
                            android.util.Log.d("NoadYTTV_Subtitle", "TrackSelector params set lang=$savedSubtitle")
                        }

                        // 設定字幕樣式：置中 + 縮小字體（透過 onCues callback + SubtitleView API）
                        player?.addListener(object : Player.Listener {
                            override fun onCues(cueGroup: CueGroup) {
                                val cues = cueGroup.cues.map { cue ->
                                    // YouTube VTT cue 的 backgroundColor 預設為 opaque black，
                                    // 需要明確設為 TRANSPARENT 才能消除黑色背景條。
                                    // clearWindowColor() 只清除 windowColor，不影響 backgroundColor。
                                    val builder = cue.buildUpon()
                                        .setLine(0.88f, Cue.LINE_TYPE_FRACTION)
                                        .setLineAnchor(Cue.ANCHOR_TYPE_END)
                                        .setPosition(0.5f)
                                        .setPositionAnchor(Cue.ANCHOR_TYPE_MIDDLE)
                                        .setTextAlignment(android.text.Layout.Alignment.ALIGN_CENTER)
                                        .setTextSize(32f, Cue.TEXT_SIZE_TYPE_ABSOLUTE)
                                    // 直接設定backgroundColor為透明（YouTube VTT 預設 opaque black）
                                    try {
                                        val bgField = builder::class.java.getDeclaredField("backgroundColor")
                                        bgField.isAccessible = true
                                        bgField.setInt(builder, Color.TRANSPARENT)
                                    } catch (_: Exception) {}
                                    builder.build()
                                }
                                playerView?.subtitleView?.setCues(cues)
                            }
                        })
                        // SubtitleView 全局樣式：禁用 VTT 內嵌樣式，固定字級，黃色文字+極淡背景
                        playerView?.subtitleView?.let { sv ->
                            sv.setApplyEmbeddedStyles(false)
                            sv.setApplyEmbeddedFontSizes(false)
                            sv.setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 32f)
                            // #0D000000 = 5% 不透明度黑底 — 有微弱保護又不搶鏡
                            sv.setStyle(CaptionStyleCompat(
                                android.graphics.Color.parseColor("#FFD700"),  // 黃色文字
                                android.graphics.Color.parseColor("#0D000000"), // 5%不透明黑底
                                android.graphics.Color.parseColor("#0D000000"), // 圓角窗口
                                CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
                                android.graphics.Color.BLACK,
                                null
                            ))
                            android.util.Log.d("NoadYTTV_Subtitle", "SubtitleView: center+32sp+5pct-bg")
                        }

                        // 解析度切換（若有設定）
                        if (savedResolution != "auto") {
                            val height = savedResolution.toIntOrNull()
                            if (height != null) {
                                switchResolution(height, title)
                            }
                        }

                        player?.addListener(object : Player.Listener {
                            override fun onPlayerError(error: PlaybackException) {
                                runOnUiThread {
                                    val errorCode = error.errorCode
                                    val errorMessage = error.message ?: "未知錯誤"
                                    Toast.makeText(
                                        this@PlayerActivity,
                                        "播放失敗 [錯誤碼：$errorCode]：$errorMessage\\n將返回搜尋結果頁面",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    val resultIntent = Intent()
                                    resultIntent.putExtra("position", intent.getIntExtra("position", -1))
                                    setResult(RESULT_OK, resultIntent)
                                    finish()
                                }
                            }
                        })
                    } else {
                        runOnUiThread {
                            Toast.makeText(
                                this@PlayerActivity,
                                "無法獲取播放資訊，將返回搜尋結果頁面",
                                Toast.LENGTH_LONG
                            ).show()
                            val resultIntent = Intent()
                            resultIntent.putExtra("position", intent.getIntExtra("position", -1))
                            setResult(RESULT_OK, resultIntent)
                            finish()
                        }
                    }
                } else {
                    val error = result.exceptionOrNull()?.message ?: "未知錯誤"
                    runOnUiThread {
                        Toast.makeText(
                            this@PlayerActivity,
                            "播放失敗 [錯誤：$error]，將返回搜尋結果頁面",
                            Toast.LENGTH_LONG
                        ).show()
                        val resultIntent = Intent()
                        resultIntent.putExtra("position", intent.getIntExtra("position", -1))
                        setResult(RESULT_OK, resultIntent)
                        finish()
                    }
                }
            }
        }
    }

    private fun buildMediaItem(playbackInfo: com.noadytv.python.PlaybackInfo, title: String): MediaItem {
        return if (playbackInfo.isDash && playbackInfo.manifestUrl != null) {
            MediaItem.Builder()
                .setUri(playbackInfo.manifestUrl!!)
                .setMimeType("application/dash+xml")
                .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
                .build()
        } else if (!playbackInfo.isDash && playbackInfo.manifestUrl != null) {
            MediaItem.Builder()
                .setUri(playbackInfo.manifestUrl!!)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
                .build()
        } else if (playbackInfo.isDash && playbackInfo.videoUrl != null && playbackInfo.audioUrl != null) {
            MediaItem.Builder()
                .setUri(playbackInfo.videoUrl!!)
                .setMimeType("application/dash+xml")
                .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
                .build()
        } else if (playbackInfo.singleFileUrl != null) {
            MediaItem.fromUri(Uri.parse(playbackInfo.singleFileUrl!!)).buildUpon()
                .setUri(Uri.parse(playbackInfo.singleFileUrl!!))
                .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
                .build()
        } else if (playbackInfo.videoUrl != null) {
            MediaItem.fromUri(Uri.parse(playbackInfo.videoUrl!!)).buildUpon()
                .setUri(Uri.parse(playbackInfo.videoUrl!!))
                .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
                .build()
        } else {
            val url = playbackInfo.audioUrl ?: playbackInfo.videoUrl
            MediaItem.fromUri(Uri.parse(url!!)).buildUpon()
                .setUri(Uri.parse(url!!))
                .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
                .build()
        }
    }

    private fun buildMediaItemWithSubtitle(playbackInfo: PlaybackInfo, title: String, subtitleTrack: SubtitleTrack?): MediaItem {
        android.util.Log.d("NoadYTTV_Subtitle", "buildMediaItemWithSubtitle: subtitleTrack=$subtitleTrack")
        if (subtitleTrack != null) {
            android.util.Log.d("NoadYTTV_Subtitle", "  subtitle url=${subtitleTrack.url} language=${subtitleTrack.language} ext=${subtitleTrack.ext}")
        }
        val builder = MediaItem.Builder()

        if (playbackInfo == null || playbackInfo.manifestUrl != null) {
            val uriToUse = playbackInfo?.manifestUrl
                ?: run { "https://dummy.invalid" }  // switchResolution 時不帶 PlaybackInfo
            if (uriToUse != "https://dummy.invalid") {
                builder.setUri(Uri.parse(uriToUse))
                    .setMimeType(if (playbackInfo?.isDash == true) "application/dash+xml" else null)
            }
        } else if (playbackInfo.singleFileUrl != null) {
            builder.setUri(Uri.parse(playbackInfo.singleFileUrl!!))
        } else if (playbackInfo.videoUrl != null) {
            builder.setUri(Uri.parse(playbackInfo.videoUrl!!))
        } else {
            val url = playbackInfo?.audioUrl ?: playbackInfo?.videoUrl
            builder.setUri(Uri.parse(url!!))
        }

        subtitleTrack?.let { sub ->
            val mimeType = when (sub.ext.lowercase()) {
                "vtt" -> "text/vtt"
                "srt" -> "application/x-subrip"
                "ttml" -> "application/ttml+xml"
                "json3" -> "application/json"
                else -> null
            }
            // 優先使用本地快取路徑（避免 YouTube API URL 過期）
            val subtitleUri = if (!sub.local_path.isNullOrEmpty() && java.io.File(sub.local_path!!).exists()) {
                Uri.fromFile(java.io.File(sub.local_path))
            } else {
                Uri.parse(sub.url ?: return@let)
            }
            android.util.Log.d("NoadYTTV_Subtitle", "buildMediaItemWithSubtitle: lang=${sub.language} using_local=${sub.local_path != null} ext=${sub.ext}")
            val configBuilder = SubtitleConfiguration.Builder(subtitleUri)
                .setLanguage(sub.language)
                .setLabel(sub.name)
            if (mimeType != null) {
                configBuilder.setMimeType(mimeType)
            }
            builder.setSubtitleConfigurations(listOf(configBuilder.build()))
        }

        builder.setMediaMetadata(
            MediaMetadata.Builder().setTitle(title).build()
        )
        return builder.build()
    }

    // ==================== 全域設定偏好（由 SettingsActivity 設定） ====================

    private fun switchResolution(targetHeight: Int, videoTitle: String) {
        val videoId = intent.getStringExtra("videoId") ?: return
        val allFormats = currentVideoInfo?.formats ?: run {
            android.util.Log.e("NoadYTTV_Subtitle", "switchResolution: no currentVideoInfo")
            return
        }
        // 過濾：高度<=目標 + URL有效（至少50字元，排除假URL如"dash"/"m3u8"）
        val validCands = allFormats.filter { f ->
            f.height != null && f.height!! <= targetHeight
                && f.url != null && f.url!!.length >= 50
                && !f.url!!.contains("signatureDecode")
        }
        android.util.Log.d("NoadYTTV_Subtitle",
            "switchResolution: target=${targetHeight}p total=${allFormats.size} validUrls=${validCands.size}")
        // Debug: print each candidate detail
        validCands.take(5).forEachIndexed { i, f ->
            android.util.Log.d("NoadYTTV_Subtitle",
                "switchResolution cand#${i+1}: fmt=${f.format_id} h=${f.height} proto=${f.protocol} tbr=${f.tbr} " +
                "url_len=${f.url?.length ?: 0} manifest_url=${if(f.manifest_url.isNullOrEmpty())"null" else "has(${f.manifest_url?.length})"}")
        }

        // 優先選擇非-m3u8 且無 manifest_url 的單一檔案格式（最可精確控管解析度）
        val directUrlCands = validCands.filter { f ->
            !(f.protocol?.contains("m3u8", true) ?: false)
                && f.manifest_url.isNullOrEmpty()
        }.sortedByDescending { it.tbr ?: 0.0 }

        // Fallback: 高 bitrate m3u8_manifest（若沒有直接 URL）
        val m3u8Cands = validCands.filter { f ->
            f.protocol?.contains("m3u8", true) == true
                && f.manifest_url != null
        }.sortedByDescending { it.tbr ?: 0.0 }

        val chosen = directUrlCands.firstOrNull() ?: m3u8Cands.firstOrNull()

        if (chosen == null) {
            android.util.Log.e("NoadYTTV_Subtitle",
                "switchResolution: NO VALID CANDIDATE for h<=${targetHeight}p")
            // Debug: print available heights
            val heights = allFormats.mapNotNull { it.height }.distinct().sorted().reversed()
            android.util.Log.e("NoadYTTV_Subtitle", "Available heights: ${heights.take(10)}")
            return
        }

        // 找字幕軌道
        val prefs = getSharedPreferences(PREF_FILE, MODE_PRIVATE)
        val savedSubtitle = prefs.getString(KEY_SUBTITLE, "none") ?: "none"
        var subtitleTrackForSwitch: SubtitleTrack? = null
        if (savedSubtitle != "none" && currentVideoInfo != null) {
            subtitleTrackForSwitch = currentVideoInfo!!.subtitles.firstOrNull { it.language == savedSubtitle }
                ?: currentVideoInfo!!.subtitles.firstOrNull { sub ->
                    sub.language.startsWith(savedSubtitle, ignoreCase = true) ||
                        savedSubtitle.startsWith(sub.language, ignoreCase = true)
                }
            android.util.Log.d("NoadYTTV_Subtitle", "switchResolution found subtitle=${subtitleTrackForSwitch?.language}")
        }

        val player = playerController?.getExoPlayer()
        // 用 manifest_url（若有）或 url 都行，ExoPlayer 會自動偵測
        val url = chosen.manifest_url ?: chosen.url ?: ""
        val isManifest = chosen.manifest_url != null
        android.util.Log.d("NoadYTTV_Subtitle",
            "switchResolution: fmt=${chosen.format_id} h=${chosen.height} proto=${chosen.protocol} " +
            "tbr=${chosen.tbr} manifest=$isManifest url_len=${url.length}")

        // 如果是 adaptive manifest（m3u8_native / dash），fallback 到 web client 拿 progressive URL
        var finalUrl = url
        if (isManifest) {
            com.noadytv.python.YouTubePythonBridge.getInstance().setLastTargetVideoId(videoId)
            val webResult = kotlinx.coroutines.runBlocking {
                com.noadytv.python.YouTubePythonBridge.getInstance().getTargetVideoUrlWeb()
            }
            webResult.onSuccess { progressiveUrl ->
                android.util.Log.d("NoadYTTV_Subtitle",
                    "switchResolution: web-client fallback got url_len=${progressiveUrl.length}")
                finalUrl = progressiveUrl
            }.onFailure { err ->
                android.util.Log.e("NoadYTTV_Subtitle",
                    "switchResolution: web-client fallback failed: ${err.message}")
            }
        }

        val miBuilder = MediaItem.Builder().setUri(Uri.parse(finalUrl))
            .setMediaMetadata(MediaMetadata.Builder().setTitle(videoTitle).build())
        subtitleTrackForSwitch?.let { sub ->
            val subUri = Uri.parse(sub.url ?: sub.local_path ?: "")
            if (subUri.toString().isNotEmpty()) {
                miBuilder.setSubtitleConfigurations(
                    listOf(SubtitleConfiguration.Builder(subUri)
                        .setLanguage(sub.language).setLabel(sub.name)
                        .setMimeType("text/vtt").build())
                )
            }
        }
        player?.setMediaItem(miBuilder.build())
        // 若選到的是 adaptive manifest，強制限制最大解析度，讓 ExoPlayer 不動態升級
        if (isManifest || chosen.protocol?.contains("m3u8", true) == true) {
            val targetH = chosen.height ?: targetHeight
            val params = player?.trackSelector?.parameters?.buildUpon()?.apply {
                setMaxVideoSize(targetH, Int.MAX_VALUE)
            }?.build()
            if (params != null) {
                player?.trackSelector?.setParameters(params)
                android.util.Log.d("NoadYTTV_Subtitle",
                    "switchResolution: capped maxVideoSize=${targetH}x${Int.MAX_VALUE}")
            }
        }
        player?.prepare()
        player?.play()
        android.util.Log.d("NoadYTTV_Subtitle", "switchResolution playing ${chosen.height}p ${chosen.protocol}")
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            val intent = Intent()
            intent.putExtra("position", intent.getIntExtra("position", -1))
            setResult(RESULT_OK, intent)
            finish()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        super.onDestroy()
        playerController?.release()
    }
}
