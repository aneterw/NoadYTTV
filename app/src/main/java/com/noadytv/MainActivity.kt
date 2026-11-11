package com.noadytv

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
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
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
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
import kotlinx.coroutines.Job
import android.graphics.Color
import android.graphics.drawable.ColorDrawable

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by lazy {
        ViewModelProvider(this)[MainViewModel::class.java]
    }

    private var imageLoader: ImageLoader? = null
    private var searchJob: Job? = null
    private var selectedPosition: Int = -1
    private var isReturningFromPlayer = false
    private var lastSearchQuery = ""
    private var clearSearchJob: Job? = null
    private var lastSelectedView: View? = null

    // 搜尋歷史（本地持久化）
    private val searchHistory = mutableListOf<String>()
    private lateinit var prefs: SharedPreferences
    private lateinit var historyContainer: LinearLayout
    private lateinit var historyScroll: HorizontalScrollView

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
                        searchingText.text = "搜索完成：找到 0 个影片 $lastSearchQuery"
                        searchingText.visibility = android.view.View.VISIBLE
                    } else {
                        val adapter = VideoAdapter(this@MainActivity, videos, imageLoader!!)
                        resultsGrid.adapter = adapter
                        resultsGrid.onItemClickListener = android.widget.AdapterView.OnItemClickListener { _, _, position, _ ->
                            val selectedVideo = videos[position]
                            playVideo(selectedVideo, position)
                        }
                        resultsGrid.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                            override fun onItemSelected(parent: android.widget.AdapterView<*>, view: android.view.View?, position: Int, id: Long) {
                                // 將上一個選中的 item 視覺恢復正常
                                lastSelectedView?.let { prev ->
                                    val prevTitle = prev.findViewById<TextView>(R.id.video_title)
                                    val prevThumbnail = prev.findViewById<ImageView>(R.id.video_thumbnail)
                                    prevTitle.setTextColor(android.graphics.Color.parseColor("#FFFFFF"))
                                    prevThumbnail.setBackgroundResource(android.R.color.transparent)
                                }
                                // 將當前選中的 item 視覺設為焦點狀態
                                view?.let { curr ->
                                    val currTitle = curr.findViewById<TextView>(R.id.video_title)
                                    val currThumbnail = curr.findViewById<ImageView>(R.id.video_thumbnail)
                                    currTitle.setTextColor(android.graphics.Color.parseColor("#FFD700"))
                                    currThumbnail.setBackgroundResource(R.drawable.focus_thumbnail_border)
                                    lastSelectedView = curr
                                }
                            }

                            override fun onNothingSelected(parent: android.widget.AdapterView<*>) {}
                        }
                        searchingText.text = "搜索完成：找到 ${videos.size} 个影片 $lastSearchQuery"
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

        // 初始化搜尋歷史
        prefs = getSharedPreferences("noadytv_prefs", Context.MODE_PRIVATE)
        historyContainer = findViewById(R.id.history_container)
        historyScroll = findViewById(R.id.history_scroll)
        loadSearchHistory()
        showSearchHistory()
    }

    private fun performSearch() {
        val searchInput = findViewById<EditText>(R.id.search_input)
        val query = searchInput.text.toString()
        if (query.isNotBlank()) {
            lastSearchQuery = query
            addSearchHistory(query)
            val searchingText = findViewById<TextView>(R.id.searching_text)
            searchingText.text = "正在搜索：$query"
            searchingText.visibility = android.view.View.VISIBLE
            viewModel.search(query)

            // 搜尋觸發後 0.5 秒自動清除關鍵字輸入框，但保留搜尋結果畫面
            clearSearchJob?.cancel()
            clearSearchJob = CoroutineScope(Dispatchers.Main).launch {
                delay(500)
                searchInput.setText("")
            }
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

    // ========== 搜尋歷史相關方法 ==========

    private fun loadSearchHistory() {
        val json = prefs.getString("search_history", null)
        if (json != null && json.isNotBlank()) {
            val items = json.split(",".toRegex()).filter { it.isNotBlank() }
            searchHistory.clear()
            searchHistory.addAll(items)
        }
    }

    private fun saveSearchHistory() {
        val json = searchHistory.joinToString(",")
        prefs.edit().putString("search_history", json).apply()
    }

    private fun addSearchHistory(query: String) {
        // 移除重複（如果已存在先移除）
        searchHistory.removeAll { it == query }
        // 加到最前面
        searchHistory.add(0, query)
        // 最多保留 20 個
        if (searchHistory.size > 20) {
            searchHistory.removeAt(searchHistory.size - 1)
        }
        saveSearchHistory()
        showSearchHistory()
    }

    private fun removeSearchHistory(query: String) {
        searchHistory.removeAll { it == query }
        saveSearchHistory()
        showSearchHistory()
    }

    private fun clearAllSearchHistory() {
        searchHistory.clear()
        saveSearchHistory()
        showSearchHistory()
    }

    private fun showSearchHistory() {
        historyContainer.removeAllViews()

        if (searchHistory.isEmpty()) {
            historyScroll.visibility = android.view.View.GONE
            return
        }

        historyScroll.visibility = android.view.View.VISIBLE

        for (keyword in searchHistory) {
            // 每個歷史關鍵字用一個水平容器包住，讓關鍵字與 X 按鈕緊貼
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            val rowParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            rowParams.setMargins(4, 0, 4, 0)
            row.layoutParams = rowParams

            // 關鍵字按鈕 → 點擊直接搜尋
            val btn = Button(this)
            btn.text = keyword
            btn.textSize = 12f
            btn.setTextColor(android.graphics.Color.WHITE)
            btn.setBackgroundResource(R.drawable.focus_history_btn)
            val padding = 6
            btn.setPadding(padding, 2, padding, 2)
            val btnMargin = 4
            val btnParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            btnParams.setMargins(0, 0, 4, 0)
            btn.layoutParams = btnParams
            btn.setOnClickListener {
                val searchInput = findViewById<EditText>(R.id.search_input)
                searchInput.setText(keyword)
                searchInput.setSelection(keyword.length)
                performSearch()
            }

            // X 刪除按鈕 → 只刪除這個關鍵字（用 TextView 避免 Button 預設最小寬度，支援焦點）
            val delBtn = TextView(this)
            delBtn.text = "×"
            delBtn.textSize = 10f
            delBtn.setTextColor(android.graphics.Color.WHITE)
            delBtn.setGravity(android.view.Gravity.CENTER)
            delBtn.setFocusable(true)
            delBtn.setFocusableInTouchMode(true)
            // 焦點變色：深紅色
            val delBgSelector = android.graphics.drawable.StateListDrawable()
            val delBgFocused = android.graphics.drawable.GradientDrawable()
            delBgFocused.setColor(android.graphics.Color.parseColor("#FF5252"))
            delBgFocused.setCornerRadius(6f)
            val delBgNormal = android.graphics.drawable.GradientDrawable()
            delBgNormal.setColor(android.graphics.Color.parseColor("#AA0000"))
            delBgNormal.setCornerRadius(6f)
            delBgSelector.addState(intArrayOf(android.R.attr.state_focused), delBgFocused)
            delBgSelector.addState(intArrayOf(), delBgNormal)
            delBtn.background = delBgSelector
            val delPadding = 2
            delBtn.setPadding(delPadding, 1, delPadding, 1)
            val delParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            delParams.setMargins(0, 0, 4, 0)
            delBtn.layoutParams = delParams
            delBtn.setOnClickListener {
                removeSearchHistory(keyword)
            }

            row.addView(btn)
            row.addView(delBtn)
            historyContainer.addView(row)
        }

        // 清除所有歷史按鈕
        if (searchHistory.isNotEmpty()) {
            val clearBtn = Button(this)
            clearBtn.text = "清除所有歷史"
            clearBtn.textSize = 14f
            clearBtn.setTextColor(android.graphics.Color.WHITE)
            clearBtn.setBackgroundResource(R.drawable.focus_history_btn)
            val padding = 10
            clearBtn.setPadding(padding, 6, padding, 6)
            val margin = 12
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(margin, 0, margin, 0)
            clearBtn.layoutParams = params
            clearBtn.setOnClickListener {
                clearAllSearchHistory()
            }
            historyContainer.addView(clearBtn)
        }
    }
}
