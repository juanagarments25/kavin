package com.linkcontrol.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// "Cursed-energy" (JJK-inspired) palette: deep void backgrounds, violet accents.
// Token names are kept (Cyan/Navy*) so the whole app re-skins without touching call sites;
// their VALUES now carry the purple/violet identity.
val NavyDeep = Color(0xFF080810)     // primary background (void)
val NavySurface = Color(0xFF121020)  // secondary background
val NavyCard = Color(0xFF19152B)     // card background
val NavyBorder = Color(0xFF3A2E63)   // violet-tinted border
val Cyan = Color(0xFF8B5CF6)         // primary cursed-energy purple (accent)
val CyanDim = Color(0xFFC026D3)      // electric violet
val NeonBlue = Color(0xFF38BDF8)     // neon blue highlight
val TextHi = Color(0xFFF8FAFC)
val TextLo = Color(0xFFA1A1B5)
val Online = Color(0xFF34D399)
val Offline = Color(0xFF6B7280)
val Danger = Color(0xFFFB7185)
val Warn = Color(0xFFFBBF24)

private val LinkControlColors = darkColorScheme(
    primary = Cyan,
    onPrimary = NavyDeep,
    secondary = CyanDim,
    background = NavyDeep,
    onBackground = TextHi,
    surface = NavySurface,
    onSurface = TextHi,
    surfaceVariant = NavyCard,
    onSurfaceVariant = TextLo,
    outline = NavyBorder,
    error = Danger,
)

private val LinkTypography = Typography(
    headlineMedium = Typography().headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
    titleLarge = Typography().titleLarge.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = Typography().labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp),
)

@Composable
fun LinkControlTheme(content: @Composable () -> Unit) {
    // App is dark by design; ignore system setting for a consistent look.
    @Suppress("UNUSED_EXPRESSION") isSystemInDarkTheme()
    MaterialTheme(colorScheme = LinkControlColors, typography = LinkTypography, content = content)
}
