package com.safepath.indore.utils

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.safepath.indore.data.HazardZone
import com.safepath.indore.receivers.GeofenceAlertReceiver

class GeofenceManager(private val context: Context) {

    private val geofencingClient: GeofencingClient = LocationServices.getGeofencingClient(context)

    private val pendingIntent: PendingIntent by lazy {
        val intent = Intent(context, GeofenceAlertReceiver::class.java)
        PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    @SuppressLint("MissingPermission")
    fun updateGeofences(hazards: List<HazardZone>) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) return

        val geofences = hazards.map { hz ->
            Geofence.Builder()
                .setRequestId("hz_${hz.id}")
                .setCircularRegion(hz.lat, hz.lng, hz.radiusM.toFloat())
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
                .build()
        }

        geofencingClient.removeGeofences(pendingIntent).addOnCompleteListener {
            if (geofences.isNotEmpty()) {
                val request = GeofencingRequest.Builder().apply {
                    setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                    geofences.forEach { addGeofence(it) }
                }.build()

                geofencingClient.addGeofences(request, pendingIntent).run {
                    addOnSuccessListener { Log.d("GeofenceManager", "Registered ${geofences.size} zones") }
                    addOnFailureListener { Log.e("GeofenceManager", "Failed", it) }
                }
            } else {
                Log.d("GeofenceManager", "No hazard zones to register")
            }
        }
    }
}
