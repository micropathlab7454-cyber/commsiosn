package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = LabPrimaryDark,
    secondary = LabSecondaryDark,
    tertiary = LabTertiaryDark,
    background = LabBackgroundDark,
    surface = LabSurfaceDark,
    onPrimary = LabOnPrimaryDark,
    onBackground = LabOnBackgroundDark,
    onSurface = LabOnSurfaceDark
)

private val LightColorScheme = lightColorScheme(
    primary = LabPrimaryLight,
    secondary = LabSecondaryLight,
    tertiary = LabTertiaryLight,
    background = LabBackgroundLight,
    surface = LabSurfaceLight,
    onPrimary = LabOnPrimaryLight,
    onBackground = LabOnBackgroundLight,
    onSurface = LabOnSurfaceLight
)

@Composable
fun MyApplicationTheme(
    themeMode: String = "light",
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme() // "system" default
    }

    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
