package com.example.squadlink.data

import com.example.squadlink.model.AirsoftField
import com.google.android.gms.maps.model.LatLng

object FieldRepository {
    val fields: List<AirsoftField> = listOf(
        AirsoftField(
            id = "cqb_los_barracones_camp1",
            name = "CQB Los Barracones - Campo 1",
            perimeter = listOf(
                listOf(
                    LatLng(41.2987916, 1.7312769),
                    LatLng(41.2980702, 1.7323605),
                    LatLng(41.2981629, 1.7337445),
                    LatLng(41.2980944, 1.7347745),
                    LatLng(41.2980581, 1.7355148),
                    LatLng(41.2985619, 1.7355416),
                    LatLng(41.2991785, 1.7354879),
                    LatLng(41.2994284, 1.7337284),
                    LatLng(41.2994646, 1.7324302),
                    LatLng(41.2994405, 1.7317811),
                    LatLng(41.2990818, 1.7314861),
                    LatLng(41.2987916, 1.7312769)
                )
            ),
            center = LatLng(41.29895, 1.73360),
            defaultZoom = 16.5f
        ),
        AirsoftField(
            id = "cqb_los_barracones_camp2",
            name = "CQB Los Barracones - Campo 2",
            perimeter = listOf(
                listOf(
                    LatLng(41.2992107, 1.735504),
                    LatLng(41.2985296, 1.7355899),
                    LatLng(41.298042, 1.7355523),
                    LatLng(41.2978888, 1.7367003),
                    LatLng(41.2975664, 1.7371938),
                    LatLng(41.29751, 1.7376981),
                    LatLng(41.2977921, 1.738256),
                    LatLng(41.2981307, 1.7385564),
                    LatLng(41.2985579, 1.738492),
                    LatLng(41.2990576, 1.7383418),
                    LatLng(41.2993558, 1.738374),
                    LatLng(41.2994767, 1.7378912),
                    LatLng(41.2994123, 1.7369149),
                    LatLng(41.2993397, 1.7361102),
                    LatLng(41.2992107, 1.735504)
                )
            ),
            center = LatLng(41.29870, 1.73705),
            defaultZoom = 16.5f
        )
    )
}
