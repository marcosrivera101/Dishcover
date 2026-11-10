package app.godishcover.data

data class DishView(
    val dish: Dish,
    val distanceKm: Double?,
    val myStars: Int?,
    val wanted: Boolean,
    val sunk: Boolean,
    val allergyHit: Boolean,
)

data class PlaceSuggestion(val place: Place?, val placeName: String, val distanceKm: Double?, val dishes: List<DishView>)

/**
 * Ranking from the step 1 plan: the user's own stars, then everyone's average, then distance.
 * Disliked categories and dishes listing one of the user's allergies move lower (never hidden);
 * want-to-try dishes move up.
 */
object Suggest {
    /** Updated from Settings: how far "nearby" reaches. */
    @Volatile var MAX_KM = 16.1

    fun isHealthy(d: Dish) = Categories.HEALTHY in d.tags

    /** Rated in the last two weeks, busiest first. */
    fun trending(views: List<DishView>, now: Long = System.currentTimeMillis()): List<DishView> =
        views.filter { it.dish.ratingCount > 0 && now - it.dish.lastRatedAt < 14L * 24 * 60 * 60 * 1000 }
            .sortedByDescending { it.dish.ratingCount * it.dish.average }

    fun view(dish: Dish, place: Place?, me: LatLng?, profile: Profile?, mine: Map<String, Rating>): DishView {
        val dist = if (me != null && place != null && (place.lat != 0.0 || place.lng != 0.0))
            Geo.distanceKm(me, LatLng(place.lat, place.lng)) else null
        val p = profile ?: Profile()
        val lname = dish.name.lowercase()
        val wanted = p.wantToTry.any { lname.contains(it.lowercase()) }
        val disliked = dish.categories.any { it in p.dislikes } && dish.categories.none { it in p.likes }
        val allergyHit = dish.allergens.any { it in p.allergies }
        return DishView(dish, dist, mine[dish.id]?.stars, wanted, disliked || allergyHit, allergyHit)
    }

    val order: Comparator<DishView> = compareBy<DishView> { if (it.sunk) 1 else 0 }
        .thenByDescending { it.myStars?.toDouble() ?: if (it.wanted) 4.5 else 0.0 }
        .thenByDescending { it.dish.average }
        .thenBy { it.distanceKm ?: Double.MAX_VALUE }

    /** [category] null means "Surprise me": the user's liked categories, or anything if none match. */
    fun rank(
        category: String?,
        dishes: List<Dish>,
        places: Map<String, Place>,
        me: LatLng?,
        profile: Profile?,
        mine: Map<String, Rating>,
    ): List<DishView> {
        val all = dishes.map { view(it, places[it.placeId], me, profile, mine) }
            .filter { it.distanceKm == null || it.distanceKm <= MAX_KM }
        val picked = if (category != null) {
            all.filter { category in it.dish.categories }
        } else {
            val likes = profile?.likes.orEmpty()
            all.filter { v -> v.dish.categories.any { it in likes } }.ifEmpty { all }
        }
        return picked.sortedWith(order)
    }

    /** Up to [maxPlaces] places with up to [perPlace] dishes each, best first. */
    fun places(ranked: List<DishView>, places: Map<String, Place>, maxPlaces: Int = 5, perPlace: Int = 3): List<PlaceSuggestion> =
        ranked.groupBy { it.dish.placeId }
            .entries
            .take(maxPlaces)
            .map { (pid, list) ->
                val first = list.first()
                PlaceSuggestion(places[pid], first.dish.placeName, first.distanceKm, list.take(perPlace))
            }
}
