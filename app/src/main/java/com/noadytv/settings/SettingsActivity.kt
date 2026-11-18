package com.noadytv.settings

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AppCompatActivity
import com.noadytv.MainActivity
import com.noadytv.R
import com.noadytv.utils.AppLocale

class SettingsActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    private val PREF_FILE = "player_settings"
        private val KEY_SUBTITLE = "pref_subtitle"
        private val KEY_RESOLUTION = "pref_resolution"
        private val KEY_LANGUAGE = "pref_language"
        private val KEY_AUTO_PLAY = "pref_auto_play_next"
        private val KEY_SEARCH_LIMIT = "pref_search_limit"
        private val KEY_CHANNEL_SEARCH_LIMIT = "pref_channel_search_limit"
        private val KEY_SEARCH_TARGET = "pref_search_target"
        private val KEY_FOCUS_INDEX = "pref_focus_index"

        private lateinit var prefs: SharedPreferences
        private var currentFocusIndex = 0  // 0=字幕, 1=解析度, 2=語言, 3=影片搜尋上限, 4=頻道搜尋上限, 5=自動播放, 6=搜尋標的, 7=返回主頁

        // 焦點項目引用
        private lateinit var subtitleRow: LinearLayout
        private lateinit var resolutionRow: LinearLayout
        private lateinit var languageRow: LinearLayout
        private lateinit var searchLimitRow: LinearLayout
        private lateinit var channelSearchLimitRow: LinearLayout
        private lateinit var autoPlayRow: LinearLayout
        private lateinit var searchTargetRow: LinearLayout
        private lateinit var btnBack: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        prefs = getSharedPreferences(PREF_FILE, MODE_PRIVATE)

        val root = FrameLayout(this)
        root.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )

        val bg = GradientDrawable()
        bg.setColor(Color.parseColor("#000000"))
        root.background = bg

        val menu = LinearLayout(this)
        menu.orientation = LinearLayout.VERTICAL
        menu.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        menu.gravity = Gravity.CENTER

        val titleView = TextView(this).apply {
            text = getString(R.string.settings_title)
            textSize = 18f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 24)
            gravity = Gravity.CENTER
        }

        subtitleRow = buildSettingRow(getString(R.string.subtitles), getCurrentSubtitleLabel(), 0)
        resolutionRow = buildSettingRow(getString(R.string.resolution_label), getCurrentResolutionLabel(), 1)
        languageRow = buildSettingRow(getString(R.string.language_label), getCurrentLanguageLabel(), 2)
        searchLimitRow = buildSettingRow(getString(R.string.search_limit_label), getCurrentSearchLimitLabel(), 3)
        channelSearchLimitRow = buildSettingRow(getString(R.string.channel_search_limit_label), getCurrentChannelSearchLimitLabel(), 4)
        autoPlayRow = buildSettingRow(getString(R.string.auto_play_next), getAutoPlayLabel(), 5)
        searchTargetRow = buildSettingRow(getString(R.string.search_target_label), getCurrentSearchTargetLabel(), 6)

        btnBack = Button(this).apply {
            text = getString(R.string.settings_back_home)
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(24, 8, 24, 8)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#333333"))
                setCornerRadius(8f)
            }
            setAllCaps(false)
            setFocusable(true)
            setFocusableInTouchMode(true)
            setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) currentFocusIndex = 6
                updateFocusVisual()
            }
            setOnClickListener {
                goBackToMain()
            }
        }

        menu.addView(titleView)
        menu.addView(subtitleRow)
        menu.addView(resolutionRow)
        menu.addView(languageRow)
        menu.addView(searchLimitRow)
        menu.addView(channelSearchLimitRow)
        menu.addView(autoPlayRow)
        menu.addView(searchTargetRow)
        menu.addView(btnBack)

        root.addView(menu)
                setContentView(root)

                // 恢復上次的焦點位置
                currentFocusIndex = prefs.getInt(KEY_FOCUS_INDEX, 0)
                updateFocusVisual()
                when (currentFocusIndex) {
                    0 -> subtitleRow.requestFocus()
                    1 -> resolutionRow.requestFocus()
                    2 -> languageRow.requestFocus()
                    3 -> searchLimitRow.requestFocus()
                    4 -> channelSearchLimitRow.requestFocus()
                    5 -> autoPlayRow.requestFocus()
                    6 -> searchTargetRow.requestFocus()
                    7 -> btnBack.requestFocus()
                }
            }

    override fun onResume() {
        super.onResume()
        val saved = AppLocale.getSavedLocale(this)
        val current = resources.configuration.locale?.toLanguageTag()
        val s = saved.replace("_", "-").lowercase()
        val c = current?.replace("_", "-")?.lowercase() ?: ""
        if (s != c) {
            recreate()
        }
    }

    private fun buildSettingRow(label: String, value: String, index: Int): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        row.gravity = Gravity.CENTER
        row.setPadding(0, 12, 0, 12)
        row.setFocusable(true)
        row.setFocusableInTouchMode(true)
        row.descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
        // 初始背景：深色
        row.background = makeRowBg(false)

        val labelView = TextView(this).apply {
            text = label
            textSize = 16f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 16, 0)
        }

        val valueView = TextView(this).apply {
            text = value
            textSize = 16f
            setTextColor(Color.parseColor("#FFD700"))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#44000000"))
                setCornerRadius(6f)
            }
            setPadding(20, 8, 20, 8)
        }

        row.addView(labelView)
        row.addView(valueView)

        // 焦點變化時更新背景視覺（黃色高亮）
        row.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) currentFocusIndex = index
            updateFocusVisual()
        }

        // 點擊切換
        row.setOnClickListener {
            when (index) {
                0 -> toggleSubtitle()
                1 -> toggleResolution()
                2 -> toggleLanguage()
                3 -> toggleSearchLimit()
                4 -> toggleChannelSearchLimit()
                5 -> toggleAutoPlay()
                6 -> toggleSearchTarget()
            }
        }

        return row
    }

    // 建立 row 背景 drawable
    private fun makeRowBg(focused: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            setColor(if (focused) Color.parseColor("#FFD700") else Color.parseColor("#1a1a1a"))
            setCornerRadius(8f)
        }
    }

    // 更新焦點視覺：當前焦點項目背景變亮黃
    private fun updateFocusVisual() {
        val rows = arrayOf(subtitleRow, resolutionRow, languageRow, searchLimitRow, channelSearchLimitRow, autoPlayRow, searchTargetRow, btnBack)

        rows.forEachIndexed { index, view ->
            val isFocused = index == currentFocusIndex
            view.background = makeRowBg(isFocused)

            if (view is Button) {
                view.setTextColor(if (isFocused) Color.BLACK else Color.WHITE)
            } else {
                // LinearLayout：子文字顏色隨焦點切換
                for (i in 0 until (view as LinearLayout).childCount) {
                    val child = (view as LinearLayout).getChildAt(i)
                    if (child is TextView) {
                        if ((view as LinearLayout).indexOfChild(child) == 0) {
                            // label：焦點時黑色，非焦點時白色
                            child.setTextColor(if (isFocused) Color.BLACK else Color.WHITE)
                        } else {
                            // value：焦點時黑色，非焦點時黃色
                            child.setTextColor(if (isFocused) Color.BLACK else Color.parseColor("#FFD700"))
                        }
                    }
                }
            }
        }
    }

    private fun moveFocus(delta: Int) {
        val total = 8  // 字幕, 解析度, 語言, 影片搜尋上限, 頻道搜尋上限, 自動播放, 搜尋標的, 返回主頁
        currentFocusIndex = (currentFocusIndex + delta + total) % total
        updateFocusVisual()

        when (currentFocusIndex) {
            0 -> subtitleRow.requestFocus()
            1 -> resolutionRow.requestFocus()
            2 -> languageRow.requestFocus()
            3 -> searchLimitRow.requestFocus()
            4 -> channelSearchLimitRow.requestFocus()
            5 -> autoPlayRow.requestFocus()
            6 -> searchTargetRow.requestFocus()
            7 -> btnBack.requestFocus()
        }
    }

    // 字幕語言列表（循環切換，僅代碼；標籤由 getSubtitleLangLabel 本地化）
    private val SUBTITLE_LANGS = listOf(
        "none",
        "auto",
        "zh-Hant",
        "zh-Hans",
        "en",
        "ja-orig",
        "ja",
        "ko",
        "fr",
        "es",
        "de"
    )

    private fun getSubtitleLangLabel(code: String): String = when (code) {
        "none" -> getString(R.string.subtitle_none)
        "auto" -> getString(R.string.subtitle_auto)
        "zh-Hant" -> getString(R.string.subtitle_zh_tw)
        "zh-Hans" -> getString(R.string.subtitle_zh_cn)
        "en" -> getString(R.string.subtitle_en)
        "ja-orig" -> getString(R.string.subtitle_ja_orig)
        "ja" -> getString(R.string.subtitle_ja)
        "ko" -> getString(R.string.subtitle_ko)
        "fr" -> getString(R.string.subtitle_fr)
        "es" -> getString(R.string.subtitle_es)
        "de" -> getString(R.string.subtitle_de)
        else -> code
    }

    private fun getCurrentSubtitleLabel(): String {
        val lang = prefs.getString(KEY_SUBTITLE, "auto")
        return getSubtitleLangLabel(lang ?: "auto")
    }

    private fun getSubtitleLangName(code: String): String {
        return getSubtitleLangLabel(code)
    }

    private fun getCurrentResolutionLabel(): String {
        val res = prefs.getString(KEY_RESOLUTION, "auto")
        return when (res) {
            "auto" -> getString(R.string.resolution_auto)
            else -> "$res${getString(R.string.quality_suffix)}"
        }
    }

    private fun getCurrentLanguageLabel(): String {
        val lang = prefs.getString(KEY_LANGUAGE, "zh_TW") ?: "zh_TW"
        return AppLocale.getLanguageDisplayName(this, lang)
    }

    private fun toggleSubtitle() {
            val current = prefs.getString(KEY_SUBTITLE, "none")
            val currentIndex = SUBTITLE_LANGS.indexOf(current)
            val nextIndex = (currentIndex + 1) % SUBTITLE_LANGS.size
            val newCode = SUBTITLE_LANGS[nextIndex]
            prefs.edit().putString(KEY_SUBTITLE, newCode).apply()
            prefs.edit().putInt(KEY_FOCUS_INDEX, currentFocusIndex).apply()
            recreate()
        }

        private fun toggleResolution() {
            val current = prefs.getString(KEY_RESOLUTION, "auto")
            val newValue = when (current) {
                "auto" -> "1080"
                "1080" -> "720"
                "720" -> "480"
                "480" -> "auto"
                else -> "auto"
            }
            prefs.edit().putString(KEY_RESOLUTION, newValue).apply()
            prefs.edit().putInt(KEY_FOCUS_INDEX, currentFocusIndex).apply()
            recreate()
        }

        private fun toggleLanguage() {
            val current = prefs.getString(KEY_LANGUAGE, "zh_TW")
            val newValue = when (current) {
                "zh_TW" -> "zh_CN"
                "zh_CN" -> "en"
                "en" -> "ja"
                "ja" -> "ko"
                "ko" -> "es"
                "es" -> "zh_TW"
                else -> "zh_TW"
            }
            AppLocale.saveAndApplyLocale(this, newValue)
            prefs.edit().putInt(KEY_FOCUS_INDEX, currentFocusIndex).apply()
            recreate()
        }

        private fun getAutoPlayLabel(): String {
            return if (prefs.getBoolean(KEY_AUTO_PLAY, true)) getString(R.string.on) else getString(R.string.off)
        }

        private fun toggleAutoPlay() {
            val current = prefs.getBoolean(KEY_AUTO_PLAY, true)
            prefs.edit().putBoolean(KEY_AUTO_PLAY, !current).apply()
            prefs.edit().putInt(KEY_FOCUS_INDEX, currentFocusIndex).apply()
            recreate()
        }

        // 影片搜尋上限列表（循環切換）
        private val SEARCH_LIMITS = listOf(40, 60, 80, 100, 120)

        private fun getCurrentSearchLimitLabel(): String {
            val limit = prefs.getInt(KEY_SEARCH_LIMIT, 60)
            return limit.toString()
        }

        // 頻道搜尋上限列表（循環切換）
        private val CHANNEL_SEARCH_LIMITS = listOf(30, 40, 50)

        private fun getCurrentChannelSearchLimitLabel(): String {
            val limit = prefs.getInt(KEY_CHANNEL_SEARCH_LIMIT, 30)
            return limit.toString()
        }

        private fun toggleChannelSearchLimit() {
            val current = prefs.getInt(KEY_CHANNEL_SEARCH_LIMIT, 30)
            val currentIndex = CHANNEL_SEARCH_LIMITS.indexOf(current)
            val nextIndex = (currentIndex + 1) % CHANNEL_SEARCH_LIMITS.size
            val newLimit = CHANNEL_SEARCH_LIMITS[nextIndex]
            prefs.edit().putInt(KEY_CHANNEL_SEARCH_LIMIT, newLimit).apply()
            prefs.edit().putInt(KEY_FOCUS_INDEX, currentFocusIndex).apply()
            recreate()
        }

        private fun toggleSearchLimit() {
            val current = prefs.getInt(KEY_SEARCH_LIMIT, 60)
            val currentIndex = SEARCH_LIMITS.indexOf(current)
            val nextIndex = (currentIndex + 1) % SEARCH_LIMITS.size
            val newLimit = SEARCH_LIMITS[nextIndex]
            prefs.edit().putInt(KEY_SEARCH_LIMIT, newLimit).apply()
            prefs.edit().putInt(KEY_FOCUS_INDEX, currentFocusIndex).apply()
            recreate()
        }

        // 搜尋標的：影片 / 頻道（循環切換）
        private val SEARCH_TARGETS = listOf("video", "channel")
        private fun getCurrentSearchTargetLabel(): String {
            return when (prefs.getString(KEY_SEARCH_TARGET, "video")) {
                "channel" -> getString(R.string.search_target_channel)
                else -> getString(R.string.search_target_video)
            }
        }
        private fun toggleSearchTarget() {
            val current = prefs.getString(KEY_SEARCH_TARGET, "video") ?: "video"
            val next = if (current == "video") "channel" else "video"
            prefs.edit().putString(KEY_SEARCH_TARGET, next).apply()
            prefs.edit().putInt(KEY_FOCUS_INDEX, currentFocusIndex).apply()
            recreate()
        }

        private fun goBackToMain() {
            val target = prefs.getString(KEY_SEARCH_TARGET, "video")
            // 終止設定頁面，讓上層 Activity 的 onResume 根據最新偏好決定顯示內容
            finish()
        }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    moveFocus(1)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_UP -> {
                    moveFocus(-1)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER -> {
                    // 當前焦點項目執行點擊
                    when (currentFocusIndex) {
                        0 -> toggleSubtitle()
                        1 -> toggleResolution()
                        2 -> toggleLanguage()
                        3 -> toggleSearchLimit()
                        4 -> toggleAutoPlay()
                        5 -> toggleSearchTarget()
                        6 -> goBackToMain()
                    }
                    return true
                }
                KeyEvent.KEYCODE_BACK -> {
                    goBackToMain()
                    return true
                }
            }
            return super.onKeyDown(keyCode, event)
        }
}
