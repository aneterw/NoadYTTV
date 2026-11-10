# ProGuard rules for NoadYTTV

# Keep Kotlin serialization
-keep class kotlinx.serialization.** { *; }
-keep class com.noadytv.python.** { *; }

# Keep Chaquopy
-keep class com.chaquo.python.** { *; }

# Keep ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Keep Coil
-keep class coil3.** { *; }
-dontwarn coil3.**

# Keep Koin
-keep class org.koin.** { *; }
-dontwarn org.koin.**

# Keep Room
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**

# Keep Compose
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Keep Navigation
-keep class androidx.navigation.** { *; }
-dontwarn androidx.navigation.**

# Keep Coroutines
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# Keep Material3
-keep class androidx.material3.** { *; }
-dontwarn androidx.material3.**

# Keep TV Compose
-keep class androidx.tv.** { *; }
-dontwarn androidx.tv.**