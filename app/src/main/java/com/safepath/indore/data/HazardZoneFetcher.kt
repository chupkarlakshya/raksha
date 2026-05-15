package com.safepath.indore.data

import android.util.Log
import com.safepath.indore.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.URL

object HazardZoneFetcher {
    private const val TAG = "HazardZoneFetcher"

    suspend fun fetchActiveZones(): List<HazardZone> = withContext(Dispatchers.IO) {
        try {
            val url = URL("${BuildConfig.SAFEPATH_API_URL}/api/hazard-zones?active=true")
            val json = url.readText()
            val array = JSONArray(json)
            (0 until array.length()).map {
                val obj = array.getJSONObject(it)
                HazardZone(
                    id = obj.getInt("id"),
                    lat = obj.getDouble("lat"),
                    lng = obj.getDouble("lng"),
                    radiusM = obj.getInt("radius_m"),
                    risk = obj.getDouble("risk")
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch hazard zones from ${BuildConfig.SAFEPATH_API_URL}", e)
            emptyList()
        }
    }
}
