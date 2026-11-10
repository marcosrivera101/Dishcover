package app.godishcover.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.ui.platform.LocalContext
import app.godishcover.data.Place
import app.godishcover.ui.StarPicker
import app.godishcover.ui.StarsText
import app.godishcover.ui.TagKind
import app.godishcover.ui.timeAgo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.godishcover.data.Categories
import app.godishcover.data.Dish
import app.godishcover.data.Geo
import app.godishcover.data.LatLng
import app.godishcover.data.Rating
import app.godishcover.data.average
import app.godishcover.ui.AppViewModel
import app.godishcover.ui.Chip
import app.godishcover.ui.ChipFlow
import app.godishcover.ui.Dc
import app.godishcover.ui.DishRow
import app.godishcover.ui.GoogleAttribution
import app.godishcover.ui.Note
import app.godishcover.ui.PrimaryButton
import app.godishcover.ui.Routes
import app.godishcover.ui.SecondaryButton
import app.godishcover.ui.SectionTitle
import app.godishcover.ui.Tag
import app.godishcover.ui.TopBar
import app.godishcover.ui.catLabel
import java.text.DateFormat
import java.util.Date

private fun date(ms: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(ms))

@Composable
private fun ShareToFeed(checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onChange(!checked) }) {
        Checkbox(checked, onChange, colors = CheckboxDefaults.colors(checkedColor = Dc.Wine))
        Text("Also share to the feed", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ReviewCard(r: Rating, mine: Boolean, reported: Boolean, onReport: () -> Unit, onBlock: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Dc.White).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row {
            Text(r.userName.ifBlank { "Someone" }, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            StarsText(r.stars)
        }
        Note(timeAgo(r.updatedAt))
        if (r.text.isNotBlank()) Text(r.text, style = MaterialTheme.typography.bodyMedium)
        if (!mine) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(enabled = !reported, onClick = onReport) { Text(if (reported) "Reported" else "Report", color = Dc.Muted) }
                TextButton(onClick = onBlock) { Text("Block ${r.userName.substringBefore(' ').ifBlank { "user" }}", color = Dc.Muted) }
            }
        }
    }
}

