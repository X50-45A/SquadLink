package com.example.squadlink.model

data class AirsoftField(
    val id: String,
    val name: String,
    val perimeter: List<GeoPoint>,
    val center: GeoPoint,
    val defaultZoom: Float = 17f
)
