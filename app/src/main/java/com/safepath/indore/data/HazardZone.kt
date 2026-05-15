package com.safepath.indore.data

data class HazardZone(
    val id: Int,
    val lat: Double,
    val lng: Double,
    val radiusM: Int,
    val risk: Double
)
