package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Romantic & Futuristic Palette
val RosePrimary = Color(0xFFE84A6E)
val RoseDark = Color(0xFFB82848)
val RoseLight = Color(0xFFFF758F)
val RoseSoft = Color(0xFFFFB3C1)
val RoseContainer = Color(0xFFFFEEF2)

val CoralSecondary = Color(0xFFF28482)
val PeachAccent = Color(0xFFF7B2AD)
val PastelPeach = Color(0xFFFDE8E4)
val PastelRose = Color(0xFFFFEEF2)
val LavenderAccent = Color(0xFFC77DFF)
val LavenderSoft = Color(0xFFEADBFF)
val SoftGold = Color(0xFFF6BD60)

// Warm Near-White & Surfaces
val WarmIvoryBackground = Color(0xFFFAF7F5)
val PureWhiteSurface = Color(0xFFFFFFFF)
val GlassSurface = Color(0xFAFDFBF9)
val CardBorderSoft = Color(0xFFF3E7EA)
val MutedSlate = Color(0xFF6B5E63)
val DarkCharcoalText = Color(0xFF261C20)
val SubtleGray = Color(0xFF8F8085)

// Status colors
val OnlineGreen = Color(0xFF2A9D8F)
val OnlineGreenSoft = Color(0xFFE8F5F1)
val LimitedOrange = Color(0xFFE76F51)
val LimitedOrangeSoft = Color(0xFFFDF0ED)
val OfflineRose = Color(0xFFE63946)
val OfflineRoseSoft = Color(0xFFFDEBED)

// Gradients
val RomanticHeroGradient = Brush.linearGradient(
    colors = listOf(
        Color(0xFFFF758F),
        Color(0xFFFFA07A),
        Color(0xFFC77DFF)
    )
)

val SoftRoseGradient = Brush.linearGradient(
    colors = listOf(
        Color(0xFFFFF0F3),
        Color(0xFFFFE5EC),
        Color(0xFFF7E1D7)
    )
)

val CardGlowGradient = Brush.linearGradient(
    colors = listOf(
        Color(0xFFFFFFFF),
        Color(0xFFFFF7F9)
    )
)
