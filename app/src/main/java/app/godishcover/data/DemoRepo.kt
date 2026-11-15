package app.godishcover.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

/**
 * Runs the whole app on the phone with made-up sample places, so it can be tried
 * before the Firebase project exists. Nothing leaves the phone and nothing is kept after closing.
 */
class DemoRepo(private val context: Context? = null) : Repo {
    override val isDemo = true
    override val user = MutableStateFlow<User?>(null)
    override val profile = MutableStateFlow<Profile?>(null)
    override val stats = MutableStateFlow(Stats())

    /** Test mode counts points on the phone so the rewards screen can be tried. */
    private fun earn(points: Int, counter: String? = null) {
        val s = stats.value
        val total = s.points + points
        val counts = if (counter == null) s.counts else s.counts + (counter to ((s.counts[counter] ?: 0L) + 1))
        val badges = buildList {
            if ((counts["ratings"] ?: 0) >= 1) add("first_bite")
            if ((counts["ratings"] ?: 0) >= 25) add("regular_rater")
            if ((counts["photos"] ?: 0) >= 10) add("photographer")
        }
        stats.value = Stats(total, Rewards.levelFor(total), counts, badges)
    }
    override val places = MutableStateFlow<List<Place>>(emptyList())
    override val dishes = MutableStateFlow<List<Dish>>(emptyList())
    override val myRatings = MutableStateFlow<Map<String, Rating>>(emptyMap())
    override val myPlaceRatings = MutableStateFlow<Map<String, Rating>>(emptyMap())
    override val feed = MutableStateFlow<List<FeedItem>>(emptyList())
    private val reviews = mutableMapOf<String, MutableList<Rating>>()
    private val placeReviewsMap = mutableMapOf<String, MutableList<Rating>>()
    private var olderFeed: List<FeedItem> = emptyList()
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
        myPlaceRatings.value = emptyMap()
    }

    override suspend fun deleteAccount() {
        reviews.values.forEach { list -> list.removeAll { it.userId == user.value?.id } }
        signOut()
    }

    override suspend fun saveProfile(profile: Profile) { this.profile.value = profile }

    override suspend fun loadNearby(center: LatLng, radiusKm: Double) {
        if (seeded) return
        seeded = true
        val real = try {
            withContext(Dispatchers.IO) { loadAssetSeed() }
        } catch (e: Throwable) {
            android.util.Log.e("DishcoverSeed", "Could not load the Hartford seed", e)
            false
        }
        android.util.Log.i("DishcoverSeed", "seed loaded=$real places=${places.value.size} dishes=${dishes.value.size}")
        if (!real) seed(center)
    }

    override suspend fun upsertPlace(place: Place): Place {
        place.googlePlaceId?.let { gid -> places.value.firstOrNull { it.googlePlaceId == gid }?.let { return it } }
        val isNew = place.id.isEmpty()
        val saved = place.copy(
            id = place.id.ifEmpty { UUID.randomUUID().toString() },
            gh4 = Geo.geohash(place.lat, place.lng),
            createdBy = user.value?.id ?: "", createdByName = myName(), createdAt = System.currentTimeMillis(),
        )
        places.value = places.value.filter { it.id != saved.id } + saved
        if (isNew) addFeed(feedFor(saved, FeedType.PLACE))
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
                price = it.price, allergens = it.allergens, tags = it.tags.toList(), createdBy = me?.id ?: "",
                createdByName = profile.value?.publicName ?: "", createdAt = now, confirmedAt = now,
            )
        }
        dishes.value = dishes.value + new
        if (new.isNotEmpty()) {
            addFeed(feedFor(place, FeedType.MENU).copy(dishNames = new.take(4).map { it.name }, dishCount = new.size, dishId = new.first().id))
        }
        return new.size
    }

    override suspend fun updateDish(dish: Dish) {
        dishes.value = dishes.value.map { if (it.id == dish.id) dish else it }
    }

    override suspend fun deleteDish(dish: Dish) {
        dishes.value = dishes.value.filter { it.id != dish.id }
    }

    override suspend fun rate(dish: Dish, stars: Int, text: String, shareToFeed: Boolean) {
        val me = user.value ?: return
        val old = myRatings.value[dish.id]
        if (old == null) earn(10, "ratings")
        val now = System.currentTimeMillis()
        val r = Rating(dish.id, me.id, myName(), stars, text.take(500), now)
        myRatings.value = myRatings.value + (dish.id to r)
        val list = reviews.getOrPut(dish.id) { mutableListOf() }
        list.removeAll { it.userId == me.id }
        list.add(0, r)
        dishes.value = dishes.value.map {
            if (it.id != dish.id) it
            else it.copy(
                ratingSum = it.ratingSum - (old?.stars ?: 0) + stars,
                ratingCount = it.ratingCount + if (old == null) 1 else 0,
                lastRatedAt = now,
            )
        }
        if (shareToFeed) {
            val place = places.value.firstOrNull { it.id == dish.placeId }
            addFeed(
                feedFor(place ?: Place(id = dish.placeId, name = dish.placeName), FeedType.REVIEW)
                    .copy(dishId = dish.id, dishName = dish.name, stars = stars, text = text.take(500)),
            )
        }
    }

    override suspend fun ratePlace(place: Place, stars: Int, text: String, shareToFeed: Boolean) {
        val me = user.value ?: return
        val old = myPlaceRatings.value[place.id]
        val r = Rating(place.id, me.id, myName(), stars, text.take(500), System.currentTimeMillis())
        myPlaceRatings.value = myPlaceRatings.value + (place.id to r)
        val list = placeReviewsMap.getOrPut(place.id) { mutableListOf() }
        list.removeAll { it.userId == me.id }
        list.add(0, r)
        places.value = places.value.map {
            if (it.id != place.id) it
            else it.copy(
                ratingSum = it.ratingSum - (old?.stars ?: 0) + stars,
                ratingCount = it.ratingCount + if (old == null) 1 else 0,
            )
        }
        if (shareToFeed) addFeed(feedFor(place, FeedType.REVIEW).copy(stars = stars, text = text.take(500)))
    }

    override suspend fun placeReviews(placeId: String): List<Rating> {
        val blocked = profile.value?.blocked.orEmpty()
        return placeReviewsMap[placeId].orEmpty().filter { it.userId !in blocked }
    }

    override suspend fun toggleSaved(placeId: String) {
        val p = profile.value ?: Profile()
        profile.value = p.copy(savedPlaces = if (placeId in p.savedPlaces) p.savedPlaces - placeId else p.savedPlaces + placeId)
    }

    override suspend fun loadFeed(center: LatLng, more: Boolean): Boolean {
        delay(350)
        if (more && olderFeed.isNotEmpty()) {
            feed.value = feed.value + olderFeed
            olderFeed = emptyList()
        }
        return olderFeed.isNotEmpty()
    }

    override suspend fun post(item: FeedItem, photoJpeg: ByteArray?) {
        addFeed(item.copy(id = UUID.randomUUID().toString(), userId = user.value?.id ?: "", userName = myName(), createdAt = System.currentTimeMillis()))
    }

    override suspend fun toggleLike(item: FeedItem) {
        val p = profile.value ?: Profile()
        val liked = item.id in p.likedFeed
        profile.value = p.copy(likedFeed = if (liked) p.likedFeed - item.id else p.likedFeed + item.id)
        feed.value = feed.value.map { if (it.id == item.id) it.copy(likeCount = (it.likeCount + if (liked) -1 else 1).coerceAtLeast(0)) else it }
    }

    override suspend fun deleteFeedItem(item: FeedItem) {
        feed.value = feed.value.filter { it.id != item.id }
    }

    override suspend fun reportFeedItem(item: FeedItem, reason: String) {}

    override suspend fun latestRelease(): Release? = null

    private fun myName(): String = profile.value?.publicName?.ifBlank { null } ?: user.value?.displayName ?: ""

    private fun feedFor(place: Place, type: String) = FeedItem(
        id = UUID.randomUUID().toString(), type = type, gh3 = place.gh4.take(3), gh4 = place.gh4,
        lat = place.lat, lng = place.lng, createdAt = System.currentTimeMillis(),
        userId = user.value?.id ?: "", userName = myName(), placeId = place.id, placeName = place.name,
    )

    private fun addFeed(item: FeedItem) {
        feed.value = listOf(item) + feed.value
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

    /**
     * Loads the real Hartford places and menus the menu check collected (assets/seed/hartford.json).
     * No ratings or reviews are made up for real restaurants; only the menu check's own posts are shown.
     */
    private fun loadAssetSeed(): Boolean {
        val json = try {
            context?.assets?.open("seed/hartford.json")?.bufferedReader()?.use { it.readText() }
        } catch (e: Exception) { null } ?: return false
        val root = JSONObject(json)
        fun JSONArray.strings() = (0 until length()).map { getString(it) }
        val ps = mutableListOf<Place>()
        val pa = root.getJSONArray("places")
        for (i in 0 until pa.length()) {
            val o = pa.getJSONObject(i)
            val lat = o.getDouble("lat"); val lng = o.getDouble("lng")
            ps += Place(
                id = o.getString("id"), name = o.getString("name"), address = o.optString("address"),
                lat = lat, lng = lng, gh4 = Geo.geohash(lat, lng), createdBy = "dishcover-menu-check",
                createdByName = "Dishcover", cuisine = o.optString("cuisine"), website = o.optString("website"),
            )
        }
        val byId = ps.associateBy { it.id }
        val now = System.currentTimeMillis()
        val ds = mutableListOf<Dish>()
        val da = root.getJSONArray("dishes")
        for (i in 0 until da.length()) {
            val o = da.getJSONObject(i)
            val p = byId[o.getString("placeId")] ?: continue
            ds += Dish(
                id = o.getString("id"), placeId = p.id, placeName = p.name, gh4 = p.gh4, name = o.getString("name"),
                description = o.optString("description"), categories = o.getJSONArray("categories").strings(),
                price = o.optString("price"), allergens = o.optJSONArray("allergens")?.strings().orEmpty(),
                tags = o.optJSONArray("tags")?.strings().orEmpty(), createdBy = "dishcover-menu-check",
                createdByName = "Dishcover", confirmedAt = now,
            )
        }
        if (ps.isEmpty()) return false
        places.value = ps
        dishes.value = ds
        // The menu check's posts: one per place with a menu, spread over the last few days.
        val hour = 60 * 60 * 1000L
        val counts = ds.groupBy { it.placeId }
        val all = ps.filter { counts.containsKey(it.id) }.mapIndexed { i, p ->
            val menu = counts.getValue(p.id)
            FeedItem(
                id = "menu-check-${p.id}", type = FeedType.MENU, gh3 = p.gh4.take(3), gh4 = p.gh4, lat = p.lat, lng = p.lng,
                createdAt = now - (1 + i * 3L) * hour, userId = "dishcover-menu-check", userName = "Dishcover menu check",
                placeId = p.id, placeName = p.name, dishNames = menu.take(4).map { it.name }, dishCount = menu.size,
                dishId = menu.first().id,
            )
        }
        feed.value = all.take(20)
        olderFeed = all.drop(20)
        return true
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

        val hour = 60 * 60 * 1000L
        val now = System.currentTimeMillis()
        val names = listOf("Dana K.", "Jess T.", "Omar L.", "Rae P.", "Luis M.", "Priya S.")
        fun item(i: Int, type: String, p: Place, ago: Long) = FeedItem(
            id = "sample-feed-$i", type = type, gh3 = p.gh4.take(3), gh4 = p.gh4, lat = p.lat, lng = p.lng,
            createdAt = now - ago, userId = "sample-user-${i % names.size}", userName = names[i % names.size],
            placeId = p.id, placeName = p.name, likeCount = (3 + i * 5 % 17).toLong(),
        )
        val all = mutableListOf<FeedItem>()
        ps.forEachIndexed { i, p ->
            val menu = ds.filter { it.placeId == p.id }
            all += item(i * 4, FeedType.MENU, p, (2 + i * 9) * hour)
                .copy(dishNames = menu.take(4).map { it.name }, dishCount = menu.size, dishId = menu.first().id)
            all += item(i * 4 + 1, FeedType.REVIEW, p, (5 + i * 13) * hour).copy(
                dishId = menu.first().id, dishName = menu.first().name, stars = 4 + i % 2,
                text = listOf(
                    "Crispy edges and a lot of cheese. Ask for extra pickles.",
                    "Worth the wait. Get it with the green salsa.",
                    "Smoky, juicy, and the bun holds up.",
                    "Best thing on the menu, no contest.",
                )[i % 4],
            )
            all += item(i * 4 + 2, FeedType.POST, p, (20 + i * 17) * hour).copy(
                dishName = menu.last().name,
                text = listOf("Friday dinner sorted.", "Lunch break done right.", "Took the family, everyone left happy.")[i % 3],
            )
            all += item(i * 4 + 3, FeedType.PLACE, p, (60 + i * 30) * hour)
        }
        all.sortByDescending { it.createdAt }
        feed.value = all.take(14)
        olderFeed = all.drop(14)
        // Sample ratings so trending and healthy picks have something to show.
        dishes.value = dishes.value.mapIndexed { i, d ->
            d.copy(
                lastRatedAt = now - (i * 7 % 20) * 24 * hour,
                tags = when {
                    "Caesar" in d.name || "Nigiri" in d.name -> listOf("healthy")
                    "Margherita" in d.name -> listOf("vegetarian")
                    "Tuna" in d.name -> listOf("healthy", "spicy")
                    else -> emptyList()
                },
            )
        }
    }
}
