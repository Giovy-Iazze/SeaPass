package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = PolishPrimaryDark,
    secondary = PolishSecondaryDark,
    tertiary = PolishTertiaryDark,
    background = PolishBgDark,
    surface = PolishSurfaceDark,
    surfaceVariant = PolishSurfaceVariantDark,
    onPrimary = PolishBgDark,
    onSecondary = PolishBgDark,
    onBackground = PolishBgLight,
    onSurface = PolishBgLight,
    outline = PolishOutline
)

private val LightColorScheme = lightColorScheme(
    primary = PolishPrimary,
    secondary = PolishSecondary,
    tertiary = PolishTertiary,
    background = PolishBgLight,
    surface = PolishSurfaceLight,
    surfaceVariant = PolishBgLight, // Pure warm bg
    onPrimary = PolishBgLight,
    onSecondary = PolishBgLight,
    onBackground = Color(0xFF1C1B1F), // Precise dark text color from HTML
    onSurface = Color(0xFF1C1B1F),
    primaryContainer = PolishBgLight,
    onPrimaryContainer = PolishPrimary,
    outline = PolishOutline,
    outlineVariant = PolishOutline
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Disable dynamic colors to keep our premium marine theme brand intact
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
