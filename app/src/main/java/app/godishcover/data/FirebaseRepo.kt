package app.godishcover.data

import android.util.Base64
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Query
import com.google.firebase.functions.functions
import com.google.firebase.storage.storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Firestore layout:
 *   users/{uid}               private profile and interview answers (owner only)
 *   places/{id}               restaurants, with gh4 geohash for nearby queries
 *   dishes/{id}               dishes, with gh4 copied from their place, plus ratingSum/ratingCount
 *   ratings/{dishId}_{uid}    one rating per user per dish
 *   flags, reports            create-only moderation queues
 */
class FirebaseRepo : Repo {
    private companion object { const val PAGE = 25 }
    override val isDemo = false
    private val auth = Firebase.auth
    private val db = Firebase.firestore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override val user = MutableStateFlow<User?>(null)
    override val profile = MutableStateFlow<Profile?>(null)
    override val places = MutableStateFlow<List<Place>>(emptyList())
    override val dishes = MutableStateFlow<List<Dish>>(emptyList())
    override val myRatings = MutableStateFlow<Map<String, Rating>>(emptyMap())
    override val myPlaceRatings = MutableStateFlow<Map<String, Rating>>(emptyMap())
    override val feed = MutableStateFlow<List<FeedItem>>(emptyList())
    override val stats = MutableStateFlow(Stats())
    private var statsListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var feedCursor: DocumentSnapshot? = null
    private var feedCells: List<String> = emptyList()

    init {
        auth.addAuthStateListener { a: FirebaseAuth ->
            val fu = a.currentUser
            user.value = fu?.let { User(it.uid, it.email ?: "", it.displayName ?: "") }
            statsListener?.remove()
            statsListener = null
            if (fu != null) {
                statsListener = db.collection("stats").document(fu.uid).addSnapshotListener { snap, _ ->
                    stats.value = snap?.toObject(Stats::class.java) ?: Stats()
                }
            } else stats.value = Stats()
            if (fu == null) {
                profile.value = null
                myRatings.value = emptyMap()
                myPlaceRatings.value = emptyMap()
            } else {
                scope.launch { runCatching { loadMine(fu.uid) } }
            }
        }
    }

    private suspend fun loadMine(uid: String) {
        val snap = db.collection("users").document(uid).get().await()
        profile.value = snap.toObject(Profile::class.java) ?: Profile()
        val rs = db.collection("ratings").whereEqualTo("userId", uid).get().await()
        myRatings.value = rs.documents.mapNotNull { it.toObject(Rating::class.java) }.associateBy { it.dishId }
        val prs = db.collection("placeRatings").whereEqualTo("userId", uid).get().await()
        myPlaceRatings.value = prs.documents.mapNotNull { it.toObject(Rating::class.java) }.associateBy { it.dishId }
    }

    override suspend fun signInWithGoogleIdToken(idToken: String) {
        auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
    }

    override suspend fun signInWithEmail(email: String, password: String, create: Boolean) {
        if (create) auth.createUserWithEmailAndPassword(email, password).await()
        else auth.signInWithEmailAndPassword(email, password).await()
    }

    override suspend fun signInDemo() { error("Demo sign-in is only for demo mode") }

    override fun signOut() = auth.signOut()

    override suspend fun deleteAccount() {
        val me = uid()
        val mine = db.collection("ratings").whereEqualTo("userId", me).get().await().documents
        for (doc in mine) {
            val r = doc.toObject(Rating::class.java) ?: continue
            runCatching {
                db.runTransaction { tx ->
                    tx.delete(doc.reference)
                tx.update(
                    db.collection("dishes").document(r.dishId),
                    mapOf(
                        "ratingSum" to FieldValue.increment(-r.stars.toLong()),
                        "ratingCount" to FieldValue.increment(-1L),
                    ),
                )
                }.await()
            }
        }
        val placeMine = db.collection("placeRatings").whereEqualTo("userId", me).get().await().documents
        for (doc in placeMine) {
            val r = doc.toObject(Rating::class.java) ?: continue
            runCatching {
                db.runTransaction { tx ->
                    tx.delete(doc.reference)
                    tx.update(
                        db.collection("places").document(r.dishId),
                        mapOf(
                            "ratingSum" to FieldValue.increment(-r.stars.toLong()),
                            "ratingCount" to FieldValue.increment(-1L),
                        ),
                    )
                }.await()
            }
        }
        val posts = db.collection("feed").whereEqualTo("userId", me).get().await().documents
        for (doc in posts) runCatching { doc.reference.delete().await() }
        db.collection("users").document(me).delete().await()
        auth.currentUser?.delete()?.await()
    }

