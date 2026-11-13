package com.vueltax.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val teal40 = Color(0xFF0F6E63)
private val teal80 = Color(0xFF7FD6C8)
private val amber40 = Color(0xFF8A5A00)
private val amber80 = Color(0xFFFFC56B)
private val coral40 = Color(0xFFB3402E)
private val coral80 = Color(0xFFFFB4A6)
private val ink = Color(0xFF16201E)
private val paper = Color(0xFFF7FBF9)

private val lightColors = lightColorScheme(
    primary = teal40,
    onPrimary = Color.White,
    secondary = amber40,
    onSecondary = Color.White,
    tertiary = coral40,
    onTertiary = Color.White,
    background = paper,
    onBackground = ink,
    surface = paper,
    onSurface = ink,
)

private val darkColors = darkColorScheme(
    primary = teal80,
    onPrimary = ink,
    secondary = amber80,
    onSecondary = ink,
    tertiary = coral80,
    onTertiary = ink,
    background = ink,
    onBackground = paper,
    surface = ink,
    onSurface = paper,
)

private val appTypography = Typography()

@Composable
fun AppTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) darkColors else lightColors,
        typography = appTypography,
        content = content,
    )
}
