package com.k1adili.walkietalkie.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB547),
    onPrimary = Color(0xFF3A2500),
    primaryContainer = Color(0xFF5A3C00),
    onPrimaryContainer = Color(0xFFFFDDB0),
    secondary = Color(0xFF5EEAD4),
    onSecondary = Color(0xFF00382F),
    secondaryContainer = Color(0xFF0B4F44),
    onSecondaryContainer = Color(0xFFB8F5EA),
    tertiary = Color(0xFFFF7A6B),
    onTertiary = Color(0xFF4A0D06),
    tertiaryContainer = Color(0xFF6B1F16),
    onTertiaryContainer = Color(0xFFFFDAD5),
    background = Color(0xFF0E1116),
    onBackground = Color(0xFFE8EAED),
    surface = Color(0xFF0E1116),
    onSurface = Color(0xFFE8EAED),
    surfaceVariant = Color(0xFF232A33),
    onSurfaceVariant = Color(0xFFB4BCC6),
    surfaceContainerLowest = Color(0xFF0A0D11),
    surfaceContainerLow = Color(0xFF12161C),
    surfaceContainer = Color(0xFF171C23),
    surfaceContainerHigh = Color(0xFF1E252E),
    surfaceContainerHighest = Color(0xFF262E38),
    outline = Color(0xFF7B8591),
    outlineVariant = Color(0xFF2E3640),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF4A0000),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF9A5B00),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDDB0),
    onPrimaryContainer = Color(0xFF301A00),
    secondary = Color(0xFF006B5E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFB8F5EA),
    onSecondaryContainer = Color(0xFF00201B),
    tertiary = Color(0xFFB3261E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDAD5),
    onTertiaryContainer = Color(0xFF410002),
    background = Color(0xFFF7F5F0),
    onBackground = Color(0xFF1B1B1F),
    surface = Color(0xFFF7F5F0),
    onSurface = Color(0xFF1B1B1F),
    surfaceVariant = Color(0xFFE8E2D8),
    onSurfaceVariant = Color(0xFF4B4639),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2EFE8),
    surfaceContainer = Color(0xFFECE8DF),
    surfaceContainerHigh = Color(0xFFE6E1D7),
    surfaceContainerHighest = Color(0xFFE0DBD0),
    outline = Color(0xFF7D7667),
    outlineVariant = Color(0xFFD0C9BA),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
)

@Composable
fun WalkieTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
