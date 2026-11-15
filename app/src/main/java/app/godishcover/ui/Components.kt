package app.godishcover.ui

import app.godishcover.data.FoodArt
import androidx.compose.ui.platform.testTag

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.godishcover.R
import app.godishcover.data.Categories
import app.godishcover.data.DishView
import app.godishcover.data.Geo
import app.godishcover.data.Suggest
import app.godishcover.data.average

/** Whether taps should buzz. Set from Settings. */
object Feel { @Volatile var haptics: Boolean = true }

/** Clickable with a soft press-in and an optional light tap. */
fun Modifier.pressable(haptic: Boolean = false, onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed && !Dc.reduceMotion) 0.97f else 1f,
        spring(stiffness = Spring.StiffnessMediumLow), label = "press",
    )
    val feedback = LocalHapticFeedback.current
    this.scale(scale).clickable(interactionSource = source, indication = null) {
        if (haptic && Feel.haptics) feedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onClick()
    }
}

@Composable
fun Wordmark(height: Dp, modifier: Modifier = Modifier, light: Boolean = Dc.isDark) {
    Image(
        painterResource(if (light) R.drawable.wordmark_light else R.drawable.wordmark),
        contentDescription = "Dishcover", modifier = modifier.height(height),
    )
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, busy: Boolean = false) {
    val feedback = LocalHapticFeedback.current
    Button(
        onClick = {
            if (Feel.haptics) feedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        enabled = enabled && !busy,
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Dc.Wine, contentColor = Dc.OnAccent,
            disabledContainerColor = Dc.ChipLine, disabledContentColor = Dc.Muted,
        ),
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Dc.Muted, strokeWidth = 2.dp)
        else Text(text, style = MaterialTheme.typography.titleMedium, color = Color.Unspecified, textAlign = TextAlign.Center)
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 52.dp),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.5.dp, Dc.ChipLine),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Dc.Ink),
        contentPadding = PaddingValues(horizontal = 20.dp),
    ) { Text(text, style = MaterialTheme.typography.titleMedium, color = Color.Unspecified, textAlign = TextAlign.Center) }
}

enum class ChipKind { Normal, Bad, Try }

@Composable
fun Chip(label: String, on: Boolean, onClick: () -> Unit, kind: ChipKind = ChipKind.Normal) {
    val target = when {
        !on -> Dc.White
        kind == ChipKind.Bad -> Dc.Wine
        kind == ChipKind.Try -> Dc.Green
        else -> Dc.Ink
    }
    val bg by animateColorAsState(target, tween(180), label = "chip")
    val border by animateColorAsState(if (on) target else Dc.ChipLine, tween(180), label = "chipBorder")
    val fg by animateColorAsState(if (on) Dc.White else Dc.Ink, tween(180), label = "chipText")
    Box(
        Modifier
            .heightIn(min = 44.dp)
            .clip(CircleShape)
            .background(bg)
            .border(1.5.dp, border, CircleShape)
            .pressable(haptic = true, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = fg)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipFlow(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

enum class TagKind { Plain, You, Try, Low }

@Composable
fun Tag(label: String, kind: TagKind = TagKind.Plain) {
    val (bg, fg) = when (kind) {
        TagKind.You -> Dc.WineTint to Dc.WineDark
        TagKind.Try -> Dc.GreenTint to Dc.GreenInk
        TagKind.Low -> Dc.AmberTint to Dc.AmberInk
        TagKind.Plain -> Dc.TagBg to Dc.TagInk
    }
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = fg,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(bg).padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DishTags(v: DishView, showCount: Boolean = false) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        v.myStars?.let { Tag("You $it★", TagKind.You) }
        if (v.wanted && v.myStars == null) Tag("Want to try", TagKind.Try)
        val d = v.dish
        if (d.ratingCount > 0) {
            Tag("Everyone " + String.format("%.1f", d.average) + if (showCount) " (${d.ratingCount})" else "")
        } else Tag("No ratings yet")
        if (Suggest.isHealthy(d)) Tag("Healthy pick", TagKind.Try)
        if (v.allergyHit) Tag("Lists your allergy", TagKind.Low)
    }
}

@Composable
fun Card(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.pressable(onClick = onClick) else Modifier)
            .clip(RoundedCornerShape(16.dp))
            .background(Dc.White)
            .border(1.dp, Dc.Line, RoundedCornerShape(16.dp))
            .padding(12.dp),
    ) { content() }
}

