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
    var createdByName: String = "",
    var createdAt: Long = 0,
    var ratingSum: Long = 0,
    var ratingCount: Long = 0,
    /** OpenStreetMap-style cuisine list, e.g. "pizza;italian". Set by the menu check. */
    var cuisine: String = "",
    var website: String = "",
    /** How many dishes the menu check found, so the app loads menus from places that have one. */
    var dishCount: Long = 0,
)

val Place.average: Double get() = if (ratingCount == 0L) 0.0 else ratingSum.toDouble() / ratingCount

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
    /** Diet tags from [Categories.TAGS], set by whoever added the dish. */
    var tags: List<String> = emptyList(),
    var lastRatedAt: Long = 0,
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
    var savedPlaces: List<String> = emptyList(),
    var likedFeed: List<String> = emptyList(),
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
    val tags: Set<String> = emptySet(),
)

data class LatLng(val lat: Double, val lng: Double)

/**
 * One entry in the "what's new" feed. Kept forever, newest first.
 * type: "menu" (dishes added to a place), "place" (new restaurant), "review" (a shared review),
 * "post" (where someone went and what they ate).
 */
data class FeedItem(
    var id: String = "",
    var type: String = "",
    /** 3-character geohash (about 150 km) used to load the feed for an area. */
    var gh3: String = "",
    var gh4: String = "",
    var lat: Double = 0.0,
    var lng: Double = 0.0,
    var createdAt: Long = 0,
    var userId: String = "",
    var userName: String = "",
    var placeId: String = "",
    var placeName: String = "",
    var dishId: String = "",
    var dishName: String = "",
    var dishNames: List<String> = emptyList(),
    var dishCount: Int = 0,
    var stars: Int = 0,
    var text: String = "",
    var photoUrl: String = "",
    var likeCount: Long = 0,
    var commentCount: Long = 0,
)

/** A comment under a feed post, stored at feed/{postId}/comments/{id}. */
data class Comment(
    var id: String = "",
    var userId: String = "",
    var userName: String = "",
    var text: String = "",
    var createdAt: Long = 0,
)

object FeedType {
    const val MENU = "menu"
    const val PLACE = "place"
    const val REVIEW = "review"
    const val POST = "post"
    val ALL = listOf(MENU, PLACE, REVIEW, POST)
    fun label(t: String) = when (t) {
        MENU -> "New on menus"
        PLACE -> "New places"
        REVIEW -> "Reviews"
        POST -> "Posts"
        else -> t
    }
}

data class NewsItem(val title: String, val link: String, val source: String, val publishedAt: Long, val summary: String = "")

/** The newest build Marcos has published, read from the server. */
data class Release(
    var versionCode: Long = 0,
    var versionName: String = "",
    var apkUrl: String = "",
    var notes: String = "",
)
