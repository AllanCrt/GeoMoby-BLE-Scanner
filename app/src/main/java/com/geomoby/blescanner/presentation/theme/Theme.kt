package com.geomoby.blescanner.presentation.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Material3 dark theme for the BLE Scanner app.
 *
 * Uses a dark-first design inspired by radar/geolocation dashboards:
 * deep navy backgrounds with cyan/blue accents that evoke signal visualization.
 * Purple is reserved for iBeacon-specific elements to create a clear visual
 * hierarchy between generic BLE devices and iBeacons.
 */
private val DarkColorScheme = darkColorScheme(
    primary = Blue40,
    onPrimary = TextPrimary,
    primaryContainer = Blue20,
    onPrimaryContainer = Blue80,
    secondary = Cyan40,
    onSecondary = TextPrimary,
    secondaryContainer = Cyan20,
    onSecondaryContainer = Cyan80,
    tertiary = IBeaconPurple,
    onTertiary = TextPrimary,
    background = SurfaceDark,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceCard,
    onSurfaceVariant = TextSecondary,
    outline = TextSecondary
)

@Composable
fun BleScannerTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Match status bar to the dark background for immersive look
            @Suppress("DEPRECATION")
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
