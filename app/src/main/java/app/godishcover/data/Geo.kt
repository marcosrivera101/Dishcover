package app.godishcover.data

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object Geo {
    private const val BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz"

    fun geohash(lat: Double, lng: Double, precision: Int = 4): String {
        var latMin = -90.0; var latMax = 90.0
        var lngMin = -180.0; var lngMax = 180.0
        val sb = StringBuilder()
        var bit = 0; var ch = 0; var even = true
        while (sb.length < precision) {
            if (even) {
                val mid = (lngMin + lngMax) / 2
                if (lng >= mid) { ch = ch or (1 shl (4 - bit)); lngMin = mid } else lngMax = mid
            } else {
                val mid = (latMin + latMax) / 2
                if (lat >= mid) { ch = ch or (1 shl (4 - bit)); latMin = mid } else latMax = mid
            }
            even = !even
            if (bit < 4) bit++ else { sb.append(BASE32[ch]); bit = 0; ch = 0 }
        }
        return sb.toString()
    }

    /** The precision-4 cell around [p] plus its 8 neighbors (about 40 x 20 km each). */
    fun nearbyCells(p: LatLng): List<String> {
        val dLat = 180.0 / 2.0.pow(10) // 10 latitude bits at precision 4
        val dLng = 360.0 / 2.0.pow(10) // 10 longitude bits at precision 4
        val cells = LinkedHashSet<String>()
        for (i in -1..1) for (j in -1..1) {
            val lat = (p.lat + i * dLat).coerceIn(-89.999, 89.999)
            var lng = p.lng + j * dLng
            if (lng > 180) lng -= 360
            if (lng < -180) lng += 360
            cells += geohash(lat, lng, 4)
        }
        return cells.toList()
    }

    fun distanceKm(a: LatLng, b: LatLng): Double {
        val r = 6371.0
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val h = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLng / 2).pow(2)
        return 2 * r * asin(sqrt(h))
    }

    fun miles(km: Double): String {
        val mi = km * 0.621371
        return if (mi < 10) String.format("%.1f mi", mi) else "${mi.toInt()} mi"
    }
}
