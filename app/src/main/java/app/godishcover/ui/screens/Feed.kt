package app.godishcover.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.godishcover.data.DishView
import app.godishcover.data.FeedItem
import app.godishcover.data.FeedType
import app.godishcover.data.Geo
import app.godishcover.data.LatLng
import app.godishcover.data.NewsItem
import app.godishcover.data.Place
import app.godishcover.data.average
import app.godishcover.ui.AppViewModel
import app.godishcover.ui.Card
import app.godishcover.ui.Chip
import app.godishcover.ui.Dc
import app.godishcover.ui.DishRow
import app.godishcover.ui.EmptyState
import app.godishcover.ui.GoogleAttribution
import app.godishcover.ui.Note
import app.godishcover.ui.PrimaryButton
import app.godishcover.ui.Routes
import app.godishcover.ui.SecondaryButton
import app.godishcover.ui.SectionTitle
import app.godishcover.ui.StarPicker
import app.godishcover.ui.StarsText
import app.godishcover.ui.Tag
import app.godishcover.ui.TagKind
import app.godishcover.ui.Thumb
import app.godishcover.ui.TopBar
import app.godishcover.ui.timeAgo
import coil.compose.AsyncImage

fun shareText(ctx: android.content.Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    runCatching { ctx.startActivity(Intent.createChooser(send, "Share")) }
}

fun openLink(ctx: android.content.Context, url: String) {
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

private const val SHARE_TAIL = "\n\nFound on Dishcover · godishcover.com"

fun shareLine(item: FeedItem): String = when (item.type) {
    FeedType.MENU -> "${item.placeName} has ${item.dishCount} new ${if (item.dishCount == 1) "dish" else "dishes"} on Dishcover: ${item.dishNames.joinToString(", ")}"
    FeedType.PLACE -> "${item.placeName} is new on Dishcover."
    FeedType.REVIEW -> "${item.userName} gave ${item.dishName.ifBlank { item.placeName }}${if (item.dishName.isNotBlank()) " at ${item.placeName}" else ""} ${item.stars}/5. ${item.text}"
    else -> "${item.userName} at ${item.placeName}${if (item.dishName.isNotBlank()) ": ${item.dishName}" else ""}. ${item.text}"
} + SHARE_TAIL

private val FILTERS = listOf(
    "all" to "All", FeedType.MENU to "New on menus", FeedType.PLACE to "New places", FeedType.REVIEW to "Reviews",
    FeedType.POST to "Posts", "trending" to "Trending", "try" to "To try", "news" to "News",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(vm: AppViewModel, nav: NavHostController) {
    val feed by vm.feed.collectAsState()
    val settings by vm.settings.collectAsState()
    val loc by vm.location.collectAsState()
    val dishes by vm.dishes.collectAsState()
    val ratings by vm.myRatings.collectAsState()
    val profile by vm.profile.collectAsState()
    val news by vm.news.collectAsState()
    val hasMore by vm.feedHasMore.collectAsState()
    val loading by vm.feedLoading.collectAsState()
    val refreshing by vm.refreshing.collectAsState()
    val user by vm.user.collectAsState()
    var filter by rememberSaveable { mutableStateOf("all") }
    val listState = rememberLazyListState()

    val items = remember(feed, loc, settings.radiusMi, settings.feedTypes, filter) {
        val near = vm.feedNearby()
        when (filter) {
            "all" -> near.filter { it.type in settings.feedTypes }
            else -> near.filter { it.type == filter }
        }
    }
    val trending = remember(dishes, ratings, loc, settings.radiusMi) { vm.trending() }
    val toTry = remember(dishes, ratings, loc, profile, settings.radiusMi) { vm.toTry() }
    val isTimeline = filter == "all" || filter in FeedType.ALL

    LaunchedEffect(feed) { vm.markFeedSeen() }

    // Keep loading older entries as the list nears its end: the feed goes back as far as there is history.
    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 4
        }
    }
    LaunchedEffect(nearEnd, isTimeline, hasMore, loading) {
        if (nearEnd && isTimeline && hasMore && !loading && feed.isNotEmpty()) vm.loadFeed(more = true)
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopBar("What's new")
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(FILTERS.filter { it.first != "news" || settings.showNews }) { (key, label) ->
                    Chip(label, filter == key, onClick = { filter = key })
                }
            }
            Note(
                "Within ${if (settings.useKm) "${(settings.radiusKm).toInt()} km" else "${settings.radiusMi} mi"} of you · change in Settings",
                Modifier.padding(horizontal = 20.dp, vertical = 8.dp).clickable { nav.navigate(Routes.SETTINGS) },
            )
            PullToRefreshBox(isRefreshing = refreshing, onRefresh = { vm.pullRefresh() }, modifier = Modifier.weight(1f)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    when {
                        filter == "trending" -> {
                            if (trending.isEmpty()) item { EmptyState("Nothing trending yet", "Dishes show up here when people near you rate them.") }
                            items(trending, key = { "t" + it.dish.id }) { v -> DishRow(v) { nav.navigate(Routes.dish(v.dish.id)) } }
                        }
                        filter == "try" -> {
                            if (toTry.isEmpty()) item { EmptyState("Nothing to suggest yet", "Add dishes to your want-to-try list in the taste interview, and well-rated dishes near you show up here.") }
                            items(toTry.take(40), key = { "y" + it.dish.id }) { v -> DishRow(v) { nav.navigate(Routes.dish(v.dish.id)) } }
                        }
                        filter == "news" -> {
                            if (news.isEmpty()) item { EmptyState("No news right now", "Pull down to try again.") }
                            items(news, key = { "n" + it.link }) { n -> NewsRow(n) }
                            item { Note("Headlines link to the publisher's own site.") }
                        }
                        else -> {
                            if (filter == "all" && trending.isNotEmpty()) {
                                item(key = "trend-strip") { TrendingStrip(trending.take(8), nav) }
                            }
                            if (items.isEmpty() && !loading) {
                                item {
                                    EmptyState(
                                        "Nothing new nearby yet",
                                        "When someone adds a menu, a place, a review or a post near you, it shows up here and stays.",
                                        "Share where you ate",
                                    ) { nav.navigate(Routes.post()) }
                                }
                            }
                            val showNews = filter == "all" && settings.showNews && news.isNotEmpty()
                            items.forEachIndexed { i, entry ->
                                item(key = entry.id) {
                                    FeedCard(
                                        entry, vm, nav, loc,
                                        liked = entry.id in profile?.likedFeed.orEmpty(),
                                        mine = entry.userId == user?.id,
                                    )
                                }
                                // A headline every six entries keeps the feed varied without burying it.
                                if (showNews && i % 6 == 5) {
                                    val n = news.getOrNull(i / 6)
                                    if (n != null) item(key = "news-" + n.link) { NewsRow(n) }
                                }
                            }
                            if (loading) item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), color = Dc.Wine, strokeWidth = 2.dp) } }
                            else if (!hasMore && items.isNotEmpty()) item { Note("That's everything so far.", Modifier.padding(vertical = 8.dp)) }
                        }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { nav.navigate(Routes.post()) },
            containerColor = Dc.Wine, contentColor = Dc.OnAccent,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        ) { Text("Share a bite", style = MaterialTheme.typography.titleMedium) }
    }
}

