package com.example.squadlink.util

import com.google.android.gms.maps.model.LatLng

/**
 * Ray-casting algorithm to check if a point is inside a polygon.
 * Works correctly for convex and concave field perimeters.
 */
fun isInsideGeofence(point: LatLng, polygon: List<LatLng>): Boolean {
    if (polygon.size < 3) return false
    var inside = false
    var j = polygon.lastIndex
    for (i in polygon.indices) {
        val xi = polygon[i].longitude; val yi = polygon[i].latitude
        val xj = polygon[j].longitude; val yj = polygon[j].latitude
        val intersect = (yi > point.latitude) != (yj > point.latitude) &&
                point.longitude < (xj - xi) * (point.latitude - yi) / (yj - yi) + xi
        if (intersect) inside = !inside
        j = i
    }
    return inside
}