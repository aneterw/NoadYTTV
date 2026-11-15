package com.noadytv.utils

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import com.noadytv.R
import java.util.Locale

object AppLocale {
    private const val PREF_FILE = "player_settings"
    private const val KEY_LANGUAGE = "pref_language"

    private val SUPPORTED_LOCALES = mapOf(
        "zh_TW" to "zh",
        "zh_CN" to "zh",
        "en" to "en",
        "ja" to "ja",
        "ko" to "ko",
        "es" to "es"
    )

    private val LOCALE_VARIANTS = mapOf(
        "zh_TW" to "TW",
        "zh_CN" to "CN",
        "en" to "",
        "ja" to "",
        "ko" to "",
        "es" to ""
    )

    /** 從 SharedPreferences 讀取並設定 App locale（在 Activity onCreate super.onCreate 之前呼叫） */
    fun applySavedLocale(context: Context) {
        val prefs = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
        val lang = prefs.getString(KEY_LANGUAGE, "zh_TW") ?: "zh_TW"
        setLocale(context, lang)
    }

    /** 設定指定語言的 locale 並更新 Resources 配置 */
    fun setLocale(context: Context, languageCode: String) {
        val locale = buildLocale(languageCode)
        val resources = context.resources
        val config = Configuration(resources.configuration)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocale(locale)
        } else {
            config.locale = locale
        }
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    /** 保存語言偏好（供 SettingsActivity toggleLanguage 呼叫），新 Activity 會從 attachBaseContext 讀取並應用 locale */
    fun saveAndApplyLocale(context: Context, languageCode: String) {
        context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, languageCode)
            .commit()
    }

    /** 根據語言代碼建立 Locale 物件 */
    private fun buildLocale(languageCode: String): Locale {
        val lang = SUPPORTED_LOCALES[languageCode] ?: "zh"
        val variant = LOCALE_VARIANTS[languageCode] ?: ""
        return when {
            variant.isNotEmpty() -> Locale(lang, variant)
            else -> Locale(lang)
        }
    }

    /** 獲取當前語言顯示名稱 */
    fun getLanguageDisplayName(context: Context, languageCode: String): String {
        return when (languageCode) {
            "zh_TW" -> context.getString(R.string.lang_zh_tw)
            "zh_CN" -> context.getString(R.string.lang_zh_cn)
            "en" -> context.getString(R.string.lang_en)
            "ja" -> context.getString(R.string.lang_ja)
            "ko" -> context.getString(R.string.lang_ko)
            "es" -> context.getString(R.string.lang_es)
            else -> languageCode
        }
    }

    /** 讀取儲存的語言代碼 */
    fun getSavedLocale(context: Context): String {
        return context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, "zh_TW") ?: "zh_TW"
    }

    /** 包裝 Context，使其帶有儲存的 locale（供 Activity.attachBaseContext 覆寫） */
    fun wrap(context: Context): Context {
        val lang = getSavedLocale(context)
        val locale = buildLocale(lang)
        val resources = context.resources
        val config = Configuration(resources.configuration)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocale(locale)
        } else {
            config.locale = locale
        }
        return context.createConfigurationContext(config)
    }
}