@Composable
private fun TrendingStrip(list: List<DishView>, nav: NavHostController) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Trending near you", style = MaterialTheme.typography.titleMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(list, key = { it.dish.id }) { v ->
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
}

@Composable
private fun NewsRow(n: NewsItem) {
    val ctx = LocalContext.current
    Card(onClick = { openLink(ctx, n.link) }) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Tag("News")
                Note(n.source + if (n.publishedAt > 0) " · " + timeAgo(n.publishedAt) else "")
            }
            Text(n.title, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (n.summary.isNotBlank()) Text(n.summary, style = MaterialTheme.typography.bodySmall, color = Dc.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun FeedCard(item: FeedItem, vm: AppViewModel, nav: NavHostController, loc: LatLng?, liked: Boolean, mine: Boolean) {
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var reported by remember { mutableStateOf(false) }
    var showComments by remember { mutableStateOf(false) }
    if (showComments) CommentsSheet(item, vm, mine) { showComments = false }
    val open = {
        if (item.dishId.isNotBlank() && item.type == FeedType.REVIEW) nav.navigate(Routes.dish(item.dishId))
        else if (item.placeId.isNotBlank()) nav.navigate(Routes.place(item.placeId))
    }
    Card(onClick = open) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val bot = item.userId == "dishcover-menu-check"
                if (bot) {
                    androidx.compose.foundation.Image(
                        androidx.compose.ui.res.painterResource(app.godishcover.R.drawable.plate), contentDescription = null,
                        modifier = Modifier.size(36.dp).clip(androidx.compose.foundation.shape.CircleShape),
                    )
                } else Thumb(item.userName.ifBlank { "D" }, 36, round = true)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (bot) (if (item.type == FeedType.PLACE) "New on Dishcover" else "New on the menu") else when (item.type) {
                            FeedType.MENU -> "${item.userName.ifBlank { "Someone" }} added to a menu"
                            FeedType.PLACE -> "${item.userName.ifBlank { "Someone" }} added a new place"
                            FeedType.REVIEW -> "${item.userName.ifBlank { "Someone" }} reviewed"
                            else -> "${item.userName.ifBlank { "Someone" }} was here"
                        },
                        style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    val dist = if (loc != null && (item.lat != 0.0 || item.lng != 0.0)) Geo.miles(Geo.distanceKm(loc, LatLng(item.lat, item.lng))) else null
                    Note(listOfNotNull(timeAgo(item.createdAt), dist).joinToString(" · "))
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = Dc.Muted) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (mine) {
                            DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; vm.deleteFeedItem(item) })
                        } else {
                            DropdownMenuItem(
                                text = { Text(if (reported) "Reported" else "Report") }, enabled = !reported,
                                onClick = { menu = false; vm.reportFeedItem(item) { reported = true } },
                            )
                            DropdownMenuItem(
                                text = { Text("Block ${item.userName.substringBefore(' ').ifBlank { "user" }}") },
                                onClick = { menu = false; vm.block(item.userId) { vm.loadFeed(more = false) } },
                            )
                        }
                    }
                }
            }
            Text(item.placeName, style = MaterialTheme.typography.titleMedium, color = Dc.Wine)
            when (item.type) {
                FeedType.MENU -> {
                    Text(
                        "${item.dishCount} new ${if (item.dishCount == 1) "dish" else "dishes"}: " + item.dishNames.joinToString(", ") +
                            if (item.dishCount > item.dishNames.size) " and more" else "",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                FeedType.PLACE -> Text("New on Dishcover. Be the first to add its menu.", style = MaterialTheme.typography.bodyMedium)
                else -> {
                    if (item.dishName.isNotBlank()) Text(item.dishName, style = MaterialTheme.typography.titleSmall)
                    if (item.stars > 0) StarsText(item.stars)
                    if (item.text.isNotBlank()) Text(item.text, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (item.photoUrl.isNotBlank()) {
                AsyncImage(
                    model = item.photoUrl, contentDescription = "Photo from ${item.userName}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(12.dp)).background(Dc.Thumb),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val scale by animateFloatAsState(
                    if (liked && !Dc.reduceMotion) 1.15f else 1f,
                    spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "like",
                )
                val tint by animateColorAsState(if (liked) Dc.Wine else Dc.Muted, label = "likeTint")
                IconButton(onClick = { vm.toggleLike(item) }) {
                    Icon(
                        if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (liked) "Unlike" else "Like", tint = tint, modifier = Modifier.scale(scale),
                    )
                }
                Note(if (item.likeCount > 0) "${item.likeCount}" else "")
                IconButton(onClick = { showComments = true }) {
                    Icon(ChatBubble, contentDescription = "Comments", tint = Dc.Muted)
                }
                Note(if (item.commentCount > 0) "${item.commentCount}" else "")
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { shareText(ctx, shareLine(item)) }) {
                    Icon(Icons.Filled.Share, contentDescription = "Share", tint = Dc.Muted)
                }
            }
        }
    }
}

/** "Share a bite": where you went, what you ate, a rating and a photo. */
@Composable
fun PostScreen(vm: AppViewModel, nav: NavHostController, placeId: String) {
    val places by vm.places.collectAsState()
    val google by vm.googleResults.collectAsState()
    val loc by vm.location.collectAsState()
    val busy by vm.busy.collectAsState()
    var place by remember { mutableStateOf<Place?>(null) }
    var query by remember { mutableStateOf("") }
    var dish by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    var stars by remember { mutableIntStateOf(0) }
    var photo by remember { mutableStateOf<Uri?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { photo = it }

    LaunchedEffect(placeId, places) {
        if (place == null && placeId.isNotEmpty()) place = places.firstOrNull { it.id == placeId }
    }
    LaunchedEffect(Unit) { if (google.isEmpty()) vm.searchGoogle("") }

    Column(Modifier.fillMaxSize()) {
        TopBar("Share a bite", onBack = { nav.popBackStack() })
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val p = place
            if (p == null) {
                SectionTitle("Where did you go?")
                OutlinedTextField(
                    query, { query = it; if (it.length >= 3) vm.searchGoogle(it) },
                    placeholder = { Text("Search places") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                val q = query.trim().lowercase()
                places.filter { q.isEmpty() || it.name.lowercase().contains(q) }.take(8).forEach { s ->
                    PlaceRow(s, loc, "In Dishcover") { place = s }
                }
                val gOnly = google.filter { vm.savedFor(it) == null }.take(8)
                if (gOnly.isNotEmpty()) {
                    GoogleAttribution()
                    gOnly.forEach { g -> PlaceRow(g, loc, "Place from Google") { place = g } }
                }
                if (query.isNotBlank()) {
                    SecondaryButton("Use \"${query.trim()}\" at my current location", onClick = {
                        place = vm.placeAtMyLocation(query.trim())
                    }, modifier = Modifier.fillMaxWidth())
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(p.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    if (placeId.isEmpty()) androidx.compose.material3.TextButton(onClick = { place = null }) { Text("Change", color = Dc.Wine) }
                }
                OutlinedTextField(dish, { dish = it.take(80) }, label = { Text("What did you eat? (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("How was it?", style = MaterialTheme.typography.titleMedium)
                StarPicker(stars, { stars = if (stars == it) 0 else it })
                OutlinedTextField(
                    text, { text = it.take(500) }, label = { Text("Say something") },
                    supportingText = { Text("${text.length}/500") }, minLines = 3, modifier = Modifier.fillMaxWidth(),
                )
                val ph = photo
                if (ph != null) {
                    AsyncImage(
                        model = ph, contentDescription = "Your photo", contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(12.dp)),
                    )
                }
                SecondaryButton(if (ph == null) "Add a photo" else "Change photo", onClick = {
                    pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }, modifier = Modifier.fillMaxWidth())
                if (vm.repo.isDemo && ph != null) Note("Demo mode: the photo isn't uploaded.")
                Note("Posts are public. Food and places only, please, and no photos of people without their OK.")
                PrimaryButton("Post", busy = busy, enabled = text.isNotBlank() || dish.isNotBlank() || stars > 0 || ph != null, onClick = {
                    vm.post(p, dish, text, stars, ph) { nav.popBackStack() }
                })
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Comments under a post: read them, add one, and remove your own (or any on your post). */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun CommentsSheet(item: FeedItem, vm: AppViewModel, myPost: Boolean, onDismiss: () -> Unit) {
    var list by remember { mutableStateOf<List<app.godishcover.data.Comment>?>(null) }
    var draft by remember { mutableStateOf("") }
    val me = vm.myUserId()
    LaunchedEffect(item.id) { vm.loadComments(item) { list = it } }
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Comments", style = MaterialTheme.typography.titleLarge)
            val l = list
            when {
                l == null -> Note("Loading…")
                l.isEmpty() -> Note("No comments yet. Say something nice.")
                else -> androidx.compose.foundation.lazy.LazyColumn(
                    Modifier.fillMaxWidth().heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(l, key = { it.id }) { c ->
                        Row(verticalAlignment = Alignment.Top) {
                            Thumb(c.userName.ifBlank { "D" }, 30, round = true)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(c.userName.ifBlank { "Someone" }, style = MaterialTheme.typography.titleSmall)
                                Text(c.text, style = MaterialTheme.typography.bodyMedium)
                                Note(timeAgo(c.createdAt))
                            }
                            if (c.userId == me || myPost) {
                                androidx.compose.material3.TextButton(onClick = {
                                    vm.deleteComment(item, c) { list = list.orEmpty().filter { it.id != c.id } }
                                }) { Text("Delete", color = Dc.Muted) }
                            }
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.OutlinedTextField(
                    draft, { draft = it.take(300) },
                    placeholder = { Text("Add a comment") },
                    modifier = Modifier.weight(1f),
                    maxLines = 4,
                )
                Spacer(Modifier.width(8.dp))
                androidx.compose.material3.TextButton(
                    enabled = draft.isNotBlank(),
                    onClick = {
                        val text = draft
                        draft = ""
                        vm.addComment(item, text) { c -> list = list.orEmpty() + c }
                    },
                ) { Text("Post", color = Dc.Wine) }
            }
        }
    }
}

/** Outlined speech bubble (the core icon set has none). */
private val ChatBubble: androidx.compose.ui.graphics.vector.ImageVector by lazy {
    androidx.compose.ui.graphics.vector.ImageVector.Builder("Chat", 24.dp, 24.dp, 24f, 24f).apply {
        addPath(
            androidx.compose.ui.graphics.vector.PathBuilder().apply {
                moveTo(20f, 2f); horizontalLineTo(4f); curveTo(2.9f, 2f, 2f, 2.9f, 2f, 4f); verticalLineTo(22f)
                lineTo(6f, 18f); horizontalLineTo(20f); curveTo(21.1f, 18f, 22f, 17.1f, 22f, 16f); verticalLineTo(4f)
                curveTo(22f, 2.9f, 21.1f, 2f, 20f, 2f); close()
                moveTo(20f, 16f); horizontalLineTo(5.17f); lineTo(4f, 17.17f); verticalLineTo(4f); horizontalLineTo(20f); close()
            }.nodes,
            fill = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color.Black),
        )
    }.build()
}
