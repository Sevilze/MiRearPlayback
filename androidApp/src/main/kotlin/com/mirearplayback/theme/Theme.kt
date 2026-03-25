package com.mirearplayback.theme

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

private val DarkColors =
    darkColorScheme(
        primary = Color(0xFFBBC3FF),
        onPrimary = Color(0xFF1B2678),
        primaryContainer = Color(0xFF333E90),
        onPrimaryContainer = Color(0xFFDEE0FF),
        secondary = Color(0xFFC3C5DD),
        onSecondary = Color(0xFF2D2F42),
        secondaryContainer = Color(0xFF434659),
        onSecondaryContainer = Color(0xFFDFE1F9),
        tertiary = Color(0xFFE5BAD8),
        onTertiary = Color(0xFF44263E),
        tertiaryContainer = Color(0xFF5D3C55),
        onTertiaryContainer = Color(0xFFFFD7F1),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        background = Color(0xFF121318),
        onBackground = Color(0xFFE3E1EC),
        surface = Color(0xFF121318),
        onSurface = Color(0xFFE3E1EC),
        surfaceVariant = Color(0xFF45464F),
        onSurfaceVariant = Color(0xFFC6C5D0),
        outline = Color(0xFF90909A),
        outlineVariant = Color(0xFF45464F),
        inverseSurface = Color(0xFFE3E1EC),
        inverseOnSurface = Color(0xFF2F3036),
        inversePrimary = Color(0xFF4B56A9),
        surfaceTint = Color(0xFFBBC3FF)
    )

private val LightColors =
    lightColorScheme(
        primary = Color(0xFF4B56A9),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFDEE0FF),
        onPrimaryContainer = Color(0xFF000F5C),
        secondary = Color(0xFF5B5D72),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFDFE1F9),
        onSecondaryContainer = Color(0xFF181A2C),
        tertiary = Color(0xFF76536D),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFFD7F1),
        onTertiaryContainer = Color(0xFF2D1228),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        background = Color(0xFFFBF8FF),
        onBackground = Color(0xFF1B1B21),
        surface = Color(0xFFFBF8FF),
        onSurface = Color(0xFF1B1B21),
        surfaceVariant = Color(0xFFE3E1EC),
        onSurfaceVariant = Color(0xFF45464F),
        outline = Color(0xFF767680),
        outlineVariant = Color(0xFFC6C5D0),
        inverseSurface = Color(0xFF303036),
        inverseOnSurface = Color(0xFFF2F0FA),
        inversePrimary = Color(0xFFBBC3FF),
        surfaceTint = Color(0xFF4B56A9)
    )

@Composable
fun MiRearPlaybackTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme =
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }

            darkTheme -> {
                DarkColors
            }

            else -> {
                LightColors
            }
        }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
