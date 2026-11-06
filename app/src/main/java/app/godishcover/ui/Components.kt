package app.godishcover.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.godishcover.data.Categories
import app.godishcover.data.DishView
import app.godishcover.data.Geo
import app.godishcover.data.average

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, busy: Boolean = false) {
    Button(
        onClick = onClick,
        enabled = enabled && !busy,
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Dc.Wine, contentColor = Dc.White, disabledContainerColor = Dc.ChipLine),
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Dc.White, strokeWidth = 2.dp)
        else Text(text, style = MaterialTheme.typography.titleMedium, color = Color.Unspecified)
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
    ) { Text(text, style = MaterialTheme.typography.titleMedium, color = Color.Unspecified) }
}

enum class ChipKind { Normal, Bad, Try }

@Composable
fun Chip(label: String, on: Boolean, onClick: () -> Unit, kind: ChipKind = ChipKind.Normal) {
    val bg = when {
        !on -> Dc.White
        kind == ChipKind.Bad -> Dc.Wine
        kind == ChipKind.Try -> Dc.Green
        else -> Dc.Ink
    }
    Box(
        Modifier
            .heightIn(min = 44.dp)
            .clip(CircleShape)
            .background(bg)
            .border(1.5.dp, if (on) bg else Dc.ChipLine, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (on) Dc.White else Dc.Ink)
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
        if (v.allergyHit) Tag("Lists your allergy", TagKind.Low)
    }
}

@Composable
fun Card(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Dc.White)
            .border(1.dp, Dc.Line, RoundedCornerShape(16.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(12.dp),
    ) { content() }
}

/** Initials block standing in for photos (no photos in version 1). */
@Composable
fun Thumb(text: String, size: Int = 52) {
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape(12.dp)).background(Dc.Thumb),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() },
            style = MaterialTheme.typography.titleSmall, color = Dc.Muted,
        )
    }
}

@Composable
fun DishRow(v: DishView, showPlace: Boolean = true, onClick: () -> Unit) {
    Card(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Thumb(v.dish.name)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row {
                    Text(v.dish.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f, fill = false))
                    if (v.dish.price.isNotBlank()) {
                        Spacer(Modifier.width(6.dp))
                        Text(v.dish.price, style = MaterialTheme.typography.bodyMedium, color = Dc.Muted)
                    }
                }
                if (showPlace) {
                    Text(
                        v.dish.placeName + (v.distanceKm?.let { " · " + Geo.miles(it) } ?: ""),
                        style = MaterialTheme.typography.bodySmall, color = Dc.Muted,
                    )
                }
                DishTags(v)
            }
        }
    }
}

@Composable
fun TopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
        } else Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        actions()
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = modifier.padding(top = 8.dp))
}

@Composable
fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = Dc.Muted)
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
        "Demo mode: sample places, nothing is saved. Connect Firebase to go live.",
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

fun catLabel(c: String) = Categories.label(c)