private val THUMB_HUES = listOf(0xFF7B1E2E, 0xFF2F6B4F, 0xFF1F5E8C, 0xFF8A6508, 0xFF5B2A6E, 0xFFB4451F)

/** Initials block standing in for photos. The color is picked from the name so lists look varied. */
@Composable
fun Thumb(text: String, size: Int = 52, round: Boolean = false, emoji: String? = null) {
    val hue = Color(THUMB_HUES[Math.floorMod(text.hashCode(), THUMB_HUES.size)])
    val bg = if (Dc.isDark) hue.copy(alpha = 0.28f) else hue.copy(alpha = 0.12f)
    Box(
        Modifier.size(size.dp).clip(if (round) CircleShape else RoundedCornerShape(12.dp)).background(bg),
        contentAlignment = Alignment.Center,
    ) {
        if (emoji != null) {
            Text(emoji, fontSize = (size * 0.52f).sp)
            return@Box
        }
        Text(
            text.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() },
            style = MaterialTheme.typography.titleSmall, color = if (Dc.isDark) Dc.Ink else hue,
        )
    }
}

/**
 * A restaurant's picture: its logo when it has a website, else its first Google photo,
 * else the food emoji.
 */
@Composable
fun PlacePic(p: app.godishcover.data.Place, size: Int = 44, emoji: String? = null) {
    val logo = remember(p.website, p.name) {
        val site = app.godishcover.data.ChainSites.site(p.name) ?: p.website
        runCatching { android.net.Uri.parse(site.let { if (it.startsWith("http")) it else "https://$it" }).host }
            .getOrNull()?.removePrefix("www.")?.takeIf { site.isNotBlank() && it.contains('.') }
            ?.let { "https://www.google.com/s2/favicons?domain=$it&sz=128" }
    }
    var url by remember(p.id, p.googlePlaceId) { mutableStateOf(logo) }
    var triedPhoto by remember(p.id, p.googlePlaceId) { mutableStateOf(false) }
    val src = LocalPlacePhotos.current
    LaunchedEffect(url, triedPhoto) {
        if (url == null && !triedPhoto && src != null) { triedPhoto = true; url = src.photoUri(p) }
    }
    val u = url
    if (u == null) { Thumb(p.name, size, emoji = emoji); return }
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape(12.dp)).background(if (Dc.isDark) Color(0xFF2A2A2A) else Color.White),
        contentAlignment = Alignment.Center,
    ) {
        coil.compose.AsyncImage(
            model = u, contentDescription = null,
            contentScale = if (u == logo) androidx.compose.ui.layout.ContentScale.Fit else androidx.compose.ui.layout.ContentScale.Crop,
            modifier = if (u == logo) Modifier.size((size * 0.72f).dp) else Modifier.size(size.dp),
            onState = { st ->
                if (st is coil.compose.AsyncImagePainter.State.Error) url = null
                else if (st is coil.compose.AsyncImagePainter.State.Success && u == logo) {
                    // Google answers a site with no icon by a tiny default globe; treat that as no logo.
                    val d = st.result.drawable
                    if (d.intrinsicWidth in 1..31) url = null
                }
            },
        )
    }
}

/** Set once at the top of the app so place pictures can ask Google for a photo. */
val LocalPlacePhotos = androidx.compose.runtime.staticCompositionLocalOf<app.godishcover.data.PlacesSource?> { null }

