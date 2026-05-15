package com.safepath.indore.utils

import android.content.Context
import android.location.Geocoder as AndroidGeocoder
import com.google.android.gms.maps.model.LatLng
import java.util.Locale

/**
 * Hybrid Geocoder:
 * 1. Checks hardcoded "landmarks" first for instant demo response.
 * 2. Falls back to Android System Geocoder for any address in Indore.
 */
object Geocoder {

    private val landmarks = linkedMapOf(
        "rajwada" to LatLng(22.7196, 75.8577),
        "vijay nagar" to LatLng(22.7530, 75.8930),
        "palasia" to LatLng(22.7430, 75.8850),
        "geeta bhavan" to LatLng(22.7330, 75.8740),
        "bhawarkuan" to LatLng(22.7050, 75.8700),
        "sapna sangeeta" to LatLng(22.7280, 75.8700),
        "indore airport" to LatLng(22.7218, 75.8011),
        "iim indore" to LatLng(22.6898, 75.8525),
        "iit indore" to LatLng(22.5210, 75.9215),
        "saket" to LatLng(22.7260, 75.8950),
        "lig square" to LatLng(22.7500, 75.8750),
        "rau" to LatLng(22.6450, 75.8120),
        "navlakha" to LatLng(22.7080, 75.8780),
        "scheme 78" to LatLng(22.7700, 75.8930),
        "bombay hospital" to LatLng(22.7610, 75.8820),
        "khajrana" to LatLng(22.7460, 75.9030)
    )

    fun knownDestinations(): List<String> = landmarks.keys.toList()

    /**
     * Attempts to find coordinates for a query.
     * Note: Android's Geocoder.getFromLocationName is a blocking network call.
     * In a production app, this should be wrapped in a Coroutine.
     */
    fun geocode(context: Context, query: String): LatLng? {
        val key = query.trim().lowercase()
        if (key.isEmpty()) return null
        
        // 1. Check Landmark Cache (Instant)
        landmarks[key]?.let { return it }
        val looseMatch = landmarks.entries.firstOrNull { (k, _) -> k.contains(key) || key.contains(k) }
        if (looseMatch != null) return looseMatch.value

        // 2. Fallback to System Geocoder (Indore biased)
        return try {
            val geocoder = AndroidGeocoder(context, Locale.getDefault())
            // Appending "Indore" to ensure local results
            val addresses = geocoder.getFromLocationName("$query, Indore, Madhya Pradesh", 1)
            if (!addresses.isNullOrEmpty()) {
                LatLng(addresses[0].latitude, addresses[0].longitude)
            } else null
        } catch (e: Exception) {
            null
        }
    }
    
    fun getPossibleMatches(context: Context, query: String): List<Pair<String, LatLng>> {
        val key = query.trim().lowercase()
        if (key.isEmpty()) return emptyList()

        val results = mutableListOf<Pair<String, LatLng>>()

        // 1. Landmarks matches
        landmarks.forEach { (name, pos) ->
            if (name.contains(key) || key.contains(name)) {
                results.add(name.replaceFirstChar { it.uppercase() } to pos)
            }
        }

        // 2. System matches
        try {
            val geocoder = AndroidGeocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocationName("$query, Indore, Madhya Pradesh", 5)
            addresses?.forEach { addr ->
                val name = addr.thoroughfare ?: addr.featureName ?: addr.subLocality ?: query
                val latLng = LatLng(addr.latitude, addr.longitude)
                // Avoid duplicates
                if (results.none { GeoUtils.distanceMeters(it.second, latLng) < 100 }) {
                    results.add(name to latLng)
                }
            }
        } catch (e: Exception) {}

        return results
    }

    /**
     * Translates coordinates back to a readable location label.
     */
    fun reverseGeocode(context: Context, location: LatLng): String? {
        // Simple heuristic for landmarks
        for ((name, pos) in landmarks) {
            if (GeoUtils.distanceMeters(location, pos) < 300) {
                return name.replaceFirstChar { it.uppercase() }
            }
        }

        return try {
            val geocoder = AndroidGeocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
            if (!addresses.isNullOrEmpty()) {
                val addr = addresses[0]
                addr.thoroughfare ?: addr.featureName ?: addr.subLocality ?: "Indore Central"
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private val client = okhttp3.OkHttpClient()
    private const val MAPS_KEY = "AIzaSyA03tc8_xkPHr9r9ow4K2xlMbHrzl3R5w8"

    fun getGoogleSuggestions(query: String, callback: (List<Pair<String, String>>) -> Unit) {
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val url = "https://maps.googleapis.com/maps/api/place/autocomplete/json" +
                "?input=$encodedQuery" +
                "&location=22.7196,75.8577&radius=10000" +
                "&key=$MAPS_KEY"

        val request = okhttp3.Request.Builder().url(url).build()
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                callback(emptyList())
            }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                val body = response.body?.string() ?: ""
                val results = mutableListOf<Pair<String, String>>()
                try {
                    val json = org.json.JSONObject(body)
                    val predictions = json.getJSONArray("predictions")
                    for (i in 0 until predictions.length()) {
                        val p = predictions.getJSONObject(i)
                        results.add(p.getString("description") to p.getString("place_id"))
                    }
                } catch (e: Exception) {}
                callback(results)
            }
        })
    }

    fun getPlaceDetails(placeId: String, callback: (LatLng?) -> Unit) {
        val url = "https://maps.googleapis.com/maps/api/place/details/json" +
                "?place_id=$placeId&fields=geometry&key=$MAPS_KEY"

        val request = okhttp3.Request.Builder().url(url).build()
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                callback(null)
            }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                val body = response.body?.string() ?: ""
                try {
                    val json = org.json.JSONObject(body)
                    val loc = json.getJSONObject("result").getJSONObject("geometry").getJSONObject("location")
                    callback(LatLng(loc.getDouble("lat"), loc.getDouble("lng")))
                } catch (e: Exception) {
                    callback(null)
                }
            }
        })
    }
}
