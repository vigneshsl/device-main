package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = GlassPrimaryDark,
    onPrimary = OnGlassPrimaryDark,
    primaryContainer = GlassPrimaryContainerDark,
    onPrimaryContainer = OnGlassPrimaryContainerDark,
    secondary = GlassSecondaryDark,
    onSecondary = OnGlassSecondaryDark,
    secondaryContainer = GlassSecondaryContainerDark,
    onSecondaryContainer = OnGlassSecondaryContainerDark,
    tertiary = GlassTertiaryDark,
    onTertiary = OnGlassTertiaryDark,
    tertiaryContainer = GlassTertiaryContainerDark,
    onTertiaryContainer = OnGlassTertiaryContainerDark,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark
)

private val LightColorScheme = lightColorScheme(
    primary = GlassPrimaryLight,
    onPrimary = OnGlassPrimaryLight,
    primaryContainer = GlassPrimaryContainerLight,
    onPrimaryContainer = OnGlassPrimaryContainerLight,
    secondary = GlassSecondaryLight,
    onSecondary = OnGlassSecondaryLight,
    secondaryContainer = GlassSecondaryContainerLight,
    onSecondaryContainer = OnGlassSecondaryContainerLight,
    tertiary = GlassTertiaryLight,
    onTertiary = OnGlassTertiaryLight,
    tertiaryContainer = GlassTertiaryContainerLight,
    onTertiaryContainer = OnGlassTertiaryContainerLight,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight
)

/**
 * Premium Glassmorphism theme with automatic Dark mode support and optional Material You dynamic color.
 */
@Composable
fun SmartNetworkAlertTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor -> {
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