@Composable
fun DishRow(v: DishView, showPlace: Boolean = true, onClick: () -> Unit) {
    Card(Modifier.testTag("dish"), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Thumb(v.dish.name, emoji = FoodArt.forDish(v.dish.name, v.dish.categories))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row {
                    Text(
                        v.dish.name, style = MaterialTheme.typography.titleMedium,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                    )
                    if (v.dish.price.isNotBlank()) {
                        Spacer(Modifier.width(6.dp))
                        Text(v.dish.price, style = MaterialTheme.typography.bodyMedium, color = Dc.Muted)
                    }
                }
                if (showPlace) {
                    Text(
                        v.dish.placeName + (v.distanceKm?.let { " · " + Geo.miles(it) } ?: ""),
                        style = MaterialTheme.typography.bodySmall, color = Dc.Muted,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                DishTags(v)
            }
        }
    }
}

/** Tappable stars with a little bounce on the one just picked. */
@Composable
fun StarPicker(stars: Int, onPick: (Int) -> Unit, size: Int = 34) {
    Row {
        (1..5).forEach { n ->
            val scale by animateFloatAsState(
                if (n == stars && !Dc.reduceMotion) 1.18f else 1f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "star",
            )
            val tint by animateColorAsState(if (n <= stars) Dc.Wine else Dc.ChipLine, tween(150), label = "starTint")
            val feedback = LocalHapticFeedback.current
            IconButton(onClick = {
                if (Feel.haptics) feedback.performHapticFeedback(HapticFeedbackType.LongPress)
                onPick(n)
            }) {
                Icon(
                    Icons.Filled.Star, contentDescription = "$n ${if (n == 1) "star" else "stars"}",
                    tint = tint, modifier = Modifier.size(size.dp).scale(scale),
                )
            }
        }
    }
}

@Composable
fun StarsText(stars: Int) {
    Text("★".repeat(stars) + "☆".repeat((5 - stars).coerceAtLeast(0)), style = MaterialTheme.typography.labelLarge, color = Dc.Wine)
}

@Composable
fun TopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Dc.Ink) }
        } else Spacer(Modifier.width(12.dp))
        Text(
            title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = modifier.padding(top = 8.dp))
}

@Composable
fun Note(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = Dc.Muted, modifier = modifier)
}

@Composable
fun AllergyNote() {
    Note("Dishes listing one of your allergies are moved lower. Menus can be incomplete, so always check allergies with the restaurant.")
}

@Composable
fun GoogleAttribution() {
    Text("Places from Google", style = MaterialTheme.typography.labelMedium, color = Dc.Muted, fontWeight = FontWeight.SemiBold)
}

@Composable
fun DemoBanner() {
    Text(
        "Test mode: real Hartford menus, nothing you do is saved.",
        style = MaterialTheme.typography.labelMedium,
        color = Dc.AmberInk,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().background(Dc.AmberTint).padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
fun EmptyState(title: String, body: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = Dc.Muted)
        if (action != null) PrimaryButton(action, onAction)
    }
}

/** A settings-style row: title, optional detail, and something on the right. */
@Composable
fun SettingRow(title: String, detail: String? = null, onClick: (() -> Unit)? = null, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 56.dp).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (detail != null) Note(detail)
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}

fun catLabel(c: String) = Categories.label(c)

fun timeAgo(ms: Long, now: Long = System.currentTimeMillis()): String {
    if (ms <= 0) return ""
    val min = (now - ms) / 60000
    return when {
        min < 1 -> "just now"
        min < 60 -> "${min}m ago"
        min < 60 * 24 -> "${min / 60}h ago"
        min < 60 * 24 * 7 -> "${min / (60 * 24)}d ago"
        else -> java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(ms))
    }
}

@Suppress("unused")
private val keepTextStyle @Composable get() = LocalTextStyle.current
