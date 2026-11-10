# NoadYTTV - Android TV YouTube Client

A third-party YouTube TV client for Android TV built with:
- **Kotlin** + **Jetpack Compose for TV**
- **Chaquopy 17.2** for Python integration
- **yt-dlp** for video extraction (ad-free streaming)
- **ExoPlayer (Media3)** for playback
- **Koin** for dependency injection

## Features

- 🔍 **Search YouTube** videos with remote-friendly UI
- 📺 **TV-optimized UI** with focus navigation (D-pad support)
- ▶️ **Ad-free playback** via yt-dlp direct stream extraction
- 🎮 **Full player controls**: Play/Pause, Stop, Next/Previous, Quality, Subtitles, Audio tracks
- 📱 **Leanback launcher** support for Android TV
- 🚫 **Zero dependencies** on Google Play Services / YouTube API

## Architecture

```
app/
├── src/main/
│   ├── java/com/noadytv/
│   │   ├── python/          # Chaquopy bridge (YouTubePythonBridge)
│   │   ├── data/            # Repository (YouTubeRepository)
│   │   ├── player/          # PlayerController, PlayerScreen, Controls
│   │   ├── ui/
│   │   │   ├── components/  # SearchBar, VideoCard, VideoGrid
│   │   │   ├── screen/      # SearchScreen
│   │   │   └── theme/       # Material3 theme
│   │   ├── MainActivity.kt
│   │   └── MainViewModel.kt
│   ├── python/
│   │   └── youtube_extractor.py  # yt-dlp wrapper
│   └── res/                 # Resources (strings, drawables, themes)
```

## Requirements

- Android Studio Koala (2024.1.1) or later
- JDK 17+
- Android SDK 34
- minSdk 21 (Android 5.0+)
- targetSdk 34

## Building

### 1. Clone and Open

```bash
git clone <repo-url>
cd NoadYTTV
# Open in Android Studio
```

### 2. Gradle Sync

The project uses:
- **Chaquopy 17.2** Gradle plugin for Python
- **Kotlin 1.9.24**
- **Compose BOM 2024.08.00**

Gradle will automatically download Python 3.13 and install `yt-dlp` via pip.

### 3. Build APK

```bash
./gradlew assembleDebug
# or
./gradlew assembleRelease
```

## How It Works

### Ad-Free Playback

The key to ad-free YouTube playback is **yt-dlp's format selection**:

1. yt-dlp extracts all available formats for a video
2. We filter out DASH manifests (which often contain ads)
3. We select direct HTTPS progressive streams (MP4/HLS)
4. ExoPlayer plays the direct stream URL

This bypasses YouTube's ad insertion which happens at the DASH manifest level.

### Python Integration (Chaquopy)

```
Kotlin (YouTubeRepository)
    ↓ Coroutines (Dispatchers.IO)
Kotlin (YouTubePythonBridge)
    ↓ Chaquopy PyObject.callAttr()
Python (youtube_extractor.YouTubeExtractor)
    ↓ yt_dlp.YoutubeDL
YouTube → JSON → Kotlin Data Classes
```

### Remote Navigation

All UI components support D-pad navigation:
- `FocusRequester` for programmatic focus
- `onFocusChanged` for visual feedback (scale, elevation, border)
- `onKeyEvent` for D-pad Center/Enter/Back handling

## Configuration

### Chaquopy Settings (`app/build.gradle.kts`)

```kotlin
chaquopy {
    defaultConfig {
        python { version = "3.13" }
        pip { install("yt-dlp"); install("requests") }
        pyc { src = false }  // Disable bytecode compilation for faster builds
    }
}
```

### yt-dlp Options (`youtube_extractor.py`)

```python
ydl_opts = {
    'format': 'bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best',
    'extractor_args': {
        'youtube': {
            'player_client': ['android', 'web'],
            'skip': ['dash', 'hls'],  # Skip DASH to avoid ads
        }
    },
}
```

## Player Controls

Press **D-pad Center/Enter** during playback to show controls:

| Option | Action |
|--------|--------|
| 播放/暫停 | Toggle play/pause |
| 停止 | Stop playback |
| 上一部 | Previous video in playlist |
| 下一部 | Next video in playlist |
| 字幕 | Select subtitle track |
| 畫質 | Select quality (1080p, 720p, etc.) |
| 聲音軌 | Select audio track |
| 影片資訊 | Show video details |
| 自動播放下一集 | Toggle auto-play next |

## Project Structure Details

### Key Classes

| Class | Responsibility |
|-------|---------------|
| `YouTubePythonBridge` | Kotlin↔Python bridge via Chaquopy |
| `YouTubeRepository` | Data layer with caching |
| `PlayerController` | ExoPlayer wrapper + playback logic |
| `PlayerScreen` | Full-screen player Composable |
| `PlayerControlsOverlay` | D-pad navigable controls menu |
| `SearchScreen` | Main search/browse UI |
| `VideoGrid`/`VideoCard` | Focusable video list items |

### Data Models (Serializable)

- `VideoSearchResult` - Search result item
- `VideoInfo` - Full video metadata + formats + subtitles
- `VideoFormat` - Individual stream format
- `QualityOption` - UI quality selector
- `SubtitleTrack` - Subtitle/caption track

## Testing

```bash
# Unit tests
./gradlew test

# Instrumented tests (requires device/emulator)
./gradlew connectedAndroidTest
```

## Troubleshooting

### Chaquopy not initializing

Ensure `NoadYTTVApplication` is in Manifest and `Python.start()` is called.

### yt-dlp extraction fails

- Check network connectivity
- yt-dlp may need update: `pip install --upgrade yt-dlp`
- YouTube changes extractors frequently

### Video won't play

- Format URL may have expired (yt-dlp URLs are short-lived)
- Try different quality/format
- Check ExoPlayer error logs

### Build fails

- Clean: `./gradlew clean`
- Invalidate caches in Android Studio
- Check JDK 17 is used

## License

This project is for educational purposes. YouTube's Terms of Service apply.

## Acknowledgments

- [yt-dlp](https://github.com/yt-dlp/yt-dlp) - Video extraction
- [Chaquopy](https://chaquo.com/chaquopy/) - Python on Android
- [ExoPlayer/Media3](https://developer.android.com/media/media3) - Playback engine
- [Jetpack Compose for TV](https://developer.android.com/tv/compose) - TV UI toolkit