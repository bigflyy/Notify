package com.example.notify.ui.theme

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

enum class ThemeMode(val label: String) { SYSTEM("System default"), LIGHT("Light"), DARK("Dark") }

fun readThemeMode(context: Context): ThemeMode = runCatching {
    ThemeMode.valueOf(context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
        .getString("theme", ThemeMode.SYSTEM.name)!!)
}.getOrDefault(ThemeMode.SYSTEM)

fun saveThemeMode(context: Context, mode: ThemeMode) {
    context.getSharedPreferences("appearance", Context.MODE_PRIVATE).edit()
        .putString("theme", mode.name).apply()
}

@Composable
fun NotifyTheme(themeMode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            (view.context as? Activity)?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
        }
    }
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors,
        typography = Typography, content = content)
}
