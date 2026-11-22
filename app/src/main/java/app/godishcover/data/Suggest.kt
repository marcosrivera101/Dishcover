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

    private val ALCOHOL = Regex("""\b(beer|ipa|lager|ale|stout|pilsner|cider|seltzer|wine|merlot|cabernet|pinot|chardonnay|prosecco|champagne|sangria|cocktail|margarita|martini|mojito|vodka|tequila|whiskey|bourbon|rum|gin|mimosa|spritz|mule|old fashioned|draft|pint)\b""", RegexOption.IGNORE_CASE)
    private val MORNING = Regex("""\b(breakfast|egg|eggs|omelet|omelette|pancakes?|waffles?|french toast|bagel|croissant|toast|hash browns?|home fries|bacon,? egg|sausage,? egg|biscuit|grits|oatmeal|granola|yogurt|parfait|crepe|benedict|scramble|frittata|chilaquiles|huevos|burrito|wrap|sandwich|avocado toast|steak and eggs|chicken and waffles)\b""", RegexOption.IGNORE_CASE)
    private val SWEET = Regex("""\b(donuts?|doughnuts?|munchkins?|cake|cakes|cupcakes?|cheesecake|cookies?|brownies?|ice cream|sundaes?|shakes?|milkshakes?|frosty|gelato|sorbet|churros?|pie|pies|pastry|pastries|danish|cinnamon rolls?|candy|fudge|tiramisu|cannoli|flan|pudding|delights|twists|coolatta|frappe|frappuccino)\b""", RegexOption.IGNORE_CASE)
    private val MORNING_CATS = setOf("breakfast", "bagels")
    private val NOT_A_MEAL = setOf("dessert", "ice cream", "coffee", "bubble tea", "smoothies and juice", "donuts", "bakery")

    /** Whether a dish belongs in suggestions for [meal]. No drinks with alcohol at breakfast; "Surprise me" sticks to meals. */
    fun fitsMeal(d: Dish, meal: Categories.Meal, category: String?): Boolean {
        val text = d.name + " " + d.section
        if (category in Categories.SWEETS_DRINKS) return !(ALCOHOL.containsMatchIn(text) && meal == Categories.Meal.Breakfast)
        // A meal mood means a meal: no desserts, sweets or drinks unless the person picks one of those moods.
        if (ALCOHOL.containsMatchIn(text) || SWEET.containsMatchIn(text) || (d.categories.any { it in NOT_A_MEAL } && d.categories.all { it in NOT_A_MEAL || it in Categories.CUISINES || it == "other" })) return false
        if (category != null) return true
        // "Surprise me" at breakfast sticks to breakfast food.
        return meal != Categories.Meal.Breakfast || d.categories.any { it in MORNING_CATS } || MORNING.containsMatchIn(text)
    }

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
        meal: Categories.Meal? = null,
    ): List<DishView> {
        val all = dishes.map { view(it, places[it.placeId], me, profile, mine) }
            .filter { it.distanceKm == null || it.distanceKm <= MAX_KM }
            .filter { meal == null || fitsMeal(it.dish, meal, category) }
        val picked = if (category != null) {
            all.filter { category in it.dish.categories }
        } else {
            val likes = profile?.likes.orEmpty()
            all.filter { v -> v.dish.categories.any { it in likes } }.ifEmpty { all }
        }
        return picked.sortedWith(order)
    }

    /** Up to [maxPlaces] places with up to [perPlace] dishes each, best first. */
    /** One location per restaurant name (chains have many): the closest one, kept at the rank of its best location. */
    fun places(ranked: List<DishView>, places: Map<String, Place>, maxPlaces: Int = 5, perPlace: Int = 3): List<PlaceSuggestion> =
        ranked.groupBy { it.dish.placeId }
            .entries
            .groupBy { (_, list) -> chainKey(list.first().dish.placeName) }
            .values
            .take(maxPlaces)
            .map { locations ->
                val (pid, list) = locations.minBy { (_, l) -> l.first().distanceKm ?: Double.MAX_VALUE }
                val first = list.first()
                PlaceSuggestion(places[pid], first.dish.placeName, first.distanceKm, list.take(perPlace))
            }

    private fun chainKey(name: String) = name.lowercase().replace(Regex("[^a-z0-9]"), "")
}
