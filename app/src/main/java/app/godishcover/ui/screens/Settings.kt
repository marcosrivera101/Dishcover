package app.godishcover.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as SystemSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.godishcover.BuildConfig
import app.godishcover.data.Accents
import app.godishcover.data.Changelog
import app.godishcover.data.CrashLog
import app.godishcover.data.FEEDBACK_EMAIL
import app.godishcover.data.FeedType
import app.godishcover.ui.AppViewModel
import app.godishcover.ui.Chip
import app.godishcover.ui.ChipFlow
import app.godishcover.ui.Dc
import app.godishcover.ui.Note
import app.godishcover.ui.PrimaryButton
import app.godishcover.ui.Routes
import app.godishcover.ui.SecondaryButton
import app.godishcover.ui.SectionTitle
import app.godishcover.ui.SettingRow
import app.godishcover.ui.TopBar
import kotlin.math.roundToInt

@Composable
private fun Toggle(checked: Boolean, onChange: (Boolean) -> Unit) {
    Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = Dc.Wine, checkedThumbColor = Dc.OnAccent))
}

/** Opens the user's email app with a message addressed to the feedback inbox. Nothing is sent without them pressing send. */
fun sendEmail(ctx: Context, subject: String, body: String) {
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$FEEDBACK_EMAIL"))
        .putExtra(Intent.EXTRA_EMAIL, arrayOf(FEEDBACK_EMAIL))
        .putExtra(Intent.EXTRA_SUBJECT, subject)
        .putExtra(Intent.EXTRA_TEXT, body)
    runCatching { ctx.startActivity(Intent.createChooser(intent, "Send email")) }
}

