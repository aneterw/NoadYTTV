package com.noadytv.channel

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
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
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import coil.ImageLoader
import coil.request.ImageRequest
import com.noadytv.R
import com.noadytv.MainActivity
import com.noadytv.PlayerActivity
import com.noadytv.python.VideoSearchResult
import com.noadytv.channel.data.ChannelSearchResult
import com.noadytv.utils.AppLocale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

class ChannelSearchActivity : ComponentActivity() {

    // ── ViewModel（與 MainViewModel 完全獨立）──────────────
    private val viewModel: ChannelSearchViewModel by lazy {
        ViewModelProvider(this)[ChannelSearchViewModel::class.java]
    }

    // ── 狀態 ────────────────────────────────────────────
    private var imageLoader: ImageLoader? = null
    private var selectedPosition: Int = -1
    // 記住進入頻道前的頻道位置，返回時還原（不與影片 position 混用）
    private var savedChannelPosition: Int = -1
    private var isReturningFromPlayer = false
    // 標記是否正在等待播放器返回（用於區分 player return vs 一般 back）
    private var isPlayerReturnPending = false
    private var lastSearchQuery = ""
    private var clearSearchJob: Job? = null
    private var lastSelectedView: View? = null
    private var lastBackPressTime = 0L
    companion object { private const val BACK_PRESS_TIMEOUT = 2000L }

    // ── 兩個獨立計時器（完全不互通）───────────────────────────
    // 頻道搜尋計時：按搜尋按鈕開始，頻道列表完成時停止，回 L1 時直接顯示上次結果
    private var channelSearchStartTime = 0L
    private var L1_statusText = ""        // 上一次的搜尋狀態列文字（包含耗時 + 關鍵字）
    // 單頻道影片載入計時：按進頻道方格開始，影片列表完成時停止，完全獨立於頻道搜尋時間
    private var videoLoadStartTime = 0L
    private var L2_statusText = ""

    // 層級切換時保存的 channel/video adapter，避免重建導致焦點跑掉
    private var currentChannelAdapter: com.noadytv.channel.ChannelSearchActivity.ChannelAdapter? = null
    private var currentVideoAdapter: com.noadytv.channel.ChannelSearchActivity.VideoAdapter? = null

    // 層級：1 = 搜尋頻道；2 = 在頻道內搜尋影片
    private var currentLayer: Int = 1

    // Layer 2 時存放當前頻道的影片列表（與 MainViewModel.searchResults 平行）
    private var channelVideoList: List<VideoSearchResult> = emptyList()
    // Layer 1 時存放當前頻道列表（供 back handler 回到 L1 時恢復 click 監聽器）
    private var currentChannelList: List<ChannelSearchResult> = emptyList()

    // ── 搜尋歷史（key="channel_search_history"，與影片歷史隔離）
    private val channelSearchHistory = mutableListOf<String>()
    private lateinit var channelPrefs: android.content.SharedPreferences
    private lateinit var historyContainer: LinearLayout
    private lateinit var historyScroll: HorizontalScrollView
    private lateinit var searchInput: EditText
    private lateinit var resultsGrid: GridView

