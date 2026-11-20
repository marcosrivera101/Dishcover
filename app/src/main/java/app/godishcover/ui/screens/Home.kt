package app.godishcover.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.platform.testTag

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.godishcover.data.Categories
import app.godishcover.data.Geo
import app.godishcover.ui.AllergyNote
import app.godishcover.ui.AppViewModel
import app.godishcover.ui.Chip
import app.godishcover.ui.ChipFlow
import app.godishcover.ui.Dc
import app.godishcover.ui.DishRow
import app.godishcover.ui.Finding
import app.godishcover.ui.DishTags
import app.godishcover.ui.EmptyState
import app.godishcover.ui.Note
import app.godishcover.ui.PrimaryButton
import app.godishcover.ui.Routes
import app.godishcover.ui.SectionTitle
import app.godishcover.ui.TopBar
import app.godishcover.ui.Wordmark
import app.godishcover.ui.SecondaryButton
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.text.style.TextOverflow
import app.godishcover.ui.Tag
import app.godishcover.ui.TagKind
import app.godishcover.data.average
import app.godishcover.ui.Card
import app.godishcover.ui.catLabel

@Composable
fun HomeScreen(vm: AppViewModel, nav: NavHostController) {
    // Collected so the screen updates when data arrives.
    val profile by vm.profile.collectAsState()
    val dishes by vm.dishes.collectAsState()
    val loadingNearby by vm.loadingNearby.collectAsState()
    val ratings by vm.myRatings.collectAsState()
    val loc by vm.location.collectAsState()
    val real by vm.usingRealLocation.collectAsState()
    val meal = remember { AppViewModel.mealNow }
    var mood by rememberSaveable { mutableStateOf(Categories.defaultMood(meal)) }
    var moreMoods by rememberSaveable { mutableStateOf(false) }
    val name = profile?.publicName.orEmpty().substringBefore(' ')
    val placesNow by vm.places.collectAsState()
    val settings by vm.settings.collectAsState()
    @Suppress("UNUSED_VARIABLE") val deps = listOf(dishes, ratings, loc, placesNow, settings)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Wordmark(30.dp)
            Spacer(Modifier.weight(1f))
            Icon(Icons.Filled.LocationOn, contentDescription = null, tint = Dc.Wine, modifier = Modifier.size(18.dp))
            Text(
                if (real) "Near you" else "Hartford, CT",
                style = MaterialTheme.typography.labelMedium, color = Dc.Muted,
            )
        }
        Text(meal.greeting + if (name.isNotBlank()) ", $name." else ".", style = MaterialTheme.typography.bodyLarge, color = Dc.Muted)
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Dc.White)
                .border(1.dp, Dc.Line, RoundedCornerShape(20.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("${meal.label} time", style = MaterialTheme.typography.labelLarge, color = Dc.Wine)
            Text("What are you in the mood for?", style = MaterialTheme.typography.headlineSmall)
            Note("Your favorites first, then ${meal.hint}. Tap More for every kind of food.")
            val likes = profile?.likes.orEmpty().filter { it in Categories.ALL }
            val quick = (likes.take(4) + Categories.moodsFor(meal)).distinct()
            ChipFlow {
                quick.forEach { m ->
                    Chip(if (m.isEmpty()) "Surprise me" else catLabel(m), on = m == mood, onClick = { mood = m })
                }
                if (!moreMoods && mood.isNotEmpty() && mood !in quick) Chip(catLabel(mood), on = true, onClick = {})
                Chip(if (moreMoods) "Fewer" else "More…", on = false, onClick = { moreMoods = !moreMoods })
            }
            AnimatedVisibility(moreMoods) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Categories.GROUPS.forEach { (title, cats) ->
                        Text(title, style = MaterialTheme.typography.labelLarge, color = Dc.Muted)
                        ChipFlow {
                            cats.filter { it !in quick }.forEach { m -> Chip(catLabel(m), on = m == mood, onClick = { mood = m }) }
                        }
                    }
                }
            }
            PrimaryButton(
                "Show me places for " + if (mood.isEmpty()) "anything good" else catLabel(mood).lowercase(),
                onClick = { nav.navigate(Routes.suggest(mood.ifEmpty { null })) },
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton("Healthy picks", onClick = { nav.navigate(Routes.search(healthy = true)) }, modifier = Modifier.weight(1f))
            SecondaryButton("Share a bite", onClick = { nav.navigate(Routes.post()) }, modifier = Modifier.weight(1f))
        }

        val trending = vm.trending().take(8)
        if (trending.isNotEmpty()) {
            SectionTitle("Trending near you")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(trending, key = { it.dish.id }) { v ->
                    Card(Modifier.width(200.dp), onClick = { nav.navigate(Routes.dish(v.dish.id)) }) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(v.dish.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(v.dish.placeName, style = MaterialTheme.typography.bodySmall, color = Dc.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Tag("★ " + String.format("%.1f", v.dish.average) + " · ${v.dish.ratingCount} ratings", TagKind.You)
                        }
                    }
                }
            }
        }

        val top = vm.topRated()
        val want = vm.wantToTry()
        val healthy = vm.healthyPicks().take(3)
        if (top.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("Your top-rated nearby", Modifier.weight(1f))
                TextButton(onClick = { nav.navigate(Routes.search()) }) { Text("See all", color = Dc.Wine) }
            }
            top.forEach { v -> DishRow(v) { nav.navigate(Routes.dish(v.dish.id)) } }
        }
        if (want.isNotEmpty()) {
            SectionTitle("On your want-to-try list")
            want.forEach { v -> DishRow(v) { nav.navigate(Routes.dish(v.dish.id)) } }
        }
        if (healthy.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("Healthy picks", Modifier.weight(1f))
                TextButton(onClick = { nav.navigate(Routes.search(healthy = true)) }) { Text("See all", color = Dc.Wine) }
            }
            healthy.forEach { v -> DishRow(v) { nav.navigate(Routes.dish(v.dish.id)) } }
            Note("Tagged healthy by the people who added them.")
        }
        if (top.isEmpty() && want.isEmpty()) {
            if (dishes.isEmpty() && loadingNearby) {
                Finding("Finding dishes near you…")
            } else if (dishes.isEmpty()) {
                EmptyState(
                    "No menus near you yet",
                    "Menus come from the people who use the app. Snap a menu at a place you love and it shows up here for everyone.",
                    "Add a menu",
                ) { nav.navigate(Routes.add()) }
            } else {
                Note("Rate a few dishes and your favorites show up here.")
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun SuggestionsScreen(vm: AppViewModel, nav: NavHostController, category: String?) {
    val dishes by vm.dishes.collectAsState()
    val ratings by vm.myRatings.collectAsState()
    val loc by vm.location.collectAsState()
    val profile by vm.profile.collectAsState()
    val placesNow by vm.places.collectAsState()
    val settings by vm.settings.collectAsState()
    @Suppress("UNUSED_VARIABLE") val deps = listOf(dishes, ratings, loc, profile, placesNow, settings)
    val meal = remember { AppViewModel.mealNow }
    val loadingNearby by vm.loadingNearby.collectAsState()
    val loadingCat by vm.loadingCategory.collectAsState()
    LaunchedEffect(category) { vm.loadCategory(category) }
    val list = vm.suggestions(category)
    Column(Modifier.fillMaxSize()) {
        TopBar("Suggestions", onBack = { nav.popBackStack() })
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val what = if (category == null) "Something good" else catLabel(category)
            Text("$what for ${meal.label.lowercase()}", style = MaterialTheme.typography.headlineMedium)
            if (list.isEmpty() && (loadingNearby || loadingCat != null)) {
                Finding("Finding ${if (category == null) "dishes" else catLabel(category).lowercase()} near you…")
            } else if (list.isEmpty()) {
                EmptyState(
                    "No ${if (category == null) "" else catLabel(category).lowercase() + " "}dishes near you yet",
                    "Menus come from the people who use the app. Snap a menu at a place you love and it shows up here for everyone.",
                    "Add a menu",
                ) { nav.navigate(Routes.add()) }
            } else {
                Note("${list.size} ${if (list.size == 1) "place" else "places"} near you, best first: your stars, then everyone's, then distance.")
                list.forEachIndexed { i, s ->
                    Card(Modifier.testTag("suggestion"), onClick = { s.place?.let { nav.navigate(Routes.place(it.id)) } }) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier.size(30.dp).clip(CircleShape).background(Dc.Ink),
                                    contentAlignment = Alignment.Center,
                                ) { Text("${i + 1}", style = MaterialTheme.typography.titleSmall, color = Dc.White) }
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(s.placeName, style = MaterialTheme.typography.titleMedium)
                                    s.distanceKm?.let { Note(Geo.miles(it)) }
                                }
                            }
                            s.dishes.forEach { v ->
                                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Dc.Paper).padding(10.dp)) {
                                    Row {
                                        Text(v.dish.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                        Text(v.dish.price, style = MaterialTheme.typography.bodySmall, color = Dc.Muted)
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    DishTags(v)
                                }
                            }
                        }
                    }
                }
                AllergyNote()
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
