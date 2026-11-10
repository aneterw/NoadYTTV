#!/usr/bin/env bash
# Build verification script for NoadYTTV

set -e

echo "=== NoadYTTV Build Verification ==="
echo

# Check required files
echo "Checking project structure..."
required_files=(
    "settings.gradle.kts"
    "build.gradle.kts"
    "gradle.properties"
    "app/build.gradle.kts"
    "app/src/main/AndroidManifest.xml"
    "app/src/main/java/com/noadytv/NoadYTTVApplication.kt"
    "app/src/main/java/com/noadytv/MainActivity.kt"
    "app/src/main/java/com/noadytv/MainViewModel.kt"
    "app/src/main/java/com/noadytv/python/YouTubePythonBridge.kt"
    "app/src/main/java/com/noadytv/data/YouTubeRepository.kt"
    "app/src/main/java/com/noadytv/player/PlayerController.kt"
    "app/src/main/java/com/noadytv/player/PlayerScreen.kt"
    "app/src/main/java/com/noadytv/player/PlayerControlsOverlay.kt"
    "app/src/main/java/com/noadytv/ui/theme/Theme.kt"
    "app/src/main/java/com/noadytv/ui/theme/Color.kt"
    "app/src/main/java/com/noadytv/ui/theme/Typography.kt"
    "app/src/main/java/com/noadytv/ui/components/SearchBar.kt"
    "app/src/main/java/com/noadytv/ui/components/VideoCard.kt"
    "app/src/main/java/com/noadytv/ui/components/VideoGrid.kt"
    "app/src/main/java/com/noadytv/ui/screen/SearchScreen.kt"
    "app/src/main/python/youtube_extractor.py"
    "app/src/main/res/values/strings.xml"
    "app/src/main/res/values/themes.xml"
    "app/src/main/res/values/colors.xml"
    "app/src/main/res/drawable/ic_launcher.xml"
    "app/src/main/res/drawable/tv_banner.xml"
    "app/src/main/res/drawable/placeholder_thumbnail.xml"
    "app/proguard-rules.pro"
)

missing=0
for file in "${required_files[@]}"; do
    if [ -f "$file" ]; then
        echo "  ✓ $file"
    else
        echo "  ✗ $file (MISSING)"
        missing=$((missing + 1))
    fi
done

echo
if [ $missing -eq 0 ]; then
    echo "All required files present!"
else
    echo "$missing files missing"
fi

# Check key configurations
echo
echo "Checking key configurations..."

# Check Chaquopy version
if grep -q "com.chaquo.python.*17.2" build.gradle.kts; then
    echo "  ✓ Chaquopy 17.2 configured"
else
    echo "  ✗ Chaquopy version mismatch"
fi

# Check yt-dlp in pip
if grep -q 'install("yt-dlp")' app/build.gradle.kts; then
    echo "  ✓ yt-dlp in pip dependencies"
else
    echo "  ✗ yt-dlp not in pip dependencies"
fi

# Check Python version
if grep -q 'version = "3.13"' app/build.gradle.kts; then
    echo "  ✓ Python 3.13 configured"
else
    echo "  ✗ Python version mismatch"
fi

# Check ExoPlayer
if grep -q "media3-exoplayer" app/build.gradle.kts; then
    echo "  ✓ ExoPlayer (Media3) dependency present"
else
    echo "  ✗ ExoPlayer dependency missing"
fi

# Check Compose for TV
if grep -q "tv-material" app/build.gradle.kts; then
    echo "  ✓ Compose for TV dependencies present"
else
    echo "  ✗ Compose for TV dependencies missing"
fi

# Check Leanback launcher in manifest
if grep -q "LEANBACK_LAUNCHER" app/src/main/AndroidManifest.xml; then
    echo "  ✓ Leanback launcher configured"
else
    echo "  ✗ Leanback launcher missing"
fi

# Check internet permission
if grep -q "INTERNET" app/src/main/AndroidManifest.xml; then
    echo "  ✓ Internet permission declared"
else
    echo "  ✗ Internet permission missing"
fi

echo
echo "=== Verification Complete ==="