package com.example.squadlink.model

import com.google.android.gms.maps.model.LatLng

data class AirsoftField(
    val id: String,
    val name: String,
    val perimeter: List<List<LatLng>>,
    val center: LatLng,
    val defaultZoom: Float = 17f
)
