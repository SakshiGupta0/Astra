// ASTRA design tokens (Visual Identity Guide, Concept 01 · Celestial luxury). Dark-first, with light mode.
package app.astra.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.astra.R
import app.astra.core.ThemeChoice

@Immutable
data class AstraColors(
    val dark: Boolean,
    val background: Color,      // color.background.primary
    val backgroundAlt: Color,   // color.background.secondary
    val surface: Color,         // color.surface.feature
    val border: Color,
    val purple: Color,
    val lavender: Color,
    val gold: Color,            // primary action fills
    val accent: Color,          // emphasis text, strokes, selected states (gold on dark, purple on light)
    val onGold: Color,
    val text: Color,
    val textSecondary: Color,
    val supportive: Color,
    val mixed: Color,
    val demanding: Color,
    val error: Color
)

val DarkAstra = AstraColors(
    dark = true,
    background = Color(0xFF080F24), backgroundAlt = Color(0xFF1B1446), surface = Color(0xFF221A4D),
    border = Color(0xFF3A3866), purple = Color(0xFF602D89), lavender = Color(0xFFA78BFA),
    gold = Color(0xFFF7D794), accent = Color(0xFFF7D794), onGold = Color(0xFF14102E),
    text = Color(0xFFF5F3FF), textSecondary = Color(0xFFAAB0C5),
    supportive = Color(0xFFF7D794), mixed = Color(0xFFA78BFA), demanding = Color(0xFF6E73A8),
    error = Color(0xFFFFB4A8)
)

/** Light mode: the same ornament on parchment, with deep indigo text and antique gold lines. */
val LightAstra = AstraColors(
    dark = false,
    background = Color(0xFFF7F2E8), backgroundAlt = Color(0xFFEFE5D2), surface = Color(0xFFFFFDF8),
    border = Color(0xFFD9C9A3), purple = Color(0xFF602D89), lavender = Color(0xFF5B3FC4),
    gold = Color(0xFFF7D794), accent = Color(0xFF602D89), onGold = Color(0xFF14102E),
    text = Color(0xFF1E1946), textSecondary = Color(0xFF5B5677),
    supportive = Color(0xFFE2B85A), mixed = Color(0xFF8C73E6), demanding = Color(0xFF5E6390),
    error = Color(0xFFB3261E)
)

val Playfair = FontFamily(Font(R.font.playfair_medium, FontWeight.Medium), Font(R.font.playfair_semibold, FontWeight.SemiBold))
val Cinzel = FontFamily(Font(R.font.cinzel_semibold, FontWeight.SemiBold))
val Inter = FontFamily(Font(R.font.inter_regular, FontWeight.Normal), Font(R.font.inter_medium, FontWeight.Medium), Font(R.font.inter_semibold, FontWeight.SemiBold))

@Immutable
data class AstraType(
    val display: TextStyle = TextStyle(fontFamily = Playfair, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = 1.sp),
    val title: TextStyle = TextStyle(fontFamily = Playfair, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp),
    val heading: TextStyle = TextStyle(fontFamily = Playfair, fontWeight = FontWeight.Medium, fontSize = 19.sp, lineHeight = 25.sp),
    val body: TextStyle = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    val bodyStrong: TextStyle = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 22.sp),
    val label: TextStyle = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
    val meta: TextStyle = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    /** Small capitals for overlines, card numerals and section marks. */
    val overline: TextStyle = TextStyle(fontFamily = Cinzel, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 3.sp),
    val hero: TextStyle = TextStyle(fontFamily = Playfair, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 38.sp)
)

object Dimens {
    val base = 4.dp
    val screen = 20.dp
    val cardRadius = 16.dp
    val buttonRadius = 24.dp
    val touch = 48.dp
}

val LocalAstraColors = staticCompositionLocalOf { DarkAstra }
val LocalAstraType = staticCompositionLocalOf { AstraType() }

object Astra {
    val colors: AstraColors @Composable get() = LocalAstraColors.current
    val type: AstraType @Composable get() = LocalAstraType.current
}

@Composable
fun AstraTheme(choice: ThemeChoice = ThemeChoice.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (choice) { ThemeChoice.DARK -> true; ThemeChoice.LIGHT -> false; ThemeChoice.SYSTEM -> isSystemInDarkTheme() }
    val c = if (dark) DarkAstra else LightAstra
    val scheme = if (dark) darkColorScheme(
        primary = c.gold, onPrimary = c.onGold, secondary = c.lavender, background = c.background, onBackground = c.text,
        surface = c.backgroundAlt, onSurface = c.text, surfaceVariant = c.surface, onSurfaceVariant = c.textSecondary,
        outline = c.border, error = c.error, surfaceContainerHigh = c.surface, surfaceContainer = c.backgroundAlt
    ) else lightColorScheme(
        primary = c.purple, onPrimary = Color.White, secondary = c.lavender, background = c.background, onBackground = c.text,
        surface = c.surface, onSurface = c.text, surfaceVariant = c.backgroundAlt, onSurfaceVariant = c.textSecondary,
        outline = c.border, error = c.error, surfaceContainerHigh = c.surface, surfaceContainer = c.backgroundAlt
    )
    CompositionLocalProvider(LocalAstraColors provides c, LocalAstraType provides AstraType()) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
