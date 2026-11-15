# NoadYTTV 字幕診斷日誌流程

## 五個定點日誌（由前到後，每步確認一關）

播放影片後從 logcat 抓取：`adb logcat -d | grep "Nood_Y\|NoadYTTV_Subtitle"`

| # | Tag + Key | 時機 | 正常值 | 異常說明 |
|---|----------|------|--------|---------|
| 1 | `CSUB_INFO` | `getVideoInfo` 回傳後立刻 | `subCount=N langs=[ja]` 或 `langs=[en]` 等 | subCount=0 → Python 層沒抓到字幕（yt-dlp timedtext + YTA 都失敗） |
| 2 | `CSUB_EFFECTIVE_LANG` | Kotlin auto 模式解析後 | `saved=auto → effective=ja detected=[ja]` | effective=en 但 subtitles 無 en → 下一關 match=null |
| 3 | `applySavedPrefs matching` | subtitle 匹配執行後 | `matched=ja url=https://...` | matched=null → 語言碼對不上或 Python 未回傳該語言軌 |
| 4 | `CSUB_AFTER_PREPARE` | `player.prepare()` 後立即 | `subLang=ja` | subLang=null → 上面 match=null，MediaItem 根本沒裝字幕 |
| 5 | `CSUB_TRACKS` | ExoPlayer `onTracksChanged` 觸發 | `groups=3+` （audio+video+text） | groups=2 → ExoPlayer 完全沒把 VTT 當成文字軌；groups=3+ → 進下一步 |

## 診斷決策樹

```
CSUB_INFO subCount=0
  └─ 問題在 Python：YouTubeTranscriptApi 失敗、timedtext URL 全部過期
     → 檢查 python.stderr 中是否有 "[Subtitle] YTA [*] failed: ..."

CSUB_INFO subCount>=1，但 CSUB_EFFECTIVE_LANG effective=? 不符預期
  └─ Python detected_languages 沒偵測到正確語言
     → 通常因為 YouTube 沒有自動語音標記；fallback 到 "en"

CSUB_EFFECTIVE_LANG effective=ja，但 applySavedPrefs matched=null
  └─ Python 回傳的 language code（如 "ja"）和 detected_languages[0]（也 "ja"）一致
     但 currentVideoInfo.subtitles 裡沒有 language=="ja" 的項目
     → 檢查 Python 端 _extract_subtitles 是否跳過了該語言（_is_valid_lang filter）

applySavedPrefs matched=ja，但 CSUB_AFTER_PREPARE subLang=null
  └─ buildMediaItemWithSubtitle 內 local_path 不存在且 url 為空
     → 用 adb shell 檢查 /data/data/com.noadytv/files/chaquopy/AssetFinder/app/subtitles/
        是否有對應 .vtt 檔案

CSUB_AFTER_PREPARE subLang=ja，但 CSUB_TRACKS groups=3（無變化）
  └─ ExoPlayer 載入文字軌失敗。最常见原因：
     · URI scheme 錯誤（Uri.parse vs Uri.fromFile）
     · VTT 檔案格式損壞（WEBVTT header 缺失）
     · MIME type 不匹配（explicitly set to wrong value）

CSUB_TRACKS groups=4+，但看不到字幕
  └─ onCues callback 未觸發或 SubtitleView style 設定有誤
     → 檢查 setApplyEmbeddedStyles(false)、setPosition/setLine、backgroundColor reflection hack
```

## CNN 英文新聞 vs 日文新聞差異

- **CNN 英文**：YouTube timedtext API 有 `en-US` 手動字幕，Python `_extract_subtitles` 在 Path A（timedtext）就能找到 → 不需要走 YTA fallback
- **日文新聞（如 NHK）**：YouTube timedtext API 通常沒有 `ja` 手動字幕，必須走 Path B（YTA fallback）→ 需要 `youtube-transcript-api` Python package
- **兩者共同要求**：`detected_languages` 非空才能推導出 effectiveLang；若 Python 完全無法連接 YouTube（網路或限流），兩類影片都會 subCount=0

## 清除 App Data 的必要性

每次修改字幕邏輯後務必清除 app data 讓快取重置：
```bash
adb -s 192.168.1.95:5555 shell pm clear com.noadytv
```
不清除的話舊的 VTT 快取持續存在，Python 端的新邏輯不會重新觸發，看起來像是「修復沒生效」。
