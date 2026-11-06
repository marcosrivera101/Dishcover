package app.godishcover.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.godishcover.R

object Dc {
    val Ink = Color(0xFF121212)
    val Paper = Color(0xFFFAFAF8)
    val White = Color(0xFFFFFFFF)
    val Wine = Color(0xFF7B1E2E)
    val WineDark = Color(0xFF5E1622)
    val WineTint = Color(0xFFF4E6E8)
    val Muted = Color(0xFF66625C)
    val Line = Color(0xFFE8E6E1)
    val ChipLine = Color(0xFFD3CFC8)
    val TagBg = Color(0xFFF2F1EE)
    val TagInk = Color(0xFF3F3C38)
    val Green = Color(0xFF2F6B4F)
    val GreenTint = Color(0xFFE3F1E8)
    val GreenInk = Color(0xFF1F5A3A)
    val AmberTint = Color(0xFFFFF3D6)
    val AmberInk = Color(0xFF6B4700)
    val Thumb = Color(0xFFF1EFEC)
}

val Jakarta = FontFamily(
    Font(R.font.jakarta_regular, FontWeight.Normal),
    Font(R.font.jakarta_semibold, FontWeight.SemiBold),
    Font(R.font.jakarta_bold, FontWeight.Bold),
    Font(R.font.jakarta_extrabold, FontWeight.ExtraBold),
)

private fun t(size: Int, weight: FontWeight, line: Int = (size * 1.35).toInt()) =
    TextStyle(fontFamily = Jakarta, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, color = Dc.Ink)

private val type = Typography(
    displaySmall = t(32, FontWeight.ExtraBold, 38),
    headlineMedium = t(26, FontWeight.ExtraBold, 32),
    headlineSmall = t(22, FontWeight.ExtraBold, 28),
    titleLarge = t(19, FontWeight.Bold),
    titleMedium = t(16, FontWeight.Bold),
    titleSmall = t(14, FontWeight.Bold),
    bodyLarge = t(16, FontWeight.Normal),
    bodyMedium = t(14, FontWeight.Normal),
    bodySmall = t(13, FontWeight.Normal),
    labelLarge = t(15, FontWeight.SemiBold),
    labelMedium = t(12, FontWeight.SemiBold),
    labelSmall = t(11, FontWeight.SemiBold),
)

@Composable
fun DishcoverTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Dc.Wine, onPrimary = Dc.White,
            secondary = Dc.Ink, onSecondary = Dc.White,
            background = Dc.Paper, onBackground = Dc.Ink,
            surface = Dc.White, onSurface = Dc.Ink,
            surfaceVariant = Dc.TagBg, onSurfaceVariant = Dc.Muted,
            outline = Dc.ChipLine, error = Dc.Wine,
        ),
        typography = type,
        content = content,
    )
}