    private fun uid(): String = auth.currentUser?.uid ?: error("Not signed in")

    override suspend fun saveProfile(profile: Profile) {
        db.collection("users").document(uid()).set(profile).await()
        this.profile.value = profile
    }

    override suspend fun loadNearby(center: LatLng, radiusKm: Double) {
        // Firestore allows 30 values per "in" query, so wide radiuses run a few queries.
        val chunks = Geo.cellsFor(center, radiusKm).chunked(30)
        val ps = mutableListOf<Place>()
        val ds = mutableListOf<Dish>()
        for (cells in chunks) {
            ps += db.collection("places").whereIn("gh4", cells).get().await()
                .documents.mapNotNull { d -> d.toObject(Place::class.java)?.also { it.id = d.id } }
            ds += db.collection("dishes").whereIn("gh4", cells).get().await()
                .documents.mapNotNull { d -> d.toObject(Dish::class.java)?.also { it.id = d.id } }
        }
        // Keep saved places that are outside the radius.
        val saved = profile.value?.savedPlaces.orEmpty().filter { id -> ps.none { it.id == id } }.take(20)
        for (id in saved) runCatching {
            val d = db.collection("places").document(id).get().await()
            d.toObject(Place::class.java)?.also { it.id = d.id }?.let { ps += it }
        }
        places.value = ps
        dishes.value = ds
    }

    override suspend fun upsertPlace(place: Place): Place {
        val col = db.collection("places")
        val gid = place.googlePlaceId
        if (gid != null) {
            val existing = col.whereEqualTo("googlePlaceId", gid).limit(1).get().await().documents.firstOrNull()
            if (existing != null) {
                // Refresh Google's cached coordinates (allowed for 30 days).
                val refreshed = mapOf(
                    "lat" to place.lat, "lng" to place.lng,
                    "gh4" to Geo.geohash(place.lat, place.lng),
                    "coordsCachedAt" to System.currentTimeMillis(),
                )
                existing.reference.set(refreshed, SetOptions.merge()).await()
                // Dishes carry the same cell so nearby search finds them; the 30-day cleanup clears both.
                val gh = Geo.geohash(place.lat, place.lng)
                val stale = db.collection("dishes").whereEqualTo("placeId", existing.id).get().await()
                    .documents.filter { it.getString("gh4") != gh }
                if (stale.isNotEmpty()) {
                    val batch = db.batch()
                    stale.forEach { batch.update(it.reference, "gh4", gh) }
                    batch.commit().await()
                }
                val p = (existing.toObject(Place::class.java) ?: place).copy(
                    id = existing.id, lat = place.lat, lng = place.lng, gh4 = Geo.geohash(place.lat, place.lng),
                )
                mergeLocal(p)
                return p
            }
        }
        val ref = col.document()
        val p = place.copy(
            id = ref.id,
            gh4 = Geo.geohash(place.lat, place.lng),
            coordsCachedAt = if (gid != null) System.currentTimeMillis() else 0,
            createdBy = uid(),
            createdByName = myName(),
            createdAt = System.currentTimeMillis(),
            ratingSum = 0, ratingCount = 0,
        )
        ref.set(p).await()
        mergeLocal(p)
        runCatching { addFeed(feedFor(p, FeedType.PLACE)) }
        return p
    }

    private fun mergeLocal(p: Place) {
        places.value = places.value.filter { it.id != p.id } + p
    }

