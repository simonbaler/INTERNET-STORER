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

private val RomanticColorScheme = lightColorScheme(
    primary = RosePrimary,
    onPrimary = Color.White,
    primaryContainer = RoseContainer,
    onPrimaryContainer = RoseDark,
    secondary = CoralSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFECE9),
    onSecondaryContainer = Color(0xFF5A1E18),
    tertiary = LavenderAccent,
    onTertiary = Color.White,
    tertiaryContainer = LavenderSoft,
    onTertiaryContainer = Color(0xFF380D5E),
    background = WarmIvoryBackground,
    onBackground = DarkCharcoalText,
    surface = PureWhiteSurface,
    onSurface = DarkCharcoalText,
    surfaceVariant = Color(0xFFFBF4F6),
    onSurfaceVariant = MutedSlate,
    outline = CardBorderSoft
)

private val LightCleanColorScheme = lightColorScheme(
    primary = RosePrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9E2),
    onPrimaryContainer = Color(0xFF3B0815),
    secondary = Color(0xFF75565B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFD9DF),
    onSecondaryContainer = Color(0xFF2B1519),
    tertiary = LavenderAccent,
    onTertiary = Color.White,
    background = Color(0xFFFFF8F8),
    onBackground = Color(0xFF201A1B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF201A1B)
)

private val DarkGentleColorScheme = darkColorScheme(
    primary = RoseLight,
    onPrimary = Color(0xFF5C1126),
    primaryContainer = RoseDark,
    onPrimaryContainer = RoseSoft,
    secondary = PeachAccent,
    onSecondary = Color(0xFF4A251E),
    tertiary = LavenderSoft,
    onTertiary = Color(0xFF380D5E),
    background = Color(0xFF1E1618),
    onBackground = Color(0xFFEBE0E2),
    surface = Color(0xFF271F22),
    onSurface = Color(0xFFEBE0E2),
    surfaceVariant = Color(0xFF362C30),
    onSurfaceVariant = Color(0xFFD6C2C7)
)

@Composable
fun InternetStorerTheme(
    themePreference: String = "Soft Romantic",
    content: @Composable () -> Unit
) {
    val colorScheme = when (themePreference) {
        "Light" -> LightCleanColorScheme
        "System" -> if (isSystemInDarkTheme()) DarkGentleColorScheme else RomanticColorScheme
        else -> RomanticColorScheme // "Soft Romantic" default
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
