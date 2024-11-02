package com.example.sbe.models

data class PointData(
    val location: Location,
    val userToken: String
) {
    data class Location(
        val latitude: Double,
        val longitude: Double
    )
}