    // ── Activity Result（相同模式：从 PlayerActivity 返回）─────────────────
    private val playerActivityLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isReturningFromPlayer = false
        isPlayerReturnPending = false  // 立即清除，避免後續誤用
        val position = result.data?.getIntExtra("position", selectedPosition) ?: selectedPosition
        android.util.Log.d("ChannelSearch", "playerReturn: resultCode=${result.resultCode}, videoAdapter=${currentVideoAdapter != null}, channelAdapter=${currentChannelAdapter != null}, currentLayer=$currentLayer, position=$position")
        // 只要還有影片列表快取，就恢復 L2（影片列表），不受 resultCode 或 pending 標記限制
        // 這樣即使播放器因錯誤/自動播放結束而非預期 finish，也不會誤落回搜索框
        if (currentVideoAdapter != null) {
            // 播放器一定從 L2 開啟，直接恢復影片列表即可
            val grid = findViewById<GridView>(R.id.results_grid)
            grid.post {
                try {
                    currentLayer = 2
                    resultsGrid.adapter = currentVideoAdapter
                    // 恢復 L2 的 click/selected 監聽器（播放影片 + 標題轉黃）
                    resultsGrid.onItemClickListener = android.widget.AdapterView.OnItemClickListener { _, _, position, _ ->
                        android.util.Log.d("ChannelSearch", "L2 itemClick after playerReturn: position=$position")
                        if (position >= 0 && position < channelVideoList.size) {
                            playVideo(channelVideoList[position], position)
                        }
                    }
                    resultsGrid.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(parent: android.widget.AdapterView<*>, view: View?, position: Int, id: Long) {
                            highlightVideo(view)
                        }
                        override fun onNothingSelected(parent: android.widget.AdapterView<*>) {
                            resetLastVideoHighlight()
                        }
                    }
                    android.util.Log.d("ChannelSearch", "playerReturn restore L2: adapter=video, layer=2")
                    resultsGrid.post {
                        resultsGrid.setSelection(position.coerceAtLeast(0))
                        resultsGrid.requestFocus()
                        // 不呼叫 searchInput.requestFocus()，保持焦點在影片方格
                        // 還原當時的影片狀態列文字（包含耗時）
                        if (L2_statusText.isNotBlank()) findViewById<TextView>(R.id.searching_text).text = L2_statusText
                        findViewById<TextView>(R.id.searching_text).visibility = View.VISIBLE
                    }
                } catch (_: Exception) {
                    searchInput.requestFocus()
                }
            }
        } else {
            // 沒有影片緩存 → 聚焦搜索框
            android.util.Log.d("ChannelSearch", "playerReturn ignored: no videoAdapter, currentLayer=$currentLayer")
            searchInput.requestFocus()
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_channel_search)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        imageLoader = ImageLoader.Builder(this).build()

        searchInput = findViewById<EditText>(R.id.search_input)
        val searchButton = findViewById<Button>(R.id.search_button)
        resultsGrid = findViewById<GridView>(R.id.results_grid)
        val searchingText = findViewById<TextView>(R.id.searching_text)
        val emptyState = findViewById<TextView>(R.id.empty_state_text)

        // 頻道頁標題徽章（與 MainActivity 影片搜尋區隔）
        findViewById<TextView>(R.id.channel_badge).visibility = View.VISIBLE

        // 設定按鈕 → SettingsActivity（與 MainActivity 相同路徑）
        val settingsBtn = findViewById<ImageView>(R.id.settings_btn)
        settingsBtn.setOnClickListener {
            startActivity(Intent(this, com.noadytv.settings.SettingsActivity::class.java))
        }
        settingsBtn.setOnFocusChangeListener { _, hasFocus ->
            settingsBtn.setBackgroundColor(if (hasFocus) Color.parseColor("#FFD700") else Color.TRANSPARENT)
        }

        // 讀取來自 Intent 的初始搜尋關鍵字（若有的話）
        val intentQuery = intent.getStringExtra("query") ?: ""
        if (intentQuery.isNotBlank()) {
            lastSearchQuery = intentQuery
            addChannelSearchHistory(intentQuery)
            searchingText.text = getString(R.string.searching_channels, intentQuery)
            searchingText.visibility = View.VISIBLE
            doChannelSearch(intentQuery)
        }

        // ── 觀察 Layer 1：頻道搜尋結果 ─────────────────────
        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                viewModel.channelResults.collect { channels ->
                    // currentLayer != 1 表示我們在影片層，頻道結果不更新畫面
                    if (currentLayer != 1) return@collect
                    android.util.Log.d("ChannelSearch", "channelResults: layer=$currentLayer, count=${channels.size}")
                    resultsGrid.adapter = null
                    searchingText.visibility = View.GONE
                    if (channels.isEmpty()) {
                        emptyState.text = "${getString(R.string.no_results)}\n\n${getString(R.string.voice_search_tip)}"
                        emptyState.visibility = View.VISIBLE
                    } else {
                        emptyState.visibility = View.GONE
                        val chAdapter = ChannelAdapter(this@ChannelSearchActivity, channels, imageLoader!!)
                        currentChannelAdapter = chAdapter
                        currentChannelList = channels  // 儲存頻道列表供 back handler 恢復 click 監聽器
                        resultsGrid.adapter = chAdapter
                        // Channel click → 進入 L2（載入該頻道的影片列表）
                        resultsGrid.onItemClickListener = android.widget.AdapterView.OnItemClickListener { _, _, position, _ ->
                            android.util.Log.d("ChannelSearch", "L1 itemClick: position=$position, totalChannels=${channels.size}, layer=$currentLayer")
                            enterChannel(channels[position], position)
                        }
                        resultsGrid.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                            override fun onItemSelected(parent: android.widget.AdapterView<*>, view: View?, position: Int, id: Long) {
                                highlightChannel(view)
                            }
                            override fun onNothingSelected(parent: android.widget.AdapterView<*>) {
                                resetLastChannelHighlight()
                            }
                        }
                        // 儲存 L1 的完整狀態（含耗時 + 關鍵字），回退時直接還原，不重新計算時間
                        val elapsed = "搜尋耗時 ${"%.1f".format((System.currentTimeMillis() - channelSearchStartTime) / 1000.0f)}s"
                        L1_statusText = "$elapsed，找到 ${channels.size} 個頻道 - $lastSearchQuery"
                        searchingText.text = L1_statusText
                        searchingText.visibility = View.VISIBLE
                    }
                }
            }
        }

        // ── 觀察頻道搜尋錯誤 ───────────────────────────────
        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                viewModel.channelSearchError.collect { error ->
                    if (error != null && currentLayer == 1) {
                        android.util.Log.e("ChannelSearch", "channel search error: $error")
                        searchingText.visibility = View.GONE
                        emptyState.text = "搜尋失敗：$error\n\n${getString(R.string.voice_search_tip)}"
                        emptyState.visibility = View.VISIBLE
                    }
                }
            }
        }

        // ── 觀察 Layer 2：頻道內影片結果（與 MainViewModel.searchResults 同等處理）
        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                viewModel.videoResults.collect { videos ->
                    if (currentLayer != 2) return@collect
                    android.util.Log.d("ChannelSearch", "videoResults: layer=$currentLayer, count=${videos.size}")
                    resultsGrid.adapter = null
                    searchingText.visibility = View.GONE
                    if (videos.isEmpty()) {
                        emptyState.text = "${getString(R.string.no_results)}\n\n${getString(R.string.voice_search_tip)}"
                        emptyState.visibility = View.VISIBLE
                    } else {
                        emptyState.visibility = View.GONE
                        val vidAdapter = VideoAdapter(this@ChannelSearchActivity, videos, imageLoader!!)
                        currentVideoAdapter = vidAdapter
                        resultsGrid.adapter = vidAdapter
                        resultsGrid.onItemClickListener = android.widget.AdapterView.OnItemClickListener { _, _, position, _ ->
                            android.util.Log.d("ChannelSearch", "L2 itemClick: position=$position, totalVideos=${videos.size}, layer=$currentLayer")
                            playVideo(videos[position], position)
                        }
                        resultsGrid.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                            override fun onItemSelected(parent: android.widget.AdapterView<*>, view: View?, position: Int, id: Long) {
                                highlightVideo(view)
                            }
                            override fun onNothingSelected(parent: android.widget.AdapterView<*>) {
                                resetLastVideoHighlight()
                            }
                        }
                        // 儲存 L2 的完整狀態（含耗時），回播放器再回來時直接還原
                        // 只有在 L2_statusText 為空時才重新計算耗時（從播放器返回時不重新計算，避免累加）
                        if (L2_statusText.isBlank()) {
                            val elapsed = "搜尋耗時 ${"%.1f".format((System.currentTimeMillis() - videoLoadStartTime) / 1000.0f)}s"
                            L2_statusText = "$elapsed，找到 ${videos.size} 部影片 - in ${viewModel.currentChannelTitle}"
                        }
                        searchingText.text = L2_statusText
                        searchingText.visibility = View.VISIBLE

                        // 首次載入影片列表時（非從播放器返回）也把焦點放會搜索框
                        if (!isReturningFromPlayer) {
                            searchInput.requestFocus()
                        }
                        // 從 PlayerActivity 返回時恢復焦點（影片層級使用 selectedPosition）
                        if (isReturningFromPlayer && selectedPosition >= 0) {
                            resultsGrid.post {
                                resultsGrid.setSelection(selectedPosition)
                                resultsGrid.requestFocusFromTouch()
                                resultsGrid.requestFocus()
                                isReturningFromPlayer = false
                            }
                        }
                    }
                }
            }
        }

        // ── 搜尋按鈕 / Enter 鍵觸發 ───────────────────────
        searchButton.setOnClickListener { performSearch(searchInput, searchingText) }
        searchInput.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER
                        && event.action == KeyEvent.ACTION_DOWN)) {
                performSearch(searchInput, searchingText)
                true
            } else false
        }

        // ── 啟動時焦點在搜尋框 + 顯示鍵盤 ─────────────────
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        searchInput.requestFocus()
        imm.showSoftInput(searchInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)

        // ── 搜尋框方向鍵：DOWN 由 Android 自然流動到歷史關鍵字列；結果出現時才跳 Grid
        searchInput.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                // 有頻道/影片結果時直接跳 Grid，否則讓焦點自然流到下方歷史按鈕
                if (resultsGrid.adapter != null) {
                    resultsGrid.post {
                        resultsGrid.requestFocus()
                        resultsGrid.requestFocusFromTouch()
                        val sel = resultsGrid.checkedItemCount.takeIf { it > 0 }?.let { it - 1 } ?: 0
                        resultsGrid.setSelection(sel)
                    }
                    true
                } else false
            } else if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                // 從歷史按鈕往上按，回到搜尋框
                if (searchInput.hasFocus()) false else { searchInput.requestFocus(); true }
            } else false
        }

        // ── 初始化歷史記錄 ───────────────────────────────
        channelPrefs = getSharedPreferences("noadytv_prefs", Context.MODE_PRIVATE)
        historyContainer = findViewById(R.id.history_container)
        historyScroll = findViewById(R.id.history_scroll)
        loadChannelSearchHistory()
        showChannelSearchHistory()

        // 表頭顯示版本號
        findViewById<TextView>(R.id.version_text).text = "v0.8.6"
    }

    // ── 搜尋觸發（Layer 1 = 搜尋頻道；Layer 2 = 載入該頻道影片）
    private fun performSearch(searchInput: EditText, searchingText: TextView) {
        val query = searchInput.text.toString()
        if (query.isBlank()) return

        lastSearchQuery = query
        addChannelSearchHistory(query)

        if (currentLayer == 1) {
            // L1 搜尋：啟動頻道計時器，重置任何舊狀態
            channelSearchStartTime = System.currentTimeMillis()
            searchingText.text = getString(R.string.searching_channels, query)
            searchingText.visibility = View.VISIBLE
            doChannelSearch(query)
        } else {
            // Layer 2 按搜索：先回到 L1 再執行頻道搜尋
            savedChannelPosition = selectedPosition.coerceAtLeast(0)
            resetLastVideoHighlight()
            clearSearchJob?.cancel()
            clearSearchJob = null
            resultsGrid.post {
                currentLayer = 1
                viewModel.clearVideoResults()
                resultsGrid.adapter = currentChannelAdapter
                resultsGrid.onItemSelectedListener = null
                val chPos = savedChannelPosition.takeIf { it >= 0 } ?: 0
                resultsGrid.post {
                    resultsGrid.setSelection(chPos)
                    resultsGrid.requestFocus()
                    searchInput.requestFocus()
                }
            }
            // 回到 L1 後開始新的頻道搜尋計時
            channelSearchStartTime = System.currentTimeMillis()
            searchingText.text = getString(R.string.searching_channels, query)
            searchingText.visibility = View.VISIBLE
            doChannelSearch(query)
        }

        // ── 搜尋後 0.5s 清空輸入框（與 MainActivity 完全一致）──
        clearSearchJob?.cancel()
        clearSearchJob = CoroutineScope(Dispatchers.Main).launch {
            delay(500)
            if (currentLayer != 1 && currentLayer != 2) return@launch
            searchInput.setText("")
            searchInput.requestFocus()
        }
    }

    private fun doChannelSearch(query: String) {
        savedChannelPosition = -1
        channelSearchStartTime = System.currentTimeMillis()
        val prefs = getSharedPreferences("player_settings", MODE_PRIVATE)
        val maxResults = prefs.getInt("pref_channel_search_limit", 30)
        viewModel.searchChannels(query, maxResults)
    }

    // ── 進入頻道（Layer 1 → Layer 2）─────────────────────
    private fun enterChannel(channel: ChannelSearchResult, position: Int) {
        android.util.Log.d("ChannelSearch", "enterChannel: channel=${channel.title}, position=$position")
        savedChannelPosition = position   // 記憶當前頻道位置
        selectedPosition = position
        viewModel.currentChannelUrl = "https://www.youtube.com/channel/${channel.id}/videos"
        viewModel.currentChannelTitle = channel.title
        // 切換層級前先清除上一層的視覺高亮，避免多標題同時發黃
        resetLastChannelHighlight()
        lastSelectedView = null
        currentLayer = 2
        // 影片載入計時器：從點擊方格開始計算，與頻道搜尋時間完全獨立
        videoLoadStartTime = System.currentTimeMillis()
        android.util.Log.d("ChannelSearch", "enterChannel done: currentLayer=2, savedChannelPosition=$savedChannelPosition")
        // 立即把焦點還給搜索框，避免 Focus 卡在設定按鈕
        searchInput.requestFocus()
        val searchingText = findViewById<TextView>(R.id.searching_text)
        val emptyState = findViewById<TextView>(R.id.empty_state_text)
        searchingText.text = getString(R.string.searching_videos_in_channel)
        searchingText.visibility = View.VISIBLE
        emptyState.visibility = View.GONE
        val prefs = getSharedPreferences("player_settings", MODE_PRIVATE)
        val maxVideos = prefs.getInt("pref_channel_video_limit", prefs.getInt("pref_search_limit", 40))
        viewModel.loadChannelVideos("https://www.youtube.com/channel/${channel.id}/videos", channel.title, maxVideos)
    }

    // ── 播放影片（與 MainActivity.playVideo 完全相同）
    private fun playVideo(video: VideoSearchResult, position: Int) {
        selectedPosition = position
        Toast.makeText(this, getString(R.string.playing, video.title), Toast.LENGTH_SHORT).show()
        isReturningFromPlayer = true
        isPlayerReturnPending = true  // 標記正在等待播放器返回
        val intent = Intent(this, PlayerActivity::class.java)
        intent.putExtra("videoId", video.id)
        intent.putExtra("videoTitle", video.title)
        intent.putExtra("position", position)
        // 傳遞 playlist（防範空列表時崩潰）
        if (channelVideoList.isNotEmpty()) {
            intent.putStringArrayListExtra("playlist_ids", java.util.ArrayList(channelVideoList.map { it.id }))
            intent.putStringArrayListExtra("playlist_titles", java.util.ArrayList(channelVideoList.map { it.title }))
        }
        try {
            playerActivityLauncher.launch(intent)
        } catch (_: Exception) {
            // 若播放器無法啟動，直接返回頻道列表
            isReturningFromPlayer = false
            Toast.makeText(this, getString(R.string.toast_playback_info_failed), Toast.LENGTH_SHORT).show()
        }
    }

    // ── 視覺高亮（與 MainActivity 相同模式）────────────
    private fun highlightChannel(view: View?) {
        lastSelectedView?.let { prev ->
            val prevName = prev.findViewById<TextView>(R.id.channel_name)
            val prevThumb = prev.findViewById<ImageView>(R.id.channel_thumbnail)
            prevName.setTextColor(Color.parseColor("#FFFFFF"))
            prevThumb.setBackgroundResource(android.R.color.transparent)
        }
        view?.let { curr ->
            val currName = curr.findViewById<TextView>(R.id.channel_name)
            val currThumb = curr.findViewById<ImageView>(R.id.channel_thumbnail)
            currName.setTextColor(Color.parseColor("#FFD700"))
            currThumb.setBackgroundResource(R.drawable.focus_thumbnail_border)
            lastSelectedView = curr
        }
    }

    private fun resetLastChannelHighlight() {
        lastSelectedView?.let { prev ->
            val prevName = prev.findViewById<TextView>(R.id.channel_name)
            val prevThumb = prev.findViewById<ImageView>(R.id.channel_thumbnail)
            prevName.setTextColor(Color.parseColor("#FFFFFF"))
            prevThumb.setBackgroundResource(android.R.color.transparent)
        }
        lastSelectedView = null
    }

    private fun highlightVideo(view: View?) {
        lastSelectedView?.let { prev ->
            val prevTitle = prev.findViewById<TextView>(R.id.video_title)
            val prevThumb = prev.findViewById<ImageView>(R.id.video_thumbnail)
            prevTitle.setTextColor(Color.parseColor("#FFFFFF"))
            prevThumb.setBackgroundResource(android.R.color.transparent)
        }
        view?.let { curr ->
            val currTitle = curr.findViewById<TextView>(R.id.video_title)
            val currThumb = curr.findViewById<ImageView>(R.id.video_thumbnail)
            currTitle.setTextColor(Color.parseColor("#FFD700"))
            currThumb.setBackgroundResource(R.drawable.focus_thumbnail_border)
            lastSelectedView = curr
        }
    }

    private fun resetLastVideoHighlight() {
        lastSelectedView?.let { prev ->
            val prevTitle = prev.findViewById<TextView>(R.id.video_title)
            val prevThumb = prev.findViewById<ImageView>(R.id.video_thumbnail)
            prevTitle.setTextColor(Color.parseColor("#FFFFFF"))
            prevThumb.setBackgroundResource(android.R.color.transparent)
        }
        lastSelectedView = null
    }

    // ── 頻道搜尋歷史（key 完全獨立於影片歷史）────────────
    private fun loadChannelSearchHistory() {
        val json = channelPrefs.getString("channel_search_history", null)
        if (json != null && json.isNotBlank()) {
            channelSearchHistory.clear()
            channelSearchHistory.addAll(json.split(",").filter { it.isNotBlank() })
        }
    }

    private fun saveChannelSearchHistory() {
        channelPrefs.edit().putString("channel_search_history", channelSearchHistory.joinToString(",")).apply()
    }

    private fun addChannelSearchHistory(query: String) {
        channelSearchHistory.removeAll { it == query }
        channelSearchHistory.add(0, query)
        if (channelSearchHistory.size > 20) channelSearchHistory.removeAt(channelSearchHistory.size - 1)
        saveChannelSearchHistory()
        showChannelSearchHistory()
    }

    private fun removeChannelSearchHistory(query: String) {
        channelSearchHistory.removeAll { it == query }
        saveChannelSearchHistory()
        showChannelSearchHistory()
        searchInput.requestFocus()
    }

    private fun clearAllChannelSearchHistory() {
        channelSearchHistory.clear()
        saveChannelSearchHistory()
        showChannelSearchHistory()
        findViewById<EditText>(R.id.search_input).requestFocus()
    }

    private fun showChannelSearchHistory() {
        historyContainer.removeAllViews()
        if (channelSearchHistory.isEmpty()) {
            historyScroll.visibility = View.GONE
        } else {
            historyScroll.visibility = View.VISIBLE
            for (keyword in channelSearchHistory) {
                // ── row（與 MainActivity 完全相同）
                val row = LinearLayout(this)
                row.orientation = LinearLayout.HORIZONTAL
                val rowParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                rowParams.setMargins(4, 0, 4, 0)
                row.layoutParams = rowParams

                // ── 關鍵字按鈕（與 MainActivity 完全相同）
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
                btnParams.setMargins(0, 0, btnMargin, 0)
                btn.layoutParams = btnParams
                btn.setOnClickListener {
                    val searchInput = findViewById<EditText>(R.id.search_input)
                    searchInput.setText(keyword)
                    searchInput.setSelection(keyword.length)
                    searchInput.requestFocus()
                    performSearch(searchInput, findViewById(R.id.searching_text))
                }

                // ── X 刪除按鈕（與 MainActivity 完全相同）
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
                    removeChannelSearchHistory(keyword)
                    val searchInput = findViewById<EditText>(R.id.search_input)
                    searchInput.requestFocus()
                }

                row.addView(btn)
                row.addView(delBtn)
                historyContainer.addView(row)
            }
            // ── 清除所有歷史按鈕
            if (channelSearchHistory.isNotEmpty()) {
                val clearBtn = Button(this)
                clearBtn.text = getString(R.string.clear_all_history)
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
                    clearAllChannelSearchHistory()
                }
                historyContainer.addView(clearBtn)
            }
        }
    }

    // ── Adapter：頻道卡片（結構與 VideoAdapter 完全相同）
    private inner class ChannelAdapter(
        context: Context,
        private val channels: List<ChannelSearchResult>,
        private val imageLoader: ImageLoader
    ) : ArrayAdapter<ChannelSearchResult>(context, R.layout.item_channel_card, channels) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_channel_card, parent, false)
            val ch = channels[position]
            view.findViewById<TextView>(R.id.channel_name).text = ch.title
            val subsText = if (ch.follower_count != null && ch.follower_count > 0) {
                "${String.format("%,d", ch.follower_count)} 訂閱者"
            } else ""
            view.findViewById<TextView>(R.id.channel_subs).text = subsText
            view.findViewById<TextView>(R.id.channel_description).text = ch.description?.takeIf { it.isNotBlank() } ?: ""
            ch.thumbnail?.let { url ->
                imageLoader.enqueue(ImageRequest.Builder(context)
                    .data(url)
                    .target { drawable ->
                        view.findViewById<ImageView>(R.id.channel_thumbnail).setImageDrawable(drawable)
                    }
                    .placeholder(ColorDrawable(Color.parseColor("#333333")))
                    .error(ColorDrawable(Color.parseColor("#333333")))
                    .build())
            } ?: run {
                view.findViewById<ImageView>(R.id.channel_thumbnail)
                    .setImageDrawable(ColorDrawable(Color.parseColor("#333333")))
            }
            return view
        }
    }

    // ── Adapter：影片卡片（與 MainActivity.VideoAdapter 完全相同，複用 item_video.xml）
    private inner class VideoAdapter(
        context: Context,
        private val videos: List<VideoSearchResult>,
        private val imageLoader: ImageLoader
    ) : ArrayAdapter<VideoSearchResult>(context, R.layout.item_video, videos) {
        init { channelVideoList = videos }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_video, parent, false)
            val v = videos[position]
            view.findViewById<TextView>(R.id.video_title).text = v.title
            view.findViewById<TextView>(R.id.video_channel).text = v.channel ?: getString(R.string.unknown_channel)
            val durText = if (v.duration != null && v.duration > 0) {
                val m = v.duration!! / 60; val s = v.duration!! % 60
                java.lang.String.format("%02d:%02d", m, s)
            } else "--:--"
            view.findViewById<TextView>(R.id.video_duration).text = durText
            v.thumbnail?.let { url ->
                imageLoader.enqueue(ImageRequest.Builder(context)
                    .data(url).target { drawable -> view.findViewById<ImageView>(R.id.video_thumbnail).setImageDrawable(drawable) }
                    .placeholder(ColorDrawable(Color.parseColor("#333333")))
                    .error(ColorDrawable(Color.parseColor("#333333"))).build())
            } ?: run { view.findViewById<ImageView>(R.id.video_thumbnail)
                .setImageDrawable(ColorDrawable(Color.parseColor("#333333"))) }
            return view
        }
    }

    override fun onResume() {
        super.onResume()
        val saved = AppLocale.getSavedLocale(this)
        val current = resources.configuration.locale?.toLanguageTag()
        if (saved.replace("_", "-").lowercase() != current?.replace("_", "-")?.lowercase()) recreate()
        // 搜尋標的若已切回「影片」，離開頻道頁回到影片主頁（避免從設定返回後仍停留在頻道）
        val targetPrefs = getSharedPreferences("player_settings", MODE_PRIVATE)
        if ("video" == targetPrefs.getString("pref_search_target", "video")) {
            startActivity(Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            finish()
            return
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        imageLoader?.shutdown()
        clearSearchJob?.cancel()
        clearSearchJob = null
    }

    /** 統一後退處理：TV 遙控 KEYCODE_BACK 走 onKeyDown，部分裝置也呼叫 onBackPressed */
    override fun onBackPressed() {
        android.util.Log.d("ChannelSearch", "onBackPressed delegate to handleBack")
        // 委托給 handleBack，避免雙重執行
        if (!handleBack()) {
            super.onBackPressed()
        }
    }

    /** 實際的返回鍵邏輯（由 onKeyDown 和 onBackPressed 共用） */
    private fun handleBack(): Boolean {
        android.util.Log.d("ChannelSearch", "handleBack: currentLayer=$currentLayer, savedPosition=$savedChannelPosition")
        if (currentLayer == 2) {
            // Layer 2 → 回 L1：清空影片層狀態，恢復頻道列表
            resetLastVideoHighlight()
            clearSearchJob?.cancel()
            clearSearchJob = null
            isPlayerReturnPending = false  // 一般按返回鍵：清除 pending 標記
            resultsGrid.post {
                currentLayer = 1
                viewModel.clearVideoResults()
                channelVideoList = emptyList()
                selectedPosition = -1
                android.util.Log.d("ChannelSearch", "backHandler(L2→L1): restoring channel adapter, position=$savedChannelPosition")
                // 恢復 L1 頻道列表
                resultsGrid.adapter = currentChannelAdapter
                // 恢復 L1 的 click/selected 監聽器（進入頻道 + 標題轉黃）
                resultsGrid.onItemClickListener = android.widget.AdapterView.OnItemClickListener { _, _, position, _ ->
                    if (position >= 0 && position < currentChannelList.size) {
                        enterChannel(currentChannelList[position], position)
                    }
                }
                resultsGrid.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: android.widget.AdapterView<*>, view: View?, position: Int, id: Long) {
                        highlightChannel(view)
                    }
                    override fun onNothingSelected(parent: android.widget.AdapterView<*>) {
                        resetLastChannelHighlight()
                    }
                }
                val selPos = savedChannelPosition.takeIf { it >= 0 } ?: 0
                resultsGrid.post {
                    resultsGrid.setSelection(selPos)
                    resultsGrid.requestFocus()
                    // 不呼叫 searchInput.requestFocus()，保持焦點在頻道方格
                    // 還原 L1 當時的狀態列（含耗時 + 關鍵字），完全不重新計時
                    if (L1_statusText.isNotBlank()) findViewById<TextView>(R.id.searching_text).text = L1_statusText
                    findViewById<TextView>(R.id.searching_text).visibility = View.VISIBLE
                }
            }
            return true
        }
        // Layer 1 按返回鍵：雙按退出 APP，不跳頁、不重置偏好
        val now = System.currentTimeMillis()
        if (now - lastBackPressTime < BACK_PRESS_TIMEOUT) {
            finish()
        } else {
            lastBackPressTime = now
            Toast.makeText(this, getString(R.string.toast_press_back_again), Toast.LENGTH_SHORT).show()
        }
        return true  // 已處理返回鍵，避免 onBackPressed 調用 super.onBackPressed() 直接 finish 回主頁
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return handleBack()
        }
        return super.onKeyDown(keyCode, event)
    }
}