    override suspend fun dishesAt(placeId: String): List<Dish> {
        val ds = db.collection("dishes").whereEqualTo("placeId", placeId).get().await()
            .documents.mapNotNull { d -> d.toObject(Dish::class.java)?.also { it.id = d.id } }
        dishes.value = dishes.value.filter { it.placeId != placeId } + ds
        return ds
    }

    override suspend fun saveDishes(place: Place, drafts: List<DraftDish>): Int {
        val me = uid()
        val now = System.currentTimeMillis()
        val batch = db.batch()
        val new = drafts.filter { it.keep && it.name.isNotBlank() }.map {
            val ref = db.collection("dishes").document()
            val d = Dish(
                id = ref.id, placeId = place.id, placeName = place.name, gh4 = place.gh4,
                name = it.name.trim().take(80), categories = it.categories.toList().ifEmpty { listOf("other") },
                price = it.price.take(20), allergens = it.allergens, tags = it.tags.toList(), createdBy = me,
                createdByName = myName(), createdAt = now, confirmedAt = now,
            )
            batch.set(ref, d)
            d
        }
        batch.commit().await()
        dishes.value = dishes.value + new
        if (new.isNotEmpty()) runCatching {
            addFeed(feedFor(place, FeedType.MENU).copy(dishNames = new.take(4).map { it.name }, dishCount = new.size, dishId = new.first().id))
        }
        return new.size
    }

    override suspend fun updateDish(dish: Dish) {
        db.collection("dishes").document(dish.id).set(
            mapOf(
                "name" to dish.name, "description" to dish.description, "categories" to dish.categories,
                "price" to dish.price, "allergens" to dish.allergens, "tags" to dish.tags,
                "confirmedAt" to System.currentTimeMillis(),
            ),
            SetOptions.merge(),
        ).await()
        dishes.value = dishes.value.map { if (it.id == dish.id) dish else it }
    }

    override suspend fun deleteDish(dish: Dish) {
        db.collection("dishes").document(dish.id).delete().await()
        dishes.value = dishes.value.filter { it.id != dish.id }
    }

    override suspend fun rate(dish: Dish, stars: Int, text: String, shareToFeed: Boolean) {
        val me = uid()
        val now = System.currentTimeMillis()
        val dishRef = db.collection("dishes").document(dish.id)
        val ratingRef = db.collection("ratings").document("${dish.id}_$me")
        val r = Rating(dish.id, me, myName(), stars.coerceIn(1, 5), text.take(500), now)
        db.runTransaction { tx ->
            val old = tx.get(ratingRef).toObject(Rating::class.java)
            tx.set(ratingRef, r)
            tx.update(
                dishRef,
                mapOf(
                    "ratingSum" to FieldValue.increment((r.stars - (old?.stars ?: 0)).toLong()),
                    "ratingCount" to FieldValue.increment(if (old == null) 1L else 0L),
                    "lastRatedAt" to now,
                ),
            )
        }.await()
        val old = myRatings.value[dish.id]
        myRatings.value = myRatings.value + (dish.id to r)
        dishes.value = dishes.value.map {
            if (it.id != dish.id) it
            else it.copy(
                ratingSum = it.ratingSum - (old?.stars ?: 0) + r.stars,
                ratingCount = it.ratingCount + if (old == null) 1 else 0,
                lastRatedAt = now,
            )
        }
        if (shareToFeed) {
            val place = places.value.firstOrNull { it.id == dish.placeId } ?: Place(id = dish.placeId, name = dish.placeName, gh4 = dish.gh4)
            addFeed(feedFor(place, FeedType.REVIEW).copy(dishId = dish.id, dishName = dish.name, stars = r.stars, text = r.text))
        }
    }

