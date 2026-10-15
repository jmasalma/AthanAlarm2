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
    primary = GoldPrimary,
    onPrimary = DarkNightBackground,
    primaryContainer = EmeraldPrimary,
    onPrimaryContainer = GoldLight,
    secondary = EmeraldAccent,
    onSecondary = DarkNightBackground,
    secondaryContainer = DarkNightSurfaceVariant,
    onSecondaryContainer = TextLight,
    tertiary = GoldLight,
    background = DarkNightBackground,
    onBackground = TextLight,
    surface = DarkNightSurface,
    onSurface = TextLight,
    surfaceVariant = DarkNightSurfaceVariant,
    onSurfaceVariant = TextSubtleLight,
    outline = Color(0xFF2C564B)
)

private val LightColorScheme = lightColorScheme(
    primary = EmeraldPrimary,
    onPrimary = Color.White,
    primaryContainer = CreamSurfaceVariant,
    onPrimaryContainer = EmeraldDark,
    secondary = GoldDark,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFF7DC),
    onSecondaryContainer = GoldDark,
    tertiary = SunAmber,
    background = CreamBackground,
    onBackground = TextDark,
    surface = CreamSurface,
    onSurface = TextDark,
    surfaceVariant = CreamSurfaceVariant,
    onSurfaceVariant = TextSubtleDark,
    outline = Color(0xFFC4D8D0)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Keep intentional Islamic aesthetic
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
