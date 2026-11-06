package app.godishcover.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

/**
 * Runs the whole app on the phone with made-up sample places, so it can be tried
 * before the Firebase project exists. Nothing leaves the phone and nothing is kept after closing.
 */
class DemoRepo : Repo {
    override val isDemo = true
    override val user = MutableStateFlow<User?>(null)
    override val profile = MutableStateFlow<Profile?>(null)
    override val places = MutableStateFlow<List<Place>>(emptyList())
    override val dishes = MutableStateFlow<List<Dish>>(emptyList())
    override val myRatings = MutableStateFlow<Map<String, Rating>>(emptyMap())
    private val reviews = mutableMapOf<String, MutableList<Rating>>()
    private var seeded = false

    override suspend fun signInWithGoogleIdToken(idToken: String) = signInDemo()
    override suspend fun signInWithEmail(email: String, password: String, create: Boolean) {
        user.value = User("demo-user", email, email.substringBefore('@'))
        if (profile.value == null) profile.value = Profile()
    }
    override suspend fun signInDemo() {
        user.value = User("demo-user", "demo@godishcover.com", "Demo")
        if (profile.value == null) profile.value = Profile()
    }
    override fun signOut() {
        user.value = null
        profile.value = null
        myRatings.value = emptyMap()
    }

    override suspend fun deleteAccount() {
        reviews.values.forEach { list -> list.removeAll { it.userId == user.value?.id } }
        signOut()
    }

    override suspend fun saveProfile(profile: Profile) { this.profile.value = profile }

    override suspend fun loadNearby(center: LatLng) {
        if (seeded) return
        seeded = true
        seed(center)
    }

    override suspend fun upsertPlace(place: Place): Place {
        place.googlePlaceId?.let { gid -> places.value.firstOrNull { it.googlePlaceId == gid }?.let { return it } }
        val saved = place.copy(
            id = place.id.ifEmpty { UUID.randomUUID().toString() },
            gh4 = Geo.geohash(place.lat, place.lng),
        )
        places.value = places.value.filter { it.id != saved.id } + saved
        return saved
    }

    override suspend fun dishesAt(placeId: String) = dishes.value.filter { it.placeId == placeId }

    override suspend fun saveDishes(place: Place, drafts: List<DraftDish>): Int {
        val now = System.currentTimeMillis()
        val me = user.value
        val new = drafts.filter { it.keep && it.name.isNotBlank() }.map {
            Dish(
                id = UUID.randomUUID().toString(), placeId = place.id, placeName = place.name, gh4 = place.gh4,
                name = it.name.trim(), categories = it.categories.toList().ifEmpty { listOf("other") },
                price = it.price, allergens = it.allergens, createdBy = me?.id ?: "",
                createdByName = profile.value?.publicName ?: "", createdAt = now, confirmedAt = now,
            )
        }
        dishes.value = dishes.value + new
        return new.size
    }

    override suspend fun updateDish(dish: Dish) {
        dishes.value = dishes.value.map { if (it.id == dish.id) dish else it }
    }

    override suspend fun deleteDish(dish: Dish) {
        dishes.value = dishes.value.filter { it.id != dish.id }
    }

    override suspend fun rate(dish: Dish, stars: Int, text: String) {
        val me = user.value ?: return
        val old = myRatings.value[dish.id]
        val r = Rating(dish.id, me.id, profile.value?.publicName ?: me.displayName, stars, text.take(500), System.currentTimeMillis())
        myRatings.value = myRatings.value + (dish.id to r)
        val list = reviews.getOrPut(dish.id) { mutableListOf() }
        list.removeAll { it.userId == me.id }
        list.add(0, r)
        dishes.value = dishes.value.map {
            if (it.id != dish.id) it
            else it.copy(
                ratingSum = it.ratingSum - (old?.stars ?: 0) + stars,
                ratingCount = it.ratingCount + if (old == null) 1 else 0,
            )
        }
    }

    override suspend fun reviews(dishId: String): List<Rating> {
        val blocked = profile.value?.blocked.orEmpty()
        return reviews[dishId].orEmpty().filter { it.text.isNotBlank() && it.userId !in blocked }
    }

    override suspend fun flagDish(dish: Dish, reason: String) {}
    override suspend fun reportReview(review: Rating, reason: String) {}
    override suspend fun block(userId: String) {
        val p = profile.value ?: Profile()
        profile.value = p.copy(blocked = (p.blocked + userId).distinct())
    }