    override suspend fun ratePlace(place: Place, stars: Int, text: String, shareToFeed: Boolean) {
        val me = uid()
        val placeRef = db.collection("places").document(place.id)
        val ratingRef = db.collection("placeRatings").document("${place.id}_$me")
        val r = Rating(place.id, me, myName(), stars.coerceIn(1, 5), text.take(500), System.currentTimeMillis())
        db.runTransaction { tx ->
            val old = tx.get(ratingRef).toObject(Rating::class.java)
            tx.set(ratingRef, r)
            tx.update(
                placeRef,
                mapOf(
                    "ratingSum" to FieldValue.increment((r.stars - (old?.stars ?: 0)).toLong()),
                    "ratingCount" to FieldValue.increment(if (old == null) 1L else 0L),
                ),
            )
        }.await()
        val old = myPlaceRatings.value[place.id]
        myPlaceRatings.value = myPlaceRatings.value + (place.id to r)
        places.value = places.value.map {
            if (it.id != place.id) it
            else it.copy(
                ratingSum = it.ratingSum - (old?.stars ?: 0) + r.stars,
                ratingCount = it.ratingCount + if (old == null) 1 else 0,
            )
        }
        if (shareToFeed) addFeed(feedFor(place, FeedType.REVIEW).copy(stars = r.stars, text = r.text))
    }

    override suspend fun placeReviews(placeId: String): List<Rating> {
        val blocked = profile.value?.blocked.orEmpty()
        return db.collection("placeRatings").whereEqualTo("dishId", placeId).get().await()
            .documents.mapNotNull { it.toObject(Rating::class.java) }
            .filter { it.userId !in blocked }
            .sortedByDescending { it.updatedAt }
    }

    override suspend fun toggleSaved(placeId: String) {
        val p = profile.value ?: Profile()
        val saving = placeId !in p.savedPlaces
        saveProfile(p.copy(savedPlaces = if (saving) p.savedPlaces + placeId else p.savedPlaces - placeId))
    }

    override suspend fun loadFeed(center: LatLng, more: Boolean): Boolean {
        if (!more) {
            feedCursor = null
            feedCells = Geo.feedCells(center)
        }
        var q = db.collection("feed").whereIn("gh3", feedCells)
            .orderBy("createdAt", Query.Direction.DESCENDING).limit(PAGE.toLong())
        feedCursor?.let { q = q.startAfter(it) }
        val snap = q.get().await()
        val blocked = profile.value?.blocked.orEmpty()
        val items = snap.documents.mapNotNull { d -> d.toObject(FeedItem::class.java)?.also { it.id = d.id } }
            .filter { it.userId !in blocked }
        if (snap.documents.isNotEmpty()) feedCursor = snap.documents.last()
        feed.value = if (more) (feed.value + items).distinctBy { it.id } else items
        return snap.size() >= PAGE
    }

    override suspend fun post(item: FeedItem, photoJpeg: ByteArray?) {
        val me = uid()
        val ref = db.collection("feed").document()
        var photoUrl = ""
        if (photoJpeg != null) {
            val file = Firebase.storage.reference.child("posts/$me/${ref.id}.jpg")
            file.putBytes(photoJpeg).await()
            photoUrl = file.downloadUrl.await().toString()
        }
        val saved = item.copy(
            id = ref.id, userId = me, userName = myName(), createdAt = System.currentTimeMillis(),
            gh3 = item.gh4.take(3), text = item.text.take(500), photoUrl = photoUrl, likeCount = 0,
        )
        ref.set(saved).await()
        feed.value = listOf(saved) + feed.value
    }

    override suspend fun toggleLike(item: FeedItem) {
        val p = profile.value ?: Profile()
        val liked = item.id in p.likedFeed
        db.collection("feed").document(item.id)
            .update("likeCount", FieldValue.increment(if (liked) -1L else 1L)).await()
        // Only the most recent likes are remembered, so the private profile stays small.
        saveProfile(p.copy(likedFeed = if (liked) p.likedFeed - item.id else (p.likedFeed + item.id).takeLast(400)))
        feed.value = feed.value.map {
            if (it.id == item.id) it.copy(likeCount = (it.likeCount + if (liked) -1 else 1).coerceAtLeast(0)) else it
        }
    }

