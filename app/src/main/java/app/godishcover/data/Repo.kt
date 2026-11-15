package app.godishcover.data

import kotlinx.coroutines.flow.StateFlow

/** Everything the screens need from the backend. [FirebaseRepo] is the real one, [DemoRepo] keeps sample data on the phone. */
interface Repo {
    val isDemo: Boolean
    val user: StateFlow<User?>
    /** Null until loaded after sign-in. */
    val profile: StateFlow<Profile?>
    /** Places and dishes around the last loaded location. */
    val places: StateFlow<List<Place>>
    val dishes: StateFlow<List<Dish>>
    /** The signed-in user's own ratings, by dish id and by place id. */
    val myRatings: StateFlow<Map<String, Rating>>
    val myPlaceRatings: StateFlow<Map<String, Rating>>
    /** The "what's new" feed loaded so far, newest first. */
    val feed: StateFlow<List<FeedItem>>
    /** The signed-in user's points, level and badges. */
    val stats: StateFlow<Stats>

    suspend fun signInWithGoogleIdToken(idToken: String)
    suspend fun signInWithEmail(email: String, password: String, create: Boolean)
    suspend fun signInDemo()
    fun signOut()
    /** Deletes the profile, the user's ratings and reviews, and the sign-in account. */
    suspend fun deleteAccount()

    suspend fun saveProfile(profile: Profile)

    /** Loads places and dishes within [radiusKm] of [center]. */
    suspend fun loadNearby(center: LatLng, radiusKm: Double)
    /** Asks the server to look up menus around here, once per area (about 5 km square). */
    suspend fun requestAreaMenus(center: LatLng) {}

    /** Saves a place (deduped by Google place id) and returns it with its id. */
    suspend fun upsertPlace(place: Place): Place
    suspend fun dishesAt(placeId: String): List<Dish>
    suspend fun saveDishes(place: Place, drafts: List<DraftDish>): Int
    suspend fun updateDish(dish: Dish)
    suspend fun deleteDish(dish: Dish)

    suspend fun rate(dish: Dish, stars: Int, text: String, shareToFeed: Boolean)
    suspend fun reviews(dishId: String): List<Rating>
    suspend fun ratePlace(place: Place, stars: Int, text: String, shareToFeed: Boolean)
    suspend fun placeReviews(placeId: String): List<Rating>
    suspend fun flagDish(dish: Dish, reason: String)
    suspend fun reportReview(review: Rating, reason: String)
    suspend fun block(userId: String)
    suspend fun toggleSaved(placeId: String)

    /** Loads the first page of the feed around [center], or the next page when [more] is true. Returns whether older entries remain. */
    suspend fun loadFeed(center: LatLng, more: Boolean): Boolean
    /** Publishes a post or shared review; [photoJpeg] is uploaded first when given. */
    suspend fun post(item: FeedItem, photoJpeg: ByteArray?)
    suspend fun toggleLike(item: FeedItem)
    suspend fun deleteFeedItem(item: FeedItem)
    suspend fun reportFeedItem(item: FeedItem, reason: String)

    /** Sends a menu photo (JPEG) or a link to the menu reader and returns draft dishes. */
    suspend fun readMenu(imageJpeg: ByteArray?, url: String?): List<DraftDish>

    /** The newest published build, or null when unknown. */
    suspend fun latestRelease(): Release?
}
