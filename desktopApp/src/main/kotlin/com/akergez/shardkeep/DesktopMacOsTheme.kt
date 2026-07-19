package com.akergez.shardkeep

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val macOsAccentColor: Color by lazy {
    runCatching {
        val process = ProcessBuilder("defaults", "read", "-g", "AppleAccentColor").start()
        val value = process.inputStream.bufferedReader().use { it.readLine() }
        process.waitFor()
        when (value?.toIntOrNull()) {
            -1 -> Color(0xFF8E8E93)
            0 -> Color(0xFFFF3E4F)
            1 -> Color(0xFFFF7A00)
            2 -> Color(0xFFFFC300)
            3 -> Color(0xFF37BD2D)
            5 -> Color(0xFFB04AAB)
            6 -> Color(0xFFFF3AA0)
            else -> Color(0xFF007CFF)
        }
    }.getOrDefault(Color(0xFF007CFF))
}

private val MacOsLightColorScheme by lazy {
    lightColorScheme(
        primary = macOsAccentColor,
        onPrimary = Color.White,
        primaryContainer = macOsAccentColor,
        onPrimaryContainer = Color.White,
        secondary = macOsAccentColor,
        onSecondary = Color.White,
        secondaryContainer = macOsAccentColor,
        onSecondaryContainer = Color.White,
        tertiary = Color(0xFFFF9500),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFFFE5CC),
        onTertiaryContainer = Color(0xFF7A4600),
        background = Color(0xFFF5F5F5),
        onBackground = Color(0xFF1C1C1E),
        surface = Color.White,
        onSurface = Color(0xFF1C1C1E),
        surfaceVariant = Color(0xFFE5E5EA),
        onSurfaceVariant = Color(0xFF48484A),
        surfaceContainer = Color(0xFFF2F2F7),
        surfaceContainerHigh = Color(0xFFE5E5EA),
        surfaceContainerHighest = Color(0xFFD1D1D6),
    )
}

private val MacOsDarkColorScheme by lazy {
    darkColorScheme(
        primary = macOsAccentColor,
        onPrimary = Color.White,
        primaryContainer = macOsAccentColor,
        onPrimaryContainer = Color.White,
        secondary = macOsAccentColor,
        onSecondary = Color.White,
        secondaryContainer = macOsAccentColor,
        onSecondaryContainer = Color.White,
        tertiary = Color(0xFFFF9F0A),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFC77700),
        onTertiaryContainer = Color.White,
        background = Color(0xFF1E1E1E),
        onBackground = Color(0xFFE5E5E5),
        surface = Color(0xFF2D2D2D),
        onSurface = Color(0xFFE5E5E5),
        surfaceVariant = Color(0xFF3A3A3A),
        onSurfaceVariant = Color(0xFFB3B3B3),
        surfaceContainer = Color(0xFF2D2D2D),
        surfaceContainerHigh = Color(0xFF3A3A3A),
        surfaceContainerHighest = Color(0xFF4A4A4A),
    )
}

private val MacOsShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

@Composable
fun DesktopMacOsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) MacOsDarkColorScheme else MacOsLightColorScheme,
        shapes = MacOsShapes,
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
            content = content,
        )
    }
}
