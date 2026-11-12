package com.noadytv.settings

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

class SettingsActivity : AppCompatActivity() {

    private val PREF_FILE = "player_settings"
    private val KEY_SUBTITLE = "pref_subtitle"
    private val KEY_RESOLUTION = "pref_resolution"
    private val KEY_LANGUAGE = "pref_language"

    private lateinit var prefs: SharedPreferences
    private var currentFocusIndex = 0  // 0=字幕, 1=解析度, 2=語言, 3=返回主頁

    // 焦點項目引用
    private lateinit var subtitleRow: LinearLayout
    private lateinit var resolutionRow: LinearLayout
    private lateinit var languageRow: LinearLayout
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
            text = "播放設定"
            textSize = 18f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 24)
            gravity = Gravity.CENTER
        }

        subtitleRow = buildSettingRow("字幕", getCurrentSubtitleLabel(), 0)
        resolutionRow = buildSettingRow("解析度", getCurrentResolutionLabel(), 1)
        languageRow = buildSettingRow("介面語言", getCurrentLanguageLabel(), 2)

        btnBack = Button(this).apply {
            text = "返回主頁"
            textSize = 14f
            setTextColor(Color.BLACK)
            setPadding(24, 8, 24, 8)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#FFD700"))
                setCornerRadius(8f)
            }
            setAllCaps(false)
            setFocusable(true)
            setFocusableInTouchMode(true)
            setOnClickListener {
                goBackToMain()
            }
        }

        menu.addView(titleView)
        menu.addView(subtitleRow)
        menu.addView(resolutionRow)
        menu.addView(languageRow)
        menu.addView(btnBack)

        root.addView(menu)
        setContentView(root)

        // 預設焦點在第一個選項
        updateFocusVisual()
        subtitleRow.requestFocus()
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

        // 點擊切換
        row.setOnClickListener {
            when (index) {
                0 -> toggleSubtitle()
                1 -> toggleResolution()
                2 -> toggleLanguage()
            }
        }

        return row
    }

    // 更新焦點視覺：當前焦點項目背景變亮
    private fun updateFocusVisual() {
        val focusColor = Color.parseColor("#FFD700")
        val normalColor = Color.parseColor("#000000")

        val rows = arrayOf(subtitleRow, resolutionRow, languageRow, btnBack)

        rows.forEachIndexed { index, view ->
            if (index == currentFocusIndex) {
                // 焦點項目：背景變亮黃
                if (view is Button) {
                    (view.background as GradientDrawable).setColor(focusColor)
                    view.setTextColor(Color.BLACK)
                } else {
                    view.setBackgroundColor(focusColor)
                    // 子文字變黑
                    for (i in 0 until (view as LinearLayout).childCount) {
                        val child = (view as LinearLayout).getChildAt(i)
                        if (child is TextView) {
                            child.setTextColor(Color.BLACK)
                        }
                    }
                }
            } else {
                // 非焦點項目：背景黑
                if (view is Button) {
                    (view.background as GradientDrawable).setColor(Color.parseColor("#FFD700"))
                    view.setTextColor(Color.BLACK)
                } else {
                    view.setBackgroundColor(normalColor)
                    for (i in 0 until (view as LinearLayout).childCount) {
                        val child = (view as LinearLayout).getChildAt(i)
                        if (child is TextView) {
                            when (child) {
                                is TextView -> {
                                    if ((view as LinearLayout).indexOfChild(child) == 0) {
                                        child.setTextColor(Color.WHITE)  // label
                                    } else {
                                        child.setTextColor(Color.parseColor("#FFD700"))  // value
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun moveFocus(delta: Int) {
        val total = 4  // 字幕, 解析度, 語言, 返回主頁
        currentFocusIndex = (currentFocusIndex + delta + total) % total
        updateFocusVisual()

        when (currentFocusIndex) {
            0 -> subtitleRow.requestFocus()
            1 -> resolutionRow.requestFocus()
            2 -> languageRow.requestFocus()
            3 -> btnBack.requestFocus()
        }
    }

    // 字幕語言列表（循環切換）
    private val SUBTITLE_LANGS = listOf(
        "none" to "不顯示字幕",
        "zh-Hant" to "繁體中文",
        "zh-Hans" to "简体中文",
        "en" to "英文",
        "ja-orig" to "日語(原版)",
        "ja" to "日語",
        "ko" to "韓語",
        "fr" to "法語",
        "es" to "西班牙語",
        "de" to "德語"
    )

    private fun getCurrentSubtitleLabel(): String {
        val lang = prefs.getString(KEY_SUBTITLE, "none")
        return SUBTITLE_LANGS.firstOrNull { it.first == lang }?.second ?: "不顯示字幕"
    }

    private fun getSubtitleLangName(code: String): String {
        return SUBTITLE_LANGS.firstOrNull { it.first == code }?.second ?: code
    }

    private fun getCurrentResolutionLabel(): String {
        val res = prefs.getString(KEY_RESOLUTION, "auto")
        return when (res) {
            "auto" -> "自動（最高解析度）"
            else -> "${res}p"
        }
    }

    private fun getCurrentLanguageLabel(): String {
        val lang = prefs.getString(KEY_LANGUAGE, "zh_TW")
        return when (lang) {
            "zh_TW" -> "繁體中文"
            "zh_CN" -> "简体中文"
            "en" -> "English"
            "ja" -> "日本語"
            "ko" -> "한국어"
            "es" -> "Español"
            else -> lang ?: "zh_TW"
        }
    }

    private fun toggleSubtitle() {
        val current = prefs.getString(KEY_SUBTITLE, "none")
        val currentIndex = SUBTITLE_LANGS.indexOfFirst { it.first == current }
        val nextIndex = (currentIndex + 1) % SUBTITLE_LANGS.size
        val (newCode, _) = SUBTITLE_LANGS[nextIndex]
        prefs.edit().putString(KEY_SUBTITLE, newCode).apply()
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
        prefs.edit().putString(KEY_LANGUAGE, newValue).apply()
        recreate()
    }

    private fun goBackToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
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
                    3 -> goBackToMain()
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
