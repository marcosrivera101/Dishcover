package app.godishcover.data

import android.content.Context
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.CircularBounds
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FetchResolvedPhotoUriRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.api.net.SearchByTextRequest
import com.google.android.libraries.places.api.net.SearchNearbyRequest
import kotlinx.coroutines.tasks.await
import com.google.android.gms.maps.model.LatLng as GLatLng
import com.google.android.libraries.places.api.model.Place as GPlace

/** Google Places (New), limited to the cheap Pro fields from the step 1 plan. */
class PlacesSource(context: Context, apiKey: String) {
    val available = apiKey.isNotBlank()
    private val client: PlacesClient? = if (available) {
        if (!Places.isInitialized()) Places.initializeWithNewPlacesApiEnabled(context.applicationContext, apiKey)
        Places.createClient(context.applicationContext)
    } else null

    // Never add rating, hours, website, price, reviews or photos: they move calls to a pricier tier.
    private val fields = listOf(
        GPlace.Field.ID,
        GPlace.Field.DISPLAY_NAME,
        GPlace.Field.FORMATTED_ADDRESS,
        GPlace.Field.LOCATION,
        GPlace.Field.PRIMARY_TYPE,
        GPlace.Field.BUSINESS_STATUS,
    )

    private val types = listOf("restaurant", "cafe", "bakery", "bar", "meal_takeaway")

    suspend fun nearby(center: LatLng, radiusM: Double = 5000.0): List<Place> {
        val c = client ?: return emptyList()
        val req = SearchNearbyRequest.builder(CircularBounds.newInstance(GLatLng(center.lat, center.lng), radiusM), fields)
            .setIncludedTypes(types)
            .setMaxResultCount(20)
            .build()
        return c.searchNearby(req).await().places.mapNotNull { it.toPlace() }
    }

    suspend fun search(query: String, center: LatLng?): List<Place> {
        val c = client ?: return emptyList()
        val b = SearchByTextRequest.builder(query, fields).setMaxResultCount(20)
        if (center != null) b.setLocationBias(CircularBounds.newInstance(GLatLng(center.lat, center.lng), 20000.0))
        return c.searchByText(b.build()).await().places.mapNotNull { it.toPlace() }
    }

    private fun GPlace.toPlace(): Place? {
        val loc = location ?: return null
        val pid = id ?: return null
        if (businessStatus == GPlace.BusinessStatus.CLOSED_PERMANENTLY) return null
        return Place(
            name = displayName ?: "",
            address = formattedAddress ?: "",
            lat = loc.latitude,
            lng = loc.longitude,
            gh4 = Geo.geohash(loc.latitude, loc.longitude),
            googlePlaceId = pid,
        )
    }

    /**
     * A small Google photo of the place. Finding the ID and the photo list are the free
     * "IDs only" calls; only the photo itself is billed, so results are kept for the session.
     */
    suspend fun photoUri(p: Place, maxPx: Int = 240): String? {
        val c = client ?: return null
        val key = p.googlePlaceId ?: (p.name + "@" + p.lat + "," + p.lng)
        photoCache[key]?.let { return it.ifEmpty { null } }
        val uri = runCatching {
            val id = p.googlePlaceId ?: run {
                val req = SearchByTextRequest.builder(p.name, listOf(GPlace.Field.ID))
                    .setMaxResultCount(1)
                    .setLocationBias(CircularBounds.newInstance(GLatLng(p.lat, p.lng), 300.0))
                    .build()
                c.searchByText(req).await().places.firstOrNull()?.id
            } ?: return@runCatching null
            val meta = c.fetchPlace(FetchPlaceRequest.newInstance(id, listOf(GPlace.Field.PHOTO_METADATAS))).await()
                .place.photoMetadatas?.firstOrNull() ?: return@runCatching null
            c.fetchResolvedPhotoUri(FetchResolvedPhotoUriRequest.builder(meta).setMaxWidth(maxPx).setMaxHeight(maxPx).build())
                .await().uri?.toString()
        }.getOrNull()
        photoCache[key] = uri ?: ""
        return uri
    }

    private val photoCache = java.util.concurrent.ConcurrentHashMap<String, String>()
}
