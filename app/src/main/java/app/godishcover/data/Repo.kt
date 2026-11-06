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
    /** The signed-in user's own ratings, by dish id. */
    val myRatings: StateFlow<Map<String, Rating>>

    suspend fun signInWithGoogleIdToken(idToken: String)
    suspend fun signInWithEmail(email: String, password: String, create: Boolean)
    suspend fun signInDemo()
    fun signOut()
    /** Deletes the profile, the user's ratings and reviews, and the sign-in account. */
    suspend fun deleteAccount()

    suspend fun saveProfile(profile: Profile)

    /** Loads places and dishes in the cells around [center]. */
    suspend fun loadNearby(center: LatLng)

    /** Saves a place (deduped by Google place id) and returns it with its id. */
    suspend fun upsertPlace(place: Place): Place
    suspend fun dishesAt(placeId: String): List<Dish>
    suspend fun saveDishes(place: Place, drafts: List<DraftDish>): Int
    suspend fun updateDish(dish: Dish)
    suspend fun deleteDish(dish: Dish)

    suspend fun rate(dish: Dish, stars: Int, text: String)
    suspend fun reviews(dishId: String): List<Rating>
    suspend fun flagDish(dish: Dish, reason: String)
    suspend fun reportReview(review: Rating, reason: String)
    suspend fun block(userId: String)

    /** Sends a menu photo (JPEG) or a link to the menu reader and returns draft dishes. */
    suspend fun readMenu(imageJpeg: ByteArray?, url: String?): List<DraftDish>
}
