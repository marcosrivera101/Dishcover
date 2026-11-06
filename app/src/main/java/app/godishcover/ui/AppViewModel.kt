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
import app.godishcover.data.Categories
import app.godishcover.data.Dish
import app.godishcover.data.DishView
import app.godishcover.data.DraftDish
import app.godishcover.data.LatLng
import app.godishcover.data.Place
import app.godishcover.data.PlaceSuggestion
import app.godishcover.data.Profile
import app.godishcover.data.Suggest
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

    val user = repo.user
    val profile = repo.profile
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
    fun placeMap(): Map<String, Place> = places.value.associateBy { it.id }

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
        runCatching { repo.loadNearby(center) }.onFailure { message.value = "Couldn't load places: ${it.message}" }
    }

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

    fun ranked(category: String?): List<DishView> =
        Suggest.rank(category, dishes.value, placeMap(), location.value, profile.value, myRatings.value)

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

    fun rate(d: Dish, stars: Int, text: String, done: () -> Unit) = launchBusy {
        repo.rate(d, stars, text)
        done()
    }

    suspend fun reviews(dishId: String) = repo.reviews(dishId)

    fun flag(d: Dish, reason: String, done: () -> Unit) = launchBusy { repo.flagDish(d, reason); done() }
    fun report(r: app.godishcover.data.Rating, done: () -> Unit) = launchBusy { repo.reportReview(r, "user report"); done() }
    fun block(userId: String, done: () -> Unit) = launchBusy { repo.block(userId); done() }
    fun updateDish(d: Dish, done: () -> Unit) = launchBusy { repo.updateDish(d); done() }
    fun deleteDish(d: Dish, done: () -> Unit) = launchBusy { repo.deleteDish(d); done() }

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
    private fun shrinkToJpeg(uri: Uri): ByteArray? {
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
