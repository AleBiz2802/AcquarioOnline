package com.acquarioonline.ui.theme

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

private val LightColors = lightColorScheme(
    primary = Color(0xFF006B7F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF001F27),
    secondary = Color(0xFF00838F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = Color(0xFF002025),
    background = Color(0xFFF0F9FF),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFF0F9FF),
    onSurface = Color(0xFF0F172A),
    surfaceTint = Color.Transparent
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF38BDF8),
    onPrimary = Color(0xFF003642),
    primaryContainer = Color(0xFF003A48),
    onPrimaryContainer = Color(0xFFE0F2FE),
    secondary = Color(0xFF00ACC1),
    onSecondary = Color(0xFF00363A),
    secondaryContainer = Color(0xFF004E5F),
    onSecondaryContainer = Color(0xFFE0F2FE),
    background = Color(0xFF0F172A),
    onBackground = Color(0xFFF1F5F9),
    surface = Color(0xFF0F172A),
    onSurface = Color(0xFFF1F5F9),
    surfaceTint = Color.Transparent
)


@Composable
fun AcquarioTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
