package app.godishcover.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import app.godishcover.R
import app.godishcover.data.Accents
import app.godishcover.data.Settings

data class Palette(
    val dark: Boolean,
    val ink: Color, val paper: Color, val surface: Color,
    val accent: Color, val accentDark: Color, val accentTint: Color,
    val muted: Color, val line: Color, val chipLine: Color,
    val tagBg: Color, val tagInk: Color,
    val green: Color, val greenTint: Color, val greenInk: Color,
    val amberTint: Color, val amberInk: Color, val thumb: Color,
)

private fun lightPalette(accent: Color) = Palette(
    dark = false,
    ink = Color(0xFF121212), paper = Color(0xFFFAFAF8), surface = Color(0xFFFFFFFF),
    accent = accent, accentDark = accent.mix(Color.Black, 0.22f), accentTint = accent.mix(Color.White, 0.88f),
    muted = Color(0xFF66625C), line = Color(0xFFE8E6E1), chipLine = Color(0xFFD3CFC8),
    tagBg = Color(0xFFF2F1EE), tagInk = Color(0xFF3F3C38),
    green = Color(0xFF2F6B4F), greenTint = Color(0xFFE3F1E8), greenInk = Color(0xFF1F5A3A),
    amberTint = Color(0xFFFFF3D6), amberInk = Color(0xFF6B4700), thumb = Color(0xFFF1EFEC),
)

private fun darkPalette(accent: Color) = Palette(
    dark = true,
    ink = Color(0xFFF4F2EE), paper = Color(0xFF0E0E0E), surface = Color(0xFF1A1A1A),
    accent = accent, accentDark = accent.mix(Color.White, 0.25f), accentTint = accent.mix(Color(0xFF0E0E0E), 0.78f),
    muted = Color(0xFFA8A39B), line = Color(0xFF2B2A28), chipLine = Color(0xFF3A3835),
    tagBg = Color(0xFF262523), tagInk = Color(0xFFD9D5CE),
    green = Color(0xFF6FC59A), greenTint = Color(0xFF16291F), greenInk = Color(0xFF9FDDBB),
    amberTint = Color(0xFF33290F), amberInk = Color(0xFFF0CF7A), thumb = Color(0xFF262523),
)

private fun Color.mix(other: Color, t: Float) = Color(
    red = red + (other.red - red) * t,
    green = green + (other.green - green) * t,
    blue = blue + (other.blue - blue) * t,
    alpha = 1f,
)

/**
 * The app's colors. Screens read them as `Dc.Ink`, `Dc.Wine` and so on; they follow the
 * theme and accent chosen in Settings. ("Wine" is the accent: wine by default.)
 */
object Dc {
    var palette by mutableStateOf(lightPalette(Color(0xFF7B1E2E)))
        internal set
    var reduceMotion by mutableStateOf(false)
        internal set

    val isDark get() = palette.dark
    val Ink get() = palette.ink
    val Paper get() = palette.paper
    val White get() = palette.surface
    /** Always-light text for use on the accent color. */
    val OnAccent get() = if (palette.dark) Color(0xFF121212) else Color(0xFFFFFFFF)
    val Wine get() = palette.accent
    val WineDark get() = palette.accentDark
    val WineTint get() = palette.accentTint
    val Muted get() = palette.muted
    val Line get() = palette.line
    val ChipLine get() = palette.chipLine
    val TagBg get() = palette.tagBg
    val TagInk get() = palette.tagInk
    val Green get() = palette.green
    val GreenTint get() = palette.greenTint
    val GreenInk get() = palette.greenInk
    val AmberTint get() = palette.amberTint
    val AmberInk get() = palette.amberInk
    val Thumb get() = palette.thumb
    /** The brand wine, whatever accent is chosen (splash, app icon). */
    val Brand = Color(0xFF7B1E2E)
}

val Jakarta = FontFamily(
    Font(R.font.jakarta_regular, FontWeight.Normal),
    Font(R.font.jakarta_semibold, FontWeight.SemiBold),
    Font(R.font.jakarta_bold, FontWeight.Bold),
    Font(R.font.jakarta_extrabold, FontWeight.ExtraBold),
)

// No color here: text takes the surrounding content color, so buttons and bars can set their own.
@Suppress("UNUSED_PARAMETER")
private fun t(size: Int, weight: FontWeight, ink: Color, line: Int = (size * 1.35).toInt()) =
    TextStyle(fontFamily = Jakarta, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp)

private fun typography(ink: Color) = Typography(
    displaySmall = t(32, FontWeight.ExtraBold, ink, 38),
    headlineMedium = t(26, FontWeight.ExtraBold, ink, 32),
    headlineSmall = t(22, FontWeight.ExtraBold, ink, 28),
    titleLarge = t(19, FontWeight.Bold, ink),
    titleMedium = t(16, FontWeight.Bold, ink),
    titleSmall = t(14, FontWeight.Bold, ink),
    bodyLarge = t(16, FontWeight.Normal, ink),
    bodyMedium = t(14, FontWeight.Normal, ink),
    bodySmall = t(13, FontWeight.Normal, ink),
    labelLarge = t(15, FontWeight.SemiBold, ink),
    labelMedium = t(12, FontWeight.SemiBold, ink),
    labelSmall = t(11, FontWeight.SemiBold, ink),
)

@Composable
fun DishcoverTheme(settings: Settings, content: @Composable () -> Unit) {
    val dark = when (settings.theme) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val pair = Accents.ALL[settings.accent] ?: Accents.ALL.getValue("wine")
    val p = if (dark) darkPalette(Color(pair.second)) else lightPalette(Color(pair.first))
    // Set before children compose so the first frame already has the right colors.
    if (Dc.palette != p) Dc.palette = p
    if (Dc.reduceMotion != settings.reduceMotion) Dc.reduceMotion = settings.reduceMotion
    val scheme = if (dark) {
        darkColorScheme(
            primary = p.accent, onPrimary = Color(0xFF121212), secondary = p.ink, onSecondary = p.paper,
            background = p.paper, onBackground = p.ink, surface = p.surface, onSurface = p.ink,
            surfaceVariant = p.tagBg, onSurfaceVariant = p.muted, outline = p.chipLine, error = p.accent,
            surfaceContainer = p.surface, surfaceContainerHigh = p.surface, surfaceContainerHighest = p.tagBg,
            surfaceContainerLow = p.surface,
        )
    } else {
        lightColorScheme(
            primary = p.accent, onPrimary = Color.White, secondary = p.ink, onSecondary = Color.White,
            background = p.paper, onBackground = p.ink, surface = p.surface, onSurface = p.ink,
            surfaceVariant = p.tagBg, onSurfaceVariant = p.muted, outline = p.chipLine, error = p.accent,
            surfaceContainer = p.surface, surfaceContainerHigh = p.surface, surfaceContainerHighest = p.tagBg,
            surfaceContainerLow = p.surface,
        )
    }
    val density = LocalDensity.current
    val scaled = if (settings.largeText) Density(density.density, density.fontScale * 1.15f) else density
    CompositionLocalProvider(LocalDensity provides scaled, LocalContentColor provides p.ink) {
        MaterialTheme(colorScheme = scheme, typography = typography(p.ink), content = content)
    }
}
