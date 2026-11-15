package app.godishcover.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import app.godishcover.data.Rewards
import app.godishcover.data.Stats
import app.godishcover.ui.Card
import app.godishcover.ui.Dc
import app.godishcover.ui.Note
import app.godishcover.ui.SectionTitle

/** Level, points, progress to the next level, badges and how to earn more. */
@Composable
fun RewardsCard(stats: Stats) {
    var showHow by rememberSaveable { mutableStateOf(false) }
    val level = stats.level.coerceAtLeast(1)
    val start = Rewards.LEVELS[level - 1].first
    val next = Rewards.nextAt(level)
    Card {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Level $level · ${Rewards.levelName(level)}", style = MaterialTheme.typography.titleLarge, color = Dc.Wine)
                    Text("${stats.points} points", style = MaterialTheme.typography.bodyMedium, color = Dc.Muted)
                }
                Text("${stats.badges.size}/${Rewards.BADGES.size} badges", style = MaterialTheme.typography.labelLarge, color = Dc.Muted)
            }
            if (next != null) {
                val frac = ((stats.points - start).toFloat() / (next - start)).coerceIn(0f, 1f)
                LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth(), color = Dc.Wine, trackColor = Dc.WineTint)
                Note("${next - stats.points} points to ${Rewards.levelName(level + 1)}")
            } else Note("Top level. You're a Dishcover Legend.")
            TextButton(onClick = { showHow = !showHow }) { Text(if (showHow) "Hide how points work" else "How points work", color = Dc.Wine) }
            AnimatedVisibility(showHow) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Rewards.HOW.forEach { (what, pts) ->
                        Row { Text(what, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f)); Text(pts, style = MaterialTheme.typography.labelLarge, color = Dc.Wine) }
                    }
                    Note("Rating the same dish again doesn't count twice, likes count up to 20 points a day, and removed posts take their points back.")
                    SectionTitle("Levels")
                    Rewards.LEVELS.forEachIndexed { i, (pts, name) ->
                        Row { Text("${i + 1}. $name", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f)); Text("$pts", style = MaterialTheme.typography.bodySmall, color = Dc.Muted) }
                    }
                    SectionTitle("Perks")
                    Rewards.PERKS.forEach { (lvl, perk) -> Text("${Rewards.levelName(lvl)}: $perk", style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
    }
    SectionTitle("Badges")
    Rewards.BADGES.chunked(2).forEach { pair ->
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            pair.forEach { b ->
                val earned = b.id in stats.badges
                Card(Modifier.weight(1f).alpha(if (earned) 1f else 0.45f)) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(b.emoji, style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.width(8.dp))
                            Text(b.name, style = MaterialTheme.typography.titleSmall)
                        }
                        Text(if (earned) "Earned" else b.how, style = MaterialTheme.typography.bodySmall, color = Dc.Muted)
                    }
                }
            }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}
