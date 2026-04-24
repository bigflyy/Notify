package com.example.notify.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryNavy,
    secondary = SecondaryNavy,
    tertiary = SecondaryNavy,
    background = BackgroundNavy,
    surface = SurfaceNavy,
    secondaryContainer = AudioPlayingBgDark,
    tertiaryContainer = SurfaceNavy
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryIndigo,
    secondary = SecondaryIndigo,
    tertiary = DividerGray,
    background = SlateBackground,
    surface = NoteSurface,
    secondaryContainer = AudioPlayingBg,
    tertiaryContainer = DividerGray,

    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = PrimaryIndigo,
    onSurface = PrimaryIndigo
)

@Composable
fun NotifyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Set to false to prioritize our custom professional theme
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
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
