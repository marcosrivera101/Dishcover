package app.godishcover.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.godishcover.data.Categories
import app.godishcover.data.Geo
import app.godishcover.data.LatLng
import app.godishcover.data.Place
import app.godishcover.data.Suggest
import app.godishcover.ui.AppViewModel
import app.godishcover.ui.Card
import app.godishcover.ui.Chip
import app.godishcover.ui.Dc
import app.godishcover.ui.DishRow
import app.godishcover.ui.EmptyState
import app.godishcover.ui.GoogleAttribution
import app.godishcover.ui.Note
import app.godishcover.ui.Routes
import app.godishcover.ui.Thumb
import app.godishcover.ui.catLabel

@Composable
fun SearchScreen(vm: AppViewModel, nav: NavHostController, initialCat: String, initialHealthy: Boolean = false, initialMode: String = "dishes") {
    val dishes by vm.dishes.collectAsState()
    val places by vm.places.collectAsState()
    val ratings by vm.myRatings.collectAsState()
    val loc by vm.location.collectAsState()
    val google by vm.googleResults.collectAsState()
    @Suppress("UNUSED_VARIABLE") val deps = listOf(ratings)
    var query by rememberSaveable { mutableStateOf("") }
    var cat by rememberSaveable { mutableStateOf(initialCat) }
    var nearOnly by rememberSaveable { mutableStateOf(true) }
    var tab by rememberSaveable { mutableStateOf(if (initialMode == "places") "places" else "dishes") }
    var healthy by rememberSaveable { mutableStateOf(initialHealthy) }
    var tag by rememberSaveable { mutableStateOf("") }
    val settings by vm.settings.collectAsState()
    @Suppress("UNUSED_VARIABLE") val deps2 = settings

    LaunchedEffect(tab, loc) { if (tab == "places") vm.searchGoogle(query) }

    val q = query.trim().lowercase()
    val dishResults = dishes.asSequence()
        .filter { cat.isEmpty() || cat in it.categories }
        .filter { !healthy || Suggest.isHealthy(it) }
        .filter { tag.isEmpty() || tag in it.tags }
        .filter { q.isEmpty() || it.name.lowercase().contains(q) || it.placeName.lowercase().contains(q) }
        .map { vm.view(it) }
        .filter { !nearOnly || it.distanceKm == null || it.distanceKm <= Suggest.MAX_KM }
        .sortedWith(Suggest.order)
        .toList()
    val counts = dishes.groupingBy { it.placeId }.eachCount()
    val savedPlaces = places
        .filter { q.isEmpty() || it.name.lowercase().contains(q) }
        .filter { cat.isEmpty() || dishes.any { d -> d.placeId == it.id && cat in d.categories } }
        .sortedBy { p -> loc?.let { Geo.distanceKm(it, LatLng(p.lat, p.lng)) } ?: 0.0 }
    val googleOnly = google.filter { g -> vm.savedFor(g) == null }

    if (tab == "places") {
        RestaurantsScreen(vm, nav, initialCat = cat) { tab = "dishes" }
        return
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ModeSwitch(dishes = true, onDishes = {}, onPlaces = { tab = "places" })
            OutlinedTextField(
                query, { query = it },
                placeholder = { Text("Search dishes") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { if (tab == "places") vm.searchGoogle(query) }),
                modifier = Modifier.fillMaxWidth(),
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Chip("Near me", nearOnly, onClick = { nearOnly = !nearOnly }) }
                item { Chip("Healthy", healthy, onClick = { healthy = !healthy }, kind = app.godishcover.ui.ChipKind.Try) }
                items(Categories.ALL) { c -> Chip(catLabel(c), c == cat, onClick = { cat = if (cat == c) "" else c }) }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Categories.TAGS - Categories.HEALTHY) { t ->
                    Chip(catLabel(t), tag == t, onClick = { tag = if (tag == t) "" else t; tab = "dishes" })
                }
            }
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (tab == "dishes") {
                if (dishResults.isEmpty()) {
                    item {
                        EmptyState(
                            "No ${if (cat.isEmpty()) "" else catLabel(cat).lowercase() + " "}dishes near you yet",
                            "Menus come from the people who use the app. Snap a menu at a place you love and it shows up here for everyone.",
                            "Add a menu",
                        ) { nav.navigate(Routes.add()) }
                    }
                }
                items(dishResults, key = { it.dish.id }) { v -> DishRow(v) { nav.navigate(Routes.dish(v.dish.id)) } }
            } else {
                items(savedPlaces, key = { it.id }) { p ->
                    PlaceRow(p, loc, "${counts[p.id] ?: 0} dishes") { nav.navigate(Routes.place(p.id)) }
                }
                if (googleOnly.isNotEmpty()) {
                    item { Spacer(Modifier.height(4.dp)); GoogleAttribution() }
                    items(googleOnly, key = { "g-" + it.googlePlaceId }) { g ->
                        PlaceRow(g, loc, "No menu yet · add one") {
                            vm.pendingPlace.value = g
                            nav.navigate(Routes.add())
                        }
                    }
                } else if (!vm.placesSource.available) {
                    item { Note("Google place search turns on once the Maps key is added.") }
                }
                if (savedPlaces.isEmpty() && googleOnly.isEmpty()) {
                    item {
                        EmptyState("No restaurants found", "Try another name, or add the place yourself.", "Add a menu") {
                            nav.navigate(Routes.add())
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
fun PlaceRow(p: Place, loc: LatLng?, sub: String, onClick: () -> Unit) {
    Card(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Thumb(p.name, 44)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.name, style = MaterialTheme.typography.titleMedium)
                val dist = loc?.let { Geo.miles(Geo.distanceKm(it, LatLng(p.lat, p.lng))) }
                Text(listOfNotNull(dist, sub).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Dc.Muted)
            }
        }
    }
}

/** The Dishes / Restaurants switch at the top of Search. */
@Composable
fun ModeSwitch(dishes: Boolean, onDishes: () -> Unit, onPlaces: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip("Dishes", dishes, onClick = onDishes)
        Chip("Restaurants", !dishes, onClick = onPlaces)
    }
}
