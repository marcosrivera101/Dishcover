package app.godishcover.data

import android.util.Base64
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import com.google.firebase.functions.functions
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
    override val isDemo = false
    private val auth = Firebase.auth
    private val db = Firebase.firestore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override val user = MutableStateFlow<User?>(null)
    override val profile = MutableStateFlow<Profile?>(null)
    override val places = MutableStateFlow<List<Place>>(emptyList())
    override val dishes = MutableStateFlow<List<Dish>>(emptyList())
    override val myRatings = MutableStateFlow<Map<String, Rating>>(emptyMap())
    private var lastCenter: LatLng? = null

    init {
        auth.addAuthStateListener { a: FirebaseAuth ->
            val fu = a.currentUser
            user.value = fu?.let { User(it.uid, it.email ?: "", it.displayName ?: "") }
            if (fu == null) {
                profile.value = null
                myRatings.value = emptyMap()
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
        db.collection("users").document(me).delete().await()
        auth.currentUser?.delete()?.await()
    }

    private fun uid(): String = auth.currentUser?.uid ?: error("Not signed in")

    override suspend fun saveProfile(profile: Profile) {
        db.collection("users").document(uid()).set(profile).await()
        this.profile.value = profile
    }

    override suspend fun loadNearby(center: LatLng) {
        lastCenter = center
        val cells = Geo.nearbyCells(center)
        val ps = db.collection("places").whereIn("gh4", cells).get().await()
            .documents.mapNotNull { d -> d.toObject(Place::class.java)?.also { it.id = d.id } }
        val ds = db.collection("dishes").whereIn("gh4", cells).get().await()
            .documents.mapNotNull { d -> d.toObject(Dish::class.java)?.also { it.id = d.id } }
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
        )
        ref.set(p).await()
        mergeLocal(p)
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
                price = it.price.take(20), allergens = it.allergens, createdBy = me,
                createdByName = profile.value?.publicName ?: "", createdAt = now, confirmedAt = now,
            )
            batch.set(ref, d)
            d
        }
        batch.commit().await()
        dishes.value = dishes.value + new
        return new.size
    }

    override suspend fun updateDish(dish: Dish) {
        db.collection("dishes").document(dish.id).set(
            mapOf(
                "name" to dish.name, "description" to dish.description, "categories" to dish.categories,
                "price" to dish.price, "allergens" to dish.allergens, "confirmedAt" to System.currentTimeMillis(),
            ),
            SetOptions.merge(),
        ).await()
        dishes.value = dishes.value.map { if (it.id == dish.id) dish else it }
    }

    override suspend fun deleteDish(dish: Dish) {
        db.collection("dishes").document(dish.id).delete().await()
        dishes.value = dishes.value.filter { it.id != dish.id }
    }

    override suspend fun rate(dish: Dish, stars: Int, text: String) {
        val me = uid()
        val dishRef = db.collection("dishes").document(dish.id)
        val ratingRef = db.collection("ratings").document("${dish.id}_$me")
        val r = Rating(dish.id, me, profile.value?.publicName ?: "", stars.coerceIn(1, 5), text.take(500), System.currentTimeMillis())
        db.runTransaction { tx ->
            val old = tx.get(ratingRef).toObject(Rating::class.java)
            tx.set(ratingRef, r)
            tx.update(
                dishRef,
                mapOf(
                    "ratingSum" to FieldValue.increment((r.stars - (old?.stars ?: 0)).toLong()),
                    "ratingCount" to FieldValue.increment(if (old == null) 1L else 0L),
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
            )
        }
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
        val root = result.data as? Map<String, Any?> ?: return emptyList()
        val list = root["dishes"] as? List<Map<String, Any?>> ?: return emptyList()
        return list.map { m ->
            val cats = (m["categories"] as? List<String>).orEmpty().map { it.lowercase() }.filter { it in Categories.ALL }
            val allergens = (m["allergens"] as? List<String>).orEmpty().filter { it in Categories.ALLERGIES }
            val price = (m["price"] as? String).orEmpty()
            DraftDish(
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
