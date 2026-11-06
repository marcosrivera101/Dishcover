package app.godishcover.data

/** Firestore fills these by reflection, so fields are var with defaults. */

/** A restaurant. [googlePlaceId] is set when it came from Google Places. */
data class Place(
    var id: String = "",
    var name: String = "",
    var address: String = "",
    var lat: Double = 0.0,
    var lng: Double = 0.0,
    /** Geohash (precision 4) of lat/lng, used for nearby queries. */
    var gh4: String = "",
    var googlePlaceId: String? = null,
    /** When Google's coordinates were cached (ms). Cleared after 30 days by a server job. */
    var coordsCachedAt: Long = 0,
    var createdBy: String = "",
)

data class Dish(
    var id: String = "",
    var placeId: String = "",
    var placeName: String = "",
    var gh4: String = "",
    var name: String = "",
    var description: String = "",
    var categories: List<String> = emptyList(),
    var price: String = "",
    /** Allergens printed on the menu, using [Categories.ALLERGIES] labels. */
    var allergens: List<String> = emptyList(),
    var createdBy: String = "",
    var createdByName: String = "",
    var createdAt: Long = 0,
    var confirmedAt: Long = 0,
    var ratingSum: Long = 0,
    var ratingCount: Long = 0,
)

val Dish.average: Double get() = if (ratingCount == 0L) 0.0 else ratingSum.toDouble() / ratingCount

data class Rating(
    var dishId: String = "",
    var userId: String = "",
    var userName: String = "",
    var stars: Int = 0,
    var text: String = "",
    var updatedAt: Long = 0,
)

/** Private sign-up interview answers. Only the owner can read them. */
data class Profile(
    var publicName: String = "",
    var agreedToRules: Boolean = false,
    var interviewDone: Boolean = false,
    var likes: List<String> = emptyList(),
    var dislikes: List<String> = emptyList(),
    var allergies: List<String> = emptyList(),
    var wantToTry: List<String> = emptyList(),
    var blocked: List<String> = emptyList(),
)

data class User(val id: String, val email: String, val displayName: String)

/** A dish the menu reader (or the user) proposes, before it is saved. */
data class DraftDish(
    val name: String,
    val price: String = "",
    val categories: Set<String> = emptySet(),
    val allergens: List<String> = emptyList(),
    val note: String = "",
    val keep: Boolean = true,
)

data class LatLng(val lat: Double, val lng: Double)
