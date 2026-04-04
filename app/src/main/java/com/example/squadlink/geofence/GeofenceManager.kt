package com.example.squadlink.geofence

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.squadlink.model.AirsoftField
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import kotlin.math.*

class GeofenceManager(private val context: Context) {

    private val geofencingClient: GeofencingClient =
        LocationServices.getGeofencingClient(context)

    private val pendingIntent: PendingIntent by lazy {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
        PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun registerFieldGeofence(field: AirsoftField) {
        val radius = computeRadiusMeters(field.center, field.perimeter)
            .coerceAtLeast(40.0)
            .coerceAtMost(3000.0)

        val geofence = Geofence.Builder()
            .setRequestId(field.id)
            .setCircularRegion(field.center.latitude, field.center.longitude, radius.toFloat())
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_EXIT or Geofence.GEOFENCE_TRANSITION_ENTER)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .build()

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()

        geofencingClient.addGeofences(request, pendingIntent)
            .addOnSuccessListener { Log.d("GeofenceManager", "Geofence registered") }
            .addOnFailureListener { Log.e("GeofenceManager", "Geofence failed: ${it.message}") }
    }

    fun clearGeofences() {
        geofencingClient.removeGeofences(pendingIntent)
            .addOnSuccessListener { Log.d("GeofenceManager", "Geofences cleared") }
            .addOnFailureListener { Log.e("GeofenceManager", "Clear failed: ${it.message}") }
    }
}

private fun computeRadiusMeters(center: LatLng, polygons: List<List<LatLng>>): Double {
    var max = 0.0
    polygons.flatten().forEach { point ->
        val d = haversineMeters(center, point)
        if (d > max) max = d
    }
    return max
}

private fun haversineMeters(a: LatLng, b: LatLng): Double {
    val r = 6371000.0
    val dLat = Math.toRadians(b.latitude - a.latitude)
    val dLon = Math.toRadians(b.longitude - a.longitude)
    val lat1 = Math.toRadians(a.latitude)
    val lat2 = Math.toRadians(b.latitude)
    val sinDLat = sin(dLat / 2)
    val sinDLon = sin(dLon / 2)
    val c = 2 * asin(sqrt(sinDLat * sinDLat + cos(lat1) * cos(lat2) * sinDLon * sinDLon))
    return r * c
}