    override suspend fun readMenu(imageJpeg: ByteArray?, url: String?): List<DraftDish> {
        delay(1200)
        return listOf(
            DraftDish("Al Pastor Tacos", "$12", setOf("tacos"), note = "Sample result: the real menu reader needs the server"),
            DraftDish("Birria Tacos", "$14", setOf("tacos")),
            DraftDish("Elote", "$6", setOf("other"), allergens = listOf("Milk"), note = "Menu lists: milk"),
            DraftDish("Churros", "", setOf("dessert"), note = "Price not printed"),
        )
    }

    private fun seed(c: LatLng) {
        data class S(val name: String, val dLat: Double, val dLng: Double, val menu: List<Triple<String, String, List<String>>>)
        val samples = listOf(
            S("Park Street Burger Bar (sample)", 0.004, 0.003, listOf(
                Triple("Smash Double", "$14", listOf("burger")),
                Triple("Mushroom Swiss", "$15", listOf("burger")),
                Triple("Crispy Chicken Sandwich", "$13", listOf("sandwich", "chicken")),
                Triple("Disco Fries", "$9", listOf("other")),
                Triple("Black and White Shake", "$8", listOf("dessert")),
            )),
            S("Capitol Taqueria (sample)", -0.008, 0.006, listOf(
                Triple("Al Pastor Tacos", "$12", listOf("tacos")),
                Triple("Birria Tacos", "$14", listOf("tacos")),
                Triple("Churros", "$6", listOf("dessert")),
            )),
            S("Bloomfield Ave Smokehouse (sample)", 0.015, -0.009, listOf(
                Triple("Brisket Burger", "$16", listOf("burger")),
                Triple("Wagyu Smash", "$19", listOf("burger")),
                Triple("Smoked Wings", "$13", listOf("chicken")),
            )),
            S("Franklin Ave Trattoria (sample)", -0.018, -0.004, listOf(
                Triple("Margherita", "$16", listOf("pizza")),
                Triple("Vodka Rigatoni", "$19", listOf("pasta")),
                Triple("Tiramisu", "$9", listOf("dessert")),
            )),
            S("Franklin Ave Diner (sample)", -0.02, 0.008, listOf(
                Triple("Patty Melt", "$13", listOf("burger", "sandwich")),
                Triple("Diner Cheeseburger", "$11", listOf("burger")),
                Triple("Pancake Stack", "$10", listOf("breakfast")),
                Triple("Bacon Egg and Cheese", "$7", listOf("breakfast", "sandwich")),
            )),
            S("Asylum Hill Sushi (sample)", 0.011, 0.014, listOf(
                Triple("Spicy Tuna Roll", "$9", listOf("sushi", "seafood")),
                Triple("Salmon Nigiri", "$8", listOf("sushi", "seafood")),
                Triple("Tonkotsu Ramen", "$16", listOf("other")),
            )),
            S("Bushnell Park Coffee (sample)", 0.002, -0.006, listOf(
                Triple("Oat Milk Latte", "$5", listOf("coffee")),
                Triple("Breakfast Burrito", "$9", listOf("breakfast")),
                Triple("Kale Caesar", "$11", listOf("salad")),
            )),
        )
        val avgs = listOf(4.6, 4.3, 4.4, 4.2, 4.5, 4.1, 4.7, 4.8, 3.9, 4.0, 4.4)
        var k = 0
        val ps = mutableListOf<Place>()
        val ds = mutableListOf<Dish>()
        samples.forEachIndexed { i, s ->
            val lat = c.lat + s.dLat
            val lng = c.lng + s.dLng
            val place = Place("sample-$i", s.name, "Sample address", lat, lng, Geo.geohash(lat, lng), createdBy = "sample")
            ps += place
            s.menu.forEachIndexed { j, (n, price, cats) ->
                val count = 5L + (k * 7) % 30
                val avg = avgs[k % avgs.size]
                k++
                ds += Dish(
                    "sample-$i-$j", place.id, place.name, place.gh4, n, "", cats, price,
                    allergens = if ("Shake" in n || "Latte" in n || "Pancake" in n) listOf("Milk") else emptyList(),
                    createdBy = "sample", createdByName = "Dishcover", createdAt = 0, confirmedAt = System.currentTimeMillis(),
                    ratingSum = Math.round(avg * count), ratingCount = count,
                )
            }
        }
        places.value = ps
        dishes.value = ds
    }
}
