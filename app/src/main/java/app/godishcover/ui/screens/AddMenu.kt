package app.godishcover.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.navigation.NavHostController
import app.godishcover.data.Categories
import app.godishcover.data.DraftDish
import app.godishcover.data.Place
import app.godishcover.ui.AppViewModel
import app.godishcover.ui.Chip
import app.godishcover.ui.ChipFlow
import app.godishcover.ui.Dc
import app.godishcover.ui.GoogleAttribution
import app.godishcover.ui.Note
import app.godishcover.ui.PrimaryButton
import app.godishcover.ui.Routes
import app.godishcover.ui.SecondaryButton
import app.godishcover.ui.SectionTitle
import app.godishcover.ui.TopBar
import app.godishcover.ui.catLabel
import kotlinx.coroutines.launch
import java.io.File

private enum class Phase { Pick, Way, Check, Done }
private enum class Way(val title: String, val sub: String) {
    Photo("Snap the menu", "Take a photo of the printed menu. AI reads it for you."),
    Link("Paste a link", "A web page with the menu on it."),
    Type("Type dishes", "Just add the few you know."),
}

@Composable
fun AddMenuScreen(vm: AppViewModel, nav: NavHostController, placeId: String) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val places by vm.places.collectAsState()
    val google by vm.googleResults.collectAsState()
    val pending by vm.pendingPlace.collectAsState()
    val loc by vm.location.collectAsState()

    var place by remember { mutableStateOf<Place?>(null) }
    var label by remember { mutableStateOf("") }
    var phase by remember { mutableStateOf(Phase.Pick) }
    var way by remember { mutableStateOf(Way.Photo) }
    var link by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var savedCount by remember { mutableStateOf(0) }
    val drafts = remember { mutableStateListOf<DraftDish>() }

    LaunchedEffect(placeId, pending, places) {
        if (place == null) {
            val chosen = if (placeId.isNotEmpty()) places.firstOrNull { it.id == placeId } else pending
            if (chosen != null) {
                place = chosen
                label = chosen.name
                phase = Phase.Way
                vm.pendingPlace.value = null
            }
        }
    }
    LaunchedEffect(loc) { if (google.isEmpty()) vm.searchGoogle("") }

    fun gotDrafts(list: List<DraftDish>) {
        drafts.clear()
        drafts.addAll(list)
        if (drafts.isEmpty()) {
            vm.message.value = "We couldn't find dishes there. Try a photo, or type them."
        } else phase = Phase.Check
    }

    fun runRead(block: suspend () -> List<DraftDish>) {
        working = true
        scope.launch {
            try { gotDrafts(block()) } catch (e: Exception) {
                vm.message.value = e.message ?: "Couldn't read that menu"
            } finally { working = false }
        }
    }

    var photoUri by remember { mutableStateOf<Uri?>(null) }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = photoUri
        if (ok && u != null) runRead {
            try { vm.readMenuPhoto(u) } finally { File(ctx.cacheDir, "menu/menu.jpg").delete() }
        }
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u ->
        if (u != null) runRead { vm.readMenuPhoto(u) }
    }

    Column(Modifier.fillMaxSize()) {
        val back: (() -> Unit)? = if (phase == Phase.Pick || phase == Phase.Done) null else ({
            phase = if (phase == Phase.Check) Phase.Way else Phase.Pick
        })
        TopBar("Add a menu", onBack = back)
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (phase) {
                Phase.Pick -> {
                    Note("Help everyone find the good stuff. Nothing goes public until you check it.")
                    SectionTitle("Which place?")
                    val nearbySaved = places.take(10)
                    nearbySaved.forEach { p ->
                        PlaceRow(p, loc, "In Dishcover") { place = p; label = p.name; phase = Phase.Way }
                    }
                    val gOnly = google.filter { vm.savedFor(it) == null }
                    if (gOnly.isNotEmpty()) {
                        GoogleAttribution()
                        gOnly.forEach { g -> PlaceRow(g, loc, "Place from Google") { place = g; label = g.name; phase = Phase.Way } }
                    }
                    Note("Can't find it? Search for it in the Search tab, or add it here.")
                    SectionTitle("Not on Google?")
                    OutlinedTextField(newName, { newName = it.take(60) }, label = { Text("Place name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    SecondaryButton("Add it at my current location", onClick = {
                        if (newName.isNotBlank()) {
                            place = vm.placeAtMyLocation(newName.trim()); label = newName.trim(); phase = Phase.Way
                        }
                    }, modifier = Modifier.fillMaxWidth())
                }
                Phase.Way -> {
                    val p = place
                    if (p != null && p.id.isEmpty() && p.googlePlaceId != null) {
                        // Google's name is shown, and the user confirms the name Dishcover keeps.
                        OutlinedTextField(label, { label = it.take(60) }, label = { Text("Name in Dishcover") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Note("${p.address} · Place from Google")
                    } else {
                        Text(label, style = MaterialTheme.typography.headlineSmall)
                    }
                    SectionTitle("How do you want to add it?")
                    Way.entries.forEach { w ->
                        val on = w == way
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                .background(if (on) Dc.WineTint else Dc.White)
                                .border(1.5.dp, if (on) Dc.Wine else Dc.Line, RoundedCornerShape(16.dp))
                                .clickable { way = w }.padding(14.dp),
                        ) {
                            Text(w.title, style = MaterialTheme.typography.titleMedium)
                            Note(w.sub)
                        }
                    }
                    when (way) {
                        Way.Photo -> {
                            Note("The photo is deleted after we read it. Please don't snap receipts.")
                            PrimaryButton("Take photo", busy = working, onClick = {
                                val dir = File(ctx.cacheDir, "menu").apply { mkdirs() }
                                val f = File(dir, "menu.jpg")
                                val u = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
                                photoUri = u
                                takePhoto.launch(u)
                            })
                            SecondaryButton("Choose from gallery", onClick = {
                                pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }, modifier = Modifier.fillMaxWidth())
                            Note("Up to 10 menu reads a day")
                        }
                        Way.Link -> {
                            OutlinedTextField(
                                link, { link = it.trim() }, label = { Text("Menu link") }, singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth(),
                            )
                            Note("Works best with plain menu pages. If we can't read it, we'll ask for a photo.")
                            PrimaryButton("Read this link", busy = working, enabled = link.startsWith("https://"), onClick = {
                                runRead { vm.readMenuLink(link) }
                            })
                        }
                        Way.Type -> PrimaryButton("Start typing dishes", onClick = {
                            drafts.clear(); drafts.add(DraftDish("", categories = emptySet())); phase = Phase.Check
                        })
                    }
                }
                Phase.Check -> {
                    Text("Check the dishes", style = MaterialTheme.typography.headlineSmall)
                    Note("Fix anything we got wrong, pick categories, and skip what isn't served. Nothing is public until you save.")
                    drafts.forEachIndexed { i, d ->
                        DraftEditor(d, onChange = { drafts[i] = it })
                    }
                    TextButton(onClick = { drafts.add(DraftDish("")) }) { Text("+ Add another dish", color = Dc.Wine) }
                    val keep = drafts.count { it.keep && it.name.isNotBlank() }
                    PrimaryButton("Save $keep ${if (keep == 1) "dish" else "dishes"}", enabled = keep > 0, busy = working, onClick = {
                        val p = place ?: return@PrimaryButton
                        working = true
                        scope.launch {
                            try {
                                val saved = vm.savePlace(p.copy(name = label.trim().ifBlank { p.name }))
                                place = saved
                                savedCount = vm.saveDishes(saved, drafts.toList())
                                phase = Phase.Done
                            } catch (e: Exception) {
                                vm.message.value = e.message ?: "Couldn't save"
                            } finally { working = false }
                        }
                    })
                }
                Phase.Done -> {
                    Spacer(Modifier.height(24.dp))
                    Text("Menu saved", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "$savedCount ${if (savedCount == 1) "dish" else "dishes"} at ${place?.name.orEmpty()} " +
                            "${if (savedCount == 1) "is" else "are"} now searchable for everyone. You can edit the dishes you added any time.",
                        style = MaterialTheme.typography.bodyLarge, color = Dc.Muted,
                    )
                    PrimaryButton("See the menu", onClick = {
                        place?.let { nav.navigate(Routes.place(it.id)) { popUpTo(Routes.HOME) } }
                    })
                    SecondaryButton("Add another menu", onClick = {
                        place = null; label = ""; drafts.clear(); newName = ""; link = ""; phase = Phase.Pick
                    }, modifier = Modifier.fillMaxWidth())
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun DraftEditor(d: DraftDish, onChange: (DraftDish) -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(if (d.keep) Dc.White else Dc.TagBg)
            .border(1.dp, Dc.Line, RoundedCornerShape(16.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(d.name, { onChange(d.copy(name = it.take(80))) }, label = { Text("Dish") }, singleLine = true, modifier = Modifier.weight(2f))
            OutlinedTextField(d.price, { onChange(d.copy(price = it.take(20))) }, label = { Text("Price") }, singleLine = true, modifier = Modifier.weight(1f))
        }
        if (d.note.isNotBlank()) Note(d.note)
        ChipFlow {
            Categories.ALL.forEach { c ->
                val on = c in d.categories
                Chip(catLabel(c), on, onClick = { onChange(d.copy(categories = if (on) d.categories - c else d.categories + c)) })
            }
        }
        Note("Anything special about it?")
        ChipFlow {
            Categories.TAGS.forEach { t ->
                val on = t in d.tags
                Chip(catLabel(t), on, kind = app.godishcover.ui.ChipKind.Try, onClick = { onChange(d.copy(tags = if (on) d.tags - t else d.tags + t)) })
            }
        }
        TextButton(onClick = { onChange(d.copy(keep = !d.keep)) }) {
            Text(if (d.keep) "Skip this dish" else "Keep this dish", color = Dc.Muted)
        }
    }
}
