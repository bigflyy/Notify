package com.example.notify.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

internal val LightColors = lightColorScheme(
    primary = Color(0xFF006B62), onPrimary = Color.White,
    primaryContainer = Color(0xFFB9EEE3), onPrimaryContainer = Color(0xFF003C36),
    secondary = Color(0xFF4A6360), onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE9E6), onSecondaryContainer = Color(0xFF263D3A),
    tertiary = Color(0xFF466477), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD3E8F5), onTertiaryContainer = Color(0xFF173B4D),
    background = Color(0xFFF5F7F8), onBackground = Color(0xFF182325),
    surface = Color(0xFFFCFDFD), onSurface = Color(0xFF182325),
    surfaceVariant = Color(0xFFE3EAE9), onSurfaceVariant = Color(0xFF4C5C5C),
    surfaceTint = Color(0xFF006B62),
    surfaceDim = Color(0xFFD8DFDF), surfaceBright = Color(0xFFFCFDFD),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF0F4F4),
    surfaceContainer = Color(0xFFEBF0EF), surfaceContainerHigh = Color(0xFFE5EBEA),
    surfaceContainerHighest = Color(0xFFDFE6E5),
    outline = Color(0xFF6E7D7D), outlineVariant = Color(0xFFC5D0CF),
    inverseSurface = Color(0xFF293536), inverseOnSurface = Color(0xFFEDF2F1),
    inversePrimary = Color(0xFF7BD8CA),
    error = Color(0xFFB3261E), onError = Color.White,
    errorContainer = Color(0xFFF9DEDC), onErrorContainer = Color(0xFF410E0B)
)

internal val DarkColors = darkColorScheme(
    primary = Color(0xFF7BD8CA), onPrimary = Color(0xFF003C36),
    primaryContainer = Color(0xFF005048), onPrimaryContainer = Color(0xFFB9EEE3),
    secondary = Color(0xFFB9CDCA), onSecondary = Color(0xFF233B38),
    secondaryContainer = Color(0xFF354E4B), onSecondaryContainer = Color(0xFFDCE9E6),
    tertiary = Color(0xFFA6CBDF), onTertiary = Color(0xFF123547),
    tertiaryContainer = Color(0xFF2C4C5F), onTertiaryContainer = Color(0xFFD3E8F5),
    background = Color(0xFF11191C), onBackground = Color(0xFFE1E9E9),
    surface = Color(0xFF151E21), onSurface = Color(0xFFE1E9E9),
    surfaceVariant = Color(0xFF354346), onSurfaceVariant = Color(0xFFB9C8C9),
    surfaceTint = Color(0xFF7BD8CA),
    surfaceDim = Color(0xFF11191C), surfaceBright = Color(0xFF354043),
    surfaceContainerLowest = Color(0xFF0D1417), surfaceContainerLow = Color(0xFF192326),
    surfaceContainer = Color(0xFF1D282B), surfaceContainerHigh = Color(0xFF273236),
    surfaceContainerHighest = Color(0xFF313D40),
    outline = Color(0xFF849596), outlineVariant = Color(0xFF3E4D50),
    inverseSurface = Color(0xFFE1E9E9), inverseOnSurface = Color(0xFF293536),
    inversePrimary = Color(0xFF006B62),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF7C2523), onErrorContainer = Color(0xFFFFDAD6)
)
