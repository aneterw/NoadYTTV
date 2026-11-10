package com.noadytv

import android.content.Intent
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
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.ui.PlayerView

class PlayerActivity : ComponentActivity() {

    private val viewModel: MainViewModel by lazy {
        ViewModelProvider(this)[MainViewModel::class.java]
    }

    private var playerController: PlayerController? = null
    private var playerView: PlayerView? = null

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
                val result = viewModel.getPlaybackInfo(videoId)
                if (result.isSuccess) {
                    val playbackInfo = result.getOrNull()
                    if (playbackInfo != null) {
                        val player = playerController?.getExoPlayer()
                        val mediaItem = buildMediaItem(playbackInfo, videoTitle)
                        player?.setMediaItem(mediaItem)
                        player?.prepare()
                        player?.play()
                        player?.addListener(object : Player.Listener {
                            override fun onPlayerError(error: PlaybackException) {
                                runOnUiThread {
                                    val errorCode = error.errorCode
                                    val errorMessage = error.message ?: "未知錯誤"
                                    Toast.makeText(
                                        this@PlayerActivity,
                                        "播放失敗 [錯誤碼：$errorCode]：$errorMessage\n將返回搜尋結果頁面",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    // 播放失敗時返回搜尋結果頁面，保持位置
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
            // DASH 自適應串流 (VOD)：使用 MPD manifest URL
            MediaItem.Builder()
                .setUri(playbackInfo.manifestUrl!!)
                .setMimeType("application/dash+xml")
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(title)
                        .build()
                )
                .build()
        } else if (!playbackInfo.isDash && playbackInfo.manifestUrl != null) {
            // HLS / m3u8_native 自適應串流：不設 mimeType，讓 ExoPlayer 自動偵測
            MediaItem.Builder()
                .setUri(playbackInfo.manifestUrl!!)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(title)
                        .build()
                )
                .build()
        } else if (playbackInfo.isDash && playbackInfo.videoUrl != null && playbackInfo.audioUrl != null) {
            // 備選：DASH 無 manifest_url 時嘗試用影片 URL
            MediaItem.Builder()
                .setUri(playbackInfo.videoUrl!!)
                .setMimeType("application/dash+xml")
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(title)
                        .build()
                )
                .build()
        } else if (playbackInfo.singleFileUrl != null) {
            // 單一檔案漸進式格式
            MediaItem.fromUri(Uri.parse(playbackInfo.singleFileUrl!!)).buildUpon()
                .setUri(Uri.parse(playbackInfo.singleFileUrl!!))
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(title)
                        .build()
                )
                .build()
        } else if (playbackInfo.videoUrl != null) {
            // 僅有影片 URL
            MediaItem.fromUri(Uri.parse(playbackInfo.videoUrl!!)).buildUpon()
                .setUri(Uri.parse(playbackInfo.videoUrl!!))
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(title)
                        .build()
                )
                .build()
        } else {
            // 最後手段
            val url = playbackInfo.audioUrl ?: playbackInfo.videoUrl
            MediaItem.fromUri(Uri.parse(url!!)).buildUpon()
                .setUri(Uri.parse(url!!))
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(title)
                        .build()
                )
                .build()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // 返回到搜索結果頁面，恢復焦點到原來的影片
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