@Composable
fun SettingsScreen(vm: AppViewModel, nav: NavHostController) {
    val s by vm.settings.collectAsState()
    val ctx = LocalContext.current
    var radius by remember { mutableFloatStateOf(s.radiusMi.toFloat()) }
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.syncAlerts() }
    fun wantAlerts() {
        if (Build.VERSION.SDK_INT >= 33) askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("Settings", onBack = { nav.popBackStack() })
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SectionTitle("Look")
            Text("Theme", style = MaterialTheme.typography.titleMedium)
            ChipFlow {
                listOf("system" to "Match phone", "light" to "Light", "dark" to "Dark").forEach { (k, label) ->
                    Chip(label, s.theme == k, onClick = { vm.setSettings { it.copy(theme = k) } })
                }
            }
            Text("Accent color", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                Accents.ALL.forEach { (name, pair) ->
                    val c = Color(if (Dc.isDark) pair.second else pair.first)
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(c)
                            .border(2.dp, if (s.accent == name) Dc.Ink else Color.Transparent, CircleShape)
                            .clickable { vm.setSettings { it.copy(accent = name) } },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (s.accent == name) Icon(Icons.Filled.Check, contentDescription = Accents.label(name), tint = Dc.OnAccent)
                    }
                }
            }
            Note("Wine is the Dishcover color. Pick another if you like.")
            SettingRow("Larger text") { Toggle(s.largeText) { v -> vm.setSettings { it.copy(largeText = v) } } }
            SettingRow("Reduce motion", "Turns off most animations.") { Toggle(s.reduceMotion) { v -> vm.setSettings { it.copy(reduceMotion = v) } } }
            SettingRow("Vibrate on taps") { Toggle(s.haptics) { v -> vm.setSettings { it.copy(haptics = v) } } }
            HorizontalDivider(color = Dc.Line)

            SectionTitle("Nearby")
            val shown = radius.roundToInt()
            Text(
                "Nearby means within " + if (s.useKm) "${(shown * 1.609344).roundToInt()} km" else "$shown ${if (shown == 1) "mile" else "miles"}",
                style = MaterialTheme.typography.titleMedium,
            )
            Slider(
                value = radius, onValueChange = { radius = it },
                onValueChangeFinished = { vm.setSettings { it.copy(radiusMi = radius.roundToInt().coerceIn(1, 50)) } },
                valueRange = 1f..50f,
                colors = SliderDefaults.colors(thumbColor = Dc.Wine, activeTrackColor = Dc.Wine, inactiveTrackColor = Dc.ChipLine),
            )
            Note("Used for suggestions, search, the feed and new-place alerts.")
            SettingRow("Use kilometers") { Toggle(s.useKm) { v -> vm.setSettings { it.copy(useKm = v) } } }
            SettingRow("Healthy picks first", "Dishes tagged healthy move up in suggestions.") {
                Toggle(s.healthyFirst) { v -> vm.setSettings { it.copy(healthyFirst = v) } }
            }
            HorizontalDivider(color = Dc.Line)

            SectionTitle("Feed")
            Note("What shows under \"All\".")
            FeedType.ALL.forEach { t ->
                SettingRow(FeedType.label(t)) {
                    Toggle(t in s.feedTypes) { v -> vm.setSettings { it.copy(feedTypes = if (v) it.feedTypes + t else it.feedTypes - t) } }
                }
            }
            SettingRow("Restaurant news", "Headlines from Eater, QSR Magazine and Restaurant Dive.") {
                Toggle(s.showNews) { v -> vm.setSettings { it.copy(showNews = v) } }
            }
            Text("Open the app on", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            ChipFlow {
                Chip("Today", s.startTab == "today", onClick = { vm.setSettings { it.copy(startTab = "today") } })
                Chip("Feed", s.startTab == "feed", onClick = { vm.setSettings { it.copy(startTab = "feed") } })
            }
            HorizontalDivider(color = Dc.Line, modifier = Modifier.padding(top = 8.dp))

            SectionTitle("Alerts")
            SettingRow("New places nearby") { Toggle(s.alertsNewPlaces) { v -> if (v) wantAlerts(); vm.setSettings { it.copy(alertsNewPlaces = v) } } }
            SettingRow("New dishes on menus nearby") { Toggle(s.alertsNewMenus) { v -> if (v) wantAlerts(); vm.setSettings { it.copy(alertsNewMenus = v) } } }
            SettingRow("New dishes at places I saved") { Toggle(s.alertsSaved) { v -> if (v) wantAlerts(); vm.setSettings { it.copy(alertsSaved = v) } } }
            if (vm.repo.isDemo) Note("Alerts turn on when the app is connected to the server.")
            TextButton(onClick = {
                runCatching {
                    ctx.startActivity(
                        Intent(SystemSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(SystemSettings.EXTRA_APP_PACKAGE, ctx.packageName),
                    )
                }
            }) { Text("Phone notification settings", color = Dc.Wine) }
            HorizontalDivider(color = Dc.Line)

            SectionTitle("Updates")
            SettingRow("Check for updates when the app opens") { Toggle(s.autoCheckUpdates) { v -> vm.setSettings { it.copy(autoCheckUpdates = v) } } }
            SettingRow("About, updates and change log", onClick = { nav.navigate(Routes.ABOUT) }) { Text("›", color = Dc.Muted) }
            SettingRow("Help and troubleshooting", onClick = { nav.navigate(Routes.HELP) }) { Text("›", color = Dc.Muted) }
            TextButton(onClick = { vm.prefs.resetSettings(); radius = vm.settings.value.radiusMi.toFloat(); vm.setSettings { it } }) {
                Text("Reset settings", color = Dc.Wine)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun UpdateBanner(vm: AppViewModel) {
    val update by vm.update.collectAsState()
    val ctx = LocalContext.current
    AnimatedVisibility(visible = update != null, enter = expandVertically(), exit = shrinkVertically()) {
        val u = update
        if (u != null) {
            Row(
                Modifier.fillMaxWidth().background(Dc.WineTint).padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Version ${u.versionName} is ready", style = MaterialTheme.typography.titleSmall, color = Dc.WineDark, modifier = Modifier.weight(1f))
                TextButton(onClick = { openLink(ctx, u.apkUrl) }) { Text("Update", color = Dc.Wine) }
                TextButton(onClick = { vm.update.value = null }) { Text("Later", color = Dc.Muted) }
            }
        }
    }
}

@Composable
private fun ChangelogList(limit: Int = Int.MAX_VALUE) {
    Changelog.ALL.take(limit).forEach { e ->
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 12.dp)) {
            Text("Version ${e.version} · ${e.date}", style = MaterialTheme.typography.titleMedium)
            e.items.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangelogSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Dc.White) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("What's new in Dishcover", style = MaterialTheme.typography.headlineSmall)
            ChangelogList(limit = 1)
            PrimaryButton("Nice", onClick = onDismiss)
        }
    }
}

