# NoadYTTV - Android TV YouTube 客戶端

> 🔄 [切換至英文版](./README_EN.md)

---

## 一、為什麼開發這個軟件？

我們通常使用 YouTube，是為了進行個人化的搜尋、獲得更好的觀看體驗，或享有更便利的功能。但我們追求的是：**一個簡單、直接的搜尋體驗——想搜尋什麼影片，就直接看什麼影片**。

YouTube 正式網站的版面設計常包含各種分類與推薦內容，版面較為混雜。此外，廣告也會干擾觀看體驗。因此我們開發 NoadYTTV，希望：

- **阻擋煩人的廣告**，提供更純淨的觀看體驗
- **簡單樸素的操作介面**，避免版面混雜
- **快速搜尋並播放** 使用者感興趣的影片
- **加入頻道搜索功能**，讓使用者能更精準地找到感興趣的頻道與影片

### 頻道搜索功能

為了提高搜尋效率，我們加入了 **頻道搜索** 功能。該功能更集中地體現頻道特性，根據使用者的搜尋意圖，聚合訂閱量較高、品質相對較好的頻道。無論是日常瀏覽還是網路推薦的知名頻道，都能幫助使用者快速找到相關影片，更精準地匹配個人興趣，獲得最佳的觀影體驗。

---

## 二、功能特色

- 🔍 **YouTube 影片搜尋** — 適用遙控器的 TV 友善介面
- 📺 **Android TV 最佳化 UI** — 支援 D-pad 焦點導航
- ▶️ **無廣告播放** — 透過 yt-dlp 直接串流擷取，跳過廣告
- 🎮 **完整播放控制** — 播放/暫停、停止、上一集/下一集、畫質、字幕、音軌
- 📱 **Leanback 啟動器支援** — 可在 Android TV 主畫面啟動
- 🚫 **無需 Google Play Services / YouTube API**
- 🔎 **頻道搜索** — 聚合高品質頻道，提升搜尋效率

---

## 三、技術說明

NoadYTTV 是一個專為 Android TV 設計的第三方 YouTube 客戶端，採用以下技術堆疊：

- **Kotlin + Jetpack Compose for TV** — 現代化 TV 介面開發
- **Chaquopy 17.2** — Kotlin 與 Python 之間的橋接
- **yt-dlp** — 影片串流擷取（實現無廣告播放）
- **ExoPlayer (Media3)** — 影片播放引擎
- **Koin** — 相依性注入

### 無廣告播放原理

yt-dlp 會擷取影片的所有可用格式，我們會過濾掉常含廣告的 DASH 串流清單，改為選擇直接的 HTTPS 累進串流（MP4/HLS），由 ExoPlayer 播放。這樣可以繞過 YouTube 在 DASH 層級插入廣告的機制。

### Python 與 Kotlin 整合

```
Kotlin (YouTubeRepository)
    ↓ Coroutines (Dispatchers.IO)
Kotlin (YouTubePythonBridge)
    ↓ Chaquopy PyObject.callAttr()
Python (youtube_extractor.YouTubeExtractor)
    ↓ yt_dlp.YoutubeDL
YouTube → JSON → Kotlin 資料類別
```

---

## 四、部署與使用指南

### 系統需求

- Android Studio Koala (2024.1.1) 或更新版本
- JDK 17+
- Android SDK 34
- minSdk 21 (Android 5.0+)
- targetSdk 34

### 步驟 1：複製專案

```bash
git clone https://github.com/aneterw/NoadYTTV.git
cd NoadYTTV
# 在 Android Studio 中開啟
```

### 步驟 2：Gradle 同步

專案使用以下 Gradle 外掛：

- Chaquopy 17.2 (Python 整合)
- Kotlin 1.9.24
- Compose BOM 2024.08.00

Gradle 會自動下載 Python 3.13，並透過 pip 安裝 `yt-dlp`。

### 步驟 3：建構 APK

```bash
# 建構除錯版本
./gradlew assembleDebug

# 或建構發行版本
./gradlew assembleRelease
```

建構完成後，APK 位於：
- 除錯版：`app/build/outputs/apk/debug/app-debug.apk`
- 發行版：`app/build/outputs/apk/release/app-release.apk`

### 步驟 4：安裝至 Android TV

將 APK 複製到 Android TV 裝置並安裝，或透過 ADB 安裝：

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 使用方式

1. 開啟 App 後，在搜尋框輸入關鍵字
2. 選擇想觀看的影片或頻道
3. 使用遙控器 D-pad 導航，按下確認鍵播放
4. 播放期間按下 D-pad 確認鍵可顯示控制選單（播放/暫停、畫質、字幕等）

---

## 五、專案結構

```
app/src/main/
├── java/com/noadytv/
│   ├── python/          # Chaquopy 橋接 (YouTubePythonBridge)
│   ├── data/            # 資料庫層 (YouTubeRepository)
│   ├── player/          # PlayerController, PlayerScreen, 控制項
│   ├── ui/
│   │   ├── components/  # SearchBar, VideoCard, VideoGrid
│   │   ├── screen/      # SearchScreen
│   │   └── theme/       # Material3 主題
│   ├── MainActivity.kt
│   └── MainViewModel.kt
├── python/
│   └── youtube_extractor.py  # yt-dlp 封裝
└── res/                 # 資源 (字串、可繪物、主題)
```

---

## 六、授權

本軟件僅供個人使用，禁止用於任何商業傳播或販賣取財。詳見 [LICENSE.md](./LICENSE.md)。

---

> 🔄 [切換至英文版](./README_EN.md)