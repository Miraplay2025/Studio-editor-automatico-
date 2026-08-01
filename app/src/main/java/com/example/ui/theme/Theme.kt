package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkEditorColorScheme = darkColorScheme(
    primary = PrimaryPurple,
    onPrimary = PrimaryPurpleText,
    primaryContainer = PrimaryPurpleLight,
    onPrimaryContainer = PrimaryPurpleText,
    secondary = SecondaryMint,
    onSecondary = Color.Black,
    tertiary = AccentGold,
    background = DarkCanvas,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    error = AlertRed,
    onError = Color.Black,
    outline = BorderLight
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkEditorColorScheme,
        typography = Typography,
        content = content
    )
}
