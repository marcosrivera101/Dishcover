package app.godishcover.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.godishcover.data.Categories
import app.godishcover.data.Geo
import app.godishcover.data.LatLng
import app.godishcover.data.Place
import app.godishcover.ui.AppViewModel
import app.godishcover.ui.Card
import app.godishcover.ui.Chip
import app.godishcover.ui.Dc
import app.godishcover.ui.EmptyState
import app.godishcover.ui.Routes
import app.godishcover.ui.Thumb
import app.godishcover.ui.catLabel

private enum class PlaceSort(val label: String) { Distance("Closest"), Rating("Top rated"), Menu("Biggest menu"), Name("A to Z") }

/** Every restaurant near you, with food-type filters, sorting, favorites and search. */
@Composable
fun RestaurantsScreen(vm: AppViewModel, nav: NavHostController) {
    val places by vm.places.collectAsState()
    val dishes by vm.dishes.collectAsState()
    val loc by vm.location.collectAsState()
    val profile by vm.profile.collectAsState()
    var cat by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(PlaceSort.Distance.name) }
    var favOnly by rememberSaveable { mutableStateOf(false) }
    var menuOnly by rememberSaveable { mutableStateOf(false) }
    val saved = profile?.savedPlaces.orEmpty().toSet()

    // What each place serves: its dish categories plus its cuisine, and its dish names for search.
    val byPlace = remember(dishes) { dishes.groupBy { it.placeId } }
    fun dist(p: Place) = loc?.let { Geo.distanceKm(it, LatLng(p.lat, p.lng)) }
    fun rating(p: Place) = if (p.ratingCount > 0) p.ratingSum.toDouble() / p.ratingCount else 0.0
    fun serves(p: Place, c: String): Boolean {
        val ds = byPlace[p.id].orEmpty()
        return ds.any { c in it.categories } || p.cuisine.lowercase().split(';', ',', ' ').any { it.trim() == c }
    }

    val shown = places.asSequence()
        .filter { !favOnly || it.id in saved }
        .filter { !menuOnly || byPlace[it.id].orEmpty().isNotEmpty() }
        .filter { cat.isEmpty() || serves(it, cat) }
        .toList()
        .let { list ->
            when (PlaceSort.valueOf(sort)) {
                PlaceSort.Distance -> list.sortedBy { dist(it) ?: Double.MAX_VALUE }
                PlaceSort.Rating -> list.sortedWith(compareByDescending<Place> { rating(it) }.thenByDescending { it.ratingCount }.thenBy { dist(it) ?: Double.MAX_VALUE })
                PlaceSort.Menu -> list.sortedWith(compareByDescending<Place> { byPlace[it.id]?.size ?: 0 }.thenBy { dist(it) ?: Double.MAX_VALUE })
                PlaceSort.Name -> list.sortedBy { it.name.lowercase() }
            }
        }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Restaurants near you", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = { nav.navigate(Routes.add()) }) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = Dc.Wine)
                    Spacer(Modifier.width(4.dp))
                    Text("Add menu", color = Dc.Wine)
                }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(PlaceSort.entries) { s -> Chip(s.label, sort == s.name, onClick = { sort = s.name }) }
                item { Chip("Favorites", favOnly, onClick = { favOnly = !favOnly }) }
                item { Chip("Has a menu", menuOnly, onClick = { menuOnly = !menuOnly }) }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Chip("All food", cat.isEmpty(), onClick = { cat = "" }) }
                items(Categories.ALL - "other") { c -> Chip(catLabel(c), c == cat, onClick = { cat = if (cat == c) "" else c }) }
            }
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { Text("${shown.size} restaurants", style = MaterialTheme.typography.bodySmall, color = Dc.Muted) }
            items(shown, key = { it.id }) { p ->
                RestaurantRow(
                    p, dist(p), rating(p), byPlace[p.id]?.size ?: 0, p.id in saved,
                    onSave = { vm.toggleSaved(p.id) },
                ) { nav.navigate(Routes.place(p.id)) }
            }
            if (shown.isEmpty()) {
                item {
                    if (favOnly && saved.isEmpty()) EmptyState("No favorites yet", "Tap the heart on any restaurant to keep it here.")
                    else EmptyState("No restaurants match", "Try another food type, or add the place yourself.", "Add a menu") { nav.navigate(Routes.add()) }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun RestaurantRow(p: Place, km: Double?, stars: Double, dishCount: Int, saved: Boolean, onSave: () -> Unit, onClick: () -> Unit) {
    Card(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Thumb(p.name, 44)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.name, style = MaterialTheme.typography.titleMedium)
                val food = p.cuisine.split(';', ',').map { it.trim() }.filter { it.isNotEmpty() }.take(2).joinToString(", ") { catLabel(it) }
                val bits = listOfNotNull(
                    km?.let { Geo.miles(it) },
                    if (p.ratingCount > 0) "★ %.1f (%d)".format(stars, p.ratingCount) else null,
                    if (dishCount > 0) "$dishCount dishes" else "No menu yet",
                    food.ifEmpty { null },
                )
                Text(bits.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Dc.Muted)
            }
            IconButton(onClick = onSave) {
                Icon(
                    if (saved) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = if (saved) "Remove from favorites" else "Add to favorites",
                    tint = if (saved) Dc.Wine else Dc.Muted,
                )
            }
        }
    }
}
