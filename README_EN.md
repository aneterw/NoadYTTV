# NoadYTTV - Android TV YouTube Client

> 🔄 [Switch to Traditional Chinese](./README.md)

---

## I. Why We Built This App

We usually use YouTube for personalized search, better viewing experience, or more convenient features. But what we pursue is: **a simple, direct search experience—search for what you want to watch, and watch it**.

The official YouTube website has various categories and layouts that can feel cluttered. Also, ads interfere with the viewing experience. So we developed NoadYTTV, hoping to:

- **Block annoying ads** for a purer viewing experience
- **Simple and intuitive interface**, avoiding cluttered layouts
- **Quickly search and play** videos users are interested in
- **Add Channel Search**, helping users find channels and videos they care about more precisely

### Channel Search

To improve search efficiency, we added **Channel Search**. This feature focuses on channel characteristics, aggregating channels with higher subscriber counts and relatively better quality based on user search intent. Whether for daily browsing or well-known channels recommended online, it helps users quickly find relevant videos, better match personal interests, and get the best viewing experience.

---

## II. Features

- 🔍 **YouTube video search** — TV-friendly remote interface
- 📺 **Android TV optimized UI** — D-pad focus navigation support
- ▶️ **Ad-free playback** — Direct stream extraction via yt-dlp, skipping ads
- 🎮 **Full playback controls** — Play/Pause, Stop, Next/Previous, Quality, Subtitles, Audio tracks
- 📱 **Leanback launcher support** — Launch from Android TV home screen
- 🚫 **No Google Play Services / YouTube API dependency**
- 🔎 **Channel Search** — Aggregates high-quality channels for better search efficiency

---

## III. Technical Overview

NoadYTTV is a third-party YouTube TV client for Android TV, built with:

- **Kotlin + Jetpack Compose for TV** — Modern TV UI development
- **Chaquopy 17.2** — Bridge between Kotlin and Python
- **yt-dlp** — Video stream extraction (enables ad-free playback)
- **ExoPlayer (Media3)** — Playback engine
- **Koin** — Dependency injection

### Ad-Free Playback Principle

yt-dlp extracts all available formats for a video. We filter out DASH manifests (which often contain ads) and select direct HTTPS progressive streams (MP4/HLS) for ExoPlayer to play. This bypasses YouTube's ad insertion at the DASH manifest level.

### Python-Kotlin Integration

```
Kotlin (YouTubeRepository)
    ↓ Coroutines (Dispatchers.IO)
Kotlin (YouTubePythonBridge)
    ↓ Chaquopy PyObject.callAttr()
Python (youtube_extractor.YouTubeExtractor)
    ↓ yt_dlp.YoutubeDL
YouTube → JSON → Kotlin Data Classes
```

---

## IV. Deployment & Usage Guide

### System Requirements

- Android Studio Koala (2024.1.1) or later
- JDK 17+
- Android SDK 34
- minSdk 21 (Android 5.0+)
- targetSdk 34

### Step 1: Clone the Project

```bash
git clone https://github.com/aneterw/NoadYTTV.git
cd NoadYTTV
# Open in Android Studio
```

### Step 2: Gradle Sync

The project uses the following Gradle plugins:

- Chaquopy 17.2 (Python integration)
- Kotlin 1.9.24
- Compose BOM 2024.08.00

Gradle will automatically download Python 3.13 and install `yt-dlp` via pip.

### Step 3: Build APK

```bash
# Build debug version
./gradlew assembleDebug

# Or build release version
./gradlew assembleRelease
```

After building, APKs are located at:
- Debug: `app/build/outputs/apk/debug/app-debug.apk`
- Release: `app/build/outputs/apk/release/app-release.apk`

### Step 4: Install on Android TV

Copy the APK to your Android TV device and install, or use ADB:

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

### How to Use

1. Open the app and enter keywords in the search box
2. Select a video or channel to watch
3. Use the D-pad to navigate, press OK to play
4. During playback, press D-pad OK to show controls (Play/Pause, Quality, Subtitles, etc.)

---

## V. Project Structure

```
app/src/main/
├── java/com/noadytv/
│   ├── python/          # Chaquopy bridge (YouTubePythonBridge)
│   ├── data/            # Repository layer (YouTubeRepository)
│   ├── player/          # PlayerController, PlayerScreen, Controls
│   ├── ui/
│   │   ├── components/  # SearchBar, VideoCard, VideoGrid
│   │   ├── screen/      # SearchScreen
│   │   └── theme/       # Material3 theme
│   ├── MainActivity.kt
│   └── MainViewModel.kt
├── python/
│   └── youtube_extractor.py  # yt-dlp wrapper
└── res/                 # Resources (strings, drawables, themes)
```

---

## VI. License

This software is for personal use only. Commercial distribution or resale is prohibited. See [LICENSE.md](./LICENSE.md).

---

> 🔄 [Switch to Traditional Chinese](./README.md)