@Composable
fun PlaceScreen(vm: AppViewModel, nav: NavHostController, placeId: String) {
    val ctx = LocalContext.current
    val places by vm.places.collectAsState()
    val dishes by vm.dishes.collectAsState()
    val ratings by vm.myRatings.collectAsState()
    val placeRatings by vm.myPlaceRatings.collectAsState()
    val profile by vm.profile.collectAsState()
    val user by vm.user.collectAsState()
    val busy by vm.busy.collectAsState()
    val loc by vm.location.collectAsState()
    @Suppress("UNUSED_VARIABLE") val deps = listOf(ratings)
    LaunchedEffect(placeId) { vm.loadPlaceDishes(placeId) }
    val place = places.firstOrNull { it.id == placeId }
    val menu = dishes.filter { it.placeId == placeId }
    var cat by remember { mutableStateOf("") }
    val cats = menu.flatMap { it.categories }.distinct().sortedBy { Categories.ALL.indexOf(it) }
    val saved = placeId in profile?.savedPlaces.orEmpty()
    val mine = placeRatings[placeId]
    var stars by remember(placeId, mine) { mutableIntStateOf(mine?.stars ?: 0) }
    var text by remember(placeId, mine) { mutableStateOf(mine?.text.orEmpty()) }
    var share by remember { mutableStateOf(false) }
    var savedRating by remember(placeId) { mutableStateOf(false) }
    val reviews = remember(placeId) { mutableStateListOf<Rating>() }
    val reported = remember(placeId) { mutableStateListOf<String>() }
    var reloadKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(placeId, reloadKey) {
        val list = runCatching { vm.placeReviews(placeId) }.getOrDefault(emptyList())
        reviews.clear(); reviews.addAll(list)
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(place?.name ?: "Restaurant", onBack = { nav.popBackStack() }) {
            if (place != null) {
                IconButton(onClick = { vm.toggleSaved(placeId) }) {
                    Icon(
                        if (saved) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (saved) "Remove from saved" else "Save place",
                        tint = if (saved) Dc.Wine else Dc.Muted,
                    )
                }
                IconButton(onClick = {
                    val best = menu.sortedByDescending { it.average }.take(3).joinToString(", ") { it.name }
                    shareText(ctx, "${place.name}" + (if (best.isNotBlank()) ": try $best." else ".") + "\n\nFound on Dishcover · godishcover.com")
                }) { Icon(Icons.Filled.Share, contentDescription = "Share", tint = Dc.Muted) }
            }
        }
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (place != null) {
                val dist = if (loc != null && (place.lat != 0.0 || place.lng != 0.0)) Geo.miles(Geo.distanceKm(loc!!, LatLng(place.lat, place.lng))) else null
                Note(listOfNotNull(place.address.ifBlank { null }, dist).joinToString(" · "))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (place.ratingCount > 0) Tag("Everyone " + String.format("%.1f", place.average) + " · ${place.ratingCount} ${if (place.ratingCount == 1L) "review" else "reviews"}")
                    if (saved) Tag("Saved", TagKind.You)
                }
                if (place.googlePlaceId != null) GoogleAttribution()
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("Add dishes", onClick = { nav.navigate(Routes.add(placeId)) }, modifier = Modifier.weight(1f))
                SecondaryButton("Share a bite", onClick = { nav.navigate(Routes.post(placeId)) }, modifier = Modifier.weight(1f))
            }
            val last = menu.maxOfOrNull { it.confirmedAt } ?: 0L
            if (last > 0) Note("Menu last confirmed ${date(last)}")
            if (cats.size > 1) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { Chip("All", cat.isEmpty(), onClick = { cat = "" }) }
                    items(cats) { c -> Chip(catLabel(c), c == cat, onClick = { cat = c }) }
                }
            }
            if (menu.isEmpty()) {
                Note("No dishes here yet. Add the ones you know and they show up for everyone.")
            }
            menu.filter { cat.isEmpty() || cat in it.categories }
                .map { vm.view(it) }
                .sortedByDescending { it.dish.average }
                .forEach { v -> DishRow(v, showPlace = false) { nav.navigate(Routes.dish(v.dish.id)) } }
            Note("Menu added by people who eat here. Something wrong or no longer served? Open the dish and flag it.")

            if (place != null) {
                SectionTitle("Rate this place")
                Note("The whole visit: food, service, the room.")
                StarPicker(stars, { stars = it; savedRating = false })
                OutlinedTextField(
                    text, { text = it.take(500); savedRating = false },
                    label = { Text("Review (optional)") }, supportingText = { Text("${text.length}/500") },
                    minLines = 2, modifier = Modifier.fillMaxWidth(),
                )
                ShareToFeed(share) { share = it }
                PrimaryButton(
                    if (savedRating) "Saved" else if (mine != null) "Update review" else "Save review",
                    onClick = { vm.ratePlace(place, stars, text, share) { savedRating = true; reloadKey++ } },
                    enabled = stars > 0 && !savedRating, busy = busy,
                )
            }
            if (reviews.isNotEmpty()) SectionTitle("Reviews of this place")
            reviews.forEach { r ->
                ReviewCard(
                    r, mine = r.userId == user?.id, reported = r.userId in reported,
                    onReport = { vm.report(r) { reported.add(r.userId) } },
                    onBlock = { vm.block(r.userId) { reviews.removeAll { it.userId == r.userId } } },
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

private val FLAG_REASONS = listOf(
    "Wrong name, price or details",
    "Not served anymore",
    "Duplicate of another dish",
    "Offensive or spam",
)

@Composable
fun DishScreen(vm: AppViewModel, nav: NavHostController, dishId: String) {
    val dishes by vm.dishes.collectAsState()
    val ratings by vm.myRatings.collectAsState()
    val user by vm.user.collectAsState()
    val busy by vm.busy.collectAsState()
    val dish = dishes.firstOrNull { it.id == dishId }
    if (dish == null) {
        Column(Modifier.fillMaxSize()) {
            TopBar("Dish", onBack = { nav.popBackStack() })
            Note("  This dish isn't available anymore.")
        }
        return
    }
    val mine = ratings[dish.id]
    var stars by remember(dish.id, mine) { mutableIntStateOf(mine?.stars ?: 0) }
    var text by remember(dish.id, mine) { mutableStateOf(mine?.text.orEmpty()) }
    var saved by remember(dish.id) { mutableStateOf(false) }
    var share by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    var flagOpen by remember { mutableStateOf(false) }
    var flagged by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    val reviews = remember(dish.id) { mutableStateListOf<Rating>() }
    val reported = remember(dish.id) { mutableStateListOf<String>() }
    var reloadKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(dish.id, reloadKey) {
        val list = runCatching { vm.reviews(dish.id) }.getOrDefault(emptyList())
        reviews.clear(); reviews.addAll(list)
    }
    val v = vm.view(dish)
    val isCreator = user?.id == dish.createdBy

    if (editing) {
        EditDishDialog(dish, onDismiss = { editing = false }, onSave = { vm.updateDish(it) { editing = false } }, onDelete = {
            vm.deleteDish(dish) { editing = false; nav.popBackStack() }
        })
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("Dish", onBack = { nav.popBackStack() }) {
            IconButton(onClick = {
                shareText(
                    ctx,
                    "${dish.name} at ${dish.placeName}" +
                        (if (dish.ratingCount > 0) " · ★ " + String.format("%.1f", dish.average) else "") +
                        "\n\nFound on Dishcover · godishcover.com",
                )
            }) { Icon(Icons.Filled.Share, contentDescription = "Share", tint = Dc.Muted) }
            if (isCreator) TextButton(onClick = { editing = true }) { Text("Edit", color = Dc.Wine) }
        }
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(dish.name, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                Text(dish.price, style = MaterialTheme.typography.titleLarge, color = Dc.Muted)
            }
            Text(
                dish.placeName, style = MaterialTheme.typography.titleMedium, color = Dc.Wine,
                modifier = Modifier.clickable { nav.navigate(Routes.place(dish.placeId)) },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                dish.categories.forEach { Tag(catLabel(it)) }
                dish.tags.forEach { Tag(catLabel(it), TagKind.Try) }
                if (dish.ratingCount > 0) Tag("Everyone " + String.format("%.1f", dish.average) + " · ${dish.ratingCount} ratings")
            }
            if (dish.description.isNotBlank()) Text(dish.description, style = MaterialTheme.typography.bodyLarge)
            Note(
                (if (dish.allergens.isNotEmpty()) "Menu lists: " + dish.allergens.joinToString(", ") { it.lowercase() } + ". " else "") +
                    "Always check allergies with the restaurant.",
            )
            if (v.allergyHit) Tag("Lists one of your allergies", app.godishcover.ui.TagKind.Low)

            SectionTitle("Your rating")
            StarPicker(stars, { stars = it; saved = false })
            OutlinedTextField(
                text, { text = it.take(500); saved = false },
                label = { Text("Review (optional)") },
                supportingText = { Text("${text.length}/500") },
                minLines = 3, modifier = Modifier.fillMaxWidth(),
            )
            ShareToFeed(share) { share = it }
            PrimaryButton(
                if (saved) "Saved" else if (mine != null) "Update rating" else "Save rating",
                onClick = { vm.rate(dish, stars, text, share) { saved = true; reloadKey++ } },
                enabled = stars > 0 && !saved,
                busy = busy,
            )

            if (reviews.isNotEmpty()) SectionTitle("Reviews")
            reviews.forEach { r ->
                ReviewCard(
                    r, mine = r.userId == user?.id, reported = r.userId in reported,
                    onReport = { vm.report(r) { reported.add(r.userId) } },
                    onBlock = { vm.block(r.userId) { reviews.removeAll { it.userId == r.userId } } },
                )
            }

            if (dish.createdByName.isNotBlank() && dish.createdAt > 0) {
                Note("Added by ${dish.createdByName} on ${date(dish.createdAt)}. Only ${dish.createdByName.substringBefore(' ')} can edit this dish.")
            }
            if (!isCreator) {
                TextButton(enabled = !flagged, onClick = { flagOpen = true }) {
                    Text(if (flagged) "Thanks, we'll check it" else "Flag a problem with this dish", color = Dc.Wine)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (flagOpen) {
        AlertDialog(
            onDismissRequest = { flagOpen = false },
            title = { Text("What's wrong with this dish?") },
            text = {
                Column {
                    FLAG_REASONS.forEach { reason ->
                        Box(
                            Modifier.fillMaxWidth().clickable {
                                vm.flag(dish, reason) { flagged = true; flagOpen = false }
                            }.padding(vertical = 12.dp),
                        ) { Text(reason, style = MaterialTheme.typography.bodyLarge) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { flagOpen = false }) { Text("Cancel", color = Dc.Muted) } },
        )
    }
}

@Composable
private fun EditDishDialog(dish: Dish, onDismiss: () -> Unit, onSave: (Dish) -> Unit, onDelete: () -> Unit) {
    var name by remember { mutableStateOf(dish.name) }
    var price by remember { mutableStateOf(dish.price) }
    var desc by remember { mutableStateOf(dish.description) }
    var cats by remember { mutableStateOf(dish.categories.toSet()) }
    var tags by remember { mutableStateOf(dish.tags.toSet()) }
    var confirmDelete by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit dish") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it.take(80) }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(price, { price = it.take(20) }, label = { Text("Price") }, singleLine = true)
                OutlinedTextField(desc, { desc = it.take(200) }, label = { Text("Description") })
                ChipFlow {
                    Categories.ALL.forEach { c ->
                        Chip(catLabel(c), c in cats, onClick = { cats = if (c in cats) cats - c else cats + c })
                    }
                }
                ChipFlow {
                    Categories.TAGS.forEach { t ->
                        Chip(catLabel(t), t in tags, kind = app.godishcover.ui.ChipKind.Try, onClick = { tags = if (t in tags) tags - t else tags + t })
                    }
                }
                TextButton(onClick = { if (confirmDelete) onDelete() else confirmDelete = true }) {
                    Text(if (confirmDelete) "Tap again to remove this dish" else "Remove dish", color = Dc.Wine)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                onSave(dish.copy(name = name.trim(), price = price.trim(), description = desc.trim(), categories = cats.toList().ifEmpty { listOf("other") }, tags = tags.toList()))
            }) { Text("Save", color = Dc.Wine) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Dc.Muted) } },
    )
}