@Composable
fun AboutScreen(vm: AppViewModel, nav: NavHostController) {
    val ctx = LocalContext.current
    val update by vm.update.collectAsState()
    val checked by vm.updateChecked.collectAsState()
    Column(Modifier.fillMaxSize()) {
        TopBar("About and updates", onBack = { nav.popBackStack() })
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Dishcover ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.headlineSmall)
            Note("Build ${BuildConfig.VERSION_CODE}" + if (vm.repo.isDemo) " · demo mode" else "")
            val u = update
            if (u != null) {
                Text("Version ${u.versionName} is ready", style = MaterialTheme.typography.titleMedium, color = Dc.Wine)
                if (u.notes.isNotBlank()) Text(u.notes, style = MaterialTheme.typography.bodyMedium)
                PrimaryButton("Download the update", onClick = { openLink(ctx, u.apkUrl) })
                Note("Your phone downloads the new version; open it from the download notice to install. Your account and ratings stay.")
            } else {
                SecondaryButton("Check for updates", onClick = { vm.checkForUpdate(manual = true) }, modifier = Modifier.fillMaxWidth())
                checked?.let { Note(it) }
            }
            SectionTitle("Change log")
            ChangelogList()
            SecondaryButton("Terms and Community rules", onClick = { nav.navigate(Routes.RULES) }, modifier = Modifier.fillMaxWidth())
            Note("Restaurant search by Google. News headlines belong to their publishers.")
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun HelpScreen(vm: AppViewModel, nav: NavHostController) {
    val ctx = LocalContext.current
    val real by vm.usingRealLocation.collectAsState()
    var idea by remember { mutableStateOf("") }
    var crash by remember { mutableStateOf(CrashLog.read(ctx)) }
    Column(Modifier.fillMaxSize()) {
        TopBar("Help", onBack = { nav.popBackStack() })
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle("Got an idea?")
            OutlinedTextField(idea, { idea = it.take(1000) }, placeholder = { Text("What should Dishcover do next?") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            PrimaryButton("Send idea", enabled = idea.isNotBlank(), onClick = {
                sendEmail(ctx, "Dishcover idea", idea.trim() + "\n\n" + CrashLog.deviceInfo())
            })
            Note("Opens your email app with a message to the Dishcover team.")

            SectionTitle("Something broken?")
            val c = crash
            if (c != null) {
                Text("Dishcover closed unexpectedly last time.", style = MaterialTheme.typography.titleMedium, color = Dc.Wine)
                Note("The report has the error details and your phone model. Nothing personal.")
            }
            PrimaryButton("Send error report", onClick = {
                sendEmail(
                    ctx, "Dishcover error report",
                    "What happened:\n\n\n---\n" + CrashLog.deviceInfo() +
                        "\nLocation on: $real · Google places: ${vm.placesSource.available}" +
                        (c?.let { "\n\nLast crash:\n$it" } ?: "\n\nNo crash recorded."),
                )
                CrashLog.clear(ctx)
                crash = null
            })

            SectionTitle("Quick fixes")
            FixRow("Places or feed look out of date", "Reload everything near you.", "Reload") { vm.pullRefresh() }
            FixRow(
                "Nearby is wrong",
                if (real) "Using your phone's location." else "Location is off, so Dishcover is showing Hartford, CT.",
                "Location settings",
            ) {
                runCatching {
                    ctx.startActivity(Intent(SystemSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName)))
                }
            }
            FixRow("Not getting alerts", "Check that notifications are allowed for Dishcover.", "Notification settings") {
                runCatching {
                    ctx.startActivity(
                        Intent(SystemSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(SystemSettings.EXTRA_APP_PACKAGE, ctx.packageName),
                    )
                }
            }
            FixRow("The app looks odd", "Put theme, colors and other settings back to how they started.", "Reset settings") {
                vm.prefs.resetSettings(); vm.setSettings { it }
            }
            FixRow("Sign-in is stuck", "Sign out, then sign back in.", "Sign out") { vm.signOut() }
            FixRow("See the intro again", "Replays the welcome screens next time you're signed out.", "Reset intro") {
                vm.setSettings { it.copy(introSeen = false) }
            }

            SectionTitle("Good to know")
            listOf(
                "Menus come from people using the app, so a place can be missing dishes. Add them from the Add tab.",
                "Allergy info only shows when a menu prints it. Always check with the restaurant.",
                "Only the person who added a dish can edit it. Use \"Flag a problem\" on the dish for anything wrong.",
                "The menu reader allows 10 reads a day per person.",
            ).forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
            Note(CrashLog.deviceInfo())
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun FixRow(title: String, detail: String, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Note(detail)
        }
        TextButton(onClick = onClick) { Text(action, color = Dc.Wine) }
    }
}
