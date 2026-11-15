package app.godishcover.ui

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.godishcover.DishcoverApp
import app.godishcover.BuildConfig
import app.godishcover.data.Alerts
import app.godishcover.data.Categories
import app.godishcover.data.FeedItem
import app.godishcover.data.FeedType
import app.godishcover.data.Geo
import app.godishcover.data.News
import app.godishcover.data.NewsItem
import app.godishcover.data.Release
import app.godishcover.data.Settings
import app.godishcover.data.average
import app.godishcover.data.Dish
import app.godishcover.data.DishView
import app.godishcover.data.DraftDish
import app.godishcover.data.LatLng
import app.godishcover.data.Place
import app.godishcover.data.PlaceSuggestion
import app.godishcover.data.Profile
import app.godishcover.data.Suggest
import app.godishcover.data.Updater
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** Hartford, CT: the seed city, used until the phone shares its location. */
val HARTFORD = LatLng(41.7658, -72.6734)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val di = app as DishcoverApp
    val repo = di.repo
    val placesSource = di.places
    val prefs = di.prefs
    val settings = prefs.settings
    val feed = repo.feed
    val myPlaceRatings = repo.myPlaceRatings
    val news = MutableStateFlow<List<NewsItem>>(emptyList())
    val feedHasMore = MutableStateFlow(true)
    val feedLoading = MutableStateFlow(false)
    val refreshing = MutableStateFlow(false)
    /** A newer build found by the update check. */
    val update = MutableStateFlow<Release?>(null)
    val updateChecked = MutableStateFlow<String?>(null)

    init {
        Suggest.MAX_KM = settings.value.radiusKm
        Feel.haptics = settings.value.haptics
    }

    fun setSettings(change: (Settings) -> Settings) {
        val before = settings.value
        prefs.update(change)
        val now = settings.value
        Feel.haptics = now.haptics
        Suggest.MAX_KM = now.radiusKm
        if (before.radiusMi != now.radiusMi) refreshLocation()
        if (before.alertsNewMenus != now.alertsNewMenus || before.alertsNewPlaces != now.alertsNewPlaces ||
            before.alertsSaved != now.alertsSaved
        ) syncAlerts()
    }

    fun syncAlerts() {
        Alerts.sync(prefs, if (usingRealLocation.value) location.value else null, profile.value?.savedPlaces.orEmpty())
    }

    val user = repo.user
    val profile = repo.profile
    val stats = repo.stats
    val places = repo.places
    val dishes = repo.dishes
    val myRatings = repo.myRatings

    val location = MutableStateFlow<LatLng?>(null)
    val usingRealLocation = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)

    /** A Google result the user picked that is not saved in Dishcover yet (goes to Add menu). */
    val pendingPlace = MutableStateFlow<Place?>(null)

    /** Screens collect the flows above to recompose; these helpers read their current values. */
    private var placeMapFor: List<Place>? = null
    private var placeMapCache: Map<String, Place> = emptyMap()

    /** Built once per places list: ranking calls this for every dish, so it must be cheap. */
    fun placeMap(): Map<String, Place> {
        val list = places.value
        if (placeMapFor !== list) {
            placeMapCache = list.associateBy { it.id }
            placeMapFor = list
        }
        return placeMapCache
    }

    fun hasLocationPermission(): Boolean {
        val ctx = getApplication<Application>()
        return ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun refreshLocation() = viewModelScope.launch {
        var here: LatLng? = null
        if (hasLocationPermission()) {
            here = runCatching {
                val client = LocationServices.getFusedLocationProviderClient(getApplication<Application>())
                val loc = client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, CancellationTokenSource().token).await()
                    ?: client.lastLocation.await()
                loc?.let { LatLng(it.latitude, it.longitude) }
            }.getOrNull()
        }
        usingRealLocation.value = here != null
        val center = here ?: HARTFORD
        location.value = center
        runCatching { repo.loadNearby(center, settings.value.radiusKm) }
            .onFailure { message.value = "Couldn't load places: ${it.message}" }
        loadFeed(more = false)
        syncAlerts()
        refreshing.value = false
    }

    fun pullRefresh() {
        refreshing.value = true
        refreshLocation()
        loadNews(force = true)
    }

    fun loadFeed(more: Boolean) = viewModelScope.launch {
        if (feedLoading.value) return@launch
        feedLoading.value = true
        runCatching { repo.loadFeed(location.value ?: HARTFORD, more) }
            .onSuccess { feedHasMore.value = it }
            .onFailure { message.value = "Couldn't load the feed: ${it.message}" }
        feedLoading.value = false
    }

    fun loadNews(force: Boolean = false) = viewModelScope.launch {
        news.value = runCatching { News.load(force) }.getOrDefault(news.value)
    }

    /** Feed entries within the chosen radius, newest first. */
    fun feedNearby(): List<FeedItem> {
        val me = location.value ?: return feed.value
        val max = settings.value.radiusKm
        return feed.value.filter { (it.lat == 0.0 && it.lng == 0.0) || Geo.distanceKm(me, LatLng(it.lat, it.lng)) <= max }
    }

    fun markFeedSeen() {
        val newest = feed.value.maxOfOrNull { it.createdAt } ?: return
        if (newest > settings.value.feedSeenAt) prefs.update { it.copy(feedSeenAt = newest) }
    }

    fun unseenFeedCount(): Int {
        val seen = settings.value.feedSeenAt
        val me = user.value?.id
        return feedNearby().count { it.createdAt > seen && it.userId != me }
    }

    fun post(place: Place, dishName: String, text: String, stars: Int, photo: Uri?, done: () -> Unit) = launchBusy {
        val jpeg = photo?.let { withContext(Dispatchers.IO) { shrinkToJpeg(it) } }
        val saved = if (place.id.isEmpty()) repo.upsertPlace(place) else place
        repo.post(
            FeedItem(
                type = FeedType.POST, gh3 = saved.gh4.take(3), gh4 = saved.gh4, lat = saved.lat, lng = saved.lng,
                placeId = saved.id, placeName = saved.name, dishName = dishName.trim().take(80),
                text = text.trim().take(500), stars = stars,
            ),
            jpeg,
        )
        done()
    }

    fun toggleLike(item: FeedItem) = launchQuiet { repo.toggleLike(item) }
    fun deleteFeedItem(item: FeedItem) = launchBusy { repo.deleteFeedItem(item) }
    fun reportFeedItem(item: FeedItem, done: () -> Unit) = launchBusy { repo.reportFeedItem(item, "user report"); done() }
    fun toggleSaved(placeId: String) = launchQuiet { repo.toggleSaved(placeId); syncAlerts() }

    fun ratePlace(p: Place, stars: Int, text: String, share: Boolean, done: () -> Unit) = launchBusy {
        repo.ratePlace(p, stars, text, share)
        done()
    }

    suspend fun placeReviews(placeId: String) = repo.placeReviews(placeId)

    fun checkForUpdate(manual: Boolean) = viewModelScope.launch {
        val r = runCatching { repo.latestRelease() }.getOrNull()
        if (r != null && r.versionCode > BuildConfig.VERSION_CODE && r.apkUrl.startsWith("https://")) {
            update.value = r
            updateChecked.value = "Version ${r.versionName} is ready."
        } else if (manual) {
            updateChecked.value = if (repo.isDemo) "Update checks turn on when the app is connected to the server."
            else "You're on the newest version."
        }
    }

    /** Download progress of an update: null when idle, 0..1 while downloading. */
    val updateProgress = MutableStateFlow<Float?>(null)
    val updateError = MutableStateFlow<String?>(null)

    /** Downloads the new build inside the app, then opens Android's installer. */
    fun installUpdate() {
        val r = update.value ?: return
        val ctx = getApplication<Application>()
        if (updateProgress.value != null) return
        if (Updater.needsInstallPermission(ctx)) {
            updateError.value = "Allow Dishcover to install updates, then tap Update again."
            Updater.openInstallPermission(ctx)
            return
        }
        updateError.value = null
        updateProgress.value = 0f
        viewModelScope.launch {
            runCatching { Updater.download(ctx, r.apkUrl) { p -> updateProgress.value = p } }
                .onSuccess { Updater.install(ctx, it) }
                .onFailure { updateError.value = it.message ?: "The download failed. Try again." }
            updateProgress.value = null
        }
    }

    fun healthyPicks(): List<DishView> = dishes.value.filter { Suggest.isHealthy(it) }.map { view(it) }
        .filter { it.distanceKm == null || it.distanceKm <= Suggest.MAX_KM }
        .sortedWith(Suggest.order)

    fun trending(): List<DishView> = Suggest.trending(
        dishes.value.map { view(it) }.filter { it.distanceKm == null || it.distanceKm <= Suggest.MAX_KM },
    )

    /** Well-rated dishes nearby the user hasn't rated yet, want-to-try first. */
    fun toTry(): List<DishView> = dishes.value.map { view(it) }
        .filter { it.myStars == null && !it.sunk && (it.distanceKm == null || it.distanceKm <= Suggest.MAX_KM) }
        .filter { it.wanted || it.dish.average >= 4.0 }
        .sortedWith(compareByDescending<DishView> { it.wanted }.thenByDescending { it.dish.average })

    fun signInWithGoogle(activityContext: Context) = launchBusy {
        if (repo.isDemo) {
            repo.signInDemo()
            return@launchBusy
        }
        val ctx = getApplication<Application>()
        val resId = ctx.resources.getIdentifier("default_web_client_id", "string", ctx.packageName)
        require(resId != 0) { "Google sign-in isn't set up in Firebase yet" }
        val option = GetSignInWithGoogleOption.Builder(ctx.getString(resId)).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val result = CredentialManager.create(activityContext).getCredential(activityContext, request)
        val cred = result.credential
        if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            repo.signInWithGoogleIdToken(GoogleIdTokenCredential.createFrom(cred.data).idToken)
        } else error("Google didn't return an account")
    }

    fun signInWithEmail(email: String, password: String, create: Boolean) = launchBusy {
        repo.signInWithEmail(email.trim(), password, create)
    }

    fun signOut() = repo.signOut()

    fun deleteAccount() = launchBusy { repo.deleteAccount() }

    fun saveProfile(p: Profile, after: () -> Unit = {}) = launchBusy {
        repo.saveProfile(p)
        after()
    }

    // ---- ranking helpers used by screens ----

    fun view(d: Dish): DishView =
        Suggest.view(d, placeMap()[d.placeId], location.value, profile.value, myRatings.value)

    fun ranked(category: String?): List<DishView> {
        val list = Suggest.rank(category, dishes.value, placeMap(), location.value, profile.value, myRatings.value)
        return if (settings.value.healthyFirst) list.sortedByDescending { Suggest.isHealthy(it.dish) } else list
    }

    fun suggestions(category: String?): List<PlaceSuggestion> = Suggest.places(ranked(category), placeMap())

    fun topRated(): List<DishView> = dishes.value.map { view(it) }
        .filter { (it.myStars ?: 0) >= 4 }
        .sortedWith(Suggest.order)
        .take(3)

    fun wantToTry(): List<DishView> = dishes.value.map { view(it) }
        .filter { it.wanted && it.myStars == null }
        .sortedWith(Suggest.order)
        .take(3)

    // ---- search ----

    val googleResults = MutableStateFlow<List<Place>>(emptyList())

    fun searchGoogle(query: String) = viewModelScope.launch {
        if (!placesSource.available) {
            googleResults.value = emptyList()
            return@launch
        }
        val center = location.value
        googleResults.value = runCatching {
            if (query.isBlank()) { if (center != null) placesSource.nearby(center) else emptyList() }
            else placesSource.search(query, center)
        }.getOrElse {
            message.value = "Google search failed: ${it.message}"
            emptyList()
        }
    }

    /** Finds the saved Dishcover place for a Google result, if any. */
    fun savedFor(g: Place): Place? = places.value.firstOrNull { it.googlePlaceId != null && it.googlePlaceId == g.googlePlaceId }

    // ---- menus and dishes ----

    fun loadPlaceDishes(placeId: String) = viewModelScope.launch {
        runCatching { repo.dishesAt(placeId) }
    }

    suspend fun savePlace(p: Place): Place = repo.upsertPlace(p)

    /** Looks up a typed address with the phone's own geocoder (no API key). Null when it can't be found. */
    suspend fun placeAtAddress(name: String, address: String): Place? = withContext(Dispatchers.IO) {
        @Suppress("DEPRECATION")
        val hit = runCatching {
            android.location.Geocoder(getApplication<Application>()).getFromLocationName(address, 1)?.firstOrNull()
        }.getOrNull() ?: return@withContext null
        val line = hit.getAddressLine(0) ?: address
        Place(name = name, address = line, lat = hit.latitude, lng = hit.longitude)
    }

    fun placeAtMyLocation(name: String): Place {
        val here = location.value ?: HARTFORD
        return Place(name = name, lat = here.lat, lng = here.lng)
    }

    suspend fun readMenuPhoto(uri: Uri): List<DraftDish> {
        val jpeg = withContext(Dispatchers.IO) { shrinkToJpeg(uri) } ?: error("Couldn't open that photo")
        return repo.readMenu(jpeg, null)
    }

    suspend fun readMenuLink(url: String): List<DraftDish> {
        val u = url.trim()
        require(u.startsWith("https://")) { "Use a link that starts with https://" }
        return repo.readMenu(null, u)
    }

    suspend fun saveDishes(place: Place, drafts: List<DraftDish>): Int = repo.saveDishes(place, drafts)

    fun rate(d: Dish, stars: Int, text: String, share: Boolean, done: () -> Unit) = launchBusy {
        repo.rate(d, stars, text, share)
        done()
    }

    suspend fun reviews(dishId: String) = repo.reviews(dishId)

    fun flag(d: Dish, reason: String, done: () -> Unit) = launchBusy { repo.flagDish(d, reason); done() }
    fun report(r: app.godishcover.data.Rating, done: () -> Unit) = launchBusy { repo.reportReview(r, "user report"); done() }
    fun block(userId: String, done: () -> Unit) = launchBusy { repo.block(userId); done() }
    fun updateDish(d: Dish, done: () -> Unit) = launchBusy { repo.updateDish(d); done() }
    fun deleteDish(d: Dish, done: () -> Unit) = launchBusy { repo.deleteDish(d); done() }

    private fun launchQuiet(block: suspend () -> Unit) = viewModelScope.launch {
        try { block() } catch (e: Exception) { message.value = e.message ?: "Something went wrong" }
    }

    private fun launchBusy(block: suspend () -> Unit) = viewModelScope.launch {
        busy.value = true
        try {
            block()
        } catch (e: Exception) {
            message.value = e.message ?: "Something went wrong"
        } finally {
            busy.value = false
        }
    }

    /** Menu photos are scaled down to about 1 MB before upload. */
    fun shrinkToJpeg(uri: Uri): ByteArray? {
        val cr = getApplication<Application>().contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2400) sample *= 2
        val bmp = cr.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val longest = maxOf(bmp.width, bmp.height)
        val scaled = if (longest > 1600) {
            val f = 1600f / longest
            Bitmap.createScaledBitmap(bmp, (bmp.width * f).toInt(), (bmp.height * f).toInt(), true)
        } else bmp
        var q = 85
        while (true) {
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, q, out)
            if (out.size() <= 1_000_000 || q <= 40) return out.toByteArray()
            q -= 15
        }
    }

    companion object {
        val mealNow: Categories.Meal get() = Categories.mealFor(java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY))
    }
}
