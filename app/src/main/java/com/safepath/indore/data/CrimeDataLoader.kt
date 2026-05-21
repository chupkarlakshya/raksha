package com.safepath.indore.data

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Loads `crime_data.csv` from assets/ and parses it into [CrimePoint]s.
 * The CSV is small (~2k rows) so we keep everything in memory.
 *
 * Expected header:
 *   timestamp,act379,act13,act279,act323,act363,act302,latitude,longitude
 */
object CrimeDataLoader {

    private const val TAG = "CrimeDataLoader"
    private val ASSET_NAMES = listOf("crime_data.csv", "bhopal.csv")

    @Volatile
    private var cache: List<CrimePoint>? = null

    fun load(context: Context): List<CrimePoint> {
        cache?.let { return it }
        synchronized(this) {
            cache?.let { return it }
            val out = parse(context)
            cache = out
            return out
        }
    }

    private fun parse(context: Context): List<CrimePoint> {
        val results = ArrayList<CrimePoint>(4000)
        for (assetName in ASSET_NAMES) {
            try {
                context.assets.open(assetName).use { stream ->
                    BufferedReader(InputStreamReader(stream)).use { reader ->
                        val header = reader.readLine() ?: return@use
                        val cols = header.split(",").map { it.trim() }
                        val idxTs = cols.indexOf("timestamp")
                        val idxAct379 = cols.indexOf("act379")
                        val idxAct323 = cols.indexOf("act323")
                        val idxAct363 = cols.indexOf("act363")
                        val idxAct302 = cols.indexOf("act302")
                        val idxLat = if (cols.indexOf("latitude") >= 0) cols.indexOf("latitude") else cols.indexOf("lat")
                        val idxLng = if (cols.indexOf("longitude") >= 0) cols.indexOf("longitude") else cols.indexOf("lng")

                        var line = reader.readLine()
                        while (line != null) {
                            val parts = line.split(",")
                            if (parts.size >= cols.size) {
                                val lat = if (idxLat >= 0) parts.getOrNull(idxLat)?.toDoubleOrNull() else null
                                val lng = if (idxLng >= 0) parts.getOrNull(idxLng)?.toDoubleOrNull() else null
                                if (lat != null && lng != null) {
                                    val ts = if (idxTs >= 0) parts.getOrNull(idxTs) else null
                                    val hour = ts?.let { parseHour(it) } ?: 12
                                    results += CrimePoint(
                                        latitude = lat,
                                        longitude = lng,
                                        hour = hour,
                                        act302 = if (idxAct302 >= 0) parts.getOrNull(idxAct302)?.toIntOrNull() ?: 0 else 0,
                                        act363 = if (idxAct363 >= 0) parts.getOrNull(idxAct363)?.toIntOrNull() ?: 0 else 0,
                                        act323 = if (idxAct323 >= 0) parts.getOrNull(idxAct323)?.toIntOrNull() ?: 0 else 0,
                                        act379 = if (idxAct379 >= 0) parts.getOrNull(idxAct379)?.toIntOrNull() ?: 0 else 0
                                    )
                                }
                            }
                            line = reader.readLine()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load $assetName", e)
            }
        }
        Log.i(TAG, "Loaded ${results.size} crime points")
        return results
    }

    /** Pulls the hour from "DD-MM-YYYY HH:MM" — falls back to 12 if it can't. */
    private fun parseHour(timestamp: String): Int {
        val space = timestamp.indexOf(' ')
        if (space < 0) return 12
        val time = timestamp.substring(space + 1)
        val colon = time.indexOf(':')
        if (colon < 0) return 12
        return time.substring(0, colon).trim().toIntOrNull() ?: 12
    }
}
