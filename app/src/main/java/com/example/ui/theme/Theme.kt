package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
  darkColorScheme(
    primary = FiberCyan,
    onPrimary = Color(0xFF00242E),
    primaryContainer = FiberTealContainer,
    onPrimaryContainer = OnFiberTealContainer,
    secondary = PulseCoral,
    onSecondary = Color(0xFF330C02),
    secondaryContainer = PulseCoralContainer,
    onSecondaryContainer = OnPulseCoralContainer,
    tertiary = EmeraldActive,
    onTertiary = Color(0xFF00291B),
    tertiaryContainer = EmeraldContainer,
    onTertiaryContainer = OnEmeraldContainer,
    background = MidnightBg,
    onBackground = Color(0xFFE6F1FA),
    surface = MidnightSurface,
    onSurface = Color(0xFFE6F1FA),
    surfaceVariant = MidnightSurfaceContainer,
    onSurfaceVariant = Color(0xFFA7BBD0),
    surfaceContainerLow = MidnightSurface,
    surfaceContainer = MidnightSurfaceContainer,
    surfaceContainerHigh = MidnightSurfaceHigh,
    outline = MidnightOutline,
    outlineVariant = Color(0xFF1F344C),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF380005),
    errorContainer = Color(0xFF5C1119),
    onErrorContainer = Color(0xFFFFD6D9),
  )

private val LightColorScheme =
  lightColorScheme(
    primary = DaylightPrimary,
    onPrimary = Color.White,
    primaryContainer = DaylightPrimaryContainer,
    onPrimaryContainer = Color(0xFF001F28),
    secondary = DaylightSecondary,
    onSecondary = Color.White,
    secondaryContainer = DaylightSecondaryContainer,
    onSecondaryContainer = Color(0xFF3A0900),
    tertiary = Color(0xFF007A55),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFB5F7DF),
    onTertiaryContainer = Color(0xFF002418),
    background = DaylightBg,
    onBackground = Color(0xFF0D1B2A),
    surface = DaylightSurface,
    onSurface = Color(0xFF0D1B2A),
    surfaceVariant = DaylightSurfaceContainer,
    onSurfaceVariant = Color(0xFF475C71),
    surfaceContainerLow = DaylightSurface,
    surfaceContainer = DaylightSurfaceContainer,
    surfaceContainerHigh = Color(0xFFDCE7F3),
    outline = Color(0xFFB4C6D8),
  )

@Composable
fun SutoLinkTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
  MaterialTheme(colorScheme = colorScheme, typography = SutoTypography, content = content)
}
