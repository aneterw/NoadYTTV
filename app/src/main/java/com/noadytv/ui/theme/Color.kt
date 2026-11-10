package com.noadytv.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFFFFFFF),
    surface = Color(0xFF121212),
    background = Color(0xFF000000),
    onPrimary = Color(0xFF000000),
    onSurface = Color(0xFFFFFFFF),
    onBackground = Color(0xFFFFFFFF),
    outline = Color(0xFF333333),
    primaryContainer = Color(0xFF1F1F1F),
    onPrimaryContainer = Color(0xFFFFFFFF),
)

val LightColorScheme = lightColorScheme(
    primary = Color(0xFF000000),
    surface = Color(0xFFFFFFFF),
    background = Color(0xFFF5F5F5),
    onPrimary = Color(0xFFFFFFFF),
    onSurface = Color(0xFF000000),
    onBackground = Color(0xFF000000),
    outline = Color(0xFFCCCCCC),
    primaryContainer = Color(0xFFE0E0E0),
    onPrimaryContainer = Color(0xFF000000),
)