package com.noadytv

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.GridView
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import coil.ImageLoader
import coil.request.ImageRequest
import com.noadytv.python.VideoSearchResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job as CoroutineJob
import android.graphics.Color
import android.graphics.drawable.ColorDrawable

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by lazy {
        ViewModelProvider(this)[MainViewModel::class.java]
    }

    private var imageLoader: ImageLoader? = null
    private var searchJob: CoroutineJob? = null
    private var selectedPosition: Int = -1
    private var isReturningFromPlayer = false

    // 使用 Activity Result API 處理從 PlayerActivity 返回
    private val playerActivityLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isReturningFromPlayer = false
        if (result.resultCode == RESULT_OK) {
            val position = result.data?.getIntExtra("position", -1) ?: -1
            if (position >= 0) {
                // 恢復焦點到原來的影片位置
                val resultsGrid = findViewById<GridView>(R.id.results_grid)
                resultsGrid.setSelection(position)
                resultsGrid.requestFocusFromTouch()
                resultsGrid.requestFocus()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        imageLoader = ImageLoader.Builder(this).build()

        val searchInput = findViewById<EditText>(R.id.search_input)
        val searchButton = findViewById<Button>(R.id.search_button)
        val resultsGrid = findViewById<GridView>(R.id.results_grid)
        val searchingText = findViewById<TextView>(R.id.searching_text)

        // 观察搜索结果
        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                viewModel.searchResults.collect { videos ->
                    resultsGrid.adapter = null
                    searchingText.visibility = android.view.View.GONE
                    if (videos.isEmpty()) {
                        resultsGrid.adapter = ArrayAdapter<String>(
                            this@MainActivity,
                            android.R.layout.simple_list_item_1,
                            listOf("无搜索结果")
                        )
                        searchingText.text = "搜索完成：找到 0 个影片"
                        searchingText.visibility = android.view.View.VISIBLE
                    } else {
                        val adapter = VideoAdapter(this@MainActivity, videos, imageLoader!!)
                        resultsGrid.adapter = adapter
                        resultsGrid.onItemClickListener = android.widget.AdapterView.OnItemClickListener { _, _, position, _ ->
                            val selectedVideo = videos[position]
                            playVideo(selectedVideo, position)
                        }
                        searchingText.text = "搜索完成：找到 ${videos.size} 个影片"
                        searchingText.visibility = android.view.View.VISIBLE
                        
                        // 如果是從 PlayerActivity 返回，恢復焦點
                        if (isReturningFromPlayer && selectedPosition >= 0) {
                            resultsGrid.setSelection(selectedPosition)
                            resultsGrid.requestFocusFromTouch()
                            resultsGrid.requestFocus()
                            isReturningFromPlayer = false
                        }
                    }
                }
            }
        }

        // 搜索按钮点击
        searchButton.setOnClickListener {
            performSearch()
        }

        // 输入框按下 OK/Enter 触发搜索
        searchInput.setOnEditorActionListener { _, actionId, event ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                performSearch()
                true
            } else {
                false
            }
        }

        // 输入即搜索（防抖 500ms）- 從 PlayerActivity 返回時不觸發搜尋
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // 如果是從 PlayerActivity 返回，不觸發搜尋
                if (isReturningFromPlayer) return@onTextChanged
                
                searchJob?.cancel()
                searchJob = CoroutineScope(Dispatchers.Main).launch {
                    delay(500)
                    if (s?.isNotBlank() == true) {
                        performSearch()
                    }
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // 启动时焦点在搜索输入框
        searchInput.requestFocus()
    }

    private fun performSearch() {
        val searchInput = findViewById<EditText>(R.id.search_input)
        val query = searchInput.text.toString()
        if (query.isNotBlank()) {
            val searchingText = findViewById<TextView>(R.id.searching_text)
            searchingText.text = "正在搜索：$query"
            searchingText.visibility = android.view.View.VISIBLE
            viewModel.search(query)
        }
    }

    private fun playVideo(video: VideoSearchResult, position: Int) {
        selectedPosition = position
        Toast.makeText(this, "播放：${video.title}", Toast.LENGTH_SHORT).show()
        isReturningFromPlayer = true
        val intent = Intent(this, com.noadytv.PlayerActivity::class.java)
        intent.putExtra("videoId", video.id)
        intent.putExtra("videoTitle", video.title)
        intent.putExtra("position", position)
        playerActivityLauncher.launch(intent)
    }

    override fun onBackPressed() {
        super.onBackPressed()
    }

    override fun onDestroy() {
        super.onDestroy()
        searchJob?.cancel()
        imageLoader?.shutdown()
    }

    // 影片卡片适配器（显示缩图、标题、频道、时长）
    private inner class VideoAdapter(
        context: Context,
        private val videos: List<VideoSearchResult>,
        private val imageLoader: ImageLoader
    ) : ArrayAdapter<VideoSearchResult>(context, R.layout.item_video, videos) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_video, parent, false)

            val video = videos[position]

            val thumbnail = view.findViewById<ImageView>(R.id.video_thumbnail)
            val title = view.findViewById<TextView>(R.id.video_title)
            val channel = view.findViewById<TextView>(R.id.video_channel)
            val duration = view.findViewById<TextView>(R.id.video_duration)

            title.text = video.title
            channel.text = video.channel ?: "未知频道"

            // 时长显示
            val durationText = if (video.duration != null && video.duration > 0) {
                val minutes = video.duration / 60
                val seconds = video.duration % 60
                java.lang.String.format("%02d:%02d", minutes, seconds)
            } else {
                "--:--"
            }
            duration.text = durationText

            // 载入缩图（使用 Coil）
            video.thumbnail?.let { url ->
                imageLoader.enqueue(ImageRequest.Builder(context)
                    .data(url)
                    .target { drawable ->
                        thumbnail.setImageDrawable(drawable)
                    }
                    .placeholder(ColorDrawable(Color.parseColor("#333333")))
                    .error(ColorDrawable(Color.parseColor("#333333")))
                    .build())
            } ?: run {
                thumbnail.setImageDrawable(ColorDrawable(Color.parseColor("#333333")))
            }

            return view
        }
    }
}