    override suspend fun deleteFeedItem(item: FeedItem) {
        db.collection("feed").document(item.id).delete().await()
        if (item.photoUrl.isNotEmpty()) runCatching { Firebase.storage.getReferenceFromUrl(item.photoUrl).delete().await() }
        feed.value = feed.value.filter { it.id != item.id }
    }

    override suspend fun reportFeedItem(item: FeedItem, reason: String) {
        db.collection("reports").add(
            mapOf("feedId" to item.id, "reviewUserId" to item.userId, "reason" to reason, "by" to uid(), "at" to System.currentTimeMillis()),
        ).await()
    }

    override suspend fun latestRelease(): Release? =
        db.collection("config").document("app").get().await().toObject(Release::class.java)

    private fun myName(): String = profile.value?.publicName?.ifBlank { null } ?: auth.currentUser?.displayName ?: ""

    private fun feedFor(place: Place, type: String) = FeedItem(
        type = type, gh3 = place.gh4.take(3), gh4 = place.gh4, lat = place.lat, lng = place.lng,
        placeId = place.id, placeName = place.name,
    )

    private suspend fun addFeed(item: FeedItem) {
        val ref = db.collection("feed").document()
        val saved = item.copy(id = ref.id, userId = uid(), userName = myName(), createdAt = System.currentTimeMillis(), likeCount = 0)
        ref.set(saved).await()
        feed.value = listOf(saved) + feed.value
    }

    override suspend fun reviews(dishId: String): List<Rating> {
        val blocked = profile.value?.blocked.orEmpty()
        return db.collection("ratings").whereEqualTo("dishId", dishId).get().await()
            .documents.mapNotNull { it.toObject(Rating::class.java) }
            .filter { it.text.isNotBlank() && it.userId !in blocked }
            .sortedByDescending { it.updatedAt }
    }

    override suspend fun flagDish(dish: Dish, reason: String) {
        db.collection("flags").add(
            mapOf("dishId" to dish.id, "placeId" to dish.placeId, "reason" to reason, "by" to uid(), "at" to System.currentTimeMillis()),
        ).await()
    }

    override suspend fun reportReview(review: Rating, reason: String) {
        db.collection("reports").add(
            mapOf(
                "dishId" to review.dishId, "reviewUserId" to review.userId, "reason" to reason,
                "by" to uid(), "at" to System.currentTimeMillis(),
            ),
        ).await()
    }

    override suspend fun block(userId: String) {
        val p = profile.value ?: Profile()
        saveProfile(p.copy(blocked = (p.blocked + userId).distinct()))
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun readMenu(imageJpeg: ByteArray?, url: String?): List<DraftDish> {
        val data = hashMapOf<String, Any>()
        if (imageJpeg != null) data["image"] = Base64.encodeToString(imageJpeg, Base64.NO_WRAP)
        if (url != null) data["url"] = url
        val result = Firebase.functions.getHttpsCallable("readMenu").call(data).await()
        val root = result.getData() as? Map<String, Any?> ?: return emptyList()
        val list = root["dishes"] as? List<Map<String, Any?>> ?: return emptyList()
        return list.map { m ->
            val cats = (m["categories"] as? List<String>).orEmpty().map { it.lowercase() }.filter { it in Categories.ALL }
            val allergens = (m["allergens"] as? List<String>).orEmpty().filter { it in Categories.ALLERGIES }
            val price = (m["price"] as? String).orEmpty()
            val tags = (m["tags"] as? List<String>).orEmpty().map { it.lowercase() }.filter { it in Categories.TAGS }
            DraftDish(
                tags = tags.toSet(),
                name = (m["name"] as? String).orEmpty(),
                price = price,
                categories = cats.toSet().ifEmpty { setOf("other") },
                allergens = allergens,
                note = when {
                    allergens.isNotEmpty() -> "Menu lists: " + allergens.joinToString(", ") { it.lowercase() }
                    m["ingredientsUnknown"] == true -> "Ingredients not listed"
                    price.isEmpty() -> "Price not printed"
                    else -> "Price read from menu"
                },
            )
        }.filter { it.name.isNotBlank() }
    }
